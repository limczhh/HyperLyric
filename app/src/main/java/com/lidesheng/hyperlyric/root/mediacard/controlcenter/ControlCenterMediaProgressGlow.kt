package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.util.WeakHashMap

/** Replaces MiPlaySeekBar with its default HyperProgressSeekBar mode. */
internal class ControlCenterMediaProgressGlow(
    private val header: View,
    private val progressContainer: View,
    private val enabled: Boolean,
) {
    private var seekBarField: Field? = null
    private var original: SeekBar? = null
    private var replacement: SeekBar? = null
    private var parent: ViewGroup? = null
    private var originalIndex = -1

    fun apply() {
        if (!enabled) return
        val field = seekBarField ?: findField(header.javaClass, "seekBar")?.also {
            it.isAccessible = true
            seekBarField = it
        } ?: return
        val native = runCatching { field.get(header) as? SeekBar }.getOrNull()
            ?: findSeekBar(progressContainer)
            ?: return

        if (native === replacement) {
            ensureAttached()
            allowOverflow(native)
            return
        }
        // The native MiPlaySeekBar is the entry point to Xiaomi's shader-backed
        // HyperProgressSeekBar. Do not replace an unrelated seek bar if a ROM
        // changes this detail layout.
        if (!native.javaClass.name.endsWith("MiPlaySeekBar")) return
        val nativeParent = native.parent as? ViewGroup ?: return
        val index = nativeParent.indexOfChild(native).takeIf { it >= 0 } ?: return
        val layoutParams = runCatching { MediaLayoutParams.copy(native.layoutParams) }.getOrNull()
            ?: return
        val listener = findField(native.javaClass, "seekBarChangeListener")?.let {
            it.isAccessible = true
            runCatching { it.get(native) as? SeekBar.OnSeekBarChangeListener }.getOrNull()
        }
        val fresh = create(native) ?: return
        val drawing = createDrawing(fresh) ?: return
        fresh.id = native.id
        fresh.layoutParams = layoutParams
        fresh.visibility = native.visibility
        fresh.alpha = native.alpha
        fresh.isEnabled = native.isEnabled
        fresh.min = native.min
        fresh.max = native.max
        fresh.progress = native.progress
        fresh.secondaryProgress = native.secondaryProgress
        fresh.contentDescription = native.contentDescription
        fresh.thumbTintList = native.thumbTintList
        fresh.progressTintList = native.progressTintList
        fresh.progressBackgroundTintList = native.progressBackgroundTintList
        fresh.setPadding(
            native.paddingLeft,
            native.paddingTop,
            native.paddingRight,
            native.paddingBottom
        )
        // HyperProgressSeekBar defaults to a shader centre at 19dp. MiPlay's
        // time-line slot is shorter, so padding alone cannot centre its track.
        fresh.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            drawing.center(view as SeekBar)
        }
        fresh.setOnSeekBarChangeListener(listener)

        try {
            nativeParent.removeView(native)
            nativeParent.addView(fresh, index.coerceIn(0, nativeParent.childCount))
            allowOverflow(fresh)
            field.set(header, fresh)
            original = native
            replacement = fresh
            parent = nativeParent
            originalIndex = index
            drawings[fresh] = drawing
        } catch (_: Throwable) {
            if (fresh.parent === nativeParent) nativeParent.removeView(fresh)
            if (native.parent == null) nativeParent.addView(native, index.coerceIn(0, nativeParent.childCount))
            runCatching { field.set(header, native) }
        }
    }

    private fun allowOverflow(view: View) {
        var host = view.parent as? ViewGroup
        while (host != null) {
            if (host.clipChildren) host.clipChildren = false
            if (host.clipToPadding) host.clipToPadding = false
            if (host === header) break
            host = host.parent as? ViewGroup
        }
    }

    private fun createDrawing(view: SeekBar): GlowDrawing? = runCatching {
        fun field(name: String) = requireNotNull(findField(view.javaClass, name)).apply { isAccessible = true }
        GlowDrawing(
            field("runtimeShader").get(view) as RuntimeShader,
            field("mPaint").get(view) as Paint,
            field("uTrackPosition").get(view) as FloatArray,
            field("uTrackSize").get(view) as FloatArray,
            (field("uHeadSize").get(view) as FloatArray)[1],
            field("mProgressSeekBarMinHeight").getInt(view).coerceAtLeast(1),
            field("mProgressHeight"),
        )
    }.getOrNull()

    /** Retain native animation/shader state; enlarge only the draw rectangle. */
    private class GlowDrawing(
        val shader: RuntimeShader,
        val paint: Paint,
        val position: FloatArray,
        val trackSize: FloatArray,
        val headHeight: Float,
        val minTrackHeight: Int,
        val heightField: Field,
    ) {
        fun center(view: SeekBar) {
            val y = view.height / 2f
            if (view.height <= 0 || position[1] == y) return
            position[1] = y
            shader.setFloatUniform("uTrackPosition", position)
        }

        fun draw(view: SeekBar, canvas: Canvas) {
            center(view)
            trackSize[1] = heightField.getInt(view).toFloat()
            shader.setFloatUniform("uTrackSize", trackSize)
            shader.setFloatUniform("uTrackProgress",
                (view.progress - view.min).toFloat() / (view.max - view.min).coerceAtLeast(1))
            val overflow = ((headHeight * trackSize[1] / minTrackHeight - view.height) / 2f)
                .coerceAtLeast(0f) + 2f
            canvas.drawRect(0f, -overflow, view.width.toFloat(), view.height + overflow, paint)
        }
    }

    private fun ensureAttached() {
        val view = replacement ?: return
        val host = parent ?: return
        if (view.parent === host) return
        (view.parent as? ViewGroup)?.removeView(view)
        original?.takeIf { it.parent === host }?.let(host::removeView)
        host.addView(view, originalIndex.coerceIn(0, host.childCount))
    }

    private fun create(original: SeekBar): SeekBar? {
        val type = original.javaClass
        val context = original.context
        val constructor = type.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Context::class.java))
        } ?: type.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(
                arrayOf(Context::class.java, AttributeSet::class.java, Int::class.javaPrimitiveType)
            )
        } ?: return null
        return runCatching {
            constructor.isAccessible = true
            when (constructor.parameterCount) {
                1 -> constructor.newInstance(context) as SeekBar
                else -> constructor.newInstance(context, null, 0) as SeekBar
            }
        }.getOrNull()
    }

    private fun findSeekBar(view: View): SeekBar? {
        if (view is SeekBar) return view
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findSeekBar(group.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { return current.getDeclaredField(name) }
            current = current.superclass
        }
        return null
    }

    companion object {
        private val drawings = WeakHashMap<SeekBar, GlowDrawing>()

        fun installOverflowDrawing(module: XposedModule, loader: ClassLoader): HookHandle {
            val type = loader.loadClass("miuix.miuixbasewidget.widget.HyperProgressSeekBar")
            val draw = type.getDeclaredMethod("drawHighEndMode", Canvas::class.java).apply { isAccessible = true }
            return module.managedHook(draw, "media.controlcenter.progress_glow", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val view = chain.thisObject as? SeekBar ?: return chain.proceed()
                    val drawing = drawings[view] ?: return chain.proceed()
                    drawing.draw(view, chain.args[0] as Canvas)
                    return null
                }
            })
        }
    }
}
