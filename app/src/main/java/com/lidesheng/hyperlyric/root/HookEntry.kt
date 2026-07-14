package com.lidesheng.hyperlyric.root

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.lyric.source.SourceManager
import com.lidesheng.hyperlyric.root.island.FakeIslandTransitionHooker
import com.lidesheng.hyperlyric.root.island.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.IslandProgressGlowHooker
import com.lidesheng.hyperlyric.root.island.IslandModuleRestoreHooker
import com.lidesheng.hyperlyric.root.island.SystemUIHookRegistry
import com.lidesheng.hyperlyric.root.island.IslandWidthHooker
import com.lidesheng.hyperlyric.root.island.RealIslandHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaCoverStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.island.renderer.IslandRenderer
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import com.lidesheng.hyperlyric.root.source.LyriconSource
import com.lidesheng.hyperlyric.root.source.LyricInfoSource
import com.lidesheng.hyperlyric.root.source.RootLyricSink
import com.lidesheng.hyperlyric.root.source.SuperLyricSource
import com.lidesheng.hyperlyric.root.aitrans.AITranslator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
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

class HookEntry : XposedModule() {

    companion object {
        private const val STATE_RUNTIME_READY = "runtimeReady"

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
            RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_COLOR,
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_GRADIENT,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH,
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
            RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
            RootConstants.KEY_HOOK_TRANSLATION_ONLY,
            RootConstants.KEY_HOOK_SWAP_TRANSLATION,
            RootConstants.KEY_HOOK_NEXT_LYRIC_LINE,
            RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR,
            RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT,
            RootConstants.KEY_HOOK_CUSTOM_FONT_PATH,
            RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE,
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND
        )
    }

    private var _prefs: android.content.SharedPreferences? = null
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var runtimeApp: Application? = null

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
        HookLogger.i("HookEntry","模块已加载")
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        val state = Bundle().apply {
            putBoolean(STATE_RUNTIME_READY, runtimeApp != null)
        }
        param.setSavedInstanceState(state)
        IslandExpandedMediaAmbientFlowHooker.releaseAll()
        NotificationMediaCoverStyleHooker.releaseAll()
        NotificationMediaAmbientFlowHooker.releaseAll()
        cleanupRuntime()
        HookLogger.i("HookEntry", "热重载准备完成")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        instance = this
        HookLogger.module = this

        HookIslandGlow.initialize(this)
        IslandProgressGlowHooker.initialize(this)
        IslandExpandedMediaAmbientFlowHooker.initialize(this)
        NotificationMediaAmbientFlowHooker.initialize(this)
        NotificationMediaCoverStyleHooker.initialize(this)
        val hadProgressBackgroundHook = param.oldHookHandles.any {
            isProgressGlowBackgroundDraw(it.executable)
        }
        val progressBackgroundClassLoader = if (hadProgressBackgroundHook) {
            null
        } else {
            param.oldHookHandles.asSequence()
                .mapNotNull { it.executable.declaringClass.classLoader }
                .distinct()
                .firstOrNull { classLoader ->
                    runCatching {
                        classLoader.loadClass(
                            "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
                        )
                    }.isSuccess
                }
        }
        val notificationMediaClassLoader = param.oldHookHandles.asSequence()
            .filter { isNotificationAmbientFlowHook(it.executable) }
            .mapNotNull { it.executable.declaringClass.classLoader }
            .firstOrNull()
            ?: findCurrentApplication()?.classLoader
        val hadIslandExpandedMediaFlowHook = param.oldHookHandles.any {
            isIslandExpandedMediaAmbientFlowHook(it.executable)
        }
        val islandExpandedMediaClassLoader = if (hadIslandExpandedMediaFlowHook) {
            null
        } else {
            findCurrentApplication()?.classLoader
        }

        var replacedCount = 0
        var removedCount = 0
        param.oldHookHandles.forEach { handle ->
            val replacement = createReplacementHooker(handle.executable)
            if (replacement != null) {
                runCatching {
                    handle.replaceHook(replacement)
                    if (isProgressGlowBackgroundDraw(handle.executable)) {
                        IslandProgressGlowHooker.adoptBackgroundHook(
                            handle.executable.declaringClass
                        )
                    }
                    replacedCount++
                }.onFailure {
                    handle.unhook()
                    removedCount++
                }
            } else {
                handle.unhook()
                removedCount++
            }
        }

        if (!hadProgressBackgroundHook && progressBackgroundClassLoader != null) {
            runCatching {
                IslandProgressGlowHooker.hook(this, progressBackgroundClassLoader)
            }.onFailure { e ->
                HookLogger.e("HookEntry", "Failed to install island background progress hook", e)
            }
        }
        notificationMediaClassLoader?.let { classLoader ->
            runCatching {
                NotificationMediaAmbientFlowHooker.hook(this, classLoader)
            }.onFailure { e ->
                HookLogger.e("HookEntry", "Failed to install notification media ambient flow hook", e)
            }
            runCatching {
                NotificationMediaCoverStyleHooker.hook(this, classLoader)
            }.onFailure { e ->
                HookLogger.e("HookEntry", "Failed to install notification media cover hook", e)
            }
        }
        islandExpandedMediaClassLoader?.let { classLoader ->
            runCatching {
                IslandExpandedMediaAmbientFlowHooker.hook(this, classLoader)
            }.onFailure { e ->
                HookLogger.e("HookEntry", "Failed to install expanded island media flow hook", e)
            }
        }

        val state = param.savedInstanceState as? Bundle
        if (state?.getBoolean(STATE_RUNTIME_READY) == true) {
            findCurrentApplication()?.let { app ->
                Handler(Looper.getMainLooper()).post {
                    initializeSystemEnvironment(app)
                    BaseIslandRenderer.refreshActiveIsland()
                }
            }
                ?: HookLogger.w("HookEntry", "热重载后未取得当前 Application，等待 Application.onCreate")
        }
        HookLogger.i("HookEntry", "热重载完成: replaced=$replacedCount removed=$removedCount")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val processName = runCatching { android.app.Application.getProcessName() }.getOrNull() ?: ""
        
        // 仅在主进程注入
        if (processName.contains(":")) return
        
        val packageName = param.packageName
        
        if (packageName == "com.android.systemui") {
            IslandExpandedMediaAmbientFlowHooker.hook(this, param.defaultClassLoader)
            NotificationMediaAmbientFlowHooker.hook(this, param.defaultClassLoader)
            NotificationMediaCoverStyleHooker.hook(this, param.defaultClassLoader)
            try {
                UnlockIslandWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 if (e is ClassNotFoundException || e is NoSuchMethodException) {
                     HookLogger.w("HookEntry","此系统版本不支持超级岛下拉小窗白名单")
                 } else {
                     HookLogger.e("HookEntry", "超级岛下拉小窗白名单注入失败", e)
                 }
            }
            try {
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 if (e is ClassNotFoundException || e is NoSuchMethodException) {
                     HookLogger.w("HookEntry","此系统版本不支持解锁焦点通知白名单")
                 } else {
                     HookLogger.e("HookEntry", "焦点通知白名单注入失败", e)
                 }
            }

            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            
            if (!isSuperIslandEnabled) {
                HookLogger.i("HookEntry","已在设置中禁用超级岛歌词功能")
            }

            activeMode = prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
            HookLogger.i("HookEntry","超级岛激活模式 = $activeMode")

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
                HookLogger.i("HookEntry","系统环境注入成功 (Application.onCreate)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w("HookEntry","未找到 Application.onCreate，无法注入环境")
                } else {
                    HookLogger.e("HookEntry", "注入 Application.onCreate 时发生错误", e)
                }
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
                HookLogger.i("HookEntry","插件拦截器已就绪 (ClassLoader)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w("HookEntry","未找到 ClassLoader 构造方法")
                } else {
                    HookLogger.e("HookEntry", "拦截 ClassLoader 时发生错误", e)
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
            val sink = RootLyricSink(renderer, prefs)

            lyriconSource.initialize(app)
            superLyricSource.initialize(app)
            lyricInfoSource = LyricInfoSource(app)

            AITranslator.init(app)

            sourceManager = SourceManager(
                sources = listOf(lyriconSource, superLyricSource, lyricInfoSource!!),
                prefs = prefs,
                sink = sink,
                prefKey = RootConstants.KEY_HOOK_LYRIC_SOURCE,
                defaultSourceId = RootConstants.DEFAULT_HOOK_LYRIC_SOURCE,
                stateResetter = LyriconDataBridge,
                logger = HookLogger
            )
            sourceManager?.start()

            prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    RootConstants.KEY_HOOK_LYRIC_SOURCE -> {
                        val newSourceId = prefs.getString(key, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE)
                            ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                        HookLogger.i("HookEntry", "歌词源切换: $newSourceId")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            sourceManager?.switchSource(newSourceId)
                        }
                    }
                    RootConstants.KEY_HOOK_LYRIC_MODE -> {
                        val newMode = prefs.getInt(key, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
                        if (newMode == activeMode) return@OnSharedPreferenceChangeListener
                        HookLogger.i("HookEntry", "歌词模式切换: $newMode")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            activeMode = newMode
                            BaseIslandRenderer.refreshActiveIsland()
                        }
                    }
                    RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (prefs.getBoolean(key, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
                                BaseIslandRenderer.refreshActiveIsland()
                            } else {
                                BaseIslandRenderer.clearAllViews()
                            }
                        }
                    }
                    RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE,
                    RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            IslandAlbumCoverStyleHooker.refresh()
                            BaseIslandRenderer.refreshActiveIsland()
                        }
                    }
                    RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            NotificationMediaAmbientFlowHooker.refreshCardTheme()
                        }
                    }
                    RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                    RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                    RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            NotificationMediaCoverStyleHooker.refresh()
                        }
                    }
                    RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            IslandExpandedMediaAmbientFlowHooker.refreshCardTheme()
                        }
                    }
                    RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
                    RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                    RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            IslandExpandedMediaAmbientFlowHooker.refreshMediaElements()
                        }
                    }
                    in SUPER_ISLAND_RUNTIME_REFRESH_KEYS -> {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            BaseIslandRenderer.refreshActiveIsland()
                        }
                    }
                }
            }
            prefListener?.let {
                prefs.registerOnSharedPreferenceChangeListener(it)
            }

            activeMode = prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
            HookLogger.i("HookEntry", "歌词源 = ${sourceManager?.getActiveSource()?.displayName}")
            HookLogger.i("HookEntry", "系统环境初始化完成")
        } catch (e: Exception) {
            HookLogger.e("HookEntry", "系统环境初始化失败", e)
        }
    }

    private fun cleanupRuntime() {
        IslandAlbumCoverStyleHooker.cleanup()
        prefListener?.let {
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        }
        prefListener = null
        runCatching { sourceManager?.stop() }
        sourceManager = null
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

    private fun createReplacementHooker(executable: Executable): Hooker? {
        val owner = executable.declaringClass.name
        if (executable is Constructor<*> && owner == "dalvik.system.BaseDexClassLoader") {
            return ClassLoaderHooker()
        }
        if (executable !is Method) return null

        val name = executable.name
        return when {
            owner == "android.app.Application" && name == "onCreate" ->
                AppCreateHooker()
            name == "mediaIslandSupportMiniWindow" ->
                UnlockIslandWhitelist.ReturnTrueHooker()
            owner == "com.android.systemui.shared.plugins.PluginInstance" && name == "loadPlugin" ->
                UnlockFocusWhitelist.PluginLoadHooker()
            name == "canShowFocus" || name == "canCustomFocus" ->
                UnlockFocusWhitelist.ReturnTrueHooker()
            owner.contains("AuthServiceCallback") && name == "invokeSuspend" ->
                UnlockFocusWhitelist.AuthResultHooker()
            name == "updateBigIslandView" ->
                RealIslandHooker.UpdateBigIslandViewHook()
            name == "calculateBigIslandWidth" ->
                IslandWidthHooker.CalculateWidthHook()
            name == "hideIslandLayout" || name == "showIslandLayout" ->
                RealIslandHooker.LayoutVisibilityHook(name)
            name == "onTrackingFakeViewStart" ->
                FakeIslandTransitionHooker.TrackingStartHook()
            name == "updateViewStateWhenOpenAnimStart" ->
                FakeIslandTransitionHooker.PrepareVisibleHook()
            owner.endsWith("DynamicIslandContentFakeView") && name == "setVisibility" ->
                FakeIslandTransitionHooker.VisibilityHook()
            owner.endsWith("IslandTemplateBuilder") && name == "updateModuleView" ->
                IslandModuleRestoreHooker.UpdateModuleViewHook()
            owner.endsWith("IslandModuleViewHolderAdapter") && name == "updateView" ->
                IslandModuleRestoreHooker.AdapterUpdateViewHook()
            owner.endsWith("DynamicIslandBaseContentView") && name == "updateTemplate" ->
                HookIslandGlow.UpdateTemplateHook()
            isIslandExpandedMediaAmbientFlowHook(executable) ->
                IslandExpandedMediaAmbientFlowHooker.hookerFor(executable)
            isNotificationAmbientFlowHook(executable) -> null
            isProgressGlowBackgroundDraw(executable) ->
                IslandProgressGlowHooker.BackgroundDrawHook()
            else -> null
        }
    }

    private fun isProgressGlowBackgroundDraw(executable: Executable): Boolean {
        return executable is Method &&
            executable.name == "onDraw" &&
            executable.parameterTypes.size == 1 &&
            executable.parameterTypes[0].name == "android.graphics.Canvas" &&
            executable.declaringClass.name ==
            "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
    }

    private fun isNotificationAmbientFlowHook(executable: Executable): Boolean {
        return executable is Method &&
            NotificationMediaAmbientFlowHooker.isTargetMethod(executable)
    }

    private fun isIslandExpandedMediaAmbientFlowHook(executable: Executable): Boolean {
        return executable is Method &&
            IslandExpandedMediaAmbientFlowHooker.isTargetMethod(executable)
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
                    // HookLogger.w("HookEntry","插件中未找到超级岛相关类")
                } else {
                    HookLogger.e("HookEntry", "动态注入超级岛插件失败", e)
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
