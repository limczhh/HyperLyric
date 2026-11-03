package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Draw-only overlays: native minibar visibility, capability and gestures remain untouched. */
internal object IslandMediaMiniBarIndicator {
    private const val TAG = "IslandMediaMiniBarIndicator"
    // A state owns an overlay attached to the bar's parent; weak values avoid retaining that tree.
    private val bars = WeakHashMap<View, WeakReference<BarState>>()

    fun update(window: View, content: Any?, pages: IntArray?) {
        // A single session needs no indicator; keep its native bar and capability unchanged.
        val indicatorPages = pages?.takeIf { it[0] > 1 }
        val active = LinkedHashMap<View, Any>()
        if (content != null && indicatorPages != null) {
            (call(content, "getMiniBar") as? View)?.let { active[it] = content }
            call(content, "getFakeView")?.let { fake ->
                (call(fake, "getMiniBar") as? View)?.let { active[it] = fake }
            }
        }
        bars.entries.toList().forEach { (bar, reference) ->
            val state = reference.get()
            if (state == null || state.window.get() === window && bar !in active) {
                state?.dispose(bar)
                bars.remove(bar)
            }
        }
        if (indicatorPages == null || content == null) return
        active.forEach { (bar, owner) ->
            val parent = bar.parent as? FrameLayout ?: return@forEach
            var state = bars[bar]?.get()
            if (state == null || bar.background !== state.transparent || state.parent !== parent) {
                state?.dispose(bar)
                val background = bar.background ?: return@forEach
                state = BarState(window, bar, parent, background, owner, content)
                bars[bar] = WeakReference(state)
            }
            state.pageCount = indicatorPages[0]
            state.selectedIndex = indicatorPages[1]
            state.updateActionColor()
            state.onPreDraw()
        }
        HookLogger.dState(
            stateId = "$TAG.pages", tag = TAG, state = "${indicatorPages[0]}/${indicatorPages[1]}/${active.size}",
        ) { "媒体岛分段指示更新: pages=${indicatorPages[0]}, selected=${indicatorPages[1]}, bars=${active.size}" }
    }

    fun clear() {
        bars.entries.toList().forEach { (bar, state) -> state.get()?.dispose(bar) }
        bars.clear()
    }

    private class BarState(
        window: View,
        bar: View,
        val parent: FrameLayout,
        private val native: Drawable,
        owner: Any,
        private val real: Any,
    ) : ViewTreeObserver.OnPreDrawListener {
        val window = WeakReference(window)
        private val bar = WeakReference(bar)
        val transparent = ColorDrawable(Color.TRANSPARENT)
        private val segmented = SegmentedMiniBarDrawable(native)
        private val indicator = View(bar.context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val observer = parent.viewTreeObserver
        private val container = Rect()
        private val position = Rect()
        private val expandedHeight = real.javaClass.getMethod("getExpandedViewHeight")
        private val marginBottom = call(owner, "getMiniBarMarginBottom") as Float
        private var action: ImageView? = null
        var pageCount = 1
        var selectedIndex = 0

        init {
            bar.background = transparent
            indicator.background = segmented
            segmented.attached()
            // ViewGroupOverlay participates only in drawing, never touch dispatch or layout.
            parent.overlay.add(indicator)
            observer.addOnPreDrawListener(this)
        }

        override fun onPreDraw(): Boolean {
            val bar = bar.get() ?: return true
            val params = bar.layoutParams as FrameLayout.LayoutParams
            val width = params.width
            val height = params.height
            if (width <= 0 || height <= 0) return true
            if (bar.visibility == View.GONE) {
                container.set(
                    parent.paddingLeft + params.leftMargin, parent.paddingTop + params.topMargin,
                    parent.width - parent.paddingRight - params.rightMargin,
                    parent.height - parent.paddingBottom - params.bottomMargin,
                )
                Gravity.apply(params.gravity, width, height, container, position, parent.layoutDirection)
                indicator.layout(position.left, position.top, position.right, position.bottom)
                indicator.translationX = 0f
                // Same native resting position, without laying out/showing the unavailable bar.
                indicator.translationY = (expandedHeight.invoke(real) as Int) - marginBottom - height
            } else {
                indicator.layout(bar.left, bar.top, bar.right, bar.bottom)
                indicator.translationX = bar.translationX
                indicator.translationY = bar.translationY
            }
            indicator.pivotX = width / 2f
            indicator.pivotY = height / 2f
            indicator.scaleX = bar.scaleX
            indicator.scaleY = bar.scaleY
            indicator.alpha = bar.alpha
            indicator.visibility = if (bar.visibility == View.INVISIBLE) View.INVISIBLE else View.VISIBLE
            val button = action
            val selectedColor = button?.imageTintList?.let {
                it.getColorForState(button.drawableState, it.defaultColor)
            } ?: Color.WHITE
            segmented.update(pageCount, selectedIndex, selectedColor)
            return true
        }

        fun updateActionColor() {
            val player = call(call(real, "getCurrentIslandData"), "getView") as? View
            val id = player?.resources?.getIdentifier("action2", "id", "com.android.systemui") ?: 0
            action = player?.findViewById(id)
        }

        fun dispose(bar: View) {
            if (observer.isAlive) observer.removeOnPreDrawListener(this)
            val currentObserver = parent.viewTreeObserver
            if (currentObserver !== observer && currentObserver.isAlive) {
                currentObserver.removeOnPreDrawListener(this)
            }
            parent.overlay.remove(indicator)
            indicator.background = null
            if (bar.background === transparent) bar.background = native
        }
    }

    private fun call(target: Any?, name: String): Any? {
        target ?: return null
        return target.javaClass.methods.first {
            it.name == name && it.parameterCount == 0
        }.apply { isAccessible = true }.invoke(target)
    }
}
