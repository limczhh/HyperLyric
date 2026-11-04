package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Only the registered notification modal. The SDK Activity is not a host. */
internal class MiPlayLandscapeSizing(private val content: View) {
    private data class Original(val width: Int, val height: Int, val weight: Float?)
    private val originals = linkedMapOf<View, Original>()

    init {
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { restore() }
        })
    }

    fun prepare() {
        if (content.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            restore()
            return
        }
        val ancestors = generateSequence(content.parent as? View) { it.parent as? View }.toList()
        val modal = ancestors.firstOrNull {
            it.javaClass.name == "com.android.systemui.statusbar.notification.modal.ModalQSControlDetail"
        } ?: return
        val container = ancestors.firstOrNull { it.parent === modal } ?: return
        val window = ancestors.firstOrNull {
            it.javaClass.name == "com.android.systemui.statusbar.notification.modal.ModalWindowView"
        } ?: return
        val density = content.resources.displayMetrics.density
        val config = content.resources.configuration
        val availableWidth = (config.screenWidthDp * density).roundToInt()
        val availableHeight = (config.screenHeightDp * density).roundToInt()
        fun dimension(name: String, fallback: Int): Int {
            val id = content.resources.getIdentifier(name, "dimen", "miui.systemui.plugin")
            return if (id != 0) content.resources.getDimensionPixelSize(id) else fallback
        }
        val edge = (16 * density).roundToInt()
        val width = dimension("miplay_detail_content_land_width", availableWidth - edge * 2)
            .coerceAtMost(availableWidth - edge * 2 - window.paddingLeft - window.paddingRight)
        val height = dimension("miplay_detail_header_height", availableHeight - edge * 2)
            .coerceAtMost(availableHeight - edge * 2)
        if (height <= 0 || width <= height) return
        for (view in sequenceOf(content) + ancestors.takeWhile { it !== modal }) {
            val params = view.layoutParams ?: continue
            remember(view)
            val targetWidth = if (view === container) width else ViewGroup.LayoutParams.MATCH_PARENT
            if (params.width != targetWidth || params.height != height ||
                (params is LinearLayout.LayoutParams && params.weight != 0f)) {
                params.width = targetWidth
                params.height = height
                (params as? LinearLayout.LayoutParams)?.weight = 0f
                view.layoutParams = params
            }
        }
        // clipChildren=false only permits drawing outside a parent. Touch
        // dispatch still rejects the widened card's right side unless every
        // ancestor, including the modal window itself, spans the whole card.
        for (view in ancestors.dropWhile { it !== modal }.takeWhile { it !== window } + window) {
            val params = view.layoutParams ?: continue
            remember(view)
            val targetWidth = if (view === window) width + window.paddingLeft + window.paddingRight
                else ViewGroup.LayoutParams.MATCH_PARENT
            if (params.width != targetWidth) {
                params.width = targetWidth
                view.layoutParams = params
            }
        }
    }

    private fun remember(view: View) {
        val params = view.layoutParams ?: return
        originals.getOrPut(view) { Original(params.width, params.height,
            (params as? LinearLayout.LayoutParams)?.weight) }
    }

    fun restore() {
        originals.forEach { (view, original) ->
            val params = view.layoutParams ?: return@forEach
            view.layoutParams = params.apply {
                width = original.width
                height = original.height
                if (this is LinearLayout.LayoutParams && original.weight != null) weight = original.weight
            }
        }
        originals.clear()
    }
}
