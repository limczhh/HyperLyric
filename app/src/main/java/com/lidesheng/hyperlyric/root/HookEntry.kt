package com.lidesheng.hyperlyric.root

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.lyric.source.SourceManager
import com.lidesheng.hyperlyric.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.effects.color.StatusBarTextColorHooker
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowController
import com.lidesheng.hyperlyric.root.island.SuperIslandHotReloadCoordinator
import com.lidesheng.hyperlyric.root.island.hooks.SystemUIHookRegistry
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import com.lidesheng.hyperlyric.root.island.renderer.IslandSettingsRefreshCoordinator
import com.lidesheng.hyperlyric.root.island.renderer.SystemUiLyricRenderer
import com.lidesheng.hyperlyric.root.mediacard.MediaCardConfigurationRefreshHooker
import com.lidesheng.hyperlyric.root.mediacard.MediaCardElementBehaviorHooker
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaCoverStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaSingleCardSwitcherHooker
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCoordinator
import com.lidesheng.hyperlyric.root.source.LyricInfoSource
import com.lidesheng.hyperlyric.root.source.LyriconSource
import com.lidesheng.hyperlyric.root.source.RootLyricSink
import com.lidesheng.hyperlyric.root.source.SuperLyricSource
import com.lidesheng.hyperlyric.root.statusbar.StatusBarLyricHooker
import com.lidesheng.hyperlyric.root.statusbar.StatusBarLyricRenderer
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val TAG = "HookEntry"

class HookEntry : XposedModule() {

