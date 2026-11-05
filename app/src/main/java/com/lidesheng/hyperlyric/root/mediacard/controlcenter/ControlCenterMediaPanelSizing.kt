package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Reserves space for the enlarged native header without altering native animation frames. */
internal class ControlCenterMediaPanelSizing(
    private val layout: ControlCenterMediaLayout,
    private val content: View,
    private val nativeContentLimit: Int,
    private val nativeFrameLimit: Int,
    private val setContentLimit: (Int) -> Unit,
    private val setFrameLimit: (Int) -> Unit,
    private val minimumDeviceHeight: Int,
    private val listTopMargin: () -> Int = { 0 },
    private val nativeLimits: () -> Pair<Int, Int> = { nativeContentLimit to nativeFrameLimit },
    private val frame: ViewGroup? = null,
    private val devices: View? = null,
) {
    val frameView: ViewGroup? get() = frame
    private data class Original(val height: Int, val weight: Float?)
    private val containers = buildList {
        var current: View? = content
        while (current != null) {
            add(current)
            if (current === frame) break
            current = if (frame != null) current.parent as? View else null
        }
    }
    // Capture the host's real parameters when first changing them. A factory
    // result inflated with a null parent has no root LayoutParams at all.
    private val originals = mutableMapOf<View, Original>()
    private val heights = mutableMapOf<View, Int>()
    private var width = 0
    private var limit = nativeContentLimit
    private var headerHeight = 0
    private var deviceHeight = minimumDeviceHeight
    var forcing = false
        private set
    var active = false
        private set
    private var preparingMeasure = false

    @Suppress("UNUSED_PARAMETER")
    fun prepare(widthHint: Int, heightSpec: Int = 0, windowLimit: Int = windowLimit()): Boolean {
        if (layout.isTransitioning) return active
        if (containers.any { it.layoutParams == null }) return false
        width = widthHint.takeIf { it > 0 } ?: width
        if (content.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Portrait reserves additional height; landscape must release it
            // and use the native limits and outer layout parameters again.
            if (active) restoreContainerHeights()
            active = false
            heights.clear()
            val (contentLimit, frameLimit) = nativeLimits()
            setContentLimit(contentLimit)
            setFrameLimit(frameLimit)
            if (!layout.isLandscapeApplied) layout.restore()
            layout.prepareModalLandscape()
            return false
        }
        width = widthHint.takeIf { it > 0 } ?: width
        if (width <= 0) return false
        // The incoming spec may contain the stale native height. Enforce exact sizes below.
        setContentLimit(Int.MAX_VALUE)
        setFrameLimit(Int.MAX_VALUE)
        headerHeight = layout.forceHeaderHeight(width)
        limit = maxOf(windowLimit, headerHeight + listTopMargin() + minimumDeviceHeight)
        active = layout.isApplied
        applyHeights()
        return active
    }

    fun availableDeviceHeight(): Int =
        (limit - headerHeight - headerMargins() - listTopMargin() - content.paddingTop - content.paddingBottom)
            .coerceAtLeast(minimumDeviceHeight)

    private fun headerMargins(): Int = (layout.header.layoutParams as? ViewGroup.MarginLayoutParams)
        ?.let { it.topMargin + it.bottomMargin } ?: 0

    fun updateDeviceHeight(target: Int) {
        if (!active) return
        deviceHeight = target.coerceAtLeast(0)
        if (!layout.isTransitioning) applyHeights()
    }

    fun heightFor(view: View): Int? = if (active && !layout.isTransitioning) heights[view] else null

    fun prepareForMeasure(view: View, widthSpec: Int) {
        // A wrap-content frame receives AT_MOST(screenWidth), not the card width.
        // Only its content's resolved width can determine the full-bleed header.
        if (view !== content || preparingMeasure) return
        val mode = View.MeasureSpec.getMode(widthSpec)
        val available = View.MeasureSpec.getSize(widthSpec)
        val params = layout.header.layoutParams as? ViewGroup.MarginLayoutParams
        val insets = content.paddingLeft + content.paddingRight +
            (params?.leftMargin ?: 0) + (params?.rightMargin ?: 0)
        val measuredWidth = when {
            mode == View.MeasureSpec.EXACTLY -> available - insets
            content.layoutParams.width > 0 -> content.layoutParams.width - insets
            else -> width
        }
        if (measuredWidth > 0) {
            preparingMeasure = true
            try {
                prepare(measuredWidth)
            } finally {
                preparingMeasure = false
            }
        }
    }

    private fun applyHeights() {
        if (!active) return
        val list = devices
        val actualDeviceHeight = list?.layoutParams?.height?.takeIf { it >= 0 } ?: deviceHeight
        val bottomMargin = (list?.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        var height = headerHeight + headerMargins() + actualDeviceHeight + listTopMargin() + bottomMargin + content.paddingTop + content.paddingBottom
        containers.forEachIndexed { index, view ->
            val params = view.layoutParams ?: return@forEachIndexed
            originals.getOrPut(view) {
                Original(params.height, (params as? LinearLayout.LayoutParams)?.weight)
            }
            if (index > 0) {
                val child = containers[index - 1]
                val params = child.layoutParams as? ViewGroup.MarginLayoutParams
                height += (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0) + view.paddingTop + view.paddingBottom
            }
            heights[view] = height
            val weighted = params is LinearLayout.LayoutParams && params.weight != 0f
            if (params.height != height || weighted) {
                params.height = height
                if (params is LinearLayout.LayoutParams) params.weight = 0f
                view.layoutParams = params
            }
        }
    }

    /** Resolve the expanded endpoint with the native parent before startShow/startHide lock layout. */
    fun layoutFromParent() {
        val root = frame ?: return
        val parent = root.parent as? ViewGroup ?: return
        val landscape = content.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if ((!active && !landscape) || forcing || width <= 0 || layout.isTransitioning ||
            root.isLayoutSuppressed || parent.isLayoutSuppressed || parent.width <= 0 || parent.height <= 0) return
        forcing = true
        try {
            containers.forEach { it.forceLayout() }
            layout.header.forceLayout()
            // The native ConstraintLayout owns centering. Reusing the animated root.left
            // mixes the compact-card origin with the expanded endpoint.
            parent.forceLayout()
            parent.measure(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.EXACTLY))
            parent.layout(parent.left, parent.top, parent.right, parent.bottom)
        } finally {
            forcing = false
        }
    }

    private fun restoreContainerHeights() {
        originals.forEach { (view, original) ->
            view.layoutParams = view.layoutParams.apply {
                height = original.height
                if (this is LinearLayout.LayoutParams && original.weight != null) weight = original.weight
            }
        }
    }

    private fun windowLimit(): Int {
        val density = content.resources.displayMetrics.density
        val configured = (content.resources.configuration.screenHeightDp * density).roundToInt()
        val metricsHeight = runCatching {
            val metrics = content.context.getSystemService(WindowManager::class.java).currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            metrics.bounds.height() - insets.top - insets.bottom
        }.getOrDefault(0)
        val available = listOf(configured, metricsHeight).filter { it > 0 }.minOrNull() ?: nativeContentLimit
        return (available - (48 * density).roundToInt()).coerceAtLeast(0)
    }
}
