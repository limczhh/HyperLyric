package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.view.View
import android.view.ViewGroup

/** Preserve native parameter types without depending on the plugin's AndroidX. */
internal object MediaLayoutParams {
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
