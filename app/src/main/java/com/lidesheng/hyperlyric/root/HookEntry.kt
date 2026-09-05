package com.lidesheng.hyperlyric.root

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.lyric.source.SourceManager
import com.lidesheng.hyperlyric.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.effects.color.StatusBarTextColorHooker
import com.lidesheng.hyperlyric.root.island.effects.glow.HookIslandGlow
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowController
import com.lidesheng.hyperlyric.root.island.hooks.IslandModuleRestoreHooker
import com.lidesheng.hyperlyric.root.island.hooks.IslandLyricShareHooker
import com.lidesheng.hyperlyric.root.island.hooks.RealIslandHooker
import com.lidesheng.hyperlyric.root.island.hooks.SystemUIHookRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandNativeRefreshCoordinator
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import com.lidesheng.hyperlyric.root.island.renderer.IslandSettingsRefreshCoordinator
import com.lidesheng.hyperlyric.root.mediacard.MediaCardConfigurationRefreshHooker
import com.lidesheng.hyperlyric.root.mediacard.MediaCardElementBehaviorHooker
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaCoverStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaSingleCardSwitcherHooker
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.plugin.PluginRuntime
import com.lidesheng.hyperlyric.root.source.LyricInfoSource
import com.lidesheng.hyperlyric.root.source.LyriconSource
import com.lidesheng.hyperlyric.root.source.RootLyricSink
import com.lidesheng.hyperlyric.root.source.SuperLyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

private const val TAG = "HookEntry"

class HookEntry : XposedModule() {

    companion object {
        private const val STATE_RUNTIME_READY = "runtimeReady"
        private const val STATE_STATUS_BAR_TEXT_COLOR = "statusBarTextColor"

        @Volatile
        var activeMode = 0
        val lyriconSource = LyriconSource()
        val superLyricSource = SuperLyricSource()
        var lyricInfoSource: LyricInfoSource? = null
        var sourceManager: SourceManager? = null
            private set

        @JvmStatic
        var instance: HookEntry? = null
            private set

        private val SUPER_ISLAND_RUNTIME_REFRESH_KEYS = setOf(
            RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SEPARATOR,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
            RootConstants.KEY_HOOK_TEXT_SIZE,
            RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
            RootConstants.KEY_HOOK_FONT_WEIGHT,
            RootConstants.KEY_HOOK_FONT_ITALIC,
            RootConstants.KEY_HOOK_FADING_EDGE_LENGTH,
            RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
            RootConstants.KEY_HOOK_CENTER_LYRIC,
            RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
            RootConstants.KEY_HOOK_RIGHT_LYRIC,
            RootConstants.KEY_HOOK_ANIM_ENABLE,
            RootConstants.KEY_HOOK_ANIM_ID,
            RootConstants.KEY_HOOK_MARQUEE_MODE,
            RootConstants.KEY_HOOK_MARQUEE_SPEED,
            RootConstants.KEY_HOOK_MARQUEE_DELAY,
            RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY,
            RootConstants.KEY_HOOK_MARQUEE_INFINITE,
            RootConstants.KEY_HOOK_MARQUEE_STOP_END,
            RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE,
            RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED,
            RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY,
            RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY,
            RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE,
            RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
            RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
            RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY,
            RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
            RootConstants.KEY_HOOK_TRANSLATION_ONLY,
            RootConstants.KEY_HOOK_SWAP_TRANSLATION,
            RootConstants.KEY_HOOK_NEXT_LYRIC_LINE,
            RootConstants.KEY_HOOK_CUSTOM_FONT_PATH,
            RootConstants.KEY_HOOK_NARROW_LATIN_FONT,
            RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_BY_CHARACTER,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE,
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND
        )
    }

    private var _prefs: android.content.SharedPreferences? = null
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? =
        null
    private var runtimeApp: Application? = null
    private var pluginRuntime: PluginRuntime? = null
    private var rootLyricSink: RootLyricSink? = null

    val prefs: android.content.SharedPreferences
        get() {
            if (_prefs == null) {
                _prefs = getRemotePreferences(UIConstants.PREF_NAME)
            }
            return _prefs!!
        }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        instance = this
        HookLogger.module = this
        HookLogger.i(
            TAG,
            "模块加载完成，当前应用版本${com.lidesheng.hyperlyric.BuildConfig.VERSION_CODE}-${com.lidesheng.hyperlyric.BuildConfig.VERSION_NAME}"
        )
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        param.setSavedInstanceState(
            Bundle().apply {
                putBoolean(STATE_RUNTIME_READY, runtimeApp != null)
                putInt(
                    STATE_STATUS_BAR_TEXT_COLOR,
                    StatusBarTextColorHooker.currentTextColor()
                )
            }
        )
        // The media-card hookers intentionally stay alive in the old generation. Their
        // configuration is restart-only, so replacing them here is both unnecessary and
        // unsafe for active SystemUI card/fake-view animations.
        cleanupRuntime(preserveMediaHooks = true)
        HookLogger.d(TAG, "超级岛歌词热重载准备完成")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        instance = this
        HookLogger.module = this

