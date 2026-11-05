package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Preserve native parameter types without depending on the plugin's AndroidX. */
internal object MediaLayoutParams {
    private data class ParentMethods(val check: Method, val generate: Method)
    private val parentMethods = ConcurrentHashMap<Class<*>, ParentMethods>()

    /** A snapshot's type belongs to its old parent, not necessarily its current one. */
    fun assign(view: View, params: ViewGroup.LayoutParams) {
        view.layoutParams = (view.parent as? ViewGroup)?.let { forParent(it, params) } ?: params
    }

    fun forParent(parent: ViewGroup, params: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        val methods = parentMethods.getOrPut(parent.javaClass) {
            fun method(name: String): Method {
                var type: Class<*>? = parent.javaClass
                while (type != null) {
                    try {
                        return type.getDeclaredMethod(name, ViewGroup.LayoutParams::class.java)
                            .apply { isAccessible = true }
                    } catch (_: NoSuchMethodException) {
                        type = type.superclass
                    }
                }
                error("Missing ViewGroup.$name")
            }
            ParentMethods(method("checkLayoutParams"), method("generateLayoutParams"))
        }
        if (methods.check.invoke(parent, params) == true) return params
        // Framework conversions preserve margins even on versions whose generic
        // generateLayoutParams constructor copies only width and height.
        val converted = when {
            parent.javaClass == FrameLayout::class.java -> FrameLayout.LayoutParams(params)
            parent.javaClass == LinearLayout::class.java -> LinearLayout.LayoutParams(params)
            else -> methods.generate.invoke(parent, params) as ViewGroup.LayoutParams
        }
        if (params is ViewGroup.MarginLayoutParams && converted is ViewGroup.MarginLayoutParams) {
            converted.setMargins(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin)
            if (params.isMarginRelative) {
                converted.marginStart = params.marginStart
                converted.marginEnd = params.marginEnd
            }
        }
        check(methods.check.invoke(parent, converted) == true) { "Invalid media LayoutParams for ${parent.javaClass.name}" }
        return converted
    }

    /** Normalize before measuring: View.setLayoutParams does not validate the parent type. */
    fun validateTree(view: View) {
        val params = view.layoutParams
        val parent = view.parent as? ViewGroup
        if (params != null && parent != null) {
            val valid = forParent(parent, params)
            if (valid !== params) view.layoutParams = valid
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) validateTree(view.getChildAt(index))
        }
    }

    /** A different View must never share a copied ConstraintLayout's solver widget. */
    fun fresh(original: ViewGroup.LayoutParams): ViewGroup.LayoutParams = original.javaClass
        .getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        .newInstance(original.width, original.height) as ViewGroup.LayoutParams

    fun copy(original: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        val type = original.javaClass
        val constructor = runCatching { type.getConstructor(type) }.getOrNull()
            ?: type.getConstructor(ViewGroup.LayoutParams::class.java)
        return constructor.newInstance(original) as ViewGroup.LayoutParams
    }

    fun constrain(view: View, width: Int, height: Int, vararg values: Pair<String, Any>) {
        val current = view.layoutParams
        val type = current.javaClass
        if (current.width == width && current.height == height &&
            values.all { (name, value) -> type.getField(name).get(current) == value }) return
        view.layoutParams = (type.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .newInstance(width, height) as ViewGroup.LayoutParams).apply {
            values.forEach { (name, value) -> type.getField(name).set(this, value) }
        }
    }
}
