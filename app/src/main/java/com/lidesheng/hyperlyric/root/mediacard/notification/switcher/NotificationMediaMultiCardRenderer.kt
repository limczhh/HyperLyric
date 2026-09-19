package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.content.Context
import android.media.session.MediaController
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostApi
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostClasses
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaForegroundStyler
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.LinkedHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal enum class NotificationMediaMultiCardSyncResult {
    /** The native player has not been attached yet; retry after attach. */
    NOT_READY,

    /** The snapshot was applied, including the one-card/empty cleanup path. */
    SUCCESS,

    /** The native carousel is attached but the requested page set failed. */
    FAILED
}

/**
 * Renders one native HyperOS notification-media card per MediaData entry.
 *
 * The stock notification path owns one [MiuiMediaViewControllerImpl] and one
 * [MiuiMediaViewHolder]. Once the carousel is active, that original pair is
 * kept hidden as SystemUI's synchronization anchor. Every visible page gets
 * its own native controller/holder pair and its own SeekBarViewModel. This
 * prevents a native top-media rebind from replacing the currently visible
 * page or leaking its artwork into another session.
 */
internal class NotificationMediaMultiCardRenderer(
    private val layoutController: Any,
    private val templateController: Any,
    private val nativeTopKey: () -> String?,
    private val onPlayerAttached: (View, Any) -> Unit,
    private val onPlayerDetached: (View) -> Unit,
    private val onPageSelected: (String) -> Unit,
    private val onPageScrolled: (Float, Int) -> Unit,
    private val onGestureStarted: () -> Unit,
    private val onPageOrderChanged: (Int) -> Unit,
    private val onCardMediaChanged: (String) -> Unit,
    private val shouldIgnoreScrollTouch: (MotionEvent) -> Boolean
) {
    private companion object {
        const val TAG = "NotificationMediaMultiCardRenderer"
        const val MIN_FLING_VELOCITY = 400f
        // The card View is miui_media_session.xml. The similarly named
        // miui_media_session_normal.xml is a ConstraintSet resource loaded by
        // MiuiMediaNotificationControllerImpl.normalLayout, not an inflatable
        // layout resource.
        const val PLAYER_LAYOUT = "miui_media_session"
        const val SIDE_PADDING_DIMEN = "notification_side_paddings"
        const val FALLBACK_SIDE_PADDING_DP = 12f
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val SYSTEMUI_LAYOUT_CLASS = "com.android.systemui.R\$layout"
        val CONTROLLER_DEPENDENCIES = listOf(
            "context",
            "activityStarter",
            "seekBarViewModel",
            "mediaTransferManager",
            "fullAodController",
            "miPlayPluginManager",
            "mediaOutputDialogManager",
            "notificationStat",
            "miuiMediaAlbumAnimationUtils",
            "dynamicIslandController",
            "mainHandler",
            "miuiMediaActionButtonUtils",
            "miuiMediaWakeLockManager"
        )
    }

    private class Card(
        var key: String,
        var data: Any?,
        val player: View,
        val holder: Any,
        val controller: Any,
        val original: Boolean
    ) {
        var playbackObserver: NotificationMediaPlaybackObserver? = null
        var fullAodRestoreCaptured = false
        var fullAodRestoreVisibility: List<Pair<View, Int>> = emptyList()
        var normalMediaBgHeight: Int? = null
    }

    /**
     * The MIUI14 notification carousel is a real HorizontalScrollView. Keep
     * the child card's dispatch hook for the seek bar and let this view own
     * horizontal motion, then snap to a child on release.
     */
    private class PageScrollView(
        context: Context,
        private val shouldIgnoreTouch: (MotionEvent) -> Boolean,
        private val onScrollPositionChanged: (Int) -> Unit,
        private val onGestureReleased: (Float) -> Unit,
        private val onGestureStarted: () -> Unit
    ) : HorizontalScrollView(context) {
        private var velocityTracker: VelocityTracker? = null
        private var ignoredGesture = false
        private var handlingGesture = false

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    finishGesture(snap = false)
                    ignoredGesture = shouldIgnoreTouch(event)
                    if (!ignoredGesture) {
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)
                    }
                }

                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (!ignoredGesture) velocityTracker?.addMovement(event)
                }
            }

            if (ignoredGesture) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    finishGesture(snap = false)
                }
                return false
            }

            val intercepted = super.onInterceptTouchEvent(event)
            if (intercepted) {
                if (!handlingGesture) {
                    onGestureStarted()
                }
                handlingGesture = true
                // Start at the header, not at this view. Calling
                // requestDisallowInterceptTouchEvent on this view itself
                // would set HorizontalScrollView's own disallow flag and
                // prevent it from intercepting the child on the next MOVE.
                parent?.requestDisallowInterceptTouchEvent(true)
            } else if ((event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL) &&
                !handlingGesture
            ) {
                finishGesture(snap = false)
            }
            return intercepted
        }

        /**
         * HorizontalScrollView starts its own OverScroller fling from
         * super.onTouchEvent(ACTION_UP). The renderer computes the target page
         * itself, so allowing both fling implementations produces a visible
         * indicator 2 -> 1 -> 2 rebound.
         */
        override fun fling(velocityX: Int) = Unit

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (ignoredGesture) return false
            if (event.actionMasked == MotionEvent.ACTION_DOWN &&
                velocityTracker == null
            ) {
                velocityTracker = VelocityTracker.obtain()
            }
            velocityTracker?.addMovement(event)
            val handled = super.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                finishGesture(snap = true)
            }
            return handled
        }

        override fun onScrollChanged(
            scrollX: Int,
            scrollY: Int,
            oldScrollX: Int,
            oldScrollY: Int
        ) {
            super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
            onScrollPositionChanged(scrollX)
        }

        private fun finishGesture(snap: Boolean) {
            if (snap && handlingGesture) {
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                onGestureReleased(tracker?.xVelocity ?: 0f)
            }
            parent?.requestDisallowInterceptTouchEvent(false)
            velocityTracker?.recycle()
            velocityTracker = null
            ignoredGesture = false
            handlingGesture = false
        }
    }

    private val controllerClass = templateController.javaClass
    private val holderClassLoader = controllerClass.classLoader
    private val bindMethod = findMethod(controllerClass, "bindMediaData") {
        it.parameterCount == 1
    }
    private val attachMethod = findMethod(controllerClass, "attach") {
        it.parameterCount == 1
    }
    private val detachMethod = findMethod(controllerClass, "detach") {
        it.parameterCount == 0
    }
    private val updateMediaBackgroundMethod = findMethod(
        controllerClass,
        "updateMediaBackground"
    ) {
        it.parameterCount == 0
    }
    private val updateForegroundColorsMethod = findMethod(
        controllerClass,
        "updateForegroundColors"
    ) {
        it.parameterCount == 0
    }

    private var holderConstructor: Constructor<*>? = null
    private var controllerConstructor: Constructor<*>? = null
    private var seekBarConstructor: Constructor<*>? = null
    private var layoutId: Int? = null

    private var header: ViewGroup? = null
    private var scrollView: PageScrollView? = null
    private var pageContainer: LinearLayout? = null
    private var originalLayoutParams: FrameLayout.LayoutParams? = null
    private var originalCard: Card? = null
    private var pageWidthPx = 0
    private var sidePaddingPx = 0
    private var pageGapPx = 0
    private var compactAodActive = false
    private var pageOrderGeneration = 0
    private var originalVisibility = View.VISIBLE
    private var originalAlpha = 1f
    private var hostLayoutChangeListener: View.OnLayoutChangeListener? = null
    private var originalHostClipChildren: Boolean? = null
    private var originalHostClipToPadding: Boolean? = null
    private var clipHost: ViewGroup? = null
    private var originalParentClipChildren: Boolean? = null
    private var originalParentClipToPadding: Boolean? = null
    private var clipParent: ViewGroup? = null
    private val nativeHostApi: NotificationMediaHostApi? = runCatching {
        templateController.javaClass.classLoader?.let(NotificationMediaHostApi::create)
    }.getOrNull()
    private val cards = LinkedHashMap<String, Card>()

    val isActive: Boolean
        get() = scrollView != null && pageContainer != null && originalCard != null

    /**
     * Identifies the current child order. Delayed scroll callbacks from an old
     * order must not overwrite the indicator after MediaSortUtils promoted a
     * different session to page zero.
     */
    val currentPageOrderGeneration: Int
        get() = pageOrderGeneration

    val pageCount: Int
        get() = cards.size

    fun foregroundColor(index: Int): Int? {
        val card = cards.values.elementAtOrNull(index) ?: return null
        return NotificationMediaForegroundStyler.foregroundColor(card.controller)
    }

    fun ownsController(controller: Any): Boolean {
        return originalCard?.controller === controller ||
            cards.values.any { it.controller === controller }
    }

    fun extraCardControllers(): Set<Any> = cards.values
        .asSequence()
        .filterNot { it.original }
        .map { it.controller }
        .toSet()

    fun attachOriginal(player: View, holder: Any): Boolean {
        if (originalCard != null) return true
        val parent = player.parent as? ViewGroup ?: return false
        header = parent
        originalCard = Card(
            key = nativeTopKey().orEmpty(),
            data = null,
            player = player,
            holder = holder,
            controller = templateController,
            original = true
        )
        return true
    }

    /**
     * Synchronizes the page list with the coordinator snapshot. Not-ready is a
     * lifecycle state, not a creation failure: MediaData callbacks can arrive
     * before the native player has been attached to its header.
     */
    fun sync(
        entries: List<Pair<String, Any>>,
        selectedIndex: Int,
        forceRebindKeys: Set<String> = emptySet()
    ): NotificationMediaMultiCardSyncResult {
        originalCard ?: return NotificationMediaMultiCardSyncResult.NOT_READY
        if (entries.size < 2) {
            if (isActive) disableMultiView()
            return NotificationMediaMultiCardSyncResult.SUCCESS
        }
        if (!isActive && !ensureContainer()) {
            return NotificationMediaMultiCardSyncResult.FAILED
        }

        val oldCards = cards.values.toList()
        val oldOrder = oldCards.map { it.key }
        val visualAnchor = captureVisualAnchor(oldCards)
        val oldByKey = oldCards.associateBy { it.key }
        val nextCards = LinkedHashMap<String, Card>()
        val createdCards = mutableListOf<Card>()

        try {
            entries.forEach { (key, data) ->
                val existing = oldByKey[key]
                if (existing != null) {
                    val dataChanged = existing.data !== data
                    existing.data = data
                    if (dataChanged || key in forceRebindKeys) {
                        bind(existing, data)
                    }
                    nextCards[key] = existing
                } else {
                    val created = createCard(key, data)
                        ?: error("无法创建媒体卡片: key=$key")
                    createdCards += created
                    nextCards[key] = created
                }
            }
        } catch (error: Throwable) {
            createdCards.forEach(::destroyExtraCard)
            disableMultiView()
            warn("创建多媒体卡片失败，多卡片视图不可用", error)
            return NotificationMediaMultiCardSyncResult.FAILED
        }

        oldCards.filter { old -> old !in nextCards.values }
            .forEach(::destroyExtraCard)

        cards.clear()
        cards.putAll(nextCards)
        val nextOrder = nextCards.keys.toList()
        val viewOrderChanged = oldCards.map { it.player } !=
            nextCards.values.map { it.player }
        val orderChanged = oldOrder != nextOrder || viewOrderChanged
        if (orderChanged) {
            rebuildPageOrder()
            val anchor = visualAnchor?.takeIf { it.key in nextCards }
            if (anchor != null) {
                restoreVisualAnchor(anchor, selectedIndex)
            } else {
                // Invalidate a previously posted anchor restore when the
                // anchor itself was removed by this update.
                pageOrderGeneration++
                onPageOrderChanged(pageOrderGeneration)
                scrollToPage(selectedIndex, animate = false)
            }
        } else {
            // Metadata/action updates must not tear down the child Views or
            // reset an in-progress native scroll animation. The individual
            // controller was already rebound above; only refresh the
            // fractional indicator from the existing scrollX.
            updatePageWidths()
            onScrollPositionChanged(scrollView?.scrollX ?: 0)
        }
        if (compactAodActive) applyAodPresentation()
        return NotificationMediaMultiCardSyncResult.SUCCESS
    }

    private data class VisualAnchor(
        val key: String,
        val screenLeft: Int
    )

    /**
     * The native top MediaData may change the logical order while the user is
     * looking at a secondary page. Preserve that page's screen coordinate;
     * only its logical index and the indicator should change.
     */
    private fun captureVisualAnchor(oldCards: List<Card>): VisualAnchor? {
        val scroller = scrollView ?: return null
        if (oldCards.isEmpty()) return null
        val oldIndex = pageLocation(scroller.scrollX)
            .roundToInt()
            .coerceIn(0, oldCards.lastIndex)
        val card = oldCards.getOrNull(oldIndex) ?: return null
        return VisualAnchor(
            key = card.key,
            screenLeft = card.player.left - scroller.scrollX
        )
    }

    private fun restoreVisualAnchor(anchor: VisualAnchor, fallbackIndex: Int) {
        val scroller = scrollView ?: return
        val pages = pageContainer ?: return
        val generation = ++pageOrderGeneration
        // scrollTo() synchronously dispatches onScrollChanged(). Notify the
        // owner before touching scrollX, otherwise PageIndicator receives an
        // intermediate position before ControllerState can force the target.
        onPageOrderChanged(generation)
        val anchorIndex = cards.keys.indexOf(anchor.key)
        if (anchorIndex < 0 || pageWidthPx <= 0) {
            scrollToPage(fallbackIndex, animate = false)
            return
        }

        // Reordering the children schedules a layout pass. Do not wait for
        // that pass before correcting scrollX: the old scrollX would expose
        // the former page for one frame (A flashes over the selected B).
        val stride = pageWidthPx + pageGapPx
        val anchorLeft = if (isLayoutRtl()) {
            sidePaddingPx + (cards.size - 1 - anchorIndex) * stride
        } else {
            sidePaddingPx + anchorIndex * stride
        }
        val contentWidth = sidePaddingPx * 2 + cards.size * pageWidthPx +
            (cards.size - 1).coerceAtLeast(0) * pageGapPx
        val viewportWidth = scroller.width.takeIf { it > 0 }
            ?: pageWidthPx + sidePaddingPx * 2
        val maxScroll = (contentWidth - viewportWidth).coerceAtLeast(0)
        val target = (anchorLeft - anchor.screenLeft).coerceIn(0, maxScroll)
        scroller.scrollTo(target, 0)
        pages.requestLayout()

        // Child.left is reliable after layout; only defer the fractional dot
        // refresh, never the visual page position itself.
        pages.post {
            if (generation == pageOrderGeneration && scrollView === scroller) {
                onScrollPositionChanged(scroller.scrollX)
            }
        }
    }

    fun setHeaderTranslation(translation: Float) {
        scrollView?.translationX = translation
    }

    fun headerTranslation(): Float? = scrollView?.translationX

    private fun currentPageIndex(): Int {
        val count = cards.size
        if (count == 0) return -1
        return pageLocation(scrollView?.scrollX ?: 0)
            .roundToInt()
            .coerceIn(0, count - 1)
    }

    private fun isLayoutRtl(): Boolean {
        return (
            header?.layoutDirection
                ?: pageContainer?.layoutDirection
                ?: scrollView?.layoutDirection
                ?: View.LAYOUT_DIRECTION_LTR
            ) == View.LAYOUT_DIRECTION_RTL
    }

    /** Mirrors the original controller's UI-mode refresh to cloned pages. */
    fun refreshUiMode() {
        if (!isActive) return
        cards.values.filterNot { it.original }.forEach { card ->
            invokeUiModeRefresh(card, updateMediaBackgroundMethod, "背景")
            invokeUiModeRefresh(card, updateForegroundColorsMethod, "前景色")
        }
    }

    private fun invokeUiModeRefresh(card: Card, method: Method?, target: String) {
        method ?: return
        runCatching { method.invoke(card.controller) }
            .onFailure { error ->
                warn("刷新多媒体卡片${target}失败: key=${card.key}", error)
            }
    }

    /**
     * Mirrors MiuiMediaViewControllerImpl.onFullAodStateChanged for every
     * cloned page. The original controller receives this callback from
     * SystemUI, but the additional controllers are not part of the native
     * media container and therefore never receive it themselves.
     */
    fun setFullAodState(active: Boolean, keepExpanded: Boolean) {
        val compact = active && !keepExpanded
        if (compactAodActive == compact) {
            if (compact) applyAodPresentation()
            return
        }
        compactAodActive = compact
        if (!isActive) return

        applyAodPresentation()
    }

    fun detach() {
        val original = originalCard
        cards.values.filter { !it.original }.forEach(::destroyExtraCard)
        cards.clear()
        removeHostLayoutListener()

        val container = scrollView
        val restoreParams = originalLayoutParams
        if (original != null) {
            if (restoreParams != null) {
                // Keep the native anchor's original LayoutParams after the
                // carousel is removed.
                original.player.layoutParams = restoreParams
            }
            restoreOriginalVisualState()
        }
        if (container != null) {
            (container.parent as? ViewGroup)?.removeView(container)
        }
        restoreCarouselClipState()

        originalCard = null
        scrollView = null
        pageContainer = null
        originalLayoutParams = null
        pageWidthPx = 0
        sidePaddingPx = 0
        pageGapPx = 0
        compactAodActive = false
        originalVisibility = View.VISIBLE
        originalAlpha = 1f
        header = null
        pageOrderGeneration++
    }

    private fun ensureContainer(): Boolean {
        if (isActive) return true
        val host = header ?: return false
        val original = originalCard ?: return false
        if (original.player.parent !== host) {
            warn("原生媒体卡片父容器已被其他逻辑接管")
            return false
        }

        val oldLayoutParams = original.player.layoutParams
        pageWidthPx = resolveOriginalPageWidth(host, original.player, oldLayoutParams)
        sidePaddingPx = resolveSidePadding(host.context)
        pageGapPx = sidePaddingPx
        captureCarouselClipState(host)
        val restoreParams = FrameLayout.LayoutParams(
            oldLayoutParams?.width ?: ViewGroup.LayoutParams.MATCH_PARENT,
            oldLayoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (oldLayoutParams is ViewGroup.MarginLayoutParams) {
            restoreParams.setMargins(
                oldLayoutParams.leftMargin,
                oldLayoutParams.topMargin,
                oldLayoutParams.rightMargin,
                oldLayoutParams.bottomMargin
            )
        }
        if (oldLayoutParams is FrameLayout.LayoutParams) {
            restoreParams.gravity = oldLayoutParams.gravity
        }

        val context = host.context
        val scroller = PageScrollView(
            context = context,
            shouldIgnoreTouch = shouldIgnoreScrollTouch,
            onScrollPositionChanged = ::onScrollPositionChanged,
            onGestureReleased = ::onGestureReleased,
            onGestureStarted = {
                onGestureStarted()
            }
        ).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            clipToOutline = false
        }
        val pages = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        scroller.addView(
            pages,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val initialCarouselWidth = pageWidthPx.takeIf { it > 0 }
            ?.let { it + sidePaddingPx * 2 }
            ?: ViewGroup.LayoutParams.MATCH_PARENT
        val containerParams = FrameLayout.LayoutParams(
            initialCarouselWidth,
            oldLayoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (oldLayoutParams is ViewGroup.MarginLayoutParams) {
            containerParams.setMargins(
                oldLayoutParams.leftMargin - sidePaddingPx,
                oldLayoutParams.topMargin,
                oldLayoutParams.rightMargin - sidePaddingPx,
                oldLayoutParams.bottomMargin
            )
        }
        if (oldLayoutParams is FrameLayout.LayoutParams) {
            containerParams.gravity = oldLayoutParams.gravity
        }

        return runCatching {
            originalVisibility = original.player.visibility
            originalAlpha = original.player.alpha
            original.player.visibility = View.INVISIBLE
            original.player.alpha = 0f
            host.addView(scroller, 0, containerParams)
            originalLayoutParams = restoreParams
            scrollView = scroller
            pageContainer = pages
            installHostLayoutListener(host)
            updatePageWidths()
            scroller.post(::updatePageWidths)
            true
        }.onFailure { error ->
            removeHostLayoutListener()
            restoreOriginalVisualState()
            restoreCarouselClipState()
            pageWidthPx = 0
            sidePaddingPx = 0
            pageGapPx = 0
            warn("创建媒体横向容器失败", error)
        }.getOrDefault(false)
    }

    private fun createCard(key: String, data: Any): Card? {
        val pages = pageContainer ?: return null
        val context = resourceContext() ?: return null
        val player = inflatePlayer(context) ?: return null
        val holder = createHolder(player) ?: return null
        applyLoadedLayouts(player, holder)
        val controller = createController() ?: return null
        val card = Card(key, data, player, holder, controller, original = false)
        val pageParams = pageLayoutParams(player)
        return runCatching {
            pages.addView(player, pageParams)
            attachMethod?.invoke(controller, holder)
            onPlayerAttached(player, holder)
            bind(card, data, refreshArtworkIfMissing = true)
            card
        }.onFailure { error ->
            onPlayerDetached(player)
            runCatching { detachMethod?.invoke(controller) }
            (player.parent as? ViewGroup)?.removeView(player)
            warn("绑定副媒体卡片失败: key=$key", error)
        }.getOrNull()
    }

    private fun bind(
        card: Card,
        data: Any,
        refreshArtworkIfMissing: Boolean = false
    ) {
        bindMethod?.invoke(card.controller, data)
        if (refreshArtworkIfMissing) {
            refreshArtworkIfMissing(card, data)
        }
        observePlayback(card)
        copyNativeChrome(card)
        if (compactAodActive) {
            captureFullAodRestoreState(card)
            applyCardAodPresentation(card, compact = true)
        }
    }

    /**
     * A SystemUI restart can create the first cloned controller before the
     * native artwork field has reached its holder. Refresh only a newly
     * created page whose album view is still empty; normal metadata updates
     * keep the native bind timing and animation untouched.
     */
    private fun refreshArtworkIfMissing(card: Card, data: Any) {
        val api = nativeHostApi ?: return
        val holder = api.getHolder(card.controller) ?: return
        val albumImage = runCatching { api.getAlbumImage(holder) }.getOrNull() ?: return
        if (albumImage.drawable != null) return

        api.refreshArtwork(card.controller, data)
        if (albumImage.drawable == null) {
            card.player.post {
                if (card.player.parent != null) {
                    val retryHolder = api.getHolder(card.controller) ?: return@post
                    val retryImage = runCatching { api.getAlbumImage(retryHolder) }
                        .getOrNull() ?: return@post
                    if (retryImage.drawable == null) {
                        api.refreshArtwork(card.controller, data)
                    }
                }
            }
        }
    }

    private fun observePlayback(card: Card) {
        if (card.original) return
        val mediaController = readField(card.controller, "mediaController") as? MediaController
        if (mediaController == null) {
            card.playbackObserver?.clear()
            card.playbackObserver = null
            return
        }
        val observer = card.playbackObserver ?: NotificationMediaPlaybackObserver {
            if (cards[card.key] === card) onCardMediaChanged(card.key)
        }.also { card.playbackObserver = it }
        observer.bind(mediaController)
    }

    private fun destroyExtraCard(card: Card) {
        if (card.original) return
        card.playbackObserver?.clear()
        card.playbackObserver = null
        onPlayerDetached(card.player)
        runCatching { detachMethod?.invoke(card.controller) }
            .onFailure { warn("销毁副媒体控制器失败: key=${card.key}", it) }
        (card.player.parent as? ViewGroup)?.removeView(card.player)
    }

    private fun disableMultiView() {
        val original = originalCard ?: return
        cards.values.filter { !it.original }.forEach(::destroyExtraCard)
        cards.clear()

        val host = header
        val container = scrollView
        if (container != null) {
            (container.parent as? ViewGroup)?.removeView(container)
        }
        removeHostLayoutListener()
        restoreCarouselClipState()
        if (host != null && original.player.parent == null) {
            runCatching {
                host.addView(
                    original.player,
                    originalLayoutParams ?: FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }.onFailure { warn("恢复系统原生媒体卡片失败", it) }
        }
        restoreOriginalVisualState()
        scrollView = null
        pageContainer = null
        originalLayoutParams = null
        pageWidthPx = 0
        sidePaddingPx = 0
        pageGapPx = 0
        originalVisibility = View.VISIBLE
        originalAlpha = 1f
    }

    private fun restoreOriginalVisualState() {
        originalCard?.player?.let { player ->
            player.visibility = originalVisibility
            player.alpha = originalAlpha
        }
    }

    private fun rebuildPageOrder() {
        val pages = pageContainer ?: return
        val orderedCards = cards.values.toList()
        pages.removeAllViews()
        orderedCards.forEach { card ->
            pages.addView(card.player, pageLayoutParams(card.player))
        }
        updatePageWidths()
    }

    private fun pageWidth(): Int = pageWidthPx.takeIf { it > 0 }
        ?: header?.width?.takeIf { it > 0 }
        ?: ViewGroup.LayoutParams.MATCH_PARENT

    private fun pageLayoutParams(player: View): LinearLayout.LayoutParams {
        val old = player.layoutParams
        val height = old?.height?.takeIf { it != 0 }
            ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val params = LinearLayout.LayoutParams(
            pageWidth(),
            height
        )
        if (old is ViewGroup.MarginLayoutParams) {
            params.setMargins(
                0,
                old.topMargin,
                pageGapPx,
                old.bottomMargin
            )
        }
        return params
    }

    private fun applyAodPresentation() {
        val compact = compactAodActive
        cards.values.forEach { card ->
            if (compact) captureFullAodRestoreState(card)
            applyCardAodPresentation(card, compact)
        }
        // MiuiMediaHeaderView owns the outer actual-height animation during
        // Full AOD. The child LayoutParams/visibility changes above already
        // request the necessary inner layout; do not force an extra pass when
        // the native state callback is repeated with the same values.
    }

    private fun applyCardAodPresentation(card: Card, compact: Boolean) {
        // Full AOD does not use the notification controller's tinyLayout.
        // The native onFullAodStateChanged() only changes mediaBg's height and
        // hides the three bottom progress views. Do not re-apply either
        // ConstraintSet here: MiuiMediaNotificationControllerImpl only uses
        // normal/tiny layouts during its normal layout update, while the
        // Full AOD transition owns the height animation separately. Replaying
        // the normal set during that animation causes a second measurement
        // pass and briefly clips the bottom of custom layouts.
        applyMediaBackgroundHeight(card, compact)

        if (compact) {
            compactHiddenViews(card).forEach { it.visibility = View.GONE }
        } else {
            card.fullAodRestoreVisibility.forEach { (view, visibility) ->
                view.visibility = visibility
            }
            card.fullAodRestoreVisibility = emptyList()
            card.fullAodRestoreCaptured = false
        }
    }

    private fun captureFullAodRestoreState(card: Card) {
        if (card.fullAodRestoreCaptured) return
        card.fullAodRestoreVisibility = compactHiddenViews(card)
            .map { it to it.visibility }
        (readField(card.holder, "mediaBg") as? View)?.layoutParams?.let { params ->
            card.normalMediaBgHeight = params.height
        }
        card.fullAodRestoreCaptured = true
    }

    private fun compactHiddenViews(card: Card): List<View> {
        val views = mutableListOf<View>()
        (
            MediaProgressStyleHooker.replacementSeekBar(card.holder)
                ?: (readField(card.holder, "seekBar") as? View)
        )?.let(views::add)
        listOf("elapsedTimeView", "totalTimeView")
            .forEach { fieldName ->
                (readField(card.holder, fieldName) as? View)?.let(views::add)
            }
        findViewByResourceName(card.player, "media_progress_bar")?.let(views::add)
        return views.distinct()
    }

    private fun applyMediaBackgroundHeight(card: Card, compact: Boolean) {
        val mediaBg = readField(card.holder, "mediaBg") as? View ?: return
        val height = if (compact) {
            resolveFullAodHeight()
        } else {
            resolveExpandedHeight(card)
        }
        if (height <= 0) return
        val params = mediaBg.layoutParams ?: return
        if (
            params.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                params.height == height
        ) return
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = height
        mediaBg.layoutParams = params
    }

    private fun findViewByResourceName(root: View, name: String): View? {
        val id = sequenceOf(root.context.packageName, SYSTEMUI_PACKAGE)
            .map { packageName -> root.resources.getIdentifier(name, "id", packageName) }
            .firstOrNull { it != 0 }
            ?: return null
        return root.findViewById(id)
    }

    private fun resolveFullAodHeight(): Int {
        val context = resourceContext() ?: header?.context ?: return 0
        return resolveDimension(context, sequenceOf(
            "qs_media_session_height_expanded_fullAod",
            "qs_media_session_height_collapsed"
        )) ?: originalCard?.player?.measuredHeight?.takeIf { it > 0 } ?: 0
    }

    private fun resolveExpandedHeight(card: Card): Int {
        val context = resourceContext() ?: header?.context
        val resourceHeight = context?.let {
            resolveDimension(it, sequenceOf("qs_media_session_height_expanded"))
        }
        return resourceHeight
            ?: card.normalMediaBgHeight?.takeIf { it > 0 }
            ?: 0
    }

    private fun resolveDimension(context: Context, names: Sequence<String>): Int? {
        val resourceId = names
            .flatMap { name ->
                sequenceOf(context.packageName, SYSTEMUI_PACKAGE)
                    .map { packageName ->
                        context.resources.getIdentifier(name, "dimen", packageName)
                    }
            }
            .firstOrNull { it != 0 }
            ?: return null
        return runCatching { context.resources.getDimensionPixelSize(resourceId) }
            .getOrNull()
    }

    private fun updatePageWidths() {
        val pages = pageContainer ?: return
        val original = originalCard
        val host = header
        val previousWidth = pageWidthPx
        val previousSidePadding = sidePaddingPx
        val previousGap = pageGapPx
        val previousContainerHeight = scrollView?.layoutParams?.height
        val currentIndex = currentPageIndex()
        val measuredWidth = if (original != null && host != null) {
            resolveOriginalPageWidth(host, original.player, original.player.layoutParams)
        } else {
            0
        }
        val width = measuredWidth.takeIf { it > 0 }
            ?: pageWidthPx.takeIf { it > 0 }
            ?: original?.player?.width?.takeIf { it > 0 }
            ?: host?.width?.takeIf { it > 0 }
            ?: 0
        if (width <= 0) return
        host?.context?.let { context ->
            sidePaddingPx = resolveSidePadding(context)
            pageGapPx = sidePaddingPx
        }
        pageWidthPx = width

        val originalParams = original?.player?.layoutParams
        val measuredHeight = original?.player?.height?.takeIf { it > 0 }
            ?: originalParams?.height?.takeIf { it > 0 }
        val layoutChanged = previousWidth != pageWidthPx ||
            previousSidePadding != sidePaddingPx ||
            previousGap != pageGapPx ||
            (measuredHeight != null && previousContainerHeight != measuredHeight)

        val scroller = scrollView
        scroller?.layoutParams?.let { rawParams ->
            val params = rawParams as? FrameLayout.LayoutParams ?: return@let
            val desiredWidth = width + sidePaddingPx * 2
            var paramsChanged = false
            if (params.width != desiredWidth) {
                params.width = desiredWidth
                paramsChanged = true
            }
            if (measuredHeight != null && params.height != measuredHeight) {
                params.height = measuredHeight
                paramsChanged = true
            }
            if (originalParams is ViewGroup.MarginLayoutParams) {
                val leftMargin = originalParams.leftMargin - sidePaddingPx
                val rightMargin = originalParams.rightMargin - sidePaddingPx
                if (params.leftMargin != leftMargin) {
                    params.leftMargin = leftMargin
                    paramsChanged = true
                }
                if (params.rightMargin != rightMargin) {
                    params.rightMargin = rightMargin
                    paramsChanged = true
                }
                if (params.topMargin != originalParams.topMargin) {
                    params.topMargin = originalParams.topMargin
                    paramsChanged = true
                }
                if (params.bottomMargin != originalParams.bottomMargin) {
                    params.bottomMargin = originalParams.bottomMargin
                    paramsChanged = true
                }
            }
            if (originalParams is FrameLayout.LayoutParams &&
                params.gravity != originalParams.gravity
            ) {
                params.gravity = originalParams.gravity
                paramsChanged = true
            }
            if (paramsChanged) {
                scroller.layoutParams = params
            }
        }
        pages.setPaddingRelative(
            sidePaddingPx,
            0,
            sidePaddingPx,
            0
        )
        pages.children().forEachIndexed { index, child ->
            val params = child.layoutParams as? LinearLayout.LayoutParams ?: return@forEachIndexed
            if (params.width != width) {
                params.width = width
            }
            params.setMarginStart(0)
            params.setMarginEnd(
                if (index == pages.childCount - 1) 0 else pageGapPx
            )
            child.layoutParams = params
        }
        pages.requestLayout()

        if (layoutChanged && currentIndex >= 0) {
            val generation = pageOrderGeneration
            pages.post {
                if (
                    generation == pageOrderGeneration &&
                    scrollView === scroller
                ) {
                    scrollToPage(currentIndex, animate = false, generation = generation)
                }
            }
        }
    }

    private fun onScrollPositionChanged(scrollX: Int) {
        val location = pageLocation(scrollX)
        onPageScrolled(location, pageOrderGeneration)
    }

    private fun onGestureReleased(velocityX: Float) {
        val scroller = scrollView ?: return
        val location = pageLocation(scroller.scrollX)
        val lower = floor(location).toInt()
        val fraction = location - lower
        // HorizontalScrollView keeps scrollX in physical coordinates. In an
        // RTL carousel, moving to the next logical page decreases scrollX,
        // so the finger velocity has the opposite page meaning from LTR.
        val logicalVelocityX = if (isLayoutRtl()) -velocityX else velocityX
        val target = when {
            logicalVelocityX < -MIN_FLING_VELOCITY -> {
                if (fraction > 0.01f) ceil(location).toInt() else lower + 1
            }

            logicalVelocityX > MIN_FLING_VELOCITY -> {
                if (fraction > 0.01f) floor(location).toInt() else lower - 1
            }

            else -> location.roundToInt()
        }.coerceIn(0, (cards.size - 1).coerceAtLeast(0))

        cards.values.elementAtOrNull(target)?.key?.let(onPageSelected)
        // Queue the single custom snap after ACTION_UP dispatch so the
        // animation starts from the actual current scrollX.
        val generation = pageOrderGeneration
        scroller.post {
            if (generation == pageOrderGeneration && scrollView === scroller) {
                scrollToPage(target, animate = true, generation = generation)
            }
        }
    }

    private fun pageLocation(scrollX: Int): Float {
        val pages = pageContainer ?: return 0f
        val scroller = scrollView ?: return 0f
        val count = cards.size
        if (count <= 1) return 0f

        val positions = (0 until count).map { index ->
            pages.getChildAt(index)?.left ?: 0
        }
        if (positions.size < 2) return 0f

        val rtl = isLayoutRtl()
        if ((!rtl && positions.last() <= positions.first()) ||
            (rtl && positions.first() <= positions.last())
        ) {
            return 0f
        }

        if (!rtl) {
            // The first page is intentionally inset by sidePadding, while the
            // scroll position starts at zero. Convert scrollX back into the
            // content coordinate used by the child positions.
            val x = (scrollX + positions.first()).coerceIn(
                positions.first(),
                positions.last()
            )
            for (index in 0 until positions.lastIndex) {
                val start = positions[index]
                val end = positions[index + 1]
                if (end <= start) continue
                if (x <= end) {
                    return index + ((x - start).toFloat() / (end - start))
                }
            }
            return positions.lastIndex.toFloat()
        }

        // In RTL, LinearLayout places page zero at the right and
        // HorizontalScrollView starts at its maximum physical scrollX. Move
        // from that right edge into the descending child coordinates.
        val maxScroll = scrollRange(scroller)
        val x = (positions.first() - (maxScroll - scrollX)).coerceIn(
            positions.last(),
            positions.first()
        )
        for (index in 0 until positions.lastIndex) {
            val start = positions[index]
            val end = positions[index + 1]
            if (start <= end) continue
            if (x >= end) {
                return index + ((start - x).toFloat() / (start - end))
            }
        }
        return positions.lastIndex.toFloat()
    }

    private fun scrollRange(scroller: PageScrollView): Int {
        val content = scroller.getChildAt(0) ?: return 0
        return (
            content.width -
                (scroller.width - scroller.paddingLeft - scroller.paddingRight)
            ).coerceAtLeast(0)
    }

    private fun scrollToPage(
        index: Int,
        animate: Boolean,
        generation: Int = pageOrderGeneration
    ) {
        val scroller = scrollView ?: return
        val pages = pageContainer ?: return
        if (generation != pageOrderGeneration) return
        val count = cards.size
        if (count == 0) return
        val target = index.coerceIn(0, count - 1)
        val targetView = pages.getChildAt(target)
        val firstView = pages.getChildAt(0)
        val x = if (targetView != null && firstView != null) {
            val distanceFromFirst = if (isLayoutRtl()) {
                firstView.left - targetView.left
            } else {
                targetView.left - firstView.left
            }
            if (isLayoutRtl()) {
                scrollRange(scroller) - distanceFromFirst
            } else {
                distanceFromFirst
            }
        } else {
            0
        }
        if (targetView == null || firstView == null ||
            (target > 0 && targetView.left == 0 && pages.width == 0)
        ) {
            scroller.post {
                if (generation == pageOrderGeneration && scrollView === scroller) {
                    scrollToPage(target, animate, generation)
                }
            }
            return
        }
        if (animate) {
            scroller.smoothScrollTo(x, 0)
        } else {
            scroller.scrollTo(x, 0)
        }
        pages.requestLayout()
    }

    private fun inflatePlayer(context: Context): View? {
        val resourceId = layoutId ?: run {
            val resolved = context.resources.getIdentifier(
                PLAYER_LAYOUT,
                "layout",
                context.packageName
            ).takeIf { it != 0 }
                ?: context.resources.getIdentifier(
                    PLAYER_LAYOUT,
                    "layout",
                    SYSTEMUI_PACKAGE
                ).takeIf { it != 0 }
                ?: resolveSystemUiLayoutId()
            layoutId = resolved
            resolved
        }
        if (resourceId == 0) {
            warn("找不到原生媒体布局: $PLAYER_LAYOUT")
            return null
        }
        return runCatching {
            LayoutInflater.from(context).inflate(resourceId, header, false)
        }.onFailure { warn("膨胀原生媒体布局失败", it) }.getOrNull()
    }

    private fun resourceContext(): Context? {
        return (readField(layoutController, "context") as? Context)
            ?: (readField(layoutController, "layoutContext") as? Context)
            ?: header?.context
    }

    private fun resolveSystemUiLayoutId(): Int {
        return runCatching {
            val clazz = holderClassLoader?.loadClass(SYSTEMUI_LAYOUT_CLASS)
                ?: error("SystemUI R.layout class unavailable")
            clazz.getDeclaredField(PLAYER_LAYOUT).apply { isAccessible = true }.getInt(null)
        }.onFailure { warn("反射 SystemUI 媒体布局资源失败", it) }.getOrDefault(0)
    }

    private fun createHolder(player: View): Any? {
        return runCatching {
            val constructor = holderConstructor ?: run {
                val clazz = holderClassLoader?.loadClass(NotificationMediaHostClasses.HOLDER)
                    ?: error("MediaViewHolder class unavailable")
                clazz.getDeclaredConstructor(View::class.java).apply {
                    isAccessible = true
                }.also { holderConstructor = it }
            }
            constructor.newInstance(player)
        }.onFailure { warn("创建原生媒体 Holder 失败", it) }.getOrNull()
    }

    private fun createController(): Any? {
        return runCatching {
            val values = CONTROLLER_DEPENDENCIES.map { name ->
                readField(templateController, name)
            }.toMutableList()
            values[2] = createSeekBarViewModel()
                ?: error("SeekBarViewModel clone unavailable")
            if (values.any { it == null }) {
                error("MiuiMediaViewControllerImpl 依赖字段不完整")
            }
            createActionButtonUtils()?.let { actionButtonUtils ->
                values[11] = actionButtonUtils
            }

            val constructor = controllerConstructor ?: controllerClass.declaredConstructors
                .firstOrNull { candidate ->
                    candidate.parameterCount == values.size && candidate.parameterTypes
                        .zip(values)
                        .all { (type, value) ->
                            value != null && type.isAssignableFrom(value.javaClass)
                        }
                }?.apply { isAccessible = true }
                ?.also { controllerConstructor = it }
                ?: error("MiuiMediaViewControllerImpl 构造函数不匹配")

            constructor.newInstance(*values.toTypedArray()).also { controller ->
                readField(templateController, "statusBarState")?.let { state ->
                    writeField(controller, "statusBarState", state)
                }
            }
        }.onFailure { warn("创建原生媒体控制器失败", it) }.getOrNull()
    }

    /**
     * MiuiMediaActionButtonUtils keeps play/next timing and pending updateJob
     * as instance state. The native graph has one controller, while this
     * renderer creates several. Give every visible page an independent
     * utility instance so one page cannot consume another page's play update.
     */
    private fun createActionButtonUtils(): Any? {
        val template = readField(templateController, "miuiMediaActionButtonUtils") ?: return null
        val dependencies = arrayOf(
            readField(template, "uiScope"),
            readField(template, "context"),
            readField(template, "notificationStat")
        )
        if (dependencies.any { it == null }) return null

        val constructor = template.javaClass.declaredConstructors.firstOrNull { candidate ->
            candidate.parameterCount == dependencies.size && candidate.parameterTypes
                .zip(dependencies)
                .all { (type, value) -> value != null && type.isAssignableFrom(value.javaClass) }
        }?.apply { isAccessible = true } ?: return null
        return runCatching { constructor.newInstance(*dependencies) }
            .onFailure { warn("创建独立媒体按钮工具失败", it) }
            .getOrNull()
    }

    private fun createSeekBarViewModel(): Any? {
        val template = readField(templateController, "seekBarViewModel") ?: return null
        val executor = readField(template, "bgExecutor") ?: return null
        val falsingManager = readField(template, "falsingManager") ?: return null
        return runCatching {
            val constructor = seekBarConstructor ?: template.javaClass.declaredConstructors
                .firstOrNull { candidate ->
                    candidate.parameterCount == 2 &&
                        candidate.parameterTypes[0].isAssignableFrom(executor.javaClass) &&
                        candidate.parameterTypes[1].isAssignableFrom(falsingManager.javaClass)
                }?.apply { isAccessible = true }
                ?.also { seekBarConstructor = it }
                ?: error("SeekBarViewModel 构造函数不匹配")
            constructor.newInstance(executor, falsingManager)
        }.onFailure { warn("创建独立 SeekBarViewModel 失败", it) }.getOrNull()
    }

    private fun applyLoadedLayouts(player: View, holder: Any) {
        applyConstraintSet(readField(layoutController, "normalLayout"), player)
        val album = readField(holder, "albumView") as? View
        applyConstraintSet(readField(layoutController, "normalAlbumLayout"), album)
    }

    /**
     * ConstraintSet resources do not carry runtime shape state. The native
     * controller (and our cover/background hooks) put the album/background
     * outline on the original View after inflation, so copy only the chrome
     * properties to an independently bound page. Artwork Drawables remain
     * owned by each page's controller.
     */
    private fun copyNativeChrome(card: Card) {
        if (card.original) return
        val original = originalCard ?: return
        copyViewChrome(original.player, card.player)
        listOf("mediaBg", "albumView", "albumImageView").forEach { fieldName ->
            copyViewChrome(
                readField(original.holder, fieldName) as? View,
                readField(card.holder, fieldName) as? View
            )
        }
    }

    private fun copyViewChrome(source: View?, target: View?) {
        if (source == null || target == null) return
        runCatching {
            target.clipToOutline = source.clipToOutline
            target.outlineProvider = source.outlineProvider
            // clipBounds is transient geometry owned by SystemUI's height and
            // removal animations. It is not part of the card style: copying a
            // short-lived native clip rectangle to a clone leaves that page
            // with a one-frame (or stale) bottom crop after AOD/style layout.
            target.clipBounds = null
            target.setPadding(
                source.paddingLeft,
                source.paddingTop,
                source.paddingRight,
                source.paddingBottom
            )
            target.elevation = source.elevation
            target.translationZ = source.translationZ
            if (target.background == null && source.background != null) {
                target.background = source.background.constantState
                    ?.newDrawable(target.resources, target.context.theme)
                    ?.mutate()
            }
            target.invalidateOutline()

            val sourceGroup = source as? ViewGroup
            val targetGroup = target as? ViewGroup
            if (sourceGroup != null && targetGroup != null) {
                targetGroup.clipChildren = sourceGroup.clipChildren
                targetGroup.clipToPadding = sourceGroup.clipToPadding
                for (index in 0 until targetGroup.childCount) {
                    val targetChild = targetGroup.getChildAt(index)
                    val sourceChild = if (targetChild.id != View.NO_ID) {
                        sourceGroup.findViewById(targetChild.id)
                    } else {
                        sourceGroup.getChildAt(index)
                    }
                    copyViewChrome(sourceChild, targetChild)
                }
            }
        }.onFailure { error ->
            warn("复制副媒体卡片圆角属性失败", error)
        }
    }

    private fun resolveOriginalPageWidth(
        host: ViewGroup,
        player: View,
        layoutParams: ViewGroup.LayoutParams?
    ): Int {
        return player.width.takeIf { it > 0 }
            ?: layoutParams?.width?.takeIf { it > 0 }
            ?: (host.width - host.paddingLeft - host.paddingRight).takeIf { it > 0 }
            ?: 0
    }

    private fun resolveSidePadding(hostContext: Context): Int {
        val context = resourceContext() ?: hostContext
        val resourceId = listOf(context.packageName, SYSTEMUI_PACKAGE)
            .asSequence()
            .map { packageName ->
                context.resources.getIdentifier(SIDE_PADDING_DIMEN, "dimen", packageName)
            }
            .firstOrNull { it != 0 }
        return runCatching {
            resourceId?.takeIf { it != 0 }?.let(context.resources::getDimensionPixelSize)
                ?: (FALLBACK_SIDE_PADDING_DP * context.resources.displayMetrics.density)
                    .roundToInt()
        }.getOrDefault(
            (FALLBACK_SIDE_PADDING_DP * hostContext.resources.displayMetrics.density)
                .roundToInt()
        ).coerceAtLeast(1)
    }

    private fun installHostLayoutListener(host: ViewGroup) {
        removeHostLayoutListener()
        val listener = View.OnLayoutChangeListener {
                _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (
                right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            ) {
                updatePageWidths()
                applyCarouselClipState()
            }
        }
        hostLayoutChangeListener = listener
        host.addOnLayoutChangeListener(listener)
    }

    private fun removeHostLayoutListener() {
        val host = header
        val listener = hostLayoutChangeListener ?: return
        host?.removeOnLayoutChangeListener(listener)
        hostLayoutChangeListener = null
    }

    /**
     * The notification host is the viewport in both orientations. Keeping
     * the host clipped prevents always-visible neighbour pages from leaking
     * into the blank side area, while still allowing HorizontalScrollView to
     * animate between pages inside that viewport.
     */
    private fun applyCarouselClipState() {
        clipHost?.let { host ->
            host.clipChildren = true
            host.clipToPadding = false
        }
        clipParent?.let { parent ->
            originalParentClipChildren?.let { parent.clipChildren = it }
            originalParentClipToPadding?.let { parent.clipToPadding = it }
        }
    }

    private fun captureCarouselClipState(host: ViewGroup) {
        if (clipHost == null) {
            clipHost = host
            originalHostClipChildren = host.clipChildren
            originalHostClipToPadding = host.clipToPadding
        }

        (host.parent as? ViewGroup)?.let { parent ->
            if (clipParent == null || clipParent !== parent) {
                clipParent = parent
                originalParentClipChildren = parent.clipChildren
                originalParentClipToPadding = parent.clipToPadding
            }
        }
        applyCarouselClipState()
    }

    private fun restoreCarouselClipState() {
        clipHost?.let { host ->
            originalHostClipChildren?.let { host.clipChildren = it }
            originalHostClipToPadding?.let { host.clipToPadding = it }
        }
        clipParent?.let { parent ->
            originalParentClipChildren?.let { parent.clipChildren = it }
            originalParentClipToPadding?.let { parent.clipToPadding = it }
        }
        clipHost = null
        clipParent = null
        originalHostClipChildren = null
        originalHostClipToPadding = null
        originalParentClipChildren = null
        originalParentClipToPadding = null
    }

    private fun applyConstraintSet(constraintSet: Any?, target: View?) {
        if (constraintSet == null || target == null) return
        runCatching {
            val method = findMethod(constraintSet.javaClass, "applyTo") {
                it.parameterCount == 1 &&
                    it.parameterTypes[0].isAssignableFrom(target.javaClass)
            } ?: return@runCatching
            method.invoke(constraintSet, target)
        }.onFailure { warn("应用原生媒体布局约束失败", it) }
    }

    private fun readField(target: Any?, name: String): Any? {
        target ?: return null
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    private fun writeField(target: Any, name: String, value: Any) {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                runCatching {
                    field.isAccessible = true
                    field.set(target, value)
                }
                return
            }
            current = current.superclass
        }
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        predicate: (Method) -> Boolean
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && predicate(it) }
                ?.apply { isAccessible = true }
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun warn(message: String, error: Throwable? = null) {
        HookLogger.w(TAG, message, error)
    }
}

private fun ViewGroup.children(): Sequence<View> = sequence {
    for (index in 0 until childCount) yield(getChildAt(index))
}
