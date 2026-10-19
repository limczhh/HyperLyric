package com.lidesheng.hyperlyric.root.mediacard.notification

import android.content.Context
import android.view.View
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Applies the system's focus-notification glass recipe to a media background view. */
internal object NotificationMediaGlassMaterial {
    private const val TAG = "NotificationMediaGlassMaterial"
    private val apis = WeakHashMap<ClassLoader, Api>()

    /** False means the caller must restore the native media effect after a partial failure. */
    fun apply(holder: Any, effectName: String, context: Context): Boolean {
        val profile = when (effectName) {
            "MediaViewGlassEffect" -> "normal"
            "MediaViewGlassOnKeyguardEffect", "MediaViewGlassOnKeyguardLightWallPaperEffect" -> "on_keyguard"
            "MediaViewGlassFullAodEffect" -> "full_aod"
            else -> return true // Normal and frosted materials keep their native implementation.
        }
        return runCatching {
            val loader = requireNotNull(holder.javaClass.classLoader)
            val api = apis.getOrPut(loader) { Api.create(loader) }
            val background = holder.javaClass.getDeclaredField("mediaBg")
                .apply { isAccessible = true }.get(holder) as View
            val resources = context.resources
            fun id(name: String, type: String): Int = resources
                .getIdentifier(name, type, "com.android.systemui")
                .also { require(it != 0) { "Missing SystemUI $type/$name" } }

            // Resolve everything before touching the view. Keep values in SystemUI resources,
            // including overlays, rather than copying shader constants into the module.
            val glass = resources.getStringArray(id("focus_notification_glass_params_$profile", "array"))
                .map(String::toFloat).toFloatArray()
            val blend = intArrayOf(
                resources.getColor(id("focus_notification_element_blend_shade_color_1", "color"), context.theme),
                resources.getInteger(id("blur_mode_linear_light", "integer")),
                resources.getColor(id("focus_notification_element_blend_shade_color_2", "color"), context.theme),
                resources.getInteger(id("blur_mode_lab", "integer")),
                resources.getColor(id("focus_notification_element_blend_shade_color_3", "color"), context.theme),
                resources.getInteger(id("blur_mode_pure", "integer"))
            )
            api.setMaterialType.invoke(null, 0, background)
            api.applyBlend.invoke(null, context, background, false, blend, false)
            api.setGlass.invoke(null, background, glass)
            api.setMaterialType.invoke(null, 1, background)
        }.onFailure { HookLogger.w(TAG, "应用焦点通知深色玻璃失败，恢复原生媒体材质", it) }
            .isSuccess
    }

    private class Api(
        val setMaterialType: Method,
        val setGlass: Method,
        val applyBlend: Method
    ) {
        companion object {
            fun create(loader: ClassLoader): Api {
                val glass = loader.loadClass("com.miui.systemui.util.MiGlassCompat")
                val notification = loader.loadClass("com.android.systemui.statusbar.notification.utils.NotificationUtil")
                return Api(
                    glass.getDeclaredMethod("setMiViewMaterialTypeCompat", Int::class.javaPrimitiveType, View::class.java),
                    glass.getDeclaredMethod("setMiGlassCompat", View::class.java, FloatArray::class.java),
                    notification.getDeclaredMethod(
                        "applyElementViewBlend", Context::class.java, View::class.java,
                        Boolean::class.javaPrimitiveType, IntArray::class.java, Boolean::class.javaPrimitiveType
                    )
                ).also { api ->
                    listOf(api.setMaterialType, api.setGlass, api.applyBlend).forEach { it.isAccessible = true }
                }
            }
        }
    }
}
