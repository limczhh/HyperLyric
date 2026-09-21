package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.session.MediaController
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostApi
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostClasses
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SpringInterpolator
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaForegroundStyler
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.LinkedHashMap
import kotlin.math.abs
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
    private val onCardPlaybackChanged: (String, Boolean) -> Unit,
    private val shouldIgnoreScrollTouch: (MotionEvent) -> Boolean,
    private val canShowEdgeAction: () -> Boolean,
    private val canUseNativeEdgeMenu: () -> Boolean,
    private val canDirectDismissEdge: () -> Boolean,
    private val isNativeEdgeMenuShowing: () -> Boolean,
    private val onNativeEdgeGestureRequested: (EdgeActionSide) -> Boolean,
    private val onNativeEdgeGestureCancelled: () -> Unit,
    private val onClearAllRequested: () -> Unit,
    private val onEdgeActionTranslationChanged: (Float) -> Unit
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
        const val FALLBACK_EDGE_DISMISS_DP = 48f
        const val EDGE_DISMISS_FRACTION = 0.6f
        const val EDGE_MENU_SPRING_DAMPING = 0.75f
        const val EDGE_MENU_SPRING_RESPONSE = 0.44f
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
    internal enum class EdgeActionSide {
        LEFT,
        RIGHT
    }

    private class PageScrollView(
        context: Context,
        private val shouldIgnoreTouch: (MotionEvent) -> Boolean,
        private val onScrollPositionChanged: (Int) -> Unit,
        private val onGestureReleased: (Float) -> Unit,
        private val onGestureStarted: () -> Unit,
        private val edgeSideForGesture: (Float) -> EdgeActionSide?,
        private val isNativeEdgeMenuShowing: () -> Boolean,
        private val onNativeEdgeGestureRequested: (EdgeActionSide) -> Boolean,
        private val onNativeEdgeGestureCancelled: () -> Unit,
        private val onEdgeDragged: (EdgeActionSide, Float) -> Unit,
        private val onEdgeReleased: (EdgeActionSide, Float) -> Unit,
        private val onEdgeCancelled: () -> Unit
    ) : HorizontalScrollView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var velocityTracker: VelocityTracker? = null
        private var ignoredGesture = false
        private var handlingGesture = false
        private var edgeGesture = false
        private var edgeSide: EdgeActionSide? = null
        private var nativeGesturePassthrough = false
        private var nativeGestureHandoff = false
        private var verticalGesture = false
        private var parentInterceptDisallowed = false
        private var touchDownX = 0f
        private var touchDownY = 0f

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    finishGesture(snap = false)
                    touchDownX = event.x
                    touchDownY = event.y
                    ignoredGesture = shouldIgnoreTouch(event)
                    if (!ignoredGesture) {
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)
                        if (isNativeEdgeMenuShowing()) {
                            // A previously opened native menu must keep the
                            // complete touch sequence with SwipeHelper. Do
                            // not let the carousel re-lock its parent.
                            nativeGesturePassthrough = true
                            parent?.requestDisallowInterceptTouchEvent(false)
                        } else {
                            // Give the carousel first ownership of the
                            // gesture. The parent is released only after the
                            // child proves this is an outward edge gesture.
                            parent?.requestDisallowInterceptTouchEvent(true)
                            parentInterceptDisallowed = true
                        }
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

            if (nativeGesturePassthrough) {
                // An already-open native menu owns the whole media header,
                // not only the visible icon. If the user starts dragging on
                // the card body, intercept once the direction is clear so
                // the player is cancelled and SwipeHelper can close the menu.
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                        nativeGestureHandoff = true
                        return true
                    }
                }
                return false
            }

            if (nativeGestureHandoff || verticalGesture) {
                return false
            }

            if (edgeGesture) return true

            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    val side = edgeSideForGesture(dx)
                    if (side != null) {
                        onGestureStarted()
                        if (onNativeEdgeGestureRequested(side)) {
                            // The target SystemUI already has a native
                            // MiuiMediaHeaderView -> SwipeHelper path. Once
                            // the carousel has proved that this is an edge
                            // pull, release the parent and let that path own
                            // the remainder of the gesture. Intercept this
                            // MOVE locally first so the active player gets
                            // ACTION_CANCEL instead of turning the swipe into
                            // a click when the outer helper needs one more
                            // MOVE to become swiping.
                            nativeGestureHandoff = true
                            parent?.requestDisallowInterceptTouchEvent(false)
                            parentInterceptDisallowed = false
                            return true
                        }
                        edgeSide = side
                        edgeGesture = true
                        return true
                    }
                } else if (abs(event.y - touchDownY) > touchSlop &&
                    abs(event.y - touchDownY) > abs(dx)
                ) {
                    // A vertical gesture belongs to the notification stack;
                    // do not keep it locked behind the horizontal carousel.
                    verticalGesture = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                    parentInterceptDisallowed = false
                    return false
                }
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
            if (nativeGesturePassthrough || nativeGestureHandoff) {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    // If the parent never intercepted after the handoff, the
                    // preparation was speculative and must be rolled back.
                    finishGesture(snap = nativeGestureHandoff)
                } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    // ACTION_CANCEL normally means SwipeHelper has taken
                    // over. Do not reset its native menu state here.
                    finishGesture(snap = false)
                }
                // Consume the sequence while waiting for the outer native
                // helper. This prevents the player below from receiving the
                // final ACTION_UP and launching its app on a failed/slow
                // handoff.
                return true
            }
            if (verticalGesture) return false
            if (edgeGesture) {
                velocityTracker?.addMovement(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        edgeSide?.let { side ->
                            val distance = when (side) {
                                // Match MiuiMediaNotificationSlideMenuRow:
                                // positive translation exposes the left menu,
                                // negative translation exposes the right one.
                                EdgeActionSide.LEFT -> event.x - touchDownX
                                EdgeActionSide.RIGHT -> touchDownX - event.x
                            }
                            onEdgeDragged(side, distance)
                        }
                    }

                    MotionEvent.ACTION_UP -> finishGesture(snap = true)
                    MotionEvent.ACTION_CANCEL -> finishGesture(snap = false)
                }
                return true
            }
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
            if (nativeGestureHandoff) {
                if (snap) onNativeEdgeGestureCancelled()
            } else if (edgeGesture) {
                val side = edgeSide
                if (side != null) {
                    val tracker = velocityTracker
                    tracker?.computeCurrentVelocity(1000)
                    if (snap) {
                        onEdgeReleased(side, tracker?.xVelocity ?: 0f)
                    } else {
                        onEdgeCancelled()
                    }
                }
            } else if (snap && handlingGesture) {
                val tracker = velocityTracker
                tracker?.computeCurrentVelocity(1000)
                onGestureReleased(tracker?.xVelocity ?: 0f)
            }
            if (parentInterceptDisallowed) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            velocityTracker?.recycle()
            velocityTracker = null
            ignoredGesture = false
            handlingGesture = false
            edgeGesture = false
            edgeSide = null
            nativeGesturePassthrough = false
            nativeGestureHandoff = false
            verticalGesture = false
            parentInterceptDisallowed = false
            touchDownX = 0f
            touchDownY = 0f
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
    private var edgeOverscrollAnimator: ValueAnimator? = null
    private var edgeActionSide: EdgeActionSide? = null
    private var edgeActionDistance = 0f
    private var directEdgeDismiss = false
    private var edgeClearPending = false
    private var nativeEdgeMenuRow: Any? = null
    private var nativeEdgeMenuShowing = false
    /** True only after the current edge gesture was accepted by native SystemUI. */
    private var nativeEdgeGestureAccepted = false
    private var hostTranslation = 0f
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
        if (!canShowEdgeAction()) {
            closeNativeEdgeMenu()
            closeEdgeOverscroll(animated = true)
        }
        if (entries.size < 2) {
            if (isActive) disableMultiView()
            return NotificationMediaMultiCardSyncResult.SUCCESS
        }
        if (!isActive && !ensureContainer()) {
            return NotificationMediaMultiCardSyncResult.FAILED
        }

        val oldCards = cards.values.toList()
        val oldOrder = oldCards.map { it.key }
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
            // A reorder or page removal changes the edge's semantic owner.
            // Never leave a revealed action attached to the old page.
            closeEdgeOverscroll(animated = false)
            rebuildPageOrder()
            // A page reorder changes the meaning of every child index. Move
            // the viewport to the same canonical selected key immediately;
            // preserving the old physical scroll position would show one key
            // while the indicator highlights another index for a frame.
            val generation = ++pageOrderGeneration
            onPageOrderChanged(generation)
            scrollToPage(selectedIndex, animate = false, generation = generation)
            pageContainer?.post {
                if (generation == pageOrderGeneration) {
                    onScrollPositionChanged(scrollView?.scrollX ?: 0)
                }
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

    fun setHeaderTranslation(translation: Float) {
        hostTranslation = translation
        val cardTranslation = hostTranslation + edgeActionTranslation()
        // Keep native/header translation on the scroll container. The custom
        // edge overscroll belongs to the page content, so HorizontalScrollView
        // never competes with it for its own scroll position.
        scrollView?.translationX = hostTranslation
        pageContainer?.translationX = edgeActionTranslation()
        onEdgeActionTranslationChanged(cardTranslation)
    }

    fun headerTranslation(): Float? = scrollView?.let {
        it.translationX
    }

    /**
     * Lets the target SystemUI's own MiuiMediaNotificationSlideMenuRow own
     * the edge gesture. The native row is already attached to the same
     * MiuiMediaHeaderView; our header translation hook moves this carousel
     * together with the native row.
     */
    fun prepareNativeEdgeGesture(side: EdgeActionSide): Boolean {
        nativeEdgeGestureAccepted = false
        if (!isActive || cards.size < 2 || !canShowEdgeAction() ||
            !canUseNativeEdgeMenu()
        ) return false
        val host = header ?: return false
        val row = readField(host, "slideMenu") ?: return false
        val menuContainer = readField(row, "mMenuContainer") as? ViewGroup
        if (menuContainer == null || menuContainer.parent == null ||
            !hasNativeMenuAction(row, menuContainer) ||
            readField(row, "mMediaData") == null ||
            readField(row, "mShouldShowMenu") != true ||
            findMethod(row.javaClass, "shouldShowMenu") {
                it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            } == null
        ) {
            warn("原生媒体滑动菜单能力不完整，改用无图标边缘清除降级")
            return false
        }
        placeCarouselBelowNativeMenu(host, menuContainer)

        // A previous native swipe can leave the reused row with its old
        // slide-menu state until the closing animation finishes. Clear that
        // state before assigning the new edge, otherwise the next gesture can
        // reuse the previous left/right menu location.
        if (!nativeEdgeMenuShowing) {
            findMethod(row.javaClass, "resetMenu") {
                it.parameterCount == 0
            }?.let { reset ->
                runCatching { reset.invoke(row) }
                    .onFailure { warn("重置原生媒体边缘菜单状态失败", it) }
            }
        }

        if (nativeEdgeMenuRow !== row) {
            restoreNativeEdgeMenuState()
            nativeEdgeMenuRow = row
        }
        // Do not mutate touchDownMediaState. It is native gesture state and
        // older rows may not even have the field. The optional shouldShowMenu
        // hook returns true only while this handoff is active, which keeps
        // the native translation/overscroll path without changing the row's
        // own playback semantics.
        nativeEdgeMenuShowing = true
        nativeEdgeGestureAccepted = true

        HookLogger.d(TAG, "多卡片边缘手势已交给 SystemUI 原生媒体滑动菜单: $side")
        return true
    }

    /**
     * MiuiMediaHeaderView inserts its native menu container below the card
     * content. Our carousel is another direct child of that header, so keep it
     * below the native menu as well; otherwise the card paints over the native
     * icon's reveal animation.
     */
    private fun placeCarouselBelowNativeMenu(
        host: ViewGroup,
        menuContainer: ViewGroup
    ) {
        val scroller = scrollView ?: return
        if (menuContainer.parent !== host) return
        val scrollerIndex = host.indexOfChild(scroller)
        val menuIndex = host.indexOfChild(menuContainer)
        if (scrollerIndex < 0 || menuIndex < 0 || scrollerIndex >= menuIndex) return

        val layoutParams = scroller.layoutParams ?: return
        host.removeViewAt(scrollerIndex)
        val targetIndex = (host.indexOfChild(menuContainer) + 1)
            .coerceAtMost(host.childCount)
        host.addView(scroller, targetIndex, layoutParams)
    }

    fun isNativeEdgeMenuShowing(): Boolean = nativeEdgeMenuShowing

    fun cancelNativeEdgeGesture() {
        closeNativeEdgeMenu()
    }

    /** Returns null when the row does not belong to this renderer. */
    fun nativeSlideMenuShouldShow(row: Any): Boolean? {
        if (!isActive || cards.size < 2) return null
        val host = header ?: return false
        if (readField(host, "slideMenu") !== row) return false
        if (!nativeEdgeMenuShowing || !canShowEdgeAction() ||
            !canUseNativeEdgeMenu()
        ) return false
        val menuContainer = readField(row, "mMenuContainer") as? ViewGroup
        if (menuContainer == null || menuContainer.parent == null ||
            !hasNativeMenuAction(row, menuContainer) || readField(row, "mMediaData") == null ||
            readField(row, "mShouldShowMenu") != true
        ) return false
        return true
    }

    fun onNativeEdgeMenuClosed(row: Any) {
        if (nativeEdgeMenuRow !== row) return
        restoreNativeEdgeMenuState()
    }

    private fun restoreNativeEdgeMenuState() {
        nativeEdgeMenuRow = null
        nativeEdgeMenuShowing = false
        nativeEdgeGestureAccepted = false
    }

    private fun closeNativeEdgeMenu() {
        if (!nativeEdgeMenuShowing) return
        val host = header
        if (host != null) {
            val reset = findMethod(host.javaClass, "resetTranslation") {
                it.parameterCount == 0
            }
            runCatching { reset?.invoke(host) }
                .onFailure { warn("关闭原生媒体边缘菜单失败", it) }
        }
        restoreNativeEdgeMenuState()
    }

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
        closeNativeEdgeMenu()
        resetEdgeOverscroll()
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
        hostTranslation = 0f
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
            },
            edgeSideForGesture = ::edgeSideForGesture,
            isNativeEdgeMenuShowing = isNativeEdgeMenuShowing,
            onNativeEdgeGestureRequested = onNativeEdgeGestureRequested,
            onNativeEdgeGestureCancelled = onNativeEdgeGestureCancelled,
            onEdgeDragged = ::onEdgeDragged,
            onEdgeReleased = ::onEdgeReleased,
            onEdgeCancelled = ::onEdgeCancelled
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

    /**
     * The native row creates its menu child lazily. In the initial hidden
     * state the container is attached but empty; the first native translation
     * changes the side and only then calls populateMenuViews(). Treat an
     * initialized menu definition plus the native placement methods as enough
     * for the handoff. Keep the clickable-child check as the fast path for
     * rows that have already been populated.
     */
    private fun hasNativeMenuAction(row: Any, menuContainer: ViewGroup): Boolean {
        for (index in 0 until menuContainer.childCount) {
            if (menuContainer.getChildAt(index)?.isClickable == true) return true
        }

        val dismissItem = readField(row, "mDismissItem")
        val ongoingItem = readField(row, "mOngoingItem")
        val leftItems = readField(row, "mLeftMenuItems") as? Collection<*>
        val rightItems = readField(row, "mRightMenuItems") as? Collection<*>
        val hasInitializedItem = dismissItem != null || ongoingItem != null ||
            !leftItems.isNullOrEmpty() || !rightItems.isNullOrEmpty()
        if (!hasInitializedItem) return false

        val canPopulate = findMethod(row.javaClass, "populateMenuViews") {
            it.parameterCount == 0
        } != null
        val canPlace = findMethod(row.javaClass, "setMenuLocation") {
            it.parameterCount == 0
        } != null
        return canPopulate && canPlace
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
        val observer = card.playbackObserver ?: NotificationMediaPlaybackObserver(
            onMediaChanged = { _ ->
                if (cards[card.key] === card) onCardMediaChanged(card.key)
            },
            onPlaybackStateChanged = { _, playing ->
                if (cards[card.key] === card) {
                    onCardPlaybackChanged(card.key, playing)
                }
            }
        ).also { card.playbackObserver = it }
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
        closeNativeEdgeMenu()
        resetEdgeOverscroll()

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
        hostTranslation = 0f
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

    /**
     * Xiaomi's media menu is exposed only when the card is pulled outwards at
     * a carousel edge. The side is a visual side: in LTR the first page owns
     * the left action and the last page owns the right action; RTL mirrors it.
     * The card moves away from that side, just like Xiaomi's native row:
     * positive translation reveals the left action and negative translation
     * reveals the right action.
     */
    private fun edgeSideForGesture(deltaX: Float): EdgeActionSide? {
        // The edge action is either rendered by SystemUI's native slide-menu
        // row or handled as a direct no-icon dismissal on older builds.
        if (cards.size < 2 || !canShowEdgeAction()) {
            return null
        }

        // Once the action is exposed, a horizontal gesture keeps owning the
        // same side so that dragging back closes it instead of starting a
        // carousel page gesture.
        edgeActionSide?.let { side ->
            if (edgeActionDistance > 0.5f || edgeOverscrollAnimator != null) {
                return side
            }
        }

        val scroller = scrollView ?: return null
        val location = pageLocation(scroller.scrollX)
        val lastPage = (cards.size - 1).coerceAtLeast(0)
        val side = when {
            location <= 0.01f -> if (isLayoutRtl()) {
                EdgeActionSide.RIGHT
            } else {
                EdgeActionSide.LEFT
            }

            location >= lastPage - 0.01f -> if (isLayoutRtl()) {
                EdgeActionSide.LEFT
            } else {
                EdgeActionSide.RIGHT
            }

            else -> return null
        }
        val outward = when (side) {
            EdgeActionSide.LEFT -> deltaX > 0f
            EdgeActionSide.RIGHT -> deltaX < 0f
        }
        return side.takeIf { outward }
    }

    private fun onEdgeDragged(side: EdgeActionSide, distance: Float) {
        if (!canShowEdgeAction()) {
            closeEdgeOverscroll(animated = true)
            return
        }
        edgeOverscrollAnimator?.cancel()
        edgeOverscrollAnimator = null
        if (edgeActionSide != side) {
            edgeActionSide = side
        }
        // If the native row is unavailable, the low-version behavior is a
        // direct media-header dismissal. There is deliberately no local icon
        // or click target in this mode.
        // Native capability is a process-level fact. It does not mean that
        // this particular gesture was accepted: the row may still reject the
        // handoff because its runtime data/menu state is incomplete. In that
        // case the current custom edge gesture must use the low-version
        // paused-media fallback instead of silently springing back.
        directEdgeDismiss = !nativeEdgeGestureAccepted && canDirectDismissEdge()
        setEdgeOverscrollDistance(distance.coerceAtLeast(0f))
    }

    private fun onEdgeReleased(side: EdgeActionSide, _velocityX: Float) {
        if (edgeActionSide != side || !canShowEdgeAction()) {
            closeEdgeOverscroll(animated = true)
            return
        }

        if (directEdgeDismiss && edgeActionDistance >= edgeDismissThreshold()) {
            startDirectEdgeDismiss()
        } else {
            closeEdgeOverscroll(animated = true)
        }
    }

    private fun onEdgeCancelled() {
        closeEdgeOverscroll(animated = true)
    }

    private fun startDirectEdgeDismiss() {
        if (edgeClearPending) return
        edgeClearPending = true
        val dismissDistance = maxOf(
            edgeActionDistance,
            (header?.width ?: pageWidthPx).coerceAtLeast(1).toFloat()
        )
        // Start the real MediaData dismissal at release time. The remaining
        // overscroll animation is visual only; a lifecycle refresh must not
        // be able to cancel the clear operation before it is dispatched.
        onClearAllRequested()
        animateEdgeOverscroll(dismissDistance) {
            if (!edgeClearPending) return@animateEdgeOverscroll
            edgeClearPending = false
            resetEdgeOverscroll()
        }
    }

    private fun animateEdgeOverscroll(
        targetDistance: Float,
        onEnd: (() -> Unit)? = null
    ) {
        edgeOverscrollAnimator?.cancel()
        edgeOverscrollAnimator = null

        if (edgeActionSide == null) {
            onEnd?.invoke()
            return
        }
        val target = targetDistance.coerceAtLeast(0f)
        val start = edgeActionDistance
        if (abs(start - target) <= 0.5f) {
            setEdgeOverscrollDistance(target)
            if (target <= 0f) {
                resetEdgeOverscroll()
            }
            onEnd?.invoke()
            return
        }

        val spring = SpringInterpolator(
            damping = EDGE_MENU_SPRING_DAMPING,
            response = EDGE_MENU_SPRING_RESPONSE
        )
        val animator = ValueAnimator.ofFloat(start, target).apply {
            duration = spring.duration.coerceIn(180L, 600L)
            interpolator = spring
            addUpdateListener { update ->
                setEdgeOverscrollDistance(update.animatedValue as Float)
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (edgeOverscrollAnimator !== animator) return
                edgeOverscrollAnimator = null
                setEdgeOverscrollDistance(target)
                if (target <= 0f) {
                    resetEdgeOverscroll()
                }
                onEnd?.invoke()
            }
        })
        edgeOverscrollAnimator = animator
        animator.start()
    }

    private fun setEdgeOverscrollDistance(distance: Float) {
        val side = edgeActionSide ?: return
        val safeDistance = distance.coerceAtLeast(0f)
        edgeActionDistance = safeDistance
        val cardTranslation = hostTranslation + edgeActionTranslation()
        scrollView?.translationX = hostTranslation
        pageContainer?.translationX = edgeActionTranslation()
        onEdgeActionTranslationChanged(cardTranslation)
    }

    private fun edgeActionTranslation(): Float {
        val distance = edgeActionDistance
        return when (edgeActionSide) {
            EdgeActionSide.LEFT -> distance
            EdgeActionSide.RIGHT -> -distance
            null -> 0f
        }
    }

    private fun edgeDismissThreshold(): Float {
        val density = header?.resources?.displayMetrics?.density ?: 1f
        val width = header?.width?.takeIf { it > 0 } ?: pageWidthPx
        return maxOf(
            width * EDGE_DISMISS_FRACTION,
            FALLBACK_EDGE_DISMISS_DP * density
        )
    }

    private fun closeEdgeOverscroll(animated: Boolean) {
        if (animated && edgeActionDistance > 0.5f && !edgeClearPending) {
            animateEdgeOverscroll(targetDistance = 0f)
        } else {
            resetEdgeOverscroll()
        }
    }

    private fun resetEdgeOverscroll() {
        edgeOverscrollAnimator?.cancel()
        edgeOverscrollAnimator = null
        edgeActionSide = null
        edgeActionDistance = 0f
        directEdgeDismiss = false
        edgeClearPending = false
        pageContainer?.translationX = 0f
        onEdgeActionTranslationChanged(hostTranslation)
    }

    private fun findResourceId(context: Context, name: String, type: String): Int {
        return sequenceOf(context.packageName, SYSTEMUI_PACKAGE)
            .map { packageName -> context.resources.getIdentifier(name, type, packageName) }
            .firstOrNull { it != 0 }
            ?: 0
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
        if (pageWidthPx <= 0 || (target > 0 && pages.width == 0)) {
            scroller.post {
                if (generation == pageOrderGeneration && scrollView === scroller) {
                    scrollToPage(target, animate, generation)
                }
            }
            return
        }
        val stride = pageWidthPx + pageGapPx
        val contentWidth = sidePaddingPx * 2 + count * pageWidthPx +
            (count - 1).coerceAtLeast(0) * pageGapPx
        val viewportWidth = scroller.width.takeIf { it > 0 }
            ?: pageWidthPx + sidePaddingPx * 2
        val maxScroll = (contentWidth - viewportWidth).coerceAtLeast(0)
        val distanceFromFirst = target * stride
        val x = if (isLayoutRtl()) {
            (maxScroll - distanceFromFirst).coerceIn(0, maxScroll)
        } else {
            distanceFromFirst.coerceIn(0, maxScroll)
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
