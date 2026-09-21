package com.lidesheng.hyperlyric.root.mediacard.layout.common

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scales the icon drawn by an action view without scaling its touch target.
 *
 * SystemUI's media actions use ImageButtons whose measured size is also used
 * for the ConstraintSet slot.  Changing only the slot/padding therefore does
 * not reliably change a drawable with a fixed intrinsic size.  Wrapping the
 * drawable keeps the button's click bounds intact while making the visual icon
 * follow the same layout scale.
 */
internal object MediaActionIconScaler {
    private val states = Collections.synchronizedMap(
        WeakHashMap<View, State>()
    )

    fun apply(actionView: View, scale: Float) {
        val imageView = findImageView(actionView) ?: return
        val state = states.getOrPut(actionView) { State(imageView) }
        state.apply(imageView, scale)
    }

    fun restore(actionView: View) {
        states.remove(actionView)?.restore()
    }

    private fun findImageView(view: View): ImageView? {
        if (view is ImageView) return view
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findImageView(group.getChildAt(index))?.let { return it }
        }
        return null
    }

    private class State(private val initialImageView: ImageView) {
        private var imageView: ImageView = initialImageView
        private var originalDrawable: Drawable? = initialImageView.drawable
        private var scaledDrawable: ScaledActionIconDrawable? = null

        fun apply(currentImageView: ImageView, scale: Float) {
            if (imageView !== currentImageView) {
                imageView = currentImageView
                originalDrawable = currentImageView.drawable
                scaledDrawable = null
            }

            val current = currentImageView.drawable
            val source = when (current) {
                scaledDrawable -> scaledDrawable?.source
                is ScaledActionIconDrawable -> current.source
                else -> current
            }
            if (source == null) {
                originalDrawable = null
                scaledDrawable = null
                return
            }

            if (current !== scaledDrawable || scaledDrawable?.scale != scale) {
                originalDrawable = source
                scaledDrawable = ScaledActionIconDrawable(source, scale)
                currentImageView.setImageDrawable(scaledDrawable)
            }
            scaledDrawable?.attachSourceCallback()
        }

        fun restore() {
            if (imageView.drawable === scaledDrawable) {
                imageView.setImageDrawable(originalDrawable)
            }
            scaledDrawable = null
        }
    }

    private class ScaledActionIconDrawable(
        val source: Drawable,
        val scale: Float
    ) : Drawable() {
        private val sourceCallback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                invalidateSelf()
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                scheduleSelf(what, `when`)
            }

            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                unscheduleSelf(what)
            }
        }

        fun attachSourceCallback() {
            source.callback = sourceCallback
        }

        override fun draw(canvas: Canvas) {
            val outer = bounds
            if (outer.isEmpty) return

            val sourceWidth = source.intrinsicWidth.takeIf { it > 0 }
                ?: source.bounds.width().takeIf { it > 0 }
                ?: outer.width()
            val sourceHeight = source.intrinsicHeight.takeIf { it > 0 }
                ?: source.bounds.height().takeIf { it > 0 }
                ?: outer.height()
            val targetWidth = min(
                outer.width(),
                max(1, (sourceWidth * scale).roundToInt())
            )
            val targetHeight = min(
                outer.height(),
                max(1, (sourceHeight * scale).roundToInt())
            )
            val left = outer.left + (outer.width() - targetWidth) / 2
            val top = outer.top + (outer.height() - targetHeight) / 2
            val previousBounds = Rect(source.bounds)
            source.setBounds(left, top, left + targetWidth, top + targetHeight)
            source.draw(canvas)
            source.bounds = previousBounds
        }

        override fun setAlpha(alpha: Int) {
            source.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            source.colorFilter = colorFilter
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = source.opacity

        override fun getIntrinsicWidth(): Int = source.intrinsicWidth

        override fun getIntrinsicHeight(): Int = source.intrinsicHeight

        override fun getMinimumWidth(): Int = source.minimumWidth

        override fun getMinimumHeight(): Int = source.minimumHeight

        override fun isStateful(): Boolean = source.isStateful

        override fun onStateChange(state: IntArray): Boolean {
            val changed = source.setState(state)
            if (changed) invalidateSelf()
            return changed
        }

        override fun onLevelChange(level: Int): Boolean {
            val changed = source.setLevel(level)
            if (changed) invalidateSelf()
            return changed
        }

        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            return source.setVisible(visible, restart)
        }

        override fun setTint(tintColor: Int) {
            source.setTint(tintColor)
        }

        override fun setTintList(tint: ColorStateList?) {
            source.setTintList(tint)
        }

        override fun setTintMode(tintMode: PorterDuff.Mode?) {
            source.setTintMode(tintMode)
        }

        override fun setAutoMirrored(mirrored: Boolean) {
            source.isAutoMirrored = mirrored
        }

        override fun isAutoMirrored(): Boolean = source.isAutoMirrored

        override fun setHotspot(x: Float, y: Float) {
            source.setHotspot(x, y)
        }

        override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {
            source.setHotspotBounds(left, top, right, bottom)
        }
    }
}