        var replacedCount = 0
        var skippedCount = 0
        param.oldHookHandles.forEach { handle ->
            val replacement = createLyricReplacementHooker(handle.executable)
            if (replacement == null) {
                // Hooks without a current replacement, including media-card hooks, stay as-is.
                skippedCount++
                return@forEach
            }
            runCatching {
                handle.replaceHook(replacement)
                replacedCount++
            }.onFailure {
                // A failed hot replacement is resolved by restarting SystemUI.
                skippedCount++
            }
        }

        val state = param.savedInstanceState as? Bundle
        if (state?.containsKey(STATE_STATUS_BAR_TEXT_COLOR) == true) {
            StatusBarTextColorHooker.restoreTextColor(
                state.getInt(STATE_STATUS_BAR_TEXT_COLOR)
            )
        }
        if (state?.getBoolean(STATE_RUNTIME_READY) == true) {
            findCurrentApplication()?.let { app ->
                Handler(Looper.getMainLooper()).post { initializeSystemEnvironment(app) }
            } ?: HookLogger.w(
                TAG,
                "热重载后未取得当前 Application，等待 Application.onCreate"
            )
        }
        HookLogger.i(
            TAG,
            "超级岛歌词热重载完成: replaced=$replacedCount, " +
                    "skipped=$skippedCount"
        )
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val processName = runCatching { Application.getProcessName() }.getOrNull() ?: ""

        // 仅在主进程注入
        if (processName.contains(":")) return

        val packageName = param.packageName

