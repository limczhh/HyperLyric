package com.lidesheng.hyperlyric.root.island.effects.glow

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.policy.IslandModificationTargetPolicy
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
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
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val DATA_CLASS =
        "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"

    private lateinit var module: XposedModule
    private val hookedClassLoaders = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
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
                HookLogger.d(TAG, "媒体岛光效 Hook 已初始化: method=updateTemplate")
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
                val result = injectTickerDataHighlightColor(data, color)
                HookLogger.dState(
                    stateId = "HookIslandGlow.apply:${System.identityHashCode(view)}",
                    tag = TAG,
                    state = "$color|$result"
                ) {
                    "媒体岛光效颜色注入: color=$color, result=$result, " +
                            "view=${System.identityHashCode(view)}"
                }
            }
            return chain.proceed()
        }
    }

    private fun prepareHighlightColor(view: View?, islandData: Any?): String? {
        return runCatching {
            val sharedPrefs = prefs ?: return@runCatching null
            if (!sharedPrefs.getBoolean(
                    RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                    RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
                )
            ) {
                HookLogger.dState(
                    stateId = "HookIslandGlow.prepare",
                    tag = TAG,
                    state = "super_island_disabled"
                ) {
                    "媒体岛光效颜色未准备: reason=super_island_disabled"
                }
                return@runCatching null
            }
            if (!sharedPrefs.getBoolean(
                    RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                    RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
                )
            ) {
                HookLogger.dState(
                    stateId = "HookIslandGlow.prepare",
                    tag = TAG,
                    state = "extract_disabled"
                ) {
                    "媒体岛光效颜色未准备: reason=color_extraction_disabled"
                }
                return@runCatching null
            }

            val target = IslandModificationTargetPolicy.resolve(
                data = islandData,
                hostRoot = view as? ViewGroup
            )
            val mediaInfoFromIsland = target.mediaInfo
                ?: run {
                    HookLogger.dState(
                        stateId = "HookIslandGlow.prepare",
                        tag = TAG,
                        state = "not_media_island"
                    ) {
                        "媒体岛光效颜色未准备: reason=not_media_island"
                    }
                    return@runCatching null
                }
            val pkgName = mediaInfoFromIsland.packageName
            val scope = IslandModificationTargetPolicy.currentScope(sharedPrefs)
            if (pkgName.isEmpty() || !IslandModificationTargetPolicy.allows(target, scope)) {
                HookLogger.dState(
                    stateId = "HookIslandGlow.prepare",
                    tag = TAG,
                    state = "scope_not_allowed|$scope|$pkgName"
                ) {
                    "媒体岛光效颜色未准备: reason=scope_not_allowed, scope=$scope, " +
                            "mediaPackage=$pkgName"
                }
                return@runCatching null
            }

            val context = view?.context ?: run {
                HookLogger.dState(
                    stateId = "HookIslandGlow.prepare",
                    tag = TAG,
                    state = "view_context_missing"
                ) {
                    "媒体岛光效颜色未准备: reason=view_context_missing"
                }
                return@runCatching null
            }
            val mediaInfo = when (scope) {
                IslandModificationTargetPolicy.Scope.ALL_MEDIA -> {
                    // Do not merge the current lyric source into an unrelated native media
                    // island. Pin the read to the token carried by that island when available.
                    MediaMetadataHelper.getMediaInfo(
                        context = context,
                        packageName = pkgName,
                        logger = HookLogger,
                        preferredSessionToken = IslandProbeUtils.extractMediaSessionToken(islandData)
                    )
                }

                IslandModificationTargetPolicy.Scope.INJECTED_LYRIC ->
                    CurrentMediaInfoResolver.getMediaInfo(context, pkgName, HookLogger)
            }

            if (scope == IslandModificationTargetPolicy.Scope.ALL_MEDIA) {
                val palette = mediaInfo.albumArt
                    ?.takeUnless { it.isRecycled }
                    ?.let(CoverColorHelper::extractPalette)
                val color = palette
                    ?.second
                    ?.firstOrNull()
                    ?: run {
                        HookLogger.dState(
                            stateId = "HookIslandGlow.prepare",
                            tag = TAG,
                            state = "no_palette|all_media|$pkgName"
                        ) {
                            "媒体岛光效颜色未准备: reason=no_native_cover_palette, " +
                                    "scope=$scope, mediaPackage=$pkgName"
                        }
                        return@runCatching null
                    }
                val colorString = String.format("#%08X", color)
                HookLogger.dState(
                    stateId = "HookIslandGlow.prepare",
                    tag = TAG,
                    state = "ready|$scope|$colorString"
                ) {
                    "媒体岛光效颜色已准备: color=$colorString, scope=$scope, source=native_cover"
                }
                return@runCatching colorString
            }

            val colorSession = CoverColorHelper.currentSession(mediaInfo) ?: run {
                HookLogger.dState(
                    stateId = "HookIslandGlow.prepare",
                    tag = TAG,
                    state = "no_color_session"
                ) {
                    "媒体岛光效颜色未准备: reason=no_matching_color_session"
                }
                return@runCatching null
            }
            val artworkRequest = CoverColorHelper.ensureArtworkColors(mediaInfo)
            val useGradient = LyricTextColorStylePolicy.usesCoverGradient(
                LyricTextColorStylePolicy.read(sharedPrefs)
            )
            val matchingArtworkRequest = artworkRequest?.takeIf {
                it.colorSession.revision == colorSession.revision
            }
            val palette = if (matchingArtworkRequest != null) {
                CoverColorHelper.getCachedColors(useGradient, matchingArtworkRequest)
            } else {
                CoverColorHelper.getCachedColors(useGradient, colorSession)
            }
            val color = palette
                ?.second
                ?.firstOrNull()
                ?: run {
                    HookLogger.dState(
                        stateId = "HookIslandGlow.prepare",
                        tag = TAG,
                        state = "no_palette|${colorSession.revision}"
                    ) {
                        "媒体岛光效颜色未准备: reason=no_cached_cover_palette, " +
                                "sessionRevision=${colorSession.revision}"
                    }
                    return@runCatching null
                }

            val colorString = String.format("#%08X", color)
            HookLogger.dState(
                stateId = "HookIslandGlow.prepare",
                tag = TAG,
                state = "ready|${colorSession.revision}|${artworkRequest?.revision}|$colorString"
            ) {
                "媒体岛光效颜色已准备: color=$colorString, sessionRevision=${colorSession.revision}, " +
                        "artworkRevision=${artworkRequest?.revision}, source=cover"
            }
            colorString
        }.onFailure { e ->
            HookLogger.e(TAG, "解析媒体岛光效颜色失败", e)
        }.getOrNull()
    }

    private fun injectTickerDataHighlightColor(islandData: Any?, color: String): String {
        return runCatching {
            val receiver = islandData ?: return@runCatching "island_data_null"
            val getTickerData = receiver.javaClass.methods.find {
                it.name == "getTickerData" && it.parameterTypes.isEmpty()
            } ?: return@runCatching "getter_missing"
            val setTickerData = receiver.javaClass.methods.find {
                it.name == "setTickerData" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java
            } ?: return@runCatching "setter_missing"

            val tickerData = getTickerData.invoke(receiver) as? String
                ?: return@runCatching "ticker_data_null"
            if (tickerData.isBlank()) return@runCatching "ticker_data_blank"

            val json = JSONObject(tickerData)
            json.put("highlightColor", color)
            setTickerData.invoke(receiver, json.toString())
            "applied"
        }.onFailure { e ->
            HookLogger.e(TAG, "向 tickerData 注入 highlightColor 失败", e)
        }.getOrDefault("exception")
    }

    fun updateMusicGlow(
        contentView: View?,
        sharedPrefs: SharedPreferences,
        forceRefresh: Boolean = false
    ) {
        val enabled = sharedPrefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
            RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
        )
        if (!enabled) {
            HookLogger.dState(
                stateId = "HookIslandGlow.config",
                tag = TAG,
                state = "disabled"
            ) {
                "媒体岛光效未生效: reason=color_extraction_disabled"
            }
            clearViewHighlightColor(contentView)
            rememberGlowEnabled(contentView, false)
            if (forceRefresh) {
                contentView?.let(::refreshTemplateForCurrentIsland)
            }
            return
        }
        if (contentView != null) {
            val wasEnabled = rememberGlowEnabled(contentView, true)
            if (forceRefresh || wasEnabled != true) {
                refreshTemplateForCurrentIsland(contentView)
            }
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