    companion object {
        private const val HOT_RELOAD_TRANSFER_VERSION = 1
        private const val STATE_RUNTIME_READY = "runtimeReady"
        private const val STATE_SYSTEM_UI_LOADED = "systemUiLoaded"
        private const val STATE_STATUS_BAR_TEXT_COLOR = "statusBarTextColor"
        private const val STATE_SNAPSHOT = "snapshot"

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
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_NO_LYRICS,
            RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
            RootConstants.KEY_HOOK_TEXT_SIZE,
            RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
            RootConstants.KEY_HOOK_FONT_WEIGHT,
            RootConstants.KEY_HOOK_FONT_ITALIC,
            RootConstants.KEY_HOOK_FADING_EDGE_LENGTH,
            RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
            RootConstants.KEY_HOOK_LYRIC_ALIGNMENT,
            RootConstants.KEY_HOOK_MUSIC_INFO_ALIGNMENT,
            RootConstants.KEY_HOOK_ANIM_ENABLE,
            RootConstants.KEY_HOOK_ANIM_ID,
            RootConstants.KEY_HOOK_ANIM_SPEED_RATE,
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
            RootConstants.KEY_HOOK_ONLY_SECONDARY,
            RootConstants.KEY_HOOK_SWAP_SECONDARY,
            RootConstants.KEY_HOOK_LYRIC_SHOW_TRANSLATION,
            RootConstants.KEY_HOOK_LYRIC_SHOW_ROMA,
            RootConstants.KEY_HOOK_LYRIC_SHOW_NEXT_LINE,
            RootConstants.KEY_HOOK_LYRIC_SHOW_BACKGROUND_VOCAL,
            RootConstants.KEY_HOOK_LYRIC_SHOW_OVERLAPPING_LINE,
            RootConstants.KEY_HOOK_LYRIC_SECONDARY_ORDER,
            RootConstants.KEY_HOOK_LYRIC_AUTO_DUET,
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
    private var systemUiClassLoader: ClassLoader? = null
    private var systemUiLoaded = false
    private var lyricEnhancementCoordinator: LyricEnhancementCoordinator? = null
    private var rootLyricSink: RootLyricSink? = null
    private val superIslandHotReloadCoordinator = SuperIslandHotReloadCoordinator(
        object : SuperIslandHotReloadCoordinator.MainThreadExecutor {
            override fun <T> execute(block: () -> T): T = runOnMainAndWait(block)
        }
    )

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
        HookRuntimeRegistry.activate(this)
        HookLogger.i(
            TAG,
            "模块加载完成，当前应用版本${com.lidesheng.hyperlyric.BuildConfig.VERSION_CODE}-${com.lidesheng.hyperlyric.BuildConfig.VERSION_NAME}"
        )
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val runtimeReady = runtimeApp != null
        val systemUiWasLoaded = systemUiLoaded
        val snapshot = LyriconDataBridge.exportHotReloadSnapshot()
        val statusBarTextColor = StatusBarTextColorHooker.currentTextColor()
        val dispatcher = StatusBarTextColorHooker.dispatcherForHotReload()

        // A main-thread handoff timeout is checked before the old owner is deactivated.  This is
        // the only point at which declining the reload can leave the old runtime untouched.
        if (runtimeReady || systemUiWasLoaded) {
            runCatching { runOnMainAndWait { Unit } }.onFailure { error ->
                HookLogger.e(TAG, "拒绝热重载: 无法取得 SystemUI 主线程交接点", error)
                return false
            }
        }

        // These listeners are external entry points. Detach them before stopping the old
        // runtime, so a failure here can decline the reload without partially tearing down views
        // or lyric sources.
        val islandWhitelistPrepared = runCatching {
            UnlockIslandWhitelist.prepareForHotReload()
        }.getOrDefault(false)
        val focusWhitelistPrepared = runCatching {
            UnlockFocusWhitelist.prepareForHotReload()
        }.getOrDefault(false)
        if (!islandWhitelistPrepared || !focusWhitelistPrepared) {
            restoreSuperIslandWhitelistListeners(
                restoreIsland = islandWhitelistPrepared,
                restoreFocus = focusWhitelistPrepared,
            )
            HookLogger.e(TAG, "拒绝热重载: 超级岛白名单偏好监听未能注销")
            return false
        }

        if (!HookRuntimeRegistry.deactivateAndAwait(this, 2500L, TimeUnit.MILLISECONDS)) {
            HookRuntimeRegistry.activate(this)
            restoreSuperIslandWhitelistListeners()
            HookLogger.e(TAG, "拒绝热重载: 旧代 Hook 回调未在交接期限内退出")
            return false
        }
        val transfers = try {
            superIslandHotReloadCoordinator.prepareForHotReload()
        } catch (error: Throwable) {
            HookRuntimeRegistry.activate(this)
            restoreSuperIslandWhitelistListeners()
            HookLogger.e(TAG, "拒绝热重载: 超级岛宿主无法安全交接", error)
            return false
        }

        val cleanupSucceeded = cleanupRuntime()
        if (!cleanupSucceeded) {
            HookRuntimeRegistry.activate(this)
            restoreSuperIslandWhitelistListeners()
            runCatching {
                superIslandHotReloadCoordinator.adoptStatusBarRoots(transfers.statusBarRoots)
            }.onFailure { error ->
                HookLogger.e(TAG, "恢复被拒绝热重载前的状态栏歌词宿主失败", error)
            }
            HookLogger.e(TAG, "拒绝热重载: 旧运行时仍有异步任务未退出")
            return false
        }

        param.setSavedInstanceState(
            encodeHotReloadState(
                runtimeReady = runtimeReady,
                systemUiLoaded = systemUiWasLoaded,
                snapshot = snapshot,
                statusBarTextColor = statusBarTextColor,
                dispatcher = dispatcher,
                transfers = transfers,
            )
        )
        HookLogger.d(
            TAG,
            "SystemUI 热重载准备完成: islandHosts=${transfers.islandHosts.size}, " +
                    "statusBarRoots=${transfers.statusBarRoots.size}, " +
                    "snapshot=${snapshot != null}, runtimeReady=$runtimeReady"
        )
        HookLogger.module = null
        HookRuntimeRegistry.release(this)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        instance = this
        HookLogger.module = this
        HookRuntimeRegistry.activate(this)
        HookRuntimeRegistry.beginHotReload(this, param.oldHookHandles)

        val state = decodeHotReloadState(param.savedInstanceState)
        val app = findCurrentApplication()
        val classLoader = app?.classLoader
            ?: param.oldHookHandles.asSequence()
                .map { it.executable.declaringClass.classLoader }
                .firstOrNull { loader -> loader != null && !loader.javaClass.name.contains("BootClassLoader") }

        try {
            if (state?.systemUiLoaded == true && classLoader == null) {
                error("SystemUI ClassLoader unavailable after hot reload")
            }
            if (classLoader != null) {
                systemUiClassLoader = classLoader
                systemUiLoaded = true
                installHotReloadSuperIslandHooks(classLoader)
            }

            // Package callbacks are not replayed. Re-enter every already-loaded Super Island
            // plugin classloader; the registry excludes media-card handles by capability.
            val pluginClassLoaders = param.oldHookHandles.asSequence()
                .map { it.executable.declaringClass.classLoader }
                .filterNotNull()
                .filterNot { it.javaClass.name.contains("BootClassLoader") }
                .toMutableSet()
            classLoader?.let(pluginClassLoaders::add)
            pluginClassLoaders.forEach { pluginClassLoader ->
                UnlockIslandWhitelist.doHookInClassLoader(pluginClassLoader)
                UnlockFocusWhitelist.doHookInClassLoader(pluginClassLoader)
            }
            superIslandHotReloadCoordinator.installPluginHooks(this, pluginClassLoaders)

            val unmatchedBeforeFinish = HookRuntimeRegistry.unmatched(this)
            if (unmatchedBeforeFinish.isNotEmpty()) {
                error(
                    "reloadable hook was not reinstalled: " +
                            unmatchedBeforeFinish.joinToString {
                                it.declaringClass.name + "." + it.name
                            }
                )
            }

            val reconcile = HookRuntimeRegistry.finishHotReload(this)
            if (!reconcile.succeeded) {
                throw IllegalStateException("hook replacement failed", reconcile.failure)
            }

            if (state != null) {
                superIslandHotReloadCoordinator.restoreStatusBarAfterHotReload(
                    textColor = state.statusBarTextColor,
                    dispatcher = state.dispatcher,
                )
            }

            if (app != null) {
                val currentApp = app
                if (!initializeSystemEnvironment(currentApp)) {
                    error("runtime reinitialization returned failure")
                }

                val restoredSnapshot = if (state?.runtimeReady == true) {
                    restoreSuperLyricSnapshotIfNeeded(state.snapshot)
                } else {
                    false
                }
                val adopted = state?.let {
                    superIslandHotReloadCoordinator.adoptHotReloadHosts(it.islandTransfers)
                } ?: 0
                val statusBarAdopted = state?.let {
                    superIslandHotReloadCoordinator.adoptStatusBarRoots(it.statusBarRoots)
                } ?: 0
                if (state != null && state.islandTransfers.isNotEmpty() && adopted == 0 && restoredSnapshot) {
                    HookLogger.w(TAG, "热重载后未接管任何现有超级岛宿主，已保持原生展示")
                }
                if (state != null && state.statusBarRoots.isNotEmpty() && statusBarAdopted == 0) {
                    HookLogger.w(TAG, "热重载后未接管现有状态栏根节点，歌词将等待状态栏重新创建")
                }
                if (restoredSnapshot) {
                    superIslandHotReloadCoordinator.refreshRestoredPresentation()
                }
            } else if (state?.runtimeReady == true) {
                error("Application unavailable for runtime reinitialization")
            }

            HookLogger.i(
                TAG,
                "超级岛热重载完成: replaced=${reconcile.replaced}, " +
                        "added=${reconcile.added}, removed=${reconcile.removed.size}"
            )
        } catch (error: Throwable) {
            val hooksQuiescent = HookRuntimeRegistry.abortAndAwait(this, 2500L, TimeUnit.MILLISECONDS)
            if (!hooksQuiescent) {
                HookLogger.e(TAG, "热重载失败代际仍有 Hook 回调未退出")
            }
            val runtimeCleaned = runCatching { cleanupRuntime() }.getOrElse {
                HookLogger.e(TAG, "热重载失败代际的歌词运行时清理异常", it)
                false
            }
            val islandCleaned = superIslandHotReloadCoordinator.cleanupFailedGeneration()
            val islandWhitelistCleaned = runCatching {
                UnlockIslandWhitelist.prepareForHotReload()
            }.getOrDefault(false)
            val focusWhitelistCleaned = runCatching {
                UnlockFocusWhitelist.prepareForHotReload()
            }.getOrDefault(false)
            val cleanupComplete = hooksQuiescent && runtimeCleaned && islandCleaned &&
                    islandWhitelistCleaned && focusWhitelistCleaned
            HookLogger.e(
                TAG,
                if (cleanupComplete) {
                    "超级岛热重载失败，已清理新运行时并恢复原生展示"
                } else {
                    "超级岛热重载失败，部分清理未确认完成，已尽力恢复原生展示"
                },
                error
            )
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val processName = runCatching { Application.getProcessName() }.getOrNull() ?: ""

        // 仅在主进程注入
        if (processName.contains(":")) return

        val packageName = param.packageName

        if (packageName == "com.android.systemui") {
            systemUiClassLoader = param.defaultClassLoader
            systemUiLoaded = true
            installSystemUiHooks(param.defaultClassLoader)

        } else if (packageName == "miui.systemui.plugin") {
            SystemUIHookRegistry.hook(this, param.defaultClassLoader)
        }
    }

    /** Cold-start installer: the media-card surface is intentionally added only here. */
    private fun installSystemUiHooks(classLoader: ClassLoader) {
        installSuperIslandStatusBarHooks(classLoader)
        installMediaCardHooks(classLoader)
        installSuperIslandLifecycleHooks(classLoader, hotReload = false)
    }

    private fun installSuperIslandStatusBarHooks(classLoader: ClassLoader) {
        StatusBarTextColorHooker.setFollowStatusBarEnabled(
            StatusBarLyricPreferences.shouldFollowStatusBarTextColor(prefs)
        )
        StatusBarTextColorHooker.setTextColorChangedListener {
            SystemUiLyricRenderer.updateTextColors()
        }
        StatusBarTextColorHooker.hook(this, classLoader)
        StatusBarLyricHooker.hook(this, classLoader)
    }

    private fun installMediaCardHooks(classLoader: ClassLoader) {
        MediaCardRuntimeConfig.load(prefs)
        MediaCardConfigurationRefreshHooker.hook(this, classLoader)
        MediaProgressStyleHooker.hook(this, classLoader)
        MediaCardElementBehaviorHooker.hook(this, classLoader)
        IslandExpandedMediaAmbientFlowHooker.hook(this, classLoader)
        IslandExpandedMediaLayoutHooker.hook(this, classLoader)
        NotificationMediaAmbientFlowHooker.hook(this, classLoader)
        NotificationMediaCoverStyleHooker.hook(this, classLoader)
        if (MediaCardRuntimeConfig.current.notification.cardSwitcherEnabled) {
            NotificationMediaSingleCardSwitcherHooker.hook(this, classLoader)
        } else {
            HookLogger.d(TAG, "通知中心多媒体卡片切换功能未启用，跳过媒体卡片切换 Hook")
        }
    }

    private fun installSuperIslandLifecycleHooks(classLoader: ClassLoader, hotReload: Boolean) {
        try {
            UnlockIslandWhitelist.hook(this, classLoader)
        } catch (e: Exception) {
            if (e is ClassNotFoundException || e is NoSuchMethodException) {
                HookLogger.w(
                    TAG,
                    if (hotReload) "热重载跳过不支持的超级岛下拉小窗白名单"
                    else "此系统版本不支持超级岛下拉小窗白名单"
                )
            } else {
                HookLogger.e(
                    TAG,
                    if (hotReload) "热重载安装超级岛下拉小窗白名单失败"
                    else "超级岛下拉小窗白名单注入失败",
                    e
                )
            }
        }
        try {
            UnlockFocusWhitelist.hook(this, classLoader)
        } catch (e: Exception) {
            if (e is ClassNotFoundException || e is NoSuchMethodException) {
                HookLogger.w(
                    TAG,
                    if (hotReload) "热重载跳过不支持的解锁焦点通知白名单"
                    else "此系统版本不支持解锁焦点通知白名单"
                )
            } else {
                HookLogger.e(
                    TAG,
                    if (hotReload) "热重载安装解锁焦点白名单失败"
                    else "焦点通知白名单注入失败",
                    e
                )
            }
        }

        if (!hotReload) {
            activeMode = prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_MODE,
                RootConstants.DEFAULT_HOOK_LYRIC_MODE
            )
        }

        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            val onCreateMethod = appClass.getDeclaredMethod("onCreate")
            managedHook(
                executable = onCreateMethod,
                capability = "lifecycle.application.on_create",
                hooker = AppCreateHooker(),
            )
        }.onFailure { error ->
            if (error is ClassNotFoundException || error is NoSuchMethodException) {
                HookLogger.w(
                    TAG,
                    if (hotReload) "热重载跳过生命周期 Hook: target=Application.onCreate"
                    else "跳过生命周期 Hook: target=Application.onCreate"
                )
            } else {
                HookLogger.e(
                    TAG,
                    if (hotReload) "热重载安装生命周期 Hook 失败: target=Application.onCreate"
                    else "安装生命周期 Hook 失败: target=Application.onCreate",
                    error
                )
            }
        }

