package com.lidesheng.hyperlyric.root.mediacard.island

import android.view.View
import java.lang.reflect.Method

/** Optional plugin material API; older plugins use classic background blending. */
internal class IslandExpandedMediaGlassApi private constructor(
    private val isBionicsMode: Method,
    private val setBackgroundStyle: Method,
    private val clearBionicsMaterial: Method,
    private val lightToken: Any
) {
    fun isGlassMode(): Boolean = isBionicsMode.invoke(null) as Boolean

    fun applyLight(view: View) {
        setBackgroundStyle.invoke(null, view, null, lightToken)
    }

    fun clear(view: View) {
        clearBionicsMaterial.invoke(null, view)
    }

    companion object {
        fun create(classLoader: ClassLoader): IslandExpandedMediaGlassApi? {
            val style = try {
                classLoader.loadClass("miui.systemui.util.MiBackgroundStyle")
            } catch (_: ClassNotFoundException) {
                return null
            }
            val token = style.getDeclaredField("DEFAULT_GLASS_TOKEN").apply { isAccessible = true }
            val setter = style.declaredMethods.single { method ->
                method.name == "setMiBackgroundStyle" && method.parameterTypes.let { types ->
                    types.size == 3 && types[0] == View::class.java && types[2] == token.type
                }
            }
            return IslandExpandedMediaGlassApi(
                style.getDeclaredMethod("isBionicsMode").apply { isAccessible = true },
                setter.apply { isAccessible = true },
                style.getDeclaredMethod("clearBionicsMaterial", View::class.java)
                    .apply { isAccessible = true },
                requireNotNull(token.get(null))
            )
        }
    }
}
