package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Clips the native bar into equal rounded segments, retaining its drawable, tint and alpha. */
internal class SegmentedMiniBarDrawable(private val native: Drawable) : Drawable(), Drawable.Callback {
    private val segment = RectF()
    private val clip = Path()
    private val highlight = Paint().apply {
        // Both supplied plugin generations use a translucent native bar. Raising its alpha
        // brightens the selected segment while leaving native RGB/tint and unselected segments intact.
        colorFilter = ColorMatrixColorFilter(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 2.5f, 0f,
        ))
    }
    private var pageCount = 1
    private var selectedIndex = 0

    fun update(count: Int, selected: Int) {
        if (pageCount == count && selectedIndex == selected) return
        pageCount = count
        selectedIndex = selected
        invalidateSelf()
    }

    fun attached() { native.callback = this }

    override fun draw(canvas: Canvas) {
        if (pageCount <= 1) {
            native.draw(canvas)
            return
        }
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val gap = minOf(height * 0.75f, width / (pageCount * 3f))
        val segmentWidth = (width - gap * (pageCount - 1)) / pageCount
        if (segmentWidth <= 0f || height <= 0f) return
        repeat(pageCount) { index ->
            val left = bounds.left + index * (segmentWidth + gap)
            segment.set(left, bounds.top.toFloat(), left + segmentWidth, bounds.bottom.toFloat())
            clip.reset()
            clip.addRoundRect(segment, height / 2f, height / 2f, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clip)
            val layer = if (index == selectedIndex) canvas.saveLayer(segment, highlight) else -1
            native.draw(canvas)
            if (layer >= 0) canvas.restoreToCount(layer)
            canvas.restoreToCount(save)
        }
    }

    override fun onBoundsChange(bounds: Rect) { native.bounds = bounds }
    override fun onStateChange(state: IntArray): Boolean = native.setState(state)
    override fun onLevelChange(level: Int): Boolean = native.setLevel(level)
    override fun isStateful(): Boolean = native.isStateful
    override fun getPadding(padding: Rect): Boolean = native.getPadding(padding)
    override fun getIntrinsicWidth(): Int = native.intrinsicWidth
    override fun getIntrinsicHeight(): Int = native.intrinsicHeight
    override fun getMinimumWidth(): Int = native.minimumWidth
    override fun getMinimumHeight(): Int = native.minimumHeight
    override fun getOutline(outline: Outline) = native.getOutline(outline)
    override fun getAlpha(): Int = native.alpha
    override fun setAlpha(alpha: Int) { native.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { native.colorFilter = colorFilter }
    override fun getColorFilter(): ColorFilter? = native.colorFilter
    override fun setTint(tintColor: Int) = native.setTint(tintColor)
    override fun setTintList(tint: ColorStateList?) = native.setTintList(tint)
    override fun setTintMode(tintMode: PorterDuff.Mode?) = native.setTintMode(tintMode)
    override fun setTintBlendMode(blendMode: BlendMode?) = native.setTintBlendMode(blendMode)
    override fun mutate(): Drawable { native.mutate(); return this }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