        runCatching {
            val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
            clClass.declaredConstructors.forEach { constructor ->
                managedHook(
                    executable = constructor,
                    capability = "lifecycle.base_dex_constructor",
                    hooker = ClassLoaderHooker(),
                )
            }
        }.onFailure { error ->
            if (error is ClassNotFoundException || error is NoSuchMethodException) {
                HookLogger.w(
                    TAG,
                    if (hotReload) "跳过热重载插件加载 Hook: target=BaseDexClassLoader"
                    else "跳过插件加载 Hook: target=BaseDexClassLoader"
                )
            } else {
                HookLogger.e(
                    TAG,
                    if (hotReload) "安装热重载插件加载 Hook 失败: target=BaseDexClassLoader"
                    else "安装插件加载 Hook 失败: target=BaseDexClassLoader",
                    error
                )
            }
        }
    }

    /** Reinstall the Super Island support surface; media-card hooks stay in the old generation. */
    private fun installHotReloadSuperIslandHooks(classLoader: ClassLoader) {
        installSuperIslandStatusBarHooks(classLoader)
        installSuperIslandLifecycleHooks(classLoader, hotReload = true)
    }

    private fun initializeSystemEnvironment(app: Application): Boolean {
        try {
            cleanupRuntime()
            runtimeApp = app

            val renderer = SystemUiLyricRenderer
            lyricEnhancementCoordinator = runCatching {
                LyricEnhancementCoordinator(this, app)
            }.onFailure { error ->
                HookLogger.w(TAG, "内置歌词增强初始化失败，继续使用原有歌词链路", error)
            }.getOrNull()
            val sink = RootLyricSink(renderer, app, prefs, lyricEnhancementCoordinator)
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
            if (shouldRunLyricSource()) {
                sourceManager?.start()
            }
            renderer.updateLyricLine()

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
                    if (StatusBarLyricPreferences.isStatusBarPreferenceKey(key)) {
                        StatusBarTextColorHooker.setFollowStatusBarEnabled(
                            StatusBarLyricPreferences.shouldFollowStatusBarTextColor(prefs)
                        )
                        Handler(Looper.getMainLooper()).post {
                            if (key == StatusBarLyricPreferences.KEY_ENABLED) {
                                updateLyricSourceRuntime()
                            }
                            StatusBarLyricRenderer.onPreferenceChanged()
                        }
                        return@OnSharedPreferenceChangeListener
                    }
                    when (key) {
                        RootConstants.KEY_HOOK_LYRIC_SOURCE -> {
                            val newSourceId =
                                prefs.getString(key, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE)
                                    ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                            if (!shouldRunLyricSource()) {
                                HookLogger.d(
                                    TAG,
                                    "配置未生效: key=$key, reason=lyric_targets_disabled"
                                )
                                return@OnSharedPreferenceChangeListener
                            }
                            Handler(Looper.getMainLooper()).post {
                                val manager = sourceManager
                                manager?.switchSource(newSourceId)
                                val activeSourceId = manager?.getActiveSource()?.id
                                if (activeSourceId == newSourceId) {
                                    HookLogger.d(TAG, "歌词源切换完成: source=$newSourceId")
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
                                HookLogger.d(TAG, "歌词模式切换完成: mode=$newMode")
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
                                    SystemUiLyricRenderer.updateLyricLine()
                                    val playbackClock = LyriconDataBridge.currentPlaybackClock()
                                    SystemUiLyricRenderer.updatePosition(
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
                                SystemUiLyricRenderer.updateMetadata()
                            }
                        }

                        RootConstants.KEY_HOOK_TEXT_COLOR_STYLE -> {
                            StatusBarTextColorHooker.setFollowStatusBarEnabled(
                                StatusBarLyricPreferences.shouldFollowStatusBarTextColor(prefs)
                            )
                            Handler(Looper.getMainLooper()).post {
                                SystemUiLyricRenderer.updateTextColors()
                            }
                        }

                        in SUPER_ISLAND_RUNTIME_REFRESH_KEYS -> {
                            Handler(Looper.getMainLooper()).post {
                                IslandSettingsRefreshCoordinator.request()
                                StatusBarLyricRenderer.updateLyricLine()
                            }
                        }
                    }
                }
            prefListener?.let {
                prefs.registerOnSharedPreferenceChangeListener(it)
            }

            HookLogger.d(
                TAG,
                "系统环境初始化完成: enabled=${SystemUiEnhancementGate.isEnabled()}, " +
                        "source=${sourceManager?.getActiveSource()?.displayName ?: "inactive"}, " +
                        "mode=$activeMode"
            )
            return true
        } catch (e: Exception) {
            HookLogger.e(TAG, "系统环境初始化失败", e)
            return false
        }
    }

    private fun updateSystemUiEnhancements(enabled: Boolean) {
        updateLyricSourceRuntime()
        if (enabled) {
            IslandMusicWaveColorHooker.refresh()
            IslandSettingsRefreshCoordinator.request()
        } else {
            BaseIslandRenderer.clearAllViews()
            IslandProgressGlowController.clearAll()
            IslandAlbumCoverStyleHooker.refresh()
            IslandMusicWaveColorHooker.refresh()
        }
        StatusBarLyricRenderer.updateLyricLine()
        HookLogger.d(TAG, "更新系统界面增强状态: enabled=$enabled")
    }

    private fun shouldRunLyricSource(): Boolean =
        prefs.getBoolean(
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
            RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND,
        ) || prefs.getBoolean(
            StatusBarLyricPreferences.KEY_ENABLED,
            StatusBarLyricPreferences.DEFAULT_ENABLED,
        )

    private fun updateLyricSourceRuntime() {
        if (shouldRunLyricSource()) {
            sourceManager?.start()
        } else {
            sourceManager?.stop()
            LyriconDataBridge.clearState()
        }
    }

    private fun restoreSuperIslandWhitelistListeners(
        restoreIsland: Boolean = true,
        restoreFocus: Boolean = true,
    ) {
        val classLoader = systemUiClassLoader ?: return
        if (restoreIsland) {
            runCatching { UnlockIslandWhitelist.hook(this, classLoader) }
                .onFailure { HookLogger.w(TAG, "恢复超级岛下拉白名单监听失败", it) }
        }
        if (restoreFocus) {
            runCatching { UnlockFocusWhitelist.hook(this, classLoader) }
                .onFailure { HookLogger.w(TAG, "恢复焦点白名单监听失败", it) }
        }
    }

    private fun cleanupRuntime(): Boolean {
        var succeeded = true
        val manager = sourceManager
        manager?.let {
            runCatching { it.stop() }.onFailure { error ->
                succeeded = false
                HookLogger.e(TAG, "歌词源停止失败", error)
            }
        }
        prefListener?.let {
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(it) }
                .onFailure { error ->
                    succeeded = false
                    HookLogger.e(TAG, "注销歌词偏好监听失败", error)
                }
        }
        prefListener = null
        val enhancement = lyricEnhancementCoordinator
        if (enhancement != null && !runCatching { enhancement.closeAndAwait() }.getOrElse {
                succeeded = false
                HookLogger.e(TAG, "歌词增强任务清理失败", it)
                false
            }
        ) {
            succeeded = false
        }
        lyricEnhancementCoordinator = null
        sourceManager = null
        runCatching {
            runOnMainAndWait { rootLyricSink?.close() }
        }.onFailure {
            succeeded = false
            HookLogger.e(TAG, "歌词接收端主线程清理失败", it)
        }
        rootLyricSink = null
        lyricInfoSource = null
        runtimeApp = null
        LyriconDataBridge.clearState()
        return succeeded
    }

    private fun encodeHotReloadState(
        runtimeReady: Boolean,
        systemUiLoaded: Boolean,
        snapshot: Bundle?,
        statusBarTextColor: Int,
        dispatcher: Any?,
        transfers: SuperIslandHotReloadCoordinator.HotReloadTransfers,
    ): Any {
        val meta = Bundle().apply {
            putBoolean(STATE_RUNTIME_READY, runtimeReady)
            putBoolean(STATE_SYSTEM_UI_LOADED, systemUiLoaded)
            putInt(STATE_STATUS_BAR_TEXT_COLOR, statusBarTextColor)
            snapshot?.let { putBundle(STATE_SNAPSHOT, it) }
        }
        val hosts = ArrayList<Any?>(transfers.islandHosts.size * 4)
        transfers.islandHosts.forEach { transfer ->
            // The ArrayList/Bundle containers are framework objects.  The only non-container
            // reference is the native host ViewGroup, after removeInjectedViewsForHotReload has
            // removed every HyperLyric View, tag and listener-owned child from its tree.
            hosts += transfer.root
            hosts += transfer.packageName
            hosts += transfer.kind.name
            hosts += transfer.moduleType
        }
        return ArrayList<Any?>(5).apply {
            add(HOT_RELOAD_TRANSFER_VERSION)
            add(meta)
            add(dispatcher)
            add(hosts)
            add(ArrayList(transfers.statusBarRoots))
        }
    }

    private fun decodeHotReloadState(raw: Any?): HotReloadState? {
        val transfer = raw as? ArrayList<*> ?: return null
        if (transfer.firstOrNull() != HOT_RELOAD_TRANSFER_VERSION) return null
        val meta = transfer.getOrNull(1) as? Bundle ?: return null
        val hosts = transfer.getOrNull(3) as? ArrayList<*> ?: return null
        val restoredHosts = mutableListOf<IslandPresentationCoordinator.HotReloadHostTransfer>()
        var index = 0
        while (index + 3 < hosts.size) {
            val root = hosts[index] as? ViewGroup
            val packageName = hosts[index + 1] as? String
            val kind = (hosts[index + 2] as? String)?.let {
                runCatching { IslandViewRegistry.HostKind.valueOf(it) }.getOrNull()
            }
            val moduleType = hosts[index + 3] as? String
            if (root != null && packageName != null && kind != null) {
                restoredHosts += IslandPresentationCoordinator.HotReloadHostTransfer(
                    root = root,
                    packageName = packageName,
                    kind = kind,
                    moduleType = moduleType,
                )
            }
            index += 4
        }
        return HotReloadState(
            runtimeReady = meta.getBoolean(STATE_RUNTIME_READY, false),
            systemUiLoaded = meta.getBoolean(STATE_SYSTEM_UI_LOADED, false),
            statusBarTextColor = meta.getInt(STATE_STATUS_BAR_TEXT_COLOR),
            snapshot = meta.getBundle(STATE_SNAPSHOT),
            dispatcher = transfer.getOrNull(2),
            islandTransfers = restoredHosts,
            statusBarRoots = (transfer.getOrNull(4) as? ArrayList<*>)
                ?.filterIsInstance<ViewGroup>()
                .orEmpty(),
        )
    }

    private fun restoreSuperLyricSnapshotIfNeeded(snapshot: Bundle?): Boolean {
        if (snapshot == null || sourceManager?.getActiveSource()?.id != superLyricSource.id) {
            return false
        }
        if (
            LyriconDataBridge.currentSong != null ||
            LyriconDataBridge.currentLyricLine != null ||
            LyriconDataBridge.currentLyric != null
        ) {
            return false
        }
        val packageName = snapshot.getString("package")?.takeIf { it.isNotBlank() }
            ?: return false
        val currentPackage = LyriconDataBridge.currentLyricPackageName
        if (!currentPackage.isNullOrBlank() && currentPackage != packageName) {
            HookLogger.w(
                TAG,
                "丢弃过期 SuperLyric 快照: snapshotPackage=$packageName, " +
                        "currentPackage=$currentPackage"
            )
            return false
        }
        val sourceId = snapshot.getString("source")
        if (sourceId != null && sourceId != superLyricSource.id) return false

        // SuperLyric has no query API.  Its publisher package is the session identity available
        // to this source; restore only a complete publisher/song snapshot and let the next
        // publisher callback replace it if that identity changes.
        val restored = LyriconDataBridge.restoreHotReloadSnapshot(snapshot)
        if (restored) {
            HookLogger.d(TAG, "已恢复 SuperLyric 中立热重载快照: package=$packageName")
        }
        return restored
    }

    private fun <T> runOnMainAndWait(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()

        val latch = CountDownLatch(1)
        var value: T? = null
        var error: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                value = block()
            } catch (throwable: Throwable) {
                error = throwable
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(2_500L, TimeUnit.MILLISECONDS)) {
            throw TimeoutException("main-thread hot reload handoff timed out")
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private data class HotReloadState(
        val runtimeReady: Boolean,
        val systemUiLoaded: Boolean,
        val statusBarTextColor: Int,
        val snapshot: Bundle?,
        val dispatcher: Any?,
        val islandTransfers: List<IslandPresentationCoordinator.HotReloadHostTransfer>,
        val statusBarRoots: List<ViewGroup>,
    )

    private fun findCurrentApplication(): Application? {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getDeclaredMethod("currentApplication")
            currentApplication.invoke(null) as? Application
        }.getOrNull()
    }

    /**
     * 动态类加载器劫持
     */
    inner class ClassLoaderHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val cl = chain.thisObject as? ClassLoader ?: return result
            try {
                SystemUIHookRegistry.hook(this@HookEntry, cl)
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
