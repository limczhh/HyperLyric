package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.RectF
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * Uses the native springs and lifecycle, but not the native two-column geometry
 * (which assumes Cover is inside Header and Devices are to Header's right).
 * All rectangles are captured in card coordinates, then converted back to the
 * actual parent on each frame. The expanded endpoint IS the static layout.
 */
internal class LandscapeMediaPanelTransition private constructor(
    private val access: Access,
    private val controller: Any,
    private val delegate: Any,
    private val values: Any,
    private val frame: View,
    private val background: View,
    private val nodes: List<Node>,
    private val footer: List<View>,
    private val deviceIcon: View?,
    private val appIcon: View?,
    private val cover: View?,
    private val sourceCoverVisible: Boolean,
    private val fonts: List<Font>,
    val generation: Int,
    private val baseFrame: (Any) -> Unit,
) {
    private data class Node(val view: View, val from: RectF, val to: RectF,
        val fontBounds: Boolean, val parent: View?, val current: RectF = RectF())
    private data class Font(val view: TextView, val from: Float, val to: Float)
    private val nodesByView = nodes.associateBy { it.view }

    fun matches(animator: Any): Boolean =
        access.field(animator.javaClass, "animValue").get(animator) === values &&
            nodes.all { it.view.parent === it.parent }

    fun render(animator: Any) {
        baseFrame(animator)
        val size = access.float(animator, "size")
        val fontBounds = access.float(animator, "fontWidthHeight")
        val fontSize = access.float(animator, "fontSize")
        val alphaBg = access.float(animator, "alphaBg")
        fun value(name: String) = access.call(values, "get$name") as Number
        val from = access.call(values, "getFromContent")!!
        val to = access.call(values, "getToContent")!!
        val width = lerp(access.number(from, "getWidth"), access.number(to, "getWidth"), size)
        val height = lerp(access.number(from, "getHeight"), access.number(to, "getHeight"), size)
        val x = lerp(value("FromCenterX").toFloat(), value("ToCenterX").toFloat(), size) - width / 2
        val y = lerp(value("FromCenterY").toFloat(), value("ToCenterY").toFloat(), size) - height / 2
        bounds(frame, x, y, width, height)
        bounds(background, 0f, 0f, width, height)
        if (access.call(animator, "isCollapsing") == true &&
            access.call(animator, "isMainPanelCollapsing") == true) frame.alpha = alphaBg
        access.call(controller, "setContentBgColor", alphaBg)
        access.call(controller, "setBlurBgRatio", size)
        access.call(controller, "setContentBgRadius", lerp(value("FromCornerRadius").toFloat(),
            value("ToCornerRadius").toFloat(), size))
        access.call(delegate, "setCoverRadius", lerp(value("FromCoverRadius").toFloat(),
            value("ToCoverRadius").toFloat(), size))

        nodes.forEach { node ->
            val p = if (node.fontBounds) fontBounds else size
            val left = lerp(node.from.left, node.to.left, size)
            val top = lerp(node.from.top, node.to.top, size)
            node.current.set(left, top, left + lerp(node.from.width(), node.to.width(), p),
                top + lerp(node.from.height(), node.to.height(), p))
        }
        nodes.forEach { node ->
            val rect = node.current
            val parent = nodesByView[node.parent]?.current
            val isFooter = node.view in footer
            if (isFooter) {
                // Resize the visual row, not only its container. Recycler children
                // and SeekBar internals retain their measured expanded geometry.
                node.view.pivotX = 0f
                node.view.pivotY = 0f
                node.view.scaleX = if (node.to.width() > 0) rect.width() / node.to.width() else 1f
                node.view.scaleY = if (node.to.height() > 0) rect.height() / node.to.height() else 1f
            }
            bounds(node.view, rect.left - (parent?.left ?: 0f), rect.top - (parent?.top ?: 0f),
                if (isFooter) node.to.width() else rect.width(),
                if (isFooter) node.to.height() else rect.height())
        }
        fonts.forEach { font ->
            font.view.setTextSize(0, lerp(font.from, font.to, fontSize))
        }
        val alphaProgress = access.float(animator, "alphaProgress")
        footer.forEach { it.alpha = alphaProgress }
        deviceIcon?.alpha = access.float(animator, "alphaDeviceIcon")
        val color = access.float(animator, "color")
        appIcon?.alpha = color
        if (!sourceCoverVisible) cover?.alpha = color
    }

    /** Cache reflection once per plugin classloader, not once per frame. */
    internal class Access {
        private val fields = HashMap<Pair<Class<*>, String>, Field>()
        private val methods = HashMap<Triple<Class<*>, String, Int>, Method>()
        fun field(type: Class<*>, name: String): Field = fields.getOrPut(type to name) {
            generateSequence(type) { it.superclass }.mapNotNull {
                runCatching { it.getDeclaredField(name) }.getOrNull()
            }.first().apply { isAccessible = true }
        }
        fun call(receiver: Any, name: String, vararg args: Any): Any? {
            val method = methods.getOrPut(Triple(receiver.javaClass, name, args.size)) {
                generateSequence(receiver.javaClass) { it.superclass }.flatMap { it.declaredMethods.asSequence() }
                    .first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }
            }
            return method.invoke(receiver, *args)
        }
        fun number(receiver: Any, getter: String) = (call(receiver, getter) as Number).toFloat()
        fun float(receiver: Any, name: String) = field(receiver.javaClass, name).getFloat(receiver)
    }

    companion object {
        fun capture(animator: Any, access: Access, generation: Int,
            baseFrame: (Any) -> Unit): LandscapeMediaPanelTransition? {
            val values = access.field(animator.javaClass, "animValue").get(animator) ?: return null
            val source = access.field(animator.javaClass, "fromView").get(animator) ?: return null
            val delegate = access.field(animator.javaClass, "contentController").get(animator) ?: return null
            val controller = access.call(animator, "getToView") ?: return null
            val frame = access.call(controller, "getItemFrame") as View
            val background = access.call(controller, "getContentBg") as View
            val sourceContent = access.call(source, "getContentView") as View
            if (frame.width <= 0 || frame.height <= 0 || sourceContent.width <= 0 || sourceContent.height <= 0) return null
            fun target(getter: String) = access.call(delegate, getter) as? View
            val pairs = listOf(
                "getCoverParentView" to "getCoverView", "getCoverView" to "getCoverView",
                "getTitleView" to "getTitleView", "getSubtitleView" to "getArtistView",
                "getNoPlayView" to "getNoPlayView", "getAvContainerView" to "getAudioContainerView",
                "getAvPrevView" to "getAudioPrevView", "getAvPlayView" to "getAudioPlayView",
                "getAvNextView" to "getAudioNextView", "getDeviceIconView" to "getDeviceIconView",
            )
            val sourceBounds = pairs.mapNotNull { (to, from) ->
                val view = target(to) ?: return@mapNotNull null
                val origin = access.call(source, from) as? View ?: return@mapNotNull null
                view to relativeBounds(origin, sourceContent)
            }.toMap()
            val fontViews = listOfNotNull(target("getTitleView"), target("getSubtitleView"), target("getNoPlayView"))
            val footer = listOfNotNull(target("getProgressContainerView"), target("getTvContainerView"),
                target("getVolumeBarContainerView"), target("getListView"))
            val appIcon = target("getAppIconContainerView")
            val tracked = (sourceBounds.keys + footer + listOfNotNull(appIcon, target("getAppIconView"))).toSet()
            val ancestors = linkedSetOf<View>()
            tracked.forEach { child ->
                var current: View? = child
                while (current != null && current !== frame) {
                    ancestors += current
                    current = current.parent as? View
                }
                check(current === frame) { "Media view is outside the native panel frame" }
            }
            val scaleX = sourceContent.width.toFloat() / frame.width
            val scaleY = sourceContent.height.toFloat() / frame.height
            val coverParent = target("getCoverParentView")
            val structural = listOfNotNull(target("getHeaderLayout"), target("getMetaInfo"))
            val targetCover = coverParent?.let { relativeBounds(it, frame) }
            val sourceCover = sourceBounds[coverParent]
            val nodes = ancestors.map { view ->
                val end = relativeBounds(view, frame)
                val start = sourceBounds[view] ?: if (view in structural) {
                    // At the compact endpoint these containers must contain ALL
                    // source labels/actions. Scaling the right-column X instead
                    // would put their left edge to the right of the source text,
                    // and native clipChildren would cut the text off mid-morph.
                    RectF(0f, 0f, sourceContent.width.toFloat(), sourceContent.height.toFloat())
                } else if ((view === appIcon || view === target("getAppIconView")) &&
                    targetCover != null && sourceCover != null && targetCover.width() > 0) {
                    val factor = sourceCover.width() / targetCover.width()
                    RectF(sourceCover.left + (end.left - targetCover.left) * factor,
                        sourceCover.top + (end.top - targetCover.top) * factor,
                        sourceCover.left + (end.right - targetCover.left) * factor,
                        sourceCover.top + (end.bottom - targetCover.top) * factor)
                } else RectF(end.left * scaleX, end.top * scaleY, end.right * scaleX, end.bottom * scaleY)
                Node(view, start, end, view in fontViews, view.parent as? View)
            }
            val fonts = listOf("Title", "Subtitle", "NoPlay").mapNotNull { name ->
                val view = target("get${name}View") as? TextView ?: return@mapNotNull null
                Font(view, access.number(delegate, "getFrom${name}FontSize"),
                    access.number(delegate, "getTo${name}FontSize"))
            }
            return LandscapeMediaPanelTransition(access, controller, delegate, values, frame, background,
                nodes, footer, target("getDeviceIconView"), appIcon, target("getCoverView"),
                (access.call(source, "getCoverView") as View).visibility == View.VISIBLE, fonts,
                generation, baseFrame)
        }

        private fun relativeBounds(view: View, root: View): RectF {
            var x = 0f
            var y = 0f
            var current = view
            while (current !== root) {
                x += current.left
                y += current.top
                current = current.parent as? View ?: error("Unrelated media views")
                x -= current.scrollX
                y -= current.scrollY
            }
            return RectF(x, y, x + view.width, y + view.height)
        }

        private fun lerp(from: Float, to: Float, progress: Float) = from + (to - from) * progress
        private fun bounds(view: View, x: Float, y: Float, width: Float, height: Float) {
            val left = x.roundToInt()
            val top = y.roundToInt()
            val right = (x + width).roundToInt()
            val bottom = (y + height).roundToInt()
            if (view is ImageView) {
                // ImageView.setFrame -> configureBounds recomputes CENTER_CROP /
                // FIT_CENTER for THIS frame. Raw edge setters leave mDrawMatrix
                // at the expanded size: a compact frame then clips its top-left.
                // Only lay out this leaf, never a suppressed parent/ViewGroup.
                view.layout(left, top, right, bottom)
            } else {
                view.left = left
                view.top = top
                view.right = right
                view.bottom = bottom
            }
        }
    }
}
