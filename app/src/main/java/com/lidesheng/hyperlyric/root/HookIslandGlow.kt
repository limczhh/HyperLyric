package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.island.IslandProbeUtils
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.util.WeakHashMap

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
object HookIslandGlow {
    private const val TAG = "HookIslandGlow"
    private const val BASE_CONTENT_VIEW_CLASS = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val DATA_CLASS = "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"

    private lateinit var module: XposedModule
    private val hookedClassLoaders = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    )
    private val lastGlowEnabledByView = WeakHashMap<View, Boolean>()

    private val prefs: SharedPreferences?
        get() = if (::module.isInitialized) (module as? HookEntry)?.prefs else null

    fun initialize(xposedModule: XposedModule) {
        module = xposedModule
    }

    fun init(xposedModule: XposedModule, cl: ClassLoader) {
        initialize(xposedModule)
        if (!hookedClassLoaders.add(cl)) return

        try {
            val baseContentViewClass = cl.loadClass(BASE_CONTENT_VIEW_CLASS)
            val dataClass = baseContentViewClass.classLoader?.loadClass(DATA_CLASS) ?: return
            val updateTemplateMethod = baseContentViewClass.declaredMethods.find {
                it.name == "updateTemplate" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == dataClass
            }

            if (updateTemplateMethod != null) {
                updateTemplateMethod.isAccessible = true
                module.deoptimize(updateTemplateMethod)
                module.hook(updateTemplateMethod).intercept(UpdateTemplateHook())
                HookLogger.i(TAG, "已 Hook DynamicIslandBaseContentView.updateTemplate，用于媒体岛光效")
            } else {
                HookLogger.w(TAG, "未找到 updateTemplate，跳过媒体岛光效 Hook")
            }
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "跳过不支持的媒体岛光效 Hook: ${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "初始化媒体岛光效 Hook 失败", e)
        }
    }

    class UpdateTemplateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.thisObject as? View
            val data = chain.args.getOrNull(0)
            val color = prepareHighlightColor(view, data)
            if (color != null) {
                injectTickerDataHighlightColor(data, color)
            }
            return chain.proceed()
        }
    }

    private fun prepareHighlightColor(view: View?, islandData: Any?): String? {
        return runCatching {
            val sharedPrefs = prefs ?: return@runCatching null
            if (!sharedPrefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
                return@runCatching null
            }
            if (!sharedPrefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR, RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR)) {
                return@runCatching null
            }

            val mediaInfoFromIsland = IslandProbeUtils.extractMediaIslandInfo(islandData)
                ?: return@runCatching null
            val pkgName = mediaInfoFromIsland.packageName
            val lyricPkg = LyriconDataBridge.currentLyricPackageName
            if (pkgName.isEmpty() || lyricPkg.isNullOrEmpty() || pkgName != lyricPkg) {
                return@runCatching null
            }

            val context = view?.context ?: return@runCatching null
            val mediaInfo = MediaMetadataHelper.getMediaInfo(context, pkgName, HookLogger)
            val albumArt = mediaInfo.albumArt ?: return@runCatching null
            val mediaColorKey = CoverColorHelper.updateMediaSession(
                packageName = pkgName,
                title = mediaInfo.title,
                artist = mediaInfo.artist,
                album = mediaInfo.album
            )
            val useGradient = sharedPrefs.getBoolean(
                RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT,
                RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_GRADIENT
            )
            val color = CoverColorHelper.extractColors(albumArt, useGradient, mediaColorKey)
                .second
                .firstOrNull()
                ?: return@runCatching null

            String.format("#%08X", color)
        }.onFailure { e ->
            HookLogger.e(TAG, "解析媒体岛光效颜色失败", e)
        }.getOrNull()
    }

    private fun injectTickerDataHighlightColor(islandData: Any?, color: String) {
        runCatching {
            val receiver = islandData ?: return
            val getTickerData = receiver.javaClass.methods.find {
                it.name == "getTickerData" && it.parameterTypes.isEmpty()
            } ?: return
            val setTickerData = receiver.javaClass.methods.find {
                it.name == "setTickerData" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == String::class.java
            } ?: return

            val tickerData = getTickerData.invoke(receiver) as? String ?: return
            if (tickerData.isBlank()) return

            val json = JSONObject(tickerData)
            json.put("highlightColor", color)
            setTickerData.invoke(receiver, json.toString())
        }.onFailure { e ->
            HookLogger.e(TAG, "向 tickerData 注入 highlightColor 失败", e)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun injectAndTriggerGlow(contentView: View, islandData: Any?, sharedPrefs: SharedPreferences) {
        // Color is injected before updateTemplate parses tickerData.
    }

    fun updateMusicGlow(contentView: View?, @Suppress("UNUSED_PARAMETER") albumArt: Bitmap?, sharedPrefs: SharedPreferences) {
        val enabled = sharedPrefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
            RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
        )
        if (!enabled) {
            clearViewHighlightColor(contentView)
            rememberGlowEnabled(contentView, false)
            return
        }
        if (contentView != null && rememberGlowEnabled(contentView, true) != true) {
            refreshTemplateForCurrentIsland(contentView)
        }
    }

    private fun rememberGlowEnabled(view: View?, enabled: Boolean): Boolean? {
        view ?: return null
        return synchronized(lastGlowEnabledByView) {
            val previous = lastGlowEnabledByView[view]
            lastGlowEnabledByView[view] = enabled
            previous
        }
    }

    private fun refreshTemplateForCurrentIsland(view: View) {
        runCatching {
            val islandData = IslandProbeUtils.getCurrentIslandData(view) ?: return
            val updateTemplate = view.javaClass.methods.find {
                it.name == "updateTemplate" && it.parameterTypes.size == 1
            } ?: return
            updateTemplate.invoke(view, islandData)
        }.onFailure { e ->
            HookLogger.e(TAG, "刷新媒体岛光效模板失败", e)
        }
    }

    private fun clearViewHighlightColor(view: View?) {
        runCatching {
            view ?: return
            val template = findFieldInHierarchy(view.javaClass, "template")?.get(view)
            template?.javaClass?.methods
                ?.find {
                    it.name == "setHighlightColor" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java
                }
                ?.invoke(template, null)

            val highlightState = findFieldInHierarchy(view.javaClass, "_highlightColor")?.get(view)
            highlightState?.javaClass?.methods
                ?.find { it.name == "setValue" && it.parameterTypes.size == 1 }
                ?.invoke(highlightState, null)
        }.onFailure { e ->
            HookLogger.e(TAG, "清除视图 highlightColor 失败", e)
        }
    }

    private fun findFieldInHierarchy(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != View::class.java) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
