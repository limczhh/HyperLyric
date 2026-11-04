package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/** Keeps native actions in the middle three slots and app actions in the outer two. */
internal class ControlCenterMediaCustomActions(
    private val container: ViewGroup?,
    private val title: TextView,
    private val enabled: Boolean,
    private val requestRefresh: () -> Unit = {},
    private val source: () -> ControlCenterMediaActions = { ControlCenterMediaActions() },
) {
    private val nativeButtons = container?.let { viewGroup ->
        (0 until viewGroup.childCount)
            .map { viewGroup.getChildAt(it) }
            .filterIsInstance<ImageView>()
            .take(3)
    }.orEmpty()
    private val nativeParams = nativeButtons.mapNotNull { button ->
        button.layoutParams?.let { params ->
            runCatching { button to MediaLayoutParams.copy(params) }.getOrNull()
        }
    }.toMap()
    private val customButtons = arrayOfNulls<ImageView>(2)
    private val available = BooleanArray(2)
    private var visible = true
    private var lastChain = emptyList<View>()
    private var nativeGap: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private var observing = false
    private var refreshPosted = false
    private val refreshTask = Runnable { refreshPosted = false; requestRefresh() }
    private val sourceIcons = arrayOfNulls<Drawable>(2)
    private val sourceChanged: () -> Unit = { scheduleRefresh() }

    init {
        if (enabled && container != null) {
            title.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) { startObserving(); scheduleRefresh() }
                override fun onViewDetachedFromWindow(view: View) {
                    ControlCenterMediaActionSource.removeListener(sourceChanged)
                    observing = false
                    handler.removeCallbacks(refreshTask)
                    refreshPosted = false
                }
            })
        }
    }

    private fun scheduleRefresh() {
        if (refreshPosted || !title.isAttachedToWindow) return
        refreshPosted = true
        handler.post(refreshTask)
    }

    private fun startObserving() {
        if (observing) return
        observing = true
        ControlCenterMediaActionSource.addListener(sourceChanged)
    }

    /** Called after measuring the original three-button hierarchy, before insertion. */
    fun captureNativeGeometry() {
        if (!enabled || lastChain.isNotEmpty() || nativeButtons.size != 3) return
        val gaps = nativeButtons.zipWithNext { left, right -> right.left - left.right }
        if (nativeButtons.all { it.width > 0 } && gaps.all { it >= 0 }) {
            nativeGap = gaps.sum() / gaps.size
        }
    }

    fun refresh(): Boolean {
        val previous = lastChain
        refreshButtons()
        return previous != lastChain
    }

    /** Extend the three-button row by one native button pitch at either end. */
    fun expandedWidth(row: View, nativeWidth: Int): Int {
        if (row !== container || lastChain.isEmpty() || nativeWidth <= 0) return nativeWidth
        val widths = nativeButtons.map { button ->
            nativeParams[button]?.width?.takeIf { it > 0 } ?: button.measuredWidth
        }
        if (widths.any { it <= 0 }) return nativeWidth
        val gap = nativeGap ?: return nativeWidth
        return row.paddingLeft + row.paddingRight + widths.sum() +
            2 * widths.first() + 4 * gap
    }

    private fun refreshButtons() {
        if (!enabled || container == null || nativeButtons.size != 3) return
        // Never insert into an unmeasured row: it would destroy the native gap
        // before the expanded layout has had a chance to capture it.
        if (nativeGap == null) return
        for (index in customButtons.indices) {
            if (customButtons[index] == null) {
                val button = newButton() ?: return
                container.addView(button)
                customButtons[index] = button
            }
        }
        if (title.isAttachedToWindow) startObserving()
        val actions = source()
        val first = actions.left?.let { createButton(0, it) }
        val last = actions.right?.let { createButton(1, it) }
        updateAvailability(0, first != null)
        updateAvailability(1, last != null)
        connectChain()
    }

    /** INVISIBLE keeps both outer slots in the chain, including during collapse. */
    fun setVisible(show: Boolean) {
        visible = show
        customButtons.forEachIndexed { index, button ->
            button?.visibility = if (show && available[index]) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun updateAvailability(index: Int, hasAction: Boolean) {
        available[index] = hasAction
        val button = customButtons[index] ?: return
        if (!hasAction) {
            button.setImageDrawable(null)
            button.setOnClickListener(null)
            button.contentDescription = null
            button.tooltipText = null
            sourceIcons[index] = null
        }
        button.isEnabled = hasAction
        button.isClickable = hasAction
        button.isFocusable = hasAction
        button.importantForAccessibility = if (hasAction) nativeButtons[0].importantForAccessibility
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO
        button.visibility = if (visible && hasAction) View.VISIBLE else View.INVISIBLE
    }

    fun restore() {
        val host = container ?: return
        if (customButtons.all { it == null } && lastChain.isEmpty()) return
        customButtons.forEach { button ->
            button?.let { if (it.parent === host) host.removeView(it) }
        }
        customButtons.fill(null)
        available.fill(false)
        sourceIcons.fill(null)
        lastChain = emptyList()
        nativeParams.forEach { (button, params) ->
            button.layoutParams = MediaLayoutParams.copy(params)
        }
        host.requestLayout()
    }

    private fun createButton(
        index: Int,
        action: ControlCenterMediaAction,
    ): ImageView? {
        val button = customButtons[index] ?: return null
        if (sourceIcons[index] !== action.icon || button.drawable == null) {
            button.setImageDrawable(copyDrawable(action.icon) ?: return null)
            sourceIcons[index] = action.icon
        }
        button.contentDescription = action.description
        button.tooltipText = action.description
        button.setOnClickListener {
            runCatching { action.run.run() }
        }
        return button
    }

    private fun newButton(): ImageView? {
        val reference = nativeButtons.getOrNull(0) ?: return null
        return ImageView(container?.context ?: title.context).also {
            it.layoutParams = runCatching { MediaLayoutParams.fresh(reference.layoutParams) }.getOrNull()
                ?: return null
            it.scaleType = reference.scaleType
            it.layoutDirection = reference.layoutDirection
            it.imageTintList = reference.imageTintList
            it.imageTintMode = reference.imageTintMode
            it.imageTintBlendMode = reference.imageTintBlendMode
            it.setPadding(
                reference.paddingLeft,
                reference.paddingTop,
                reference.paddingRight,
                reference.paddingBottom
            )
            it.alpha = reference.alpha
            it.visibility = View.INVISIBLE
            it.isEnabled = false
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            it.id = View.generateViewId()
            it.isClickable = false
            it.isFocusable = false
        }
    }

    private fun connectChain() {
        val host = container ?: return
        val chain = buildList {
            customButtons[0]?.let(::add)
            addAll(nativeButtons)
            customButtons[1]?.let(::add)
        }
        if (chain == lastChain) return
        lastChain = chain
        chain.forEachIndexed { index, view ->
            val params = MediaLayoutParams.fresh(view.layoutParams)
            setInt(params, "topToTop", 0)
            setInt(params, "bottomToBottom", 0)
            (params as ViewGroup.MarginLayoutParams).marginStart = if (index == 0) 0 else nativeGap ?: 0
            if (index == 0) {
                setInt(params, "startToStart", 0)
            } else {
                setInt(params, "startToEnd", chain[index - 1].id)
            }
            if (index == chain.lastIndex) {
                setInt(params, "endToEnd", 0)
            } else {
                setInt(params, "endToStart", chain[index + 1].id)
            }
            // Explicit native gaps, rather than redistributing whatever width
            // the solver happens to give the row during a transition.
            setInt(params, "horizontalChainStyle", 2)
            view.layoutParams = params
        }
        host.requestLayout()
    }

    private fun setInt(params: ViewGroup.LayoutParams, name: String, value: Int) {
        runCatching { params.javaClass.getField(name).setInt(params, value) }
    }

    private fun copyDrawable(icon: Drawable): Drawable? = runCatching {
        icon.constantState?.newDrawable(title.resources)?.mutate() ?: run {
            // A notification's drawable must keep its own callback/bounds.
            val bitmap = Bitmap.createBitmap(icon.intrinsicWidth.coerceAtLeast(1),
                icon.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val bounds = Rect(icon.bounds)
            try {
                icon.setBounds(0, 0, bitmap.width, bitmap.height)
                icon.draw(Canvas(bitmap))
            } finally { icon.bounds = bounds }
            BitmapDrawable(title.resources, bitmap)
        }
    }.getOrNull()
}
