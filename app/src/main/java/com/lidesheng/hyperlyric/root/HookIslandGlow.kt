package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.content.SharedPreferences
import android.view.View
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import io.github.libxposed.api.XposedModule

/**
 * 超级岛胶囊边缘描边颜色注入
 *
 * 策略：Hook updateTemplate()，在系统解析完 IslandTemplate 后，
 * 将 highlightColor 设置为专辑封面主色。系统会在 updateMedianLuma /
 * updateDarkLightMode 中自动使用该颜色渲染描边。
 */
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
object HookIslandGlow {
    private const val BASE_CONTENT_VIEW_CLASS = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val DATA_CLASS = "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"

    private lateinit var module: XposedModule
    private var isHooked = false

    private val prefs: SharedPreferences?
        get() = if (::module.isInitialized) (module as? HookEntry)?.prefs else null

    fun init(xposedModule: XposedModule, cl: ClassLoader) {
        if (isHooked) return
        module = xposedModule

        runCatching {
            val baseContentViewClass = cl.loadClass(BASE_CONTENT_VIEW_CLASS)

            // Hook updateTemplate — 注入 highlightColor
            val dataClass = baseContentViewClass.classLoader?.loadClass(DATA_CLASS) ?: return@runCatching
            val updateTemplateMethod = baseContentViewClass.declaredMethods.find { 
                it.name == "updateTemplate" && it.parameterTypes.size == 1 && it.parameterTypes[0] == dataClass
            }

            if (updateTemplateMethod != null) {
                updateTemplateMethod.isAccessible = true
                module.deoptimize(updateTemplateMethod)
                module.hook(updateTemplateMethod).intercept { chain ->
                    val result = chain.proceed()
                    injectHighlightColor(chain)
                    result
                }
                HookLogger.i("HookIslandGlow","ModuleInit : 超级岛外圈光效注入成功")
            } else {
                HookLogger.w("HookIslandGlow","ModuleInit : 未找到超级岛外圈光效注入位置")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e("HookIslandGlow", "ModuleInit : 超级岛外圈光效初始化失败", e)
            }
        }

        isHooked = true
    }

    private fun injectHighlightColor(chain: io.github.libxposed.api.XposedInterface.Chain) {
        runCatching {
            val extractEnabled = prefs?.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
            ) ?: false

            if (!extractEnabled) return

            val colors = CoverColorHelper.getCachedColors()?.second
            val mainColor = colors?.firstOrNull() ?: return

            val view = chain.thisObject as? View ?: return

            // 校验：当前岛是否是正在播放的音乐 App
            val pkgName = getPkgNameFromView(view)
            if (pkgName.isEmpty() || pkgName != LyriconDataBridge.activePackageName) return
            if (!HookIslandLyric.isPackageHookEnabled(pkgName)) return

            // 获取 template 字段
            val templateField = findFieldInHierarchy(view.javaClass, "template") ?: return
            val template = templateField.get(view) ?: return

            // 设置 highlightColor
            val setHlMethod = template.javaClass.methods.find { it.name == "setHighlightColor" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java }
            if (setHlMethod != null) {
                val colorStr = String.format("#%08X", mainColor)
                setHlMethod.invoke(template, colorStr)

                // 同时更新 _highlightColor LiveData/StateFlow，触发系统渲染管线
                val hlField = findFieldInHierarchy(view.javaClass, "_highlightColor")
                if (hlField != null) {
                    val hlLiveData = hlField.get(view)
                    if (hlLiveData != null) {
                        val setValueMethod = hlLiveData.javaClass.methods.find { it.name == "setValue" && it.parameterTypes.size == 1 }
                        setValueMethod?.invoke(hlLiveData, colorStr)
                    }
                }
            }
        }.onFailure { e ->
            HookLogger.e("HookIslandGlow", "HookIslandGlow : 颜色提取失败", e)
        }
    }

    private fun getPkgNameFromView(view: View): String {
        return runCatching {
            val dataField = findFieldInHierarchy(view.javaClass, "currentIslandData") ?: return ""
            val islandData = dataField.get(view) ?: return ""
            val getExtrasMethod = islandData.javaClass.methods.find { it.name == "getExtras" && it.parameterTypes.isEmpty() }
            val extras = getExtrasMethod?.invoke(islandData) as? android.os.Bundle ?: return ""
            extras.getString("miui.pkg.name") ?: ""
        }.getOrDefault("")
    }

    private fun findFieldInHierarchy(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
        var c: Class<*>? = clazz
        while (c != null && c != View::class.java) {
            try {
                val field = c.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    /**
     * 空桩，保持调用方兼容
     */
    @Suppress("UNUSED_PARAMETER")
    fun injectAndTriggerGlow(contentView: View, islandData: Any?, sharedPrefs: SharedPreferences) {
        // highlightColor 已通过 updateTemplate Hook 注入，系统自动处理
    }

    fun updateMusicGlow(albumArt: Bitmap?, sharedPrefs: SharedPreferences) {
        val enabled = sharedPrefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
            RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
        )
        if (albumArt != null && enabled && CoverColorHelper.getCachedColors() == null) {
            CoverColorHelper.extractColors(albumArt, false)
        }
    }
}

