package com.lidesheng.hyperlyric.root.mediacard.island.background

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.mediacard.notification.background.MediaBackgroundRendererPool
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaColorConfig
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal data class IslandExpandedBackgroundTarget(
    val owner: View,
    val expandedView: View,
    val customBackgroundView: View = expandedView,
    val extensionBackgroundView: View? = null,
    val viewportWidth: Int? = null,
    val viewportHeight: Int? = null,
    val transitionContentBounds: Rect? = null,
    val transitionOccludingViews: List<View> = emptyList(),
    val nativeBackgroundViews: List<View> = listOf(expandedView)
)

internal data class IslandExpandedMediaBackgroundHost(
    val target: IslandExpandedBackgroundTarget,
    val holder: Any,
    val miniBar: View?
)

internal interface IslandExpandedMediaBackgroundApi {
    fun getContext(binder: Any): Context
    fun getPackageName(binder: Any): String?
    fun getArtwork(binder: Any): Drawable?
    fun getBackgroundHosts(binder: Any): List<IslandExpandedMediaBackgroundHost>
    fun prepareCustomBackground(target: IslandExpandedBackgroundTarget)
    fun restoreNativeBackground(target: IslandExpandedBackgroundTarget)
    fun applyCustomForeground(holder: Any, colors: NotificationMediaColorConfig)
}

internal object IslandExpandedMediaBackgroundController {
    private const val TAG = "IslandExpandedMediaBg"
    private val states = Collections.synchronizedMap(WeakHashMap<Any, BinderState>())
    private val fakeStates = Collections.synchronizedMap(
        WeakHashMap<View, FakeState>()
    )
    private val foregroundColors = Collections.synchronizedMap(
        WeakHashMap<Any, NotificationMediaColorConfig>()
    )
    private val latestLock = Any()

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var executor: ExecutorService = newExecutor()

    private var latestPresentation: LatestPresentation? = null

    private val prefs: SharedPreferences?
        get() = (module as? HookEntry)?.prefs

    fun initialize(xposedModule: XposedModule) {
        module = xposedModule
        if (executor.isShutdown) executor = newExecutor()
    }

    fun isActive(): Boolean {
        return currentStyle() != RootConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT
    }

    fun apply(binder: Any, api: IslandExpandedMediaBackgroundApi) {
        if (!isActive()) {
            restore(binder)
            return
        }
        val context = api.getContext(binder)
        val packageName = api.getPackageName(binder) ?: return
        val artwork = api.getArtwork(binder)
        val hosts = api.getBackgroundHosts(binder)
        if (hosts.isEmpty()) return

        val binderState = states.getOrPut(binder) { BinderState(api) }
        binderState.api = api
        val activeViews = hosts.mapTo(HashSet()) { it.target.expandedView }
        binderState.targets.entries.removeAll { (view, state) ->
            if (view in activeViews) return@removeAll false
            state.request.incrementAndGet()
            restoreTarget(state, api)
            true
        }

        hosts.groupBy { it.target.expandedView }.values.forEach { groupedHosts ->
            applyTarget(
                binder,
                binderState,
                groupedHosts,
                context,
                packageName,
                artwork,
                api
            )
        }
    }

    fun restore(binder: Any) {
        val state = states.remove(binder) ?: return
        state.targets.values.forEach { target ->
            target.request.incrementAndGet()
            restoreTarget(target, state.api)
        }
    }

