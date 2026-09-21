package com.lidesheng.hyperlyric.root.mediacard.notification.style

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SquigglySeekBar
import com.lidesheng.hyperlyric.root.mediacard.style.MediaCardForegroundColors
import com.lidesheng.hyperlyric.root.mediacard.style.MediaCardForegroundColorSource
import com.lidesheng.hyperlyric.root.mediacard.style.MediaCardForegroundPalette
import com.lidesheng.hyperlyric.root.mediacard.style.MediaCardForegroundPaletteResolver
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Applies the notification-center media foreground as one semantic palette.
 *
 * Background renderers own pixels and publish the tone measured from the
 * final bitmap. They do not own title, action, time, or progress colors. This
 * keeps a background-style change from silently changing individual roles.
 */
internal object NotificationMediaForegroundStyler {
    private const val TAG = "NotificationMediaForegroundStyler"

    private val foregroundIconFields = arrayOf(
        "seamlessIcon", "action0", "action1", "action2", "action3", "action4"
    )
    private val appliedPalettes = Collections.synchronizedMap(
        WeakHashMap<Any, MediaCardForegroundPalette>()
    )
    private val foregroundStates = Collections.synchronizedMap(
        WeakHashMap<Any, HolderForegroundState>()
    )
    private val seekBarColors = Collections.synchronizedMap(
        WeakHashMap<SeekBar, SeekBarColorState>()
    )
    @Volatile
    private var foregroundColorsAppliedListener: ((Any) -> Unit)? = null
    private val foregroundColorsAppliedListeners = CopyOnWriteArrayList<(Any) -> Unit>()

    fun setAppliedListener(listener: ((Any) -> Unit)?) {
        foregroundColorsAppliedListener = listener
    }

    fun addAppliedListener(listener: (Any) -> Unit) {
        foregroundColorsAppliedListeners.addIfAbsent(listener)
    }

    fun apply(
        controller: Any,
        backgroundIsDark: Boolean,
        foregroundSource: MediaCardForegroundColorSource? = null
    ) {
        val holder = readField(controller, "holder") ?: return
        apply(controller, holder, backgroundIsDark, foregroundSource)
    }

    fun apply(
        controller: Any,
        holder: Any,
        backgroundIsDark: Boolean,
        foregroundSource: MediaCardForegroundColorSource? = null
    ) {
        val context = readField(controller, "context") as? Context
        val colors = MediaCardForegroundPaletteResolver.resolve(
            context = context,
            backgroundIsDark = backgroundIsDark,
            foregroundSource = foregroundSource
        ) ?: run {
            clear(controller)
            return
        }
        val oldState = foregroundStates[controller]
        if (oldState != null && oldState.holder !== holder) {
            oldState.restore()
            foregroundStates.remove(controller)
        }
        val state = foregroundStates.getOrPut(controller) {
            HolderForegroundState(holder)
        }
        applyForeground(holder, colors, state)
        appliedPalettes[controller] = colors
        notifyApplied(controller)
    }

    fun clear(controller: Any) {
        val hadState = foregroundStates.remove(controller)?.also { it.restore() } != null
        val hadPalette = appliedPalettes.remove(controller) != null
        if (hadState || hadPalette) notifyApplied(controller)
    }

    fun hasAppliedPalette(controller: Any): Boolean = appliedPalettes.containsKey(controller)

    /** Returns the semantic foreground roles currently visible on the card. */
    fun foregroundColors(controller: Any): MediaCardForegroundColors? {
        appliedPalettes[controller]?.let { colors ->
            return MediaCardForegroundColors(
                primary = colors.primary,
                secondary = colors.secondary
            )
        }
        return nativeForegroundColors(controller)
    }

    /** Returns the primary foreground currently visible on the card. */
    fun foregroundColor(controller: Any): Int? {
        return foregroundColors(controller)?.primary
    }

    /** Resolves and applies the shared progress foreground and track. */
    fun applyProgressColors(
        controller: Any,
        seekBar: SeekBar
    ) {
        val colors = appliedPalettes[controller]?.let { palette ->
            MediaCardForegroundColors(
                primary = palette.primary,
                secondary = palette.secondary
            )
        } ?: nativeForegroundColors(controller) ?: return

        val tint = ColorStateList.valueOf(colors.primary)
        seekBar.thumbTintList = tint
        seekBar.progressTintList = tint
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(colors.secondary)
        if (seekBar is SquigglySeekBar) {
            seekBar.setWaveColors(colors.primary, colors.secondary)
        }
        seekBarColors[seekBar] = SeekBarColorState(colors.primary, colors.secondary)
        applySeekBarForegroundColor(seekBar, colors.primary)
        applySeekBarTrackColor(seekBar, colors.secondary)
        seekBar.invalidate()
    }

    fun applySeekBarColor(seekBar: Any) {
        val view = seekBar as? SeekBar ?: return
        val colors = seekBarColors[view] ?: return
        if (view is SquigglySeekBar) {
            view.setWaveColors(colors.foreground, colors.track)
        }
        applySeekBarForegroundColor(view, colors.foreground)
        applySeekBarTrackColor(view, colors.track)
    }

    private fun applySeekBarForegroundColor(view: SeekBar, color: Int) {
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        (readField(view, "mPaint") as? Paint)?.colorFilter = filter
        (readField(view, "mProgressDrawable") as? Drawable)?.colorFilter = filter
    }

