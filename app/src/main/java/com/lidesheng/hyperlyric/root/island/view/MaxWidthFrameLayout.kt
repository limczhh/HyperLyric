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
     */
    var maxWidthPx: Int = -1

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

        val givenWidth = MeasureSpec.getSize(widthMeasureSpec)
        val newWidth =
            if (maxWidthPx > 0 && (givenWidth == 0 || givenWidth > maxWidthPx)) maxWidthPx else givenWidth
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.AT_MOST),
            heightMeasureSpec
        )
        if (maxWidthPx > 0 && measuredWidth > maxWidthPx) {
            setMeasuredDimension(maxWidthPx, measuredHeight)
        }
    }
}
