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

    /** Interpret a zero maximum as a zero-width viewport instead of the unbounded sentinel. */
    var allowZeroWidth: Boolean = false

    /**
     * Requested content width（像素）。设置为 -1（默认）时回退到 [maxWidthPx] 或父级规格。
     */
    var desiredWidthPx: Int = -1

    /**
     * Fill an exact parent width instead of shrinking back to [maxWidthPx].
     *
     * Fake dynamic-island areas already receive the final width calculated from the real tree.
     * Their injected content must consume that width during the final layout pass, while AT_MOST
     * pre-measure passes still use the regular content-based maximum-width behavior.
     */
    var fillExactParentWidth: Boolean = false

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
        if (fillExactParentWidth && MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        if (allowZeroWidth && (maxWidthPx == 0 || desiredWidthPx == 0)) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY),
                heightMeasureSpec,
            )
            return
        }

        if (desiredWidthPx <= 0) {
            // A cap alone must not become a requested fixed width. Preserve the existing
            // content measurement for fixed-width and split slots outside dynamic mode.
            if (maxWidthPx <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            val givenWidth = MeasureSpec.getSize(widthMeasureSpec)
            val width = if (givenWidth == 0 || givenWidth > maxWidthPx) maxWidthPx else givenWidth
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (measuredWidth > maxWidthPx) setMeasuredDimension(maxWidthPx, measuredHeight)
            return
        }

        val requestedWidth = desiredWidthPx
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