    private fun applyForeground(
        holder: Any,
        colors: MediaCardForegroundPalette,
        state: HolderForegroundState
    ) {
        val title = readField(holder, "titleText") as? TextView
        title?.let {
            state.captureText(it)
            it.setTextColor(colors.primary)
        }
        val artist = readField(holder, "artistText") as? TextView
        artist?.let {
            state.captureText(it)
            it.setTextColor(colors.secondary)
        }
        foregroundIconFields.forEach { fieldName ->
            (readField(holder, fieldName) as? ImageView)?.let { icon ->
                state.captureIcon(icon)
                icon.imageTintList = ColorStateList.valueOf(colors.primary)
                icon.invalidate()
            }
        }
        listOf("elapsedTimeView", "totalTimeView").forEach { fieldName ->
            (readField(holder, fieldName) as? TextView)?.let { time ->
                state.captureText(time)
                time.setTextColor(colors.secondary)
            }
        }

        val nativeSeekBar = readField(holder, "seekBar") as? SeekBar
        val seekBar = MediaProgressStyleHooker.replacementSeekBar(holder) ?: nativeSeekBar ?: return
        state.captureSeekBar(seekBar)
        val tint = ColorStateList.valueOf(colors.primary)
        seekBar.thumbTintList = tint
        seekBar.progressTintList = tint
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(colors.secondary)
        if (seekBar is SquigglySeekBar) {
            seekBar.setWaveColors(colors.primary, colors.secondary)
        }
        seekBarColors[seekBar] = SeekBarColorState(colors.primary, colors.secondary)
        applySeekBarForegroundColor(seekBar, colors.primary)
        applySeekBarTrackColor(seekBar, colors.secondary)
        seekBar.invalidate()
    }

    /**
     * Uses the foreground already resolved by native SystemUI when the card
     * has no custom background palette. This is needed for replacement views
     * such as the native glow SeekBar: copying the old View's initial tint can
     * otherwise leave the replacement white after SystemUI turns the card
     * foreground black.
     */
    private fun nativeForegroundColors(controller: Any): MediaCardForegroundColors? {
        val holder = readField(controller, "holder") ?: return null
        val primary = foregroundIconFields.asSequence()
            .mapNotNull { fieldName ->
                (readField(holder, fieldName) as? ImageView)?.imageTintList?.defaultColor
            }
            .firstOrNull()
            ?: (readField(holder, "titleText") as? TextView)?.currentTextColor
            ?: (readField(holder, "artistText") as? TextView)?.currentTextColor
            ?: return null
        val secondary = (readField(holder, "artistText") as? TextView)?.currentTextColor
            ?: primary
        return MediaCardForegroundColors(primary = primary, secondary = secondary)
    }

    private fun applySeekBarTrackColor(view: SeekBar, color: Int) {
        (readField(view, "mBackgroundDrawable") as? Drawable)?.colorFilter =
            PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun notifyApplied(controller: Any) {
        runCatching { foregroundColorsAppliedListener?.invoke(controller) }
            .onFailure { error ->
                HookLogger.e(TAG, "通知中心媒体前景色同步回调失败", error)
            }
        foregroundColorsAppliedListeners.forEach { listener ->
            runCatching { listener(controller) }
                .onFailure { error ->
                    HookLogger.e(TAG, "通知中心媒体前景色观察者回调失败", error)
                }
        }
    }

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

    private data class SeekBarColorState(
        val foreground: Int,
        val track: Int
    )

    private data class SeekBarState(
        val thumbTintList: ColorStateList?,
        val progressTintList: ColorStateList?,
        val progressBackgroundTintList: ColorStateList?,
        val paintColorFilter: ColorFilter?,
        val progressDrawableColorFilter: ColorFilter?,
        val backgroundDrawableColorFilter: ColorFilter?
    ) {
        fun restore(seekBar: SeekBar) {
            seekBar.thumbTintList = thumbTintList
            seekBar.progressTintList = progressTintList
            seekBar.progressBackgroundTintList = progressBackgroundTintList
            if (seekBar is SquigglySeekBar) {
                seekBar.clearWaveColors()
            }
            (readField(seekBar, "mPaint") as? Paint)?.colorFilter = paintColorFilter
            (readField(seekBar, "mProgressDrawable") as? Drawable)?.colorFilter =
                progressDrawableColorFilter
            (readField(seekBar, "mBackgroundDrawable") as? Drawable)?.colorFilter =
                backgroundDrawableColorFilter
            seekBar.invalidate()
        }

        companion object {
            fun capture(seekBar: SeekBar): SeekBarState {
                return SeekBarState(
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
        }
    }

    private class HolderForegroundState(val holder: Any) {
        private val textColors = IdentityHashMap<TextView, ColorStateList>()
        private val iconTints = IdentityHashMap<ImageView, ColorStateList?>()
        private val seekBarStates = IdentityHashMap<SeekBar, SeekBarState>()

        fun captureText(view: TextView) {
            if (!textColors.containsKey(view)) textColors[view] = view.textColors
        }

        fun captureIcon(view: ImageView) {
            if (!iconTints.containsKey(view)) iconTints[view] = view.imageTintList
        }

        fun captureSeekBar(view: SeekBar) {
            if (!seekBarStates.containsKey(view)) {
                seekBarStates[view] = SeekBarState.capture(view)
            }
        }

        fun restore() {
            textColors.forEach { (view, colors) -> view.setTextColor(colors) }
            iconTints.forEach { (view, tint) -> view.imageTintList = tint }
            seekBarStates.forEach { (view, state) ->
                seekBarColors.remove(view)
                state.restore(view)
            }
        }
    }
}
