package com.lidesheng.hyperlyric.root.mediacard.notification.background

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.TransitionDrawable
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.SystemUiEnhancementGate
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object NotificationMediaBackgroundController {
    private const val TAG = "NotificationMediaBackgroundController"
    private val states = Collections.synchronizedMap(WeakHashMap<Any, ControllerState>())
    private val unavailableLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val supportedLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val seekBarColors = Collections.synchronizedMap(WeakHashMap<SeekBar, Int>())
    private val seekBarStates = Collections.synchronizedMap(WeakHashMap<SeekBar, SeekBarState>())

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var executor: ExecutorService = newExecutor()

    private val prefs
        get() = (module as? HookEntry)?.prefs

    fun initialize(xposedModule: XposedModule) {
        module = xposedModule
        if (executor.isShutdown) executor = newExecutor()
    }

    fun isActive(controller: Any): Boolean {
        if (currentStyle() == RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT) return false
        val classLoader = controller.javaClass.classLoader ?: return false
        return supportedLoaders.contains(classLoader) && resolveRenderer(classLoader) != null
    }

    fun setNativeHooksAvailable(classLoader: ClassLoader, available: Boolean) {
        if (available) supportedLoaders.add(classLoader) else supportedLoaders.remove(classLoader)
    }

    fun onBind(controller: Any, mediaData: Any?) {
        val state = states.getOrPut(controller) { ControllerState() }
        state.lastMediaData = mediaData
        if (!isActive(controller)) {
            state.token = null
            state.customApplied = false
            state.renderPending = false
            return
        }
        mediaData ?: return
        val context = readField(controller, "context") as? Context ?: return
        val holder = readField(controller, "holder") ?: return
        val mediaBg = readField(holder, "mediaBg") as? ImageView ?: return
        if (state.mediaBg !== mediaBg || (!state.customApplied && !state.renderPending)) {
            captureNativeBackground(state, mediaBg)
        }
        val packageName = readField(mediaData, "packageName") as? String ?: return
        val artwork = readField(mediaData, "artwork") as? Icon
        val width = mediaBg.measuredWidth.takeIf { it > 0 }
            ?: mediaBg.layoutParams?.width?.takeIf { it > 0 }
            ?: return
        val height = mediaBg.measuredHeight.takeIf { it > 0 }
            ?: mediaBg.layoutParams?.height?.takeIf { it > 0 }
            ?: return
        val style = currentStyle()
        val blurAmount = currentBlurAmount()
        val autoInvert = currentAutoInvert()
        val softCoverTone = currentSoftCoverTone()
        val artworkUpdated = readField(controller, "isArtWorkUpdate") == true
        val token = "$style:$blurAmount:$autoInvert:$softCoverTone:$packageName:$width:$height"
        if (state.token == token && (state.customApplied || state.renderPending) && !artworkUpdated) {
            return
        }
        state.token = token
        state.renderPending = true
        val request = state.request.incrementAndGet()
        val renderer = resolveRenderer(controller.javaClass.classLoader) ?: return

        executor.execute {
            val rendered = runCatching {
                renderer.render(
                    context, artwork, packageName, style, blurAmount,
                    autoInvert, softCoverTone, width, height
                )
            }.onFailure { error ->
                HookLogger.e(TAG, "渲染通知中心媒体背景失败", error)
            }.getOrNull()
            if (rendered == null) {
                mediaBg.post {
                    if (states[controller] === state && state.request.get() == request) {
                        state.renderPending = false
                    }
                }
                return@execute
            }
            mediaBg.post {
                val current = states[controller]
                if (
                    current !== state || current.request.get() != request ||
                    currentStyle() != style || !isActive(controller)
                ) {
                    rendered.bitmap.recycle()
                    return@post
                }
                if (
                    state.customApplied && state.appliedToken == token &&
                    state.artworkFingerprint == rendered.artworkFingerprint
                ) {
                    rendered.bitmap.recycle()
                    state.renderPending = false
                    return@post
                }
                applyBackground(mediaBg, rendered.bitmap)
                applyForeground(holder, rendered.colors)
                state.customApplied = true
                state.appliedToken = token
                state.artworkFingerprint = rendered.artworkFingerprint
                state.renderPending = false
            }
        }
    }

    fun onDetach(controller: Any) {
        clearSeekBarColor(controller)
        states.remove(controller)?.let { state ->
            state.request.incrementAndGet()
            restoreMediaBackground(state)
        }
    }

    fun refresh(controllers: Collection<Any>, refreshNative: (Any) -> Unit) {
        val task = Runnable {
            controllers.forEach { controller ->
                val state = states.getOrPut(controller) { ControllerState() }
                state.token = null
                state.renderPending = false
                state.request.incrementAndGet()
                if (isActive(controller)) {
                    onBind(controller, state.lastMediaData)
                } else {
                    clearSeekBarColor(controller)
                    restoreMediaBackground(state)
                    state.customApplied = false
                    state.appliedToken = null
                    state.artworkFingerprint = null
                    refreshNative(controller)
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) task.run()
        else Handler(Looper.getMainLooper()).post(task)
    }

    fun applySeekBarColor(seekBar: Any) {
        val view = seekBar as? SeekBar ?: return
        val color = seekBarColors[view] ?: return
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        (readField(view, "mPaint") as? Paint)?.colorFilter = filter
        (readField(view, "mProgressDrawable") as? Drawable)?.colorFilter = filter
        (readField(view, "mBackgroundDrawable") as? Drawable)?.colorFilter =
            PorterDuffColorFilter(
                color and 0x00ffffff or (0x33 shl 24),
                PorterDuff.Mode.SRC_IN
            )
    }

    fun releaseAll() {
        executor.shutdownNow()
        val snapshot = synchronized(states) { states.values.toList() }
        snapshot.forEach { state ->
            state.request.incrementAndGet()
            restoreMediaBackground(state)
        }
        states.clear()
        unavailableLoaders.clear()
        supportedLoaders.clear()
        val seekBarSnapshot = synchronized(seekBarStates) { seekBarStates.toMap() }
        seekBarSnapshot.forEach { (seekBar, state) -> restoreSeekBarState(seekBar, state) }
        seekBarStates.clear()
        seekBarColors.clear()
    }

    private fun applyBackground(mediaBg: ImageView, bitmap: Bitmap) {
        mediaBg.setPadding(0, 0, 0, 0)
        mediaBg.clipToOutline = true
        val next = BitmapDrawable(mediaBg.resources, bitmap)
        if (!currentColorAnimation() || !mediaBg.isShown || !mediaBg.isAttachedToWindow) {
            mediaBg.setImageDrawable(next)
            return
        }
        val previous = mediaBg.drawable
        if (previous == null) {
            mediaBg.setImageDrawable(next)
            return
        }
        val transition = TransitionDrawable(arrayOf(previous, next)).apply {
            isCrossFadeEnabled = true
        }
        mediaBg.setImageDrawable(transition)
        transition.startTransition(333)
        mediaBg.postDelayed({
            if (mediaBg.drawable === transition) {
                mediaBg.setImageDrawable(next)
            }
        }, 350L)
    }

    private fun applyForeground(holder: Any, colors: NotificationMediaColorConfig) {
        val primary = ColorStateList.valueOf(colors.textPrimary)
        (readField(holder, "titleText") as? TextView)?.setTextColor(colors.textPrimary)
        (readField(holder, "artistText") as? TextView)?.setTextColor(colors.textSecondary)
        listOf("seamlessIcon", "action0", "action1", "action2", "action3", "action4")
            .forEach { fieldName ->
                (readField(holder, fieldName) as? ImageView)?.imageTintList = primary
            }
        (readField(holder, "elapsedTimeView") as? TextView)?.setTextColor(colors.textPrimary)
        (readField(holder, "totalTimeView") as? TextView)?.setTextColor(colors.textPrimary)
        val seekBar = readField(holder, "seekBar") as? SeekBar ?: return
        seekBarStates.getOrPut(seekBar) {
            SeekBarState(
                thumbTintList = seekBar.thumbTintList,
                progressTintList = seekBar.progressTintList,
                progressBackgroundTintList = seekBar.progressBackgroundTintList,
                paintColorFilter = (readField(seekBar, "mPaint") as? Paint)?.colorFilter,
                progressDrawableColorFilter =
                    (readField(seekBar, "mProgressDrawable") as? Drawable)?.colorFilter,
                backgroundDrawableColorFilter =
                    (readField(seekBar, "mBackgroundDrawable") as? Drawable)?.colorFilter
            )
        }
        seekBar.thumbTintList = primary
        seekBar.progressTintList = primary
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(
            colors.textPrimary and 0x00ffffff or (0x33 shl 24)
        )
        seekBarColors[seekBar] = colors.textPrimary
        seekBar.invalidate()
    }

    private fun clearSeekBarColor(controller: Any) {
        val holder = readField(controller, "holder") ?: return
        val seekBar = readField(holder, "seekBar") as? SeekBar ?: return
        seekBarColors.remove(seekBar)
        seekBarStates.remove(seekBar)?.let { state ->
            restoreSeekBarState(seekBar, state)
        }
    }

    private fun restoreSeekBarState(seekBar: SeekBar, state: SeekBarState) {
        seekBar.thumbTintList = state.thumbTintList
        seekBar.progressTintList = state.progressTintList
        seekBar.progressBackgroundTintList = state.progressBackgroundTintList
        (readField(seekBar, "mPaint") as? Paint)?.colorFilter = state.paintColorFilter
        (readField(seekBar, "mProgressDrawable") as? Drawable)?.colorFilter =
            state.progressDrawableColorFilter
        (readField(seekBar, "mBackgroundDrawable") as? Drawable)?.colorFilter =
            state.backgroundDrawableColorFilter
        seekBar.invalidate()
    }

    private fun captureNativeBackground(state: ControllerState, mediaBg: ImageView) {
        state.mediaBg = mediaBg
        state.originalDrawable = mediaBg.drawable
        state.originalScaleType = mediaBg.scaleType
        state.originalClipToOutline = mediaBg.clipToOutline
        state.originalPadding = intArrayOf(
            mediaBg.paddingLeft,
            mediaBg.paddingTop,
            mediaBg.paddingRight,
            mediaBg.paddingBottom
        )
    }

    private fun restoreMediaBackground(state: ControllerState) {
        val mediaBg = state.mediaBg ?: return
        mediaBg.setImageDrawable(state.originalDrawable)
        state.originalScaleType?.let { mediaBg.scaleType = it }
        val padding = state.originalPadding
        mediaBg.setPadding(padding[0], padding[1], padding[2], padding[3])
        mediaBg.clipToOutline = state.originalClipToOutline
        mediaBg.invalidate()
    }

    private fun resolveRenderer(classLoader: ClassLoader?): NotificationMediaBackgroundRenderer? {
        classLoader ?: return null
        if (unavailableLoaders.contains(classLoader)) return null
        return runCatching { MediaBackgroundRendererPool.get(classLoader) }
            .onFailure { error ->
                unavailableLoaders.add(classLoader)
            HookLogger.w(TAG, "通知中心 Monet 背景接口不可用: reason=${error.message}")
            }
            .getOrNull()
    }

    private fun currentStyle(): Int {
        if (!SystemUiEnhancementGate.isEnabled()) {
            return RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT
        }
        return prefs?.getInt(
            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE,
            RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE
        )?.coerceIn(
            RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT,
            RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER
        ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE
    }

    private fun currentSoftCoverTone(): Int = prefs?.getInt(
        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE,
        RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE
    )?.coerceIn(
        RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
        RootConstants.MEDIA_SOFT_COVER_TONE_DARK
    ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE

    private fun currentBlurAmount(): Int = prefs?.getInt(
        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR,
        RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR
    )?.coerceIn(1, 20) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR

    private fun currentAutoInvert(): Boolean = prefs?.getBoolean(
        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
        RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT
    ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT

    private fun currentColorAnimation(): Boolean = prefs?.getBoolean(
        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
        RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION
    ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION

    private fun readField(receiver: Any, name: String): Any? {
        return findField(receiver.javaClass, name)?.let { field ->
            runCatching { field.get(receiver) }.getOrNull()
        }
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        return null
    }

    private fun newExecutor(): ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HyperLyric-MediaBackground").apply { isDaemon = true }
    }

    private data class ControllerState(
        var lastMediaData: Any? = null,
        var token: String? = null,
        var appliedToken: String? = null,
        var artworkFingerprint: Long? = null,
        var customApplied: Boolean = false,
        var renderPending: Boolean = false,
        var mediaBg: ImageView? = null,
        var originalDrawable: Drawable? = null,
        var originalScaleType: ImageView.ScaleType? = null,
        var originalPadding: IntArray = intArrayOf(0, 0, 0, 0),
        var originalClipToOutline: Boolean = false,
        val request: AtomicInteger = AtomicInteger()
    )

    private data class SeekBarState(
        val thumbTintList: ColorStateList?,
        val progressTintList: ColorStateList?,
        val progressBackgroundTintList: ColorStateList?,
        val paintColorFilter: ColorFilter?,
        val progressDrawableColorFilter: ColorFilter?,
        val backgroundDrawableColorFilter: ColorFilter?
    )
}