        if (packageName == "com.android.systemui") {
            StatusBarTextColorHooker.setFollowStatusBarEnabled(
                LyricTextColorStylePolicy.followsStatusBar(
                    LyricTextColorStylePolicy.read(prefs)
                )
            )
            StatusBarTextColorHooker.setTextColorChangedListener {
                BaseIslandRenderer.updateTextColors()
            }
            StatusBarTextColorHooker.hook(this, param.defaultClassLoader)
            MediaCardRuntimeConfig.load(prefs)
            MediaCardConfigurationRefreshHooker.hook(this, param.defaultClassLoader)
            MediaProgressStyleHooker.hook(this, param.defaultClassLoader)
            MediaCardElementBehaviorHooker.hook(this, param.defaultClassLoader)
            IslandExpandedMediaAmbientFlowHooker.hook(this, param.defaultClassLoader)
            IslandExpandedMediaLayoutHooker.hook(this, param.defaultClassLoader)
            NotificationMediaAmbientFlowHooker.hook(this, param.defaultClassLoader)
            NotificationMediaCoverStyleHooker.hook(this, param.defaultClassLoader)
            if (MediaCardRuntimeConfig.current.notification.cardSwitcherEnabled) {
                NotificationMediaSingleCardSwitcherHooker.hook(this, param.defaultClassLoader)
            } else {
                HookLogger.d(TAG, "通知中心多媒体卡片切换功能未启用，跳过媒体卡片切换 Hook")
            }
            try {
                UnlockIslandWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                HookLogger.w(TAG, "此系统版本不支持超级岛下拉小窗白名单")
                } else {
                    HookLogger.e(TAG, "超级岛下拉小窗白名单注入失败", e)
                }
            }
            try {
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                HookLogger.w(TAG, "此系统版本不支持解锁焦点通知白名单")
                } else {
                    HookLogger.e(TAG, "焦点通知白名单注入失败", e)
                }
            }

            activeMode = prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_MODE,
                RootConstants.DEFAULT_HOOK_LYRIC_MODE
            )

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w(TAG, "跳过生命周期 Hook: target=Application.onCreate")
                } else {
                    HookLogger.e(
                        TAG,
                        "安装生命周期 Hook 失败: target=Application.onCreate",
                        e
                    )
                }
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w(TAG, "跳过插件加载 Hook: target=BaseDexClassLoader")
                } else {
                    HookLogger.e(
                        TAG,
                        "安装插件加载 Hook 失败: target=BaseDexClassLoader",
                        e
                    )
                }
            }

        } else if (packageName == "miui.systemui.plugin") {
            SystemUIHookRegistry.hook(this, param.defaultClassLoader)
        }
    }

    private fun initializeSystemEnvironment(app: Application) {
        try {
            cleanupRuntime()
            runtimeApp = app

            val renderer = BaseIslandRenderer
            pluginRuntime = runCatching {
                PluginRuntime(this, app).also { it.loadInstalledPlugins() }
            }.onFailure { error ->
                HookLogger.w(TAG, "插件 Runtime 初始化失败，继续使用原有歌词链路", error)
            }.getOrNull()
            val sink = RootLyricSink(renderer, app, prefs, pluginRuntime)
            rootLyricSink = sink

            lyriconSource.initialize(app, prefs)
            superLyricSource.initialize(app)
            lyricInfoSource = LyricInfoSource(app)

            sourceManager = SourceManager(
                sources = listOf(lyriconSource, superLyricSource, lyricInfoSource!!),
                prefs = prefs,
                sink = sink,
                prefKey = RootConstants.KEY_HOOK_LYRIC_SOURCE,
                defaultSourceId = RootConstants.DEFAULT_HOOK_LYRIC_SOURCE,
                logger = HookLogger
            )
            activeMode = prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_MODE,
                RootConstants.DEFAULT_HOOK_LYRIC_MODE
            )
            if (SystemUiEnhancementGate.isEnabled()) {
                sourceManager?.start()
            }

            prefListener =
                android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == UIConstants.KEY_LOG_LEVEL) {
                        HookLogger.refreshLogLevel()
                        HookLogger.d(TAG, "日志配置已刷新: level=${prefs.getInt(key, UIConstants.DEFAULT_LOG_LEVEL)}")
                        return@OnSharedPreferenceChangeListener
                    }
                    if (key?.startsWith(RootConstants.KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX) == true) {
                        lyriconSource.onPreferenceChanged(key)
                    }
                    when (key) {
                        RootConstants.KEY_HOOK_LYRIC_SOURCE -> {
                            val newSourceId =
                                prefs.getString(key, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE)
                                    ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                            if (!SystemUiEnhancementGate.isEnabled()) {
                                HookLogger.d(
                                    TAG,
                                    "配置未生效: key=$key, reason=system_ui_enhancement_disabled"
                                )
                                return@OnSharedPreferenceChangeListener
                            }
                            Handler(Looper.getMainLooper()).post {
                                val manager = sourceManager
                                manager?.switchSource(newSourceId)
                                val activeSourceId = manager?.getActiveSource()?.id
                                if (activeSourceId == newSourceId) {
                                    HookLogger.i(TAG, "歌词源切换完成: source=$newSourceId")
                                }
                            }
                        }

                        RootConstants.KEY_HOOK_LYRIC_MODE -> {
                            val newMode = prefs.getInt(key, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
                            if (newMode == activeMode) {
                                HookLogger.d(
                                    TAG,
                                    "配置未生效: key=$key, value=$newMode, reason=value_unchanged"
                                )
                                return@OnSharedPreferenceChangeListener
                            }
                            Handler(Looper.getMainLooper()).post {
                                activeMode = newMode
                                IslandSettingsRefreshCoordinator.request()
                                HookLogger.i(TAG, "歌词模式切换完成: mode=$newMode")
                            }
                        }

                        RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT -> {
                            val format = prefs.getInt(
                                key,
                                RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
                            )
                            Handler(Looper.getMainLooper()).post {
                                val changed = LyriconDataBridge.updatePlaceholderFormat(format)
                                if (changed) {
                                    BaseIslandRenderer.updateLyricLine()
                                    val playbackClock = LyriconDataBridge.currentPlaybackClock()
                                    BaseIslandRenderer.updatePosition(
                                        playbackClock.positionMs,
                                        playbackClock.playbackSpeed
                                    )
                                } else {
                                    HookLogger.d(
                                        TAG,
                                        "配置未生效: key=$key, value=$format, reason=value_unchanged_or_no_active_lyric"
                                    )
                                }
                            }
                        }

                        RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND -> {
                            Handler(Looper.getMainLooper()).post {
                                updateSystemUiEnhancements(SystemUiEnhancementGate.isEnabled())
                            }
                        }

                        RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandAlbumCoverStyleHooker.refresh()
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }

                        RootConstants.KEY_HOOK_ISLAND_MODIFICATION_SCOPE -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandAlbumCoverStyleHooker.refresh()
                                IslandMusicWaveColorHooker.refresh()
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }

                        RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandMusicWaveColorHooker.refresh()
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }

                        RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS -> {
                            Handler(Looper.getMainLooper()).post {
                                BaseIslandRenderer.updateMetadata()
                            }
                        }

                        RootConstants.KEY_HOOK_TEXT_COLOR_STYLE -> {
                            StatusBarTextColorHooker.setFollowStatusBarEnabled(
                                LyricTextColorStylePolicy.followsStatusBar(
                                    LyricTextColorStylePolicy.read(prefs)
                                )
                            )
                            Handler(Looper.getMainLooper()).post {
                                BaseIslandRenderer.updateTextColors()
                            }
                        }

                        in SUPER_ISLAND_RUNTIME_REFRESH_KEYS -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                            }
                        }
                    }
                }
            prefListener?.let {
                prefs.registerOnSharedPreferenceChangeListener(it)
            }

            HookLogger.i(
                TAG,
                "系统环境初始化完成: enabled=${SystemUiEnhancementGate.isEnabled()}, " +
                        "source=${sourceManager?.getActiveSource()?.displayName ?: "inactive"}, " +
                        "mode=$activeMode"
            )
        } catch (e: Exception) {
            HookLogger.e(TAG, "系统环境初始化失败", e)
        }
    }

    private fun updateSystemUiEnhancements(enabled: Boolean) {
        if (enabled) {
            sourceManager?.start()
            IslandMusicWaveColorHooker.refresh()
            IslandSettingsRefreshCoordinator.request()
        } else {
            sourceManager?.stop()
            LyriconDataBridge.clearState()
            BaseIslandRenderer.clearAllViews()
            IslandProgressGlowController.clearAll()
            IslandAlbumCoverStyleHooker.refresh()
            IslandMusicWaveColorHooker.refresh()
        }
        HookLogger.i(TAG, "更新系统界面增强状态: enabled=$enabled")
    }

    private fun cleanupRuntime(preserveMediaHooks: Boolean = false) {
        IslandNativeRefreshCoordinator.clear()
        if (!preserveMediaHooks) {
            IslandAlbumCoverStyleHooker.cleanup()
            IslandMusicWaveColorHooker.cleanup()
        }
        prefListener?.let {
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        }
        prefListener = null
        runCatching { pluginRuntime?.close() }
        pluginRuntime = null
        runCatching { sourceManager?.stop() }
        sourceManager = null
        runCatching { rootLyricSink?.close() }
        rootLyricSink = null
        lyricInfoSource = null
        runtimeApp = null
    }

    private fun findCurrentApplication(): Application? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getDeclaredMethod("currentApplication")
            currentApplication.invoke(null) as? Application
        }.getOrNull()
    }

    private fun createLyricReplacementHooker(executable: Executable): Hooker? {
        val owner = executable.declaringClass.name
        if (executable is Constructor<*> && owner == "dalvik.system.BaseDexClassLoader") {
            return ClassLoaderHooker(lyricsOnly = true)
        }
        if (executable is Constructor<*>) {
            return StatusBarTextColorHooker.createReplacement(executable)
        }
        if (executable !is Method) return null

        return when (executable.name) {
            "onCreate" -> AppCreateHooker().takeIf { owner == "android.app.Application" }
            "updateBigIslandView" -> RealIslandHooker.UpdateBigIslandViewHook()
            "bindData" -> IslandModuleRestoreHooker.AdapterBindDataHook()
                .takeIf { owner.endsWith("IslandModuleViewHolderAdapter") }

            "updateView" -> IslandModuleRestoreHooker.AdapterUpdateViewHook()
                .takeIf { owner.endsWith("IslandModuleViewHolderAdapter") }

            "onLongPressed" -> IslandLyricShareHooker.LongPressedHook()
                .takeIf { owner.endsWith("DynamicIslandBaseContentViewController") }

            "updateTemplate" -> HookIslandGlow.UpdateTemplateHook()
                .takeIf { owner.endsWith("DynamicIslandBaseContentView") }

            else -> StatusBarTextColorHooker.createReplacement(executable)
        }
    }

    /**
     * 动态类加载器劫持
     */
    inner class ClassLoaderHooker(
        private val lyricsOnly: Boolean = false
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (PluginRuntime.isCreatingPluginClassLoader()) return result
            val cl = chain.thisObject as? ClassLoader ?: return result
            try {
                SystemUIHookRegistry.hook(this@HookEntry, cl, lyricsOnly = lyricsOnly)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                } else {
                    HookLogger.e(TAG, "注入超级岛插件失败", e)
                }
            }
            return result
        }
    }

    /**
     * Application 生命周期劫持
     */
    class AppCreateHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val app = chain.thisObject as? Application
            app?.let { instance?.initializeSystemEnvironment(it) }
            return chain.proceed()
        }
    }
}
