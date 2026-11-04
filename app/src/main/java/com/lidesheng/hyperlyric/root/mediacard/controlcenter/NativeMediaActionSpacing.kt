package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** The native free space between top-aligned labels and bottom-aligned actions. */
internal class NativeMediaActionSpacing private constructor(
    private val labelBottoms: Map<View, Int>,
    private val rowTops: Map<View, Int>,
    private val margins: Map<View, Int>,
) {
    fun before(row: View): Int {
        val labelBottom = labelBottoms.asSequence().filter { it.key.visibility != View.GONE }.maxOfOrNull { it.value }
            ?: return margins.getValue(row)
        // The native coordinates already include the labels' top margins and
        // the action row's bottom margin. Do not add either margin again.
        return (rowTops.getValue(row) - labelBottom).coerceAtLeast(0)
    }

    companion object {
        /** Call only with the ORIGINAL native hierarchy and LayoutParams restored. */
        fun capture(header: LinearLayout, width: Int, labels: List<View>, rows: List<View>): NativeMediaActionSpacing {
            val hidden = (labels + rows).filter { it.visibility == View.GONE }
            try {
                // Resolve both audio/video and label metrics even before a media
                // session exists. Restore visibility before returning/drawing.
                hidden.forEach { it.visibility = View.INVISIBLE }
                header.forceLayout()
                header.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                header.layout(header.left, header.top, header.left + width, header.top + header.measuredHeight)
                return NativeMediaActionSpacing(
                    labels.associateWith { topInHeader(it, header) + it.height },
                    rows.associateWith { topInHeader(it, header) },
                    rows.associateWith { (it.layoutParams as ViewGroup.MarginLayoutParams).topMargin },
                )
            } finally {
                hidden.forEach { it.visibility = View.GONE }
            }
        }

        private fun topInHeader(view: View, header: View): Int {
            var current = view
            var top = 0
            while (current !== header) {
                top += current.top
                current = current.parent as? View ?: error("Media control is outside Header")
                top -= current.scrollY
            }
            return top
        }
    }
}
