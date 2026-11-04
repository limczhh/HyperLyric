package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.ImageView
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import kotlin.math.roundToInt

/** Crossfades the already blurred native artwork into the real, untouched backdrop. */
internal class AlbumBlurTransition(
    private val artwork: ImageView,
    loader: ClassLoader,
) {
    private val native = runCatching { NativeBlur(loader) }.getOrNull()
    private val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var active = false
    private var ownsBlur = false
    private var failed = false
    private var background: View? = null
    private var configured: Geometry? = null
    private val drawBounds = RectF()

    private data class Geometry(val width: Int, val height: Int, val top: Int,
        val bottom: Int, val radius: Int, val progress: Float)

    fun setActive(enabled: Boolean) {
        active = enabled
        if (!enabled) clear()
    }

    fun setBackground(view: View) {
        background = view
    }

    fun invalidateContent() { configured = null }

    fun sync(regionTop: Int, regionBottom: Int, progress: Float) {
        val api = native
        val amount = progress.coerceIn(0f, 1f)
        val width = artwork.width
        val height = artwork.height
        if (!active || api == null || failed || (artwork.drawable == null && artwork.background == null) ||
            width <= 0 || height <= 0 || amount <= 0f) {
            clear()
            return
        }
        val top = regionTop.coerceIn(0, height)
        val bottom = regionBottom.coerceIn(top, height)
        if (bottom <= top) {
            clear()
            return
        }
        val radius = (api.radius(artwork, background) * amount).roundToInt().coerceAtLeast(1)
        val next = Geometry(width, height, top, bottom, radius, amount)
        if (next == configured) return
        val span = bottom - top
        // Native gradient parameters are x0,y0,r0,x1,y1,r1 (not an alpha array).
        // Use exactly the same title-to-artist interval as the opacity mask.
        // No extra region above the title or early full-blur plateau.
        ownsBlur = true
        if (!api.apply(artwork, radius, top.toFloat(), bottom.toFloat())) {
            clear()
            failed = true
            HookLogger.w("ControlCenterMediaLayout", "封面原生局部模糊不可用，保留原生封面")
            return
        }
        val samples = 32
        val positions = FloatArray(samples + 3)
        val colors = IntArray(samples + 3)
        positions[0] = 0f
        colors[0] = Color.WHITE
        for (index in 0..samples) {
            val t = index.toFloat() / samples
            val blend = t * t * (3f - 2f * t)
            // The clear artwork fades all the way to zero at the artist's lower
            // edge. The real system backdrop underneath therefore becomes the
            // only visible layer at the endpoint.
            val contribution = 1f - blend
            val alpha = ((1f - amount * (1f - contribution)) * 255f).roundToInt()
            positions[index + 1] = (top + span * t) / height
            colors[index + 1] = Color.argb(alpha, 255, 255, 255)
        }
        // Below the artist, keep the zero-artwork endpoint instead of starting
        // another transition or bringing the clear artwork back.
        positions[positions.lastIndex] = 1f
        colors[colors.lastIndex] = colors[colors.lastIndex - 1]
        mask.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors, positions, Shader.TileMode.CLAMP)
        configured = next
        invalidateParent()
    }

    fun draw(canvas: Canvas, original: () -> Any?): Any? {
        if (!active || mask.shader == null) return original()
        // drawChild renders the whole native child (background and self-blur)
        // before applying the mask in its PARENT. Masking inside onDraw lets the
        // blur kernel spread higher-opacity pixels back into the bottom edge.
        drawBounds.set(0f, 0f, artwork.width.toFloat(), artwork.height.toFloat())
        artwork.matrix.mapRect(drawBounds)
        drawBounds.offset(artwork.left.toFloat(), artwork.top.toFloat())
        val save = canvas.saveLayer(drawBounds, null)
        return try {
            val result = original()
            val local = canvas.save()
            try {
                canvas.translate(artwork.left.toFloat(), artwork.top.toFloat())
                canvas.concat(artwork.matrix)
                canvas.drawRect(0f, 0f, artwork.width.toFloat(), artwork.height.toFloat(), mask)
            } finally {
                canvas.restoreToCount(local)
            }
            result
        } finally {
            canvas.restoreToCount(save)
        }
    }

    fun clear() {
        if (ownsBlur) {
            native?.clear(artwork)
            ownsBlur = false
            invalidateParent()
        }
        configured = null
        mask.shader = null
    }

    private fun invalidateParent() {
        artwork.invalidate()
        // The alpha mask is recorded by the parent; invalidating just the child
        // can otherwise reuse the parent's old display list during native morphing.
        (artwork.parent as? View)?.invalidate()
    }

    private class NativeBlur(loader: ClassLoader) {
        private val api = loader.loadClass("miuix.core.util.MiuiBlurUtils")
        private fun method(name: String, vararg types: Class<*>): Method =
            api.getDeclaredMethod(name, *types).apply { isAccessible = true }
        private val blur = method("setSelfBlur", View::class.java,
            Int::class.javaPrimitiveType!!, ArrayList::class.java)
        private val type = method("setSelfBlurType", View::class.java, Int::class.javaPrimitiveType!!)
        private val gradient = method("setSelfGradientBlurParams", View::class.java,
            FloatArray::class.java, Int::class.javaPrimitiveType!!)
        private val getRadius = method("getBackgroundBlurRadius", View::class.java)
        private val noBlend = arrayListOf<android.graphics.Point>()

        fun radius(view: View, background: View?): Int {
            val current = runCatching { background?.let { getRadius.invoke(null, it) as Int } ?: 0 }
                .getOrDefault(0)
            if (current > 0) return current
            val id = view.resources.getIdentifier("secondary_max_blur_radius", "dimen", "miui.systemui.plugin")
            return if (id != 0) view.resources.getDimensionPixelSize(id)
                else (20f * view.resources.displayMetrics.density).roundToInt()
        }

        fun apply(view: View, radius: Int, top: Float, blurredTop: Float): Boolean = runCatching {
            blur.invoke(null, view, radius, noBlend) == true &&
                type.invoke(null, view, 2) == true &&
                gradient.invoke(null, view, floatArrayOf(0f, top, 0f, 0f, blurredTop, radius.toFloat()), 1) == true
        }.getOrDefault(false)

        fun clear(view: View) {
            runCatching { type.invoke(null, view, 0) }
            runCatching { blur.invoke(null, view, 0, noBlend) }
        }
    }
}