    fun applyFakeTransition(
        target: IslandExpandedBackgroundTarget,
        miniBar: View?,
        api: IslandExpandedMediaBackgroundApi
    ): Boolean {
        if (!isActive()) return false
        api.prepareCustomBackground(target)
        val view = target.customBackgroundView
        val transitionRoot = target.extensionBackgroundView ?: return false
        val presentation = acquireLatestPresentation() ?: return false
        var retainedByState = false
        try {
            val state = fakeStates[view]
            if (state?.identity == presentation.identity) {
                state.originalOccludingBackgrounds.forEach { (occludingView, _) ->
                    occludingView.background = null
                }
                transitionRoot.background = state.transitionBackground
                view.background = null
            } else {
                val originalTransitionBackground = state?.originalTransitionBackground
                    ?: transitionRoot.background
                val originalOccludingBackgrounds = state?.originalOccludingBackgrounds
                    ?: target.transitionOccludingViews.map { it to it.background }
                originalOccludingBackgrounds.forEach { (occludingView, _) ->
                    occludingView.background = null
                }
                val transitionBackground = FakeTransitionBackgroundDrawable(
                    presentation.bitmap,
                    target.transitionContentBounds
                        ?: descendantBounds(transitionRoot, target.expandedView),
                    presentation.visibleSource,
                    presentation.colors.backgroundEnd
                )
                transitionRoot.background = transitionBackground
                view.background = null
                val previous = fakeStates.put(
                    view,
                    FakeState(
                        presentation.identity,
                        transitionBackground,
                        presentation,
                        target,
                        api,
                        originalTransitionBackground,
                        originalOccludingBackgrounds
                    )
                )
                retainedByState = true
                previous?.let(::releaseFakeState)
            }
            applyMiniBarTint(miniBar, presentation.colors.textPrimary.withAlpha(0x99))
            return true
        } finally {
            if (!retainedByState) releasePresentation(presentation)
        }
    }

    fun applyForeground(binder: Any, api: IslandExpandedMediaBackgroundApi) {
        states[binder]?.targets?.values?.forEach { target ->
            val colors = target.colors ?: return@forEach
            target.holders.forEach { holder -> applyForeground(holder, colors, api) }
        }
    }

    fun applyForeground(
        binder: Any,
        holder: Any,
        api: IslandExpandedMediaBackgroundApi
    ): Boolean {
        val colors = states[binder]
            ?.targets
            ?.values
            ?.firstNotNullOfOrNull(TargetState::colors)
            ?: return false
        applyForeground(holder, colors, api)
        return true
    }

    fun onExpandedViewShown(view: View) {
        if (!isActive()) return
        val matches = synchronized(states) {
            states.entries.mapNotNull { (binder, binderState) ->
                binderState.targets.values.firstOrNull {
                    it.target.expandedView === view
                }?.let { targetState ->
                    ExpandedTargetBinding(binder, binderState, targetState)
                }
            }
        }
        if (matches.isEmpty()) return
        view.postOnAnimation {
            matches.forEach { binding ->
                if (states[binding.binder] !== binding.binderState || !isActive()) {
                    return@forEach
                }
                apply(binding.binder, binding.binderState.api)
            }
        }
    }

    fun shouldSkipNativeBackgroundUpdate(view: View): Boolean {
        if (!isActive()) return false
        val hasRealBackground = synchronized(states) {
            states.values.any { binderState ->
                binderState.targets.values.any { targetState ->
                    targetState.customApplied &&
                        targetState.background != null &&
                        targetState.target.nativeBackgroundViews.any { it === view }
                }
            }
        }
        if (hasRealBackground) return true
        return synchronized(fakeStates) {
            fakeStates.values.any { state ->
                state.target.nativeBackgroundViews.any { it === view }
            }
        }
    }

    fun restoreFakeTransition(
        target: IslandExpandedBackgroundTarget,
        api: IslandExpandedMediaBackgroundApi
    ) {
        val state = fakeStates.remove(target.customBackgroundView) ?: return
        state.target.extensionBackgroundView?.background = state.originalTransitionBackground
        state.originalOccludingBackgrounds.forEach { (view, background) ->
            view.background = background
        }
        api.restoreNativeBackground(target)
        releaseFakeState(state)
    }

