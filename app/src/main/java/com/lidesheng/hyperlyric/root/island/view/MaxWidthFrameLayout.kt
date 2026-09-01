package com.lidesheng.hyperlyric.root.island.view

import android.content.Context
import android.widget.FrameLayout

/**
 * 限制最大测量宽度的 FrameLayout 容器。
 * 取代了原先以匿名类实现的测量逻辑，规避了 Xposed 对 maxWidthPx 的反射调用。
 */
class MaxWidthFrameLayout(context: Context) : FrameLayout(context) {

    /**
     * 最大宽度（像素）。设置为 -1（默认）则不限制。
     *
     * This is deliberately kept separate from [desiredWidthPx]. A dynamic lyric update changes
     * the desired width; it must not turn the current width into the next measurement ceiling.
     */
    var maxWidthPx: Int = -1

    /**
     * Requested content width（像素）。设置为 -1（默认）时回退到 [maxWidthPx] 或父级规格。
     */
    var desiredWidthPx: Int = -1

    /**
     * Used only by injected Super Island test blocks.
     */
    var keepVisible: Boolean = false

    override fun setVisibility(visibility: Int) {
        if (keepVisible && visibility != VISIBLE) {
            super.setVisibility(VISIBLE)
            return
        }
        super.setVisibility(visibility)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = when {
            desiredWidthPx > 0 -> desiredWidthPx
            maxWidthPx > 0 -> maxWidthPx
            else -> -1
        }

        if (requestedWidth <= 0) {
            // Preserve normal FrameLayout measurement when no explicit width is configured.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val widthLimit = maxWidthPx.takeIf { it > 0 } ?: requestedWidth
        val boundedWidth = requestedWidth.coerceAtMost(widthLimit)
        val parentMode = MeasureSpec.getMode(widthMeasureSpec)
        val parentSize = MeasureSpec.getSize(widthMeasureSpec)
        val resolvedWidth = when (parentMode) {
            MeasureSpec.UNSPECIFIED -> boundedWidth
            MeasureSpec.AT_MOST,
            MeasureSpec.EXACTLY -> boundedWidth.coerceAtMost(parentSize)
            else -> boundedWidth
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(resolvedWidth, MeasureSpec.EXACTLY),
            heightMeasureSpec
        )

        val cappedMeasuredWidth = measuredWidth.coerceAtMost(boundedWidth)
        if (cappedMeasuredWidth != measuredWidth) {
            setMeasuredDimension(cappedMeasuredWidth, measuredHeight)
        }
    }
}