    fun releaseAll() {
        executor.shutdownNow()
        val snapshot = synchronized(states) { states.values.toList() }
        val fakeSnapshot = synchronized(fakeStates) { fakeStates.values.toList() }
        states.clear()
        fakeStates.clear()
        foregroundColors.clear()
        retireLatestPresentation()
        val cleanup = Runnable {
            snapshot.forEach { state ->
                state.targets.values.forEach { target ->
                    target.request.incrementAndGet()
                    restoreTarget(target, state.api)
                }
            }
            fakeSnapshot.forEach { state ->
                state.target.extensionBackgroundView?.background =
                    state.originalTransitionBackground
                state.originalOccludingBackgrounds.forEach { (view, background) ->
                    view.background = background
                }
                state.api.restoreNativeBackground(state.target)
                releaseFakeState(state)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run()
        else Handler(Looper.getMainLooper()).post(cleanup)
    }

    private fun applyTarget(
        binder: Any,
        binderState: BinderState,
        hosts: List<IslandExpandedMediaBackgroundHost>,
        context: Context,
        packageName: String,
        artwork: Drawable?,
        api: IslandExpandedMediaBackgroundApi
    ) {
        val host = hosts.first()
        val view = host.target.expandedView
        val targetState = binderState.targets.getOrPut(view) {
            TargetState(
                target = host.target,
                miniBar = host.miniBar,
                originalMiniBarTint = host.miniBar?.backgroundTintList,
                holders = hosts.map { it.holder }
            )
        }
        targetState.target = host.target
        if (targetState.miniBar !== host.miniBar) {
            targetState.miniBar?.backgroundTintList = targetState.originalMiniBarTint
            targetState.miniBar = host.miniBar
            targetState.originalMiniBarTint = host.miniBar?.backgroundTintList
            targetState.appliedMiniBarColor = null
        }
        val nextHolders = hosts.map { it.holder }
        targetState.holders
            .filter { previous -> nextHolders.none { current -> current === previous } }
            .forEach(foregroundColors::remove)
        targetState.holders = nextHolders
        val width = host.target.viewportWidth?.takeIf { it > 0 }
            ?: view.width.takeIf { it > 0 }
            ?: view.measuredWidth.takeIf { it > 0 }
        val height = host.target.viewportHeight?.takeIf { it > 0 }
            ?: view.height.takeIf { it > 0 }
            ?: view.measuredHeight.takeIf { it > 0 }
        if (width == null || height == null) {
            if (!targetState.layoutPending) {
                targetState.layoutPending = true
                view.post {
                    targetState.layoutPending = false
                    if (states[binder] === binderState && isActive()) apply(binder, api)
                }
            }
            return
        }

        val style = currentStyle()
        if (
            targetState.customApplied && targetState.appliedStyle != null &&
            targetState.appliedStyle != style && !view.isShown
        ) {
            targetState.request.incrementAndGet()
            targetState.token = null
            targetState.renderPending = false
            return
        }
        val blurAmount = currentBlurAmount()
        val autoInvert = currentAutoInvert()
        val token = "$style:$blurAmount:$autoInvert:$packageName:$width:$height"
        if (
            targetState.customApplied && targetState.token == token &&
            targetState.lastArtwork === artwork
        ) {
            ensureBackgroundAttached(targetState, api)
            targetState.colors?.let { colors ->
                hosts.forEach { applyForeground(it.holder, colors, api) }
                applyMiniBarTint(targetState, colors.textPrimary.withAlpha(0x99))
            }
            return
        }

        targetState.token = token
        targetState.lastArtwork = artwork
        targetState.renderPending = true
        val request = targetState.request.incrementAndGet()
        val classLoader = binder.javaClass.classLoader ?: return

        executor.execute {
            val renderer = runCatching { MediaBackgroundRendererPool.get(classLoader) }
                .onFailure { error ->
                    HookLogger.e(TAG, "Failed to initialize media background renderer", error)
                }
                .getOrNull()
            if (renderer == null) {
                view.post {
                    if (states[binder] === binderState && targetState.request.get() == request) {
                        targetState.renderPending = false
                    }
                }
                return@execute
            }
            val renderHeight = if (
                style == RootConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT
            ) {
                height
            } else {
                maxOf(width, height)
            }
            val rendered = runCatching {
                renderer.renderDrawable(
                    context,
                    artwork,
                    packageName,
                    style,
                    blurAmount,
                    autoInvert,
                    width,
                    renderHeight
                )
            }.onFailure { error ->
                HookLogger.e(TAG, "Failed to render expanded media background", error)
            }.getOrNull()
            if (rendered == null) {
                view.post {
                    if (states[binder] === binderState && targetState.request.get() == request) {
                        targetState.renderPending = false
                    }
                }
                return@execute
            }
            val presentationBitmap = rendered.bitmap.copy(Bitmap.Config.ARGB_8888, false)

            view.post {
                if (
                    states[binder] !== binderState || targetState.request.get() != request ||
                    currentStyle() != style || !isActive()
                ) {
                    rendered.bitmap.recycle()
                    presentationBitmap.recycle()
                    return@post
                }
                if (
                    targetState.customApplied && targetState.appliedToken == token &&
                    targetState.artworkFingerprint == rendered.artworkFingerprint
                ) {
                    rendered.bitmap.recycle()
                    ensureBackgroundAttached(targetState, api)
                    hosts.forEach { applyForeground(it.holder, rendered.colors, api) }
                    applyMiniBarTint(
                        targetState,
                        rendered.colors.textPrimary.withAlpha(0x99)
                    )
                    targetState.colors = rendered.colors
                    targetState.renderPending = false
                    updateLatest(
                        token,
                        rendered.artworkFingerprint,
                        presentationBitmap,
                        rendered.colors,
                        width,
                        height
                    )
                    return@post
                }

                api.prepareCustomBackground(targetState.target)
                setBackground(
                    targetState,
                    rendered.bitmap,
                    currentColorAnimation() && targetState.appliedStyle == style
                )
                hosts.forEach { applyForeground(it.holder, rendered.colors, api) }
                applyMiniBarTint(targetState, rendered.colors.textPrimary.withAlpha(0x99))
                targetState.customApplied = true
                targetState.appliedStyle = style
                targetState.appliedToken = token
                targetState.artworkFingerprint = rendered.artworkFingerprint
                targetState.colors = rendered.colors
                targetState.renderPending = false
                updateLatest(
                    token,
                    rendered.artworkFingerprint,
                    presentationBitmap,
                    rendered.colors,
                    width,
                    height
                )
            }
        }
    }

    private fun restoreTarget(
        state: TargetState,
        api: IslandExpandedMediaBackgroundApi
    ) {
        state.miniBar?.backgroundTintList = state.originalMiniBarTint
        state.appliedMiniBarColor = null
        state.holders.forEach(foregroundColors::remove)
        val customApplied = state.customApplied
        state.customApplied = false
        if (customApplied) api.restoreNativeBackground(state.target)
        state.appliedStyle = null
        state.renderPending = false
        state.appliedToken = null
        state.artworkFingerprint = null
        state.colors = null
        state.background = null
        state.transitionBackground = null
    }

    private fun ensureBackgroundAttached(
        state: TargetState,
        api: IslandExpandedMediaBackgroundApi
    ) {
        val background = state.background ?: return
        val view = state.target.customBackgroundView
        val current = view.background
        if (current === background || current === state.transitionBackground) return
        api.prepareCustomBackground(state.target)
        view.background = background
        state.transitionBackground = null
    }

    private fun setBackground(state: TargetState, bitmap: Bitmap, animate: Boolean) {
        val view = state.target.customBackgroundView
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val next = FixedViewportBitmapDrawable(
            bitmap,
            state.target.viewportWidth?.takeIf { it > 0 } ?: width,
            state.target.viewportHeight?.takeIf { it > 0 } ?: height
        ).apply {
            setBounds(0, 0, width, height)
        }
        state.background = next
        if (!animate || !view.isShown || !view.isAttachedToWindow) {
            view.background = next
            state.transitionBackground = null
            view.invalidate()
            return
        }
        val previous = view.background
        if (previous == null) {
            view.background = next
            state.transitionBackground = null
            return
        }
        val transition = TransitionDrawable(arrayOf(previous, next)).apply {
            isCrossFadeEnabled = true
            setBounds(0, 0, width, height)
        }
        state.transitionBackground = transition
        view.background = transition
        transition.startTransition(333)
        view.postDelayed({
            if (view.background === transition) {
                next.setBounds(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                view.background = next
                view.invalidate()
            }
            if (state.transitionBackground === transition) state.transitionBackground = null
        }, 350L)
        view.postOnAnimation {
            view.background?.setBounds(
                0,
                0,
                view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1)
            )
            view.invalidate()
        }
    }

    private fun updateLatest(
        token: String,
        fingerprint: Long,
        presentationBitmap: Bitmap,
        colors: NotificationMediaColorConfig,
        viewportWidth: Int,
        viewportHeight: Int
    ) {
        val identity = "$token:$fingerprint"
        val visibleSource = centerCropSourceRect(
            presentationBitmap.width,
            presentationBitmap.height,
            viewportWidth,
            viewportHeight
        )
        var recycleNew = false
        var retiredBitmap: Bitmap? = null
        synchronized(latestLock) {
            if (latestPresentation?.identity == identity) {
                recycleNew = true
            } else {
                latestPresentation?.let { previous ->
                    previous.retired = true
                    if (previous.referenceCount == 0) retiredBitmap = previous.bitmap
                }
                latestPresentation = LatestPresentation(
                    identity,
                    presentationBitmap,
                    colors,
                    visibleSource
                )
            }
        }
        if (recycleNew && !presentationBitmap.isRecycled) presentationBitmap.recycle()
        retiredBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
    }

    private fun acquireLatestPresentation(): LatestPresentation? {
        return synchronized(latestLock) {
            latestPresentation?.also { it.referenceCount++ }
        }
    }

    private fun releasePresentation(presentation: LatestPresentation) {
        val bitmap = synchronized(latestLock) {
            if (presentation.referenceCount > 0) presentation.referenceCount--
            if (presentation.retired && presentation.referenceCount == 0) {
                presentation.bitmap
            } else {
                null
            }
        }
        bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
    }

    private fun retireLatestPresentation() {
        val bitmap = synchronized(latestLock) {
            val latest = latestPresentation ?: return@synchronized null
            latestPresentation = null
            latest.retired = true
            latest.bitmap.takeIf { latest.referenceCount == 0 }
        }
        bitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
    }

    private fun releaseFakeState(state: FakeState) {
        releasePresentation(state.presentation)
    }

    private fun applyForeground(
        holder: Any,
        colors: NotificationMediaColorConfig,
        api: IslandExpandedMediaBackgroundApi
    ) {
        if (foregroundColors[holder] == colors) return
        api.applyCustomForeground(holder, colors)
        foregroundColors[holder] = colors
    }

    private fun applyMiniBarTint(state: TargetState, color: Int) {
        if (state.appliedMiniBarColor == color) return
        applyMiniBarTint(state.miniBar, color)
        state.appliedMiniBarColor = color
    }

    private fun applyMiniBarTint(view: View?, color: Int) {
        view ?: return
        if (view.backgroundTintList?.defaultColor == color) return
        view.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun descendantBounds(root: View, descendant: View): Rect {
        val rootLocation = IntArray(2)
        val descendantLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        descendant.getLocationInWindow(descendantLocation)
        val left = descendantLocation[0] - rootLocation[0]
        val top = descendantLocation[1] - rootLocation[1]
        return Rect(left, top, left + descendant.width, top + descendant.height)
    }

    private fun centerCropSourceRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Rect {
        return if (bitmapWidth.toLong() * targetHeight > bitmapHeight.toLong() * targetWidth) {
            val sourceWidth = (bitmapHeight.toLong() * targetWidth / targetHeight)
                .toInt()
                .coerceIn(1, bitmapWidth)
            val left = (bitmapWidth - sourceWidth) / 2
            Rect(left, 0, left + sourceWidth, bitmapHeight)
        } else {
            val sourceHeight = (bitmapWidth.toLong() * targetHeight / targetWidth)
                .toInt()
                .coerceIn(1, bitmapHeight)
            val top = (bitmapHeight - sourceHeight) / 2
            Rect(0, top, bitmapWidth, top + sourceHeight)
        }
    }

    private fun currentStyle(): Int = prefs?.getInt(
        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE,
        RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE
    )?.coerceIn(
        RootConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT,
        RootConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT
    ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE

    private fun currentBlurAmount(): Int = prefs?.getInt(
        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR,
        RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR
    )?.coerceIn(1, 20) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR

    private fun currentAutoInvert(): Boolean = prefs?.getBoolean(
        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
        RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT
    ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT

    private fun currentColorAnimation(): Boolean = prefs?.getBoolean(
        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
        RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION
    ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION

    private fun Int.withAlpha(alpha: Int): Int {
        return this and 0x00ffffff or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun newExecutor(): ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HyperLyric-IslandMediaBackground").apply { isDaemon = true }
    }

    private data class BinderState(
        var api: IslandExpandedMediaBackgroundApi,
        val targets: MutableMap<View, TargetState> = WeakHashMap()
    )

    private data class ExpandedTargetBinding(
        val binder: Any,
        val binderState: BinderState,
        val targetState: TargetState
    )

    private data class TargetState(
        var target: IslandExpandedBackgroundTarget,
        var miniBar: View?,
        var originalMiniBarTint: ColorStateList?,
        var holders: List<Any>,
        var token: String? = null,
        var appliedToken: String? = null,
        var artworkFingerprint: Long? = null,
        var lastArtwork: Drawable? = null,
        var colors: NotificationMediaColorConfig? = null,
        var background: Drawable? = null,
        var transitionBackground: Drawable? = null,
        var customApplied: Boolean = false,
        var appliedStyle: Int? = null,
        var appliedMiniBarColor: Int? = null,
        var renderPending: Boolean = false,
        var layoutPending: Boolean = false,
        val request: AtomicInteger = AtomicInteger()
    )

    private class LatestPresentation(
        val identity: String,
        val bitmap: Bitmap,
        val colors: NotificationMediaColorConfig,
        val visibleSource: Rect,
        var referenceCount: Int = 0,
        var retired: Boolean = false
    )

    private data class FakeState(
        val identity: String,
        val transitionBackground: Drawable,
        val presentation: LatestPresentation,
        val target: IslandExpandedBackgroundTarget,
        val api: IslandExpandedMediaBackgroundApi,
        val originalTransitionBackground: Drawable?,
        val originalOccludingBackgrounds: List<Pair<View, Drawable?>>
    )

    private class FixedViewportBitmapDrawable(
        private val bitmap: Bitmap,
        viewportWidth: Int,
        viewportHeight: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val viewportWidth = viewportWidth.coerceAtLeast(1)
        private val viewportHeight = viewportHeight.coerceAtLeast(1)
        private val source = centerCropSourceRect(
            bitmap.width,
            bitmap.height,
            this.viewportWidth,
            this.viewportHeight
        )
        private val destination = Rect()

        override fun draw(canvas: Canvas) {
            if (bitmap.isRecycled || bounds.isEmpty) return
            val destinationHeight = (
                bounds.width().toLong() * viewportHeight / viewportWidth
                ).toInt().coerceIn(1, bounds.height())
            destination.set(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.top + destinationHeight
            )
            canvas.drawBitmap(bitmap, source, destination, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = bitmap.width

        override fun getIntrinsicHeight(): Int = bitmap.height
    }

    private class FakeTransitionBackgroundDrawable(
        private val bitmap: Bitmap,
        private val contentBounds: Rect,
        initialSource: Rect,
        private val fallbackColor: Int
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val fillPaint = Paint().apply { color = fallbackColor }
        private val visibleSource = Rect(initialSource)
        private val source = Rect()
        private val destination = Rect()
        private val bitmapDestination = Rect()

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val left = contentBounds.left.coerceIn(bounds.left, bounds.right)
            val right = contentBounds.right.coerceIn(bounds.left, bounds.right)
            val top = contentBounds.top.coerceIn(bounds.top, bounds.bottom)
            if (left >= right || top >= bounds.bottom) return
            destination.set(left, top, right, bounds.bottom)
            canvas.drawRect(destination, fillPaint)
            if (bitmap.isRecycled || destination.width() <= 0 || destination.height() <= 0) return

            val availableSourceHeight = bitmap.height - visibleSource.top
            if (availableSourceHeight <= 0) return
            val sourceHeight = (
                destination.height().toLong() * visibleSource.width() / destination.width()
                ).toInt().coerceIn(1, availableSourceHeight)
            val destinationHeight = (
                sourceHeight.toLong() * destination.width() / visibleSource.width()
                ).toInt().coerceIn(1, destination.height())
            source.set(
                visibleSource.left,
                visibleSource.top,
                visibleSource.right,
                visibleSource.top + sourceHeight
            )
            bitmapDestination.set(
                destination.left,
                destination.top,
                destination.right,
                destination.top + destinationHeight
            )
            canvas.drawBitmap(bitmap, source, bitmapDestination, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            fillPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    }
}
