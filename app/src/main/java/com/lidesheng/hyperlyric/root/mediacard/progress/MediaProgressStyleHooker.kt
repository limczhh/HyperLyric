/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * The wave progress-bar lifecycle is adapted from XiaomiHelper's
 * CustomProgressBar implementation.
 * Copyright (C) 2026 HowieHChen
 */

package com.lidesheng.hyperlyric.root.mediacard.progress

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaCoverStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaBackgroundController
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaForegroundStyler
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SquigglySeekBar
import com.lidesheng.hyperlyric.root.mediacard.progress.view.ThumbStyle
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

object MediaProgressStyleHooker {
    private const val TAG = "MediaProgressStyleHooker"
    private const val NOTIFICATION_HOLDER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder"
    private const val NOTIFICATION_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
    private const val NOTIFICATION_OBSERVER_CLASS =
        "${NOTIFICATION_CONTROLLER_CLASS}\$seekBarObserver\$1"
    private const val ISLAND_HOLDER_CLASS =
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder"
    private const val ISLAND_BINDER_CLASS =
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewBinderImpl"
    private const val PROGRESS_CLASS =
        "com.android.systemui.media.controls.ui.viewmodel.SeekBarViewModel\$Progress"
    private const val SEEK_BAR_VIEW_MODEL_CLASS =
        "com.android.systemui.media.controls.ui.viewmodel.SeekBarViewModel"
    private const val SEEK_BAR_CHANGE_LISTENER_CLASS =
        "${SEEK_BAR_VIEW_MODEL_CLASS}\$SeekBarChangeListener"
    private const val HYPER_PROGRESS_SEEK_BAR_CLASS =
        "miuix.miuixbasewidget.widget.HyperProgressSeekBar"

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val waveSeekBars =
        Collections.synchronizedMap(WeakHashMap<Any, SquigglySeekBar>())
    private val waveStates =
        Collections.synchronizedMap(WeakHashMap<Any, WaveSeekBarState>())
    private val nativeGlowStates =
        Collections.synchronizedMap(WeakHashMap<Any, NativeGlowState>())
    private val nativeGlowSeekBars =
        Collections.synchronizedMap(WeakHashMap<Any, SeekBar>())

    /**
     * The holder's seekBar field is a concrete HyperProgressSeekBar on some
     * SystemUI builds, so a wave replacement cannot be stored in that field.
     * Consumers that operate on the visible view must resolve this registry
     * before falling back to the native field value.
     */
    internal fun replacementSeekBar(holder: Any): SeekBar? =
        waveSeekBars[holder] ?: nativeGlowSeekBars[holder]

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

        val notificationStyle = MediaCardRuntimeConfig.current.notification.progressStyle
        val notificationHeadGlow =
            MediaCardRuntimeConfig.current.notification.progressHeadGlow
        val islandStyle = MediaCardRuntimeConfig.current.islandExpanded.progressStyle
        if (
            notificationStyle == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT &&
            !notificationHeadGlow &&
            islandStyle == RootConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT
        ) {
            return
        }

        val needsWaveProgressApi =
            notificationStyle == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE ||
                islandStyle == RootConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE
        val commonApi = if (needsWaveProgressApi) {
            runCatching { CommonApi.create(classLoader) }
                .onFailure {
                    HookLogger.w(TAG, "媒体进度数据接口不可用: reason=${it.message}")
                }
                .getOrNull()
        } else {
            null
        }

        var installed = 0
        if (
            notificationStyle == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE ||
            notificationHeadGlow
        ) {
            installed += installNotificationHooks(
                xposedModule,
                classLoader,
                commonApi,
                notificationStyle,
                notificationHeadGlow
            )
        }
        if (islandStyle == RootConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE) {
            installed += installIslandHooks(xposedModule, classLoader, commonApi)
        }

        if (installed == 0) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "未找到兼容的媒体进度条 Hook")
        } else {
            HookLogger.d(
                TAG,
                "媒体进度条 Hook 已初始化: notificationStyle=$notificationStyle, " +
                    "notificationHeadGlow=$notificationHeadGlow, islandStyle=$islandStyle, " +
                    "methods=$installed"
            )
        }
    }

    private fun installNotificationHooks(
        xposedModule: XposedModule,
        classLoader: ClassLoader,
        commonApi: CommonApi?,
        style: Int,
        progressHeadGlow: Boolean
    ): Int {
        if (
            style == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE &&
            commonApi == null
        ) {
            return 0
        }
        val api = runCatching {
            NotificationApi.create(classLoader, commonApi)
        }.onFailure {
            HookLogger.w(TAG, "通知中心进度条接口不可用: reason=${it.message}")
        }.getOrNull() ?: return 0

        if (
            style == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE &&
            (api.observerOnChanged == null || api.attachMethod == null)
        ) {
            HookLogger.w(TAG, "通知中心进度或拖动接口不可用，跳过波浪进度条替换")
            return 0
        }

        var installed = 0
        api.holderConstructors.forEach { constructor ->
            runCatching {
                constructor.isAccessible = true
                xposedModule.managedHook(
                    executable = constructor,
                    capability = "media.progress.notification_holder",
                    hooker = NotificationHolderHook(api, style, progressHeadGlow),
                )
                installed++
            }.onFailure {
                HookLogger.e(TAG, "安装通知中心进度条构造 Hook 失败", it)
            }
        }

        if (style == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE) {
            api.observerOnChanged?.let { method ->
                installMethod(xposedModule, method, NotificationProgressHook(api)) {
                    installed++
                }
            }
            api.attachMethod?.let { method ->
                installMethod(xposedModule, method, NotificationControllerHook(api, Action.ATTACH)) {
                    installed++
                }
            }
            api.fullAodMethod?.let { method ->
                installMethod(xposedModule, method, NotificationControllerHook(api, Action.FULL_AOD)) {
                    installed++
                }
            }
        }
        if (
            style == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE ||
            progressHeadGlow
        ) {
            api.detachMethod?.let { method ->
                installMethod(xposedModule, method, NotificationControllerHook(api, Action.DETACH)) {
                    installed++
                }
            }
        }
        if (
            progressHeadGlow &&
            style != RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE
        ) {
            api.attachMethod?.let { method ->
                installMethod(
                    xposedModule,
                    method,
                    NotificationControllerHook(api, Action.NATIVE_GLOW_ATTACH)
                ) {
                    installed++
                }
            }
        }
        if (
            style == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE ||
            progressHeadGlow
        ) {
            api.updateForegroundColorsMethod?.let { method ->
                installMethod(
                    xposedModule,
                    method,
                    NotificationControllerHook(api, Action.FOREGROUND)
                ) {
                    installed++
                }
            }
        }
        return installed
    }

    private fun installIslandHooks(
        xposedModule: XposedModule,
        classLoader: ClassLoader,
        commonApi: CommonApi?
    ): Int {
        if (commonApi == null) return 0
        val api = runCatching {
            IslandApi.create(classLoader, commonApi)
        }.onFailure {
            HookLogger.w(TAG, "超级岛进度条接口不可用: reason=${it.message}")
        }.getOrNull() ?: return 0

        var installed = 0
        api.holderConstructors.forEach { constructor ->
            runCatching {
                constructor.isAccessible = true
                xposedModule.managedHook(
                    executable = constructor,
                    capability = "media.progress.island_holder",
                    hooker = IslandHolderHook(api),
                )
                installed++
            }.onFailure {
                HookLogger.e(TAG, "安装超级岛进度条构造 Hook 失败", it)
            }
        }
        installMethod(xposedModule, api.seekBarChangedMethod, IslandProgressHook(api)) {
            installed++
        }
        api.attachMethod?.let { method ->
            installMethod(xposedModule, method, IslandBinderHook(api, Action.ATTACH)) {
                installed++
            }
        }
        api.detachMethod?.let { method ->
            installMethod(xposedModule, method, IslandBinderHook(api, Action.DETACH)) {
                installed++
            }
        }
        return installed
    }

    private inline fun installMethod(
        xposedModule: XposedModule,
        method: Method,
        hooker: Hooker,
        onInstalled: () -> Unit
    ) {
        runCatching {
            method.isAccessible = true
            xposedModule.managedHook(
                executable = method,
                capability = "media.progress.${method.name}",
                hooker = hooker,
            )
            onInstalled()
        }.onFailure {
            HookLogger.e(TAG, "安装媒体进度条 Hook 失败: method=${method.name}", it)
        }
    }

    private class NotificationHolderHook(
        private val api: NotificationApi,
        private val style: Int,
        private val progressHeadGlow: Boolean
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result
            val holder = chain.thisObject ?: return result
            runCatching {
                when (style) {
                    RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE ->
                        api.replaceWithWave(holder)

                    else -> if (progressHeadGlow) api.replaceWithNativeGlow(holder)
                }
            }.onFailure {
                HookLogger.e(TAG, "初始化通知中心进度条失败", it)
            }
            return result
        }
    }

    private class IslandHolderHook(private val api: IslandApi) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result
            chain.thisObject?.let { holder ->
                runCatching { api.replaceWithWave(holder) }
                    .onFailure { HookLogger.e(TAG, "初始化超级岛波浪进度条失败", it) }
            }
            return result
        }
    }

    private class NotificationProgressHook(private val api: NotificationApi) : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!MediaCardRuntimeConfig.current.enabled) return chain.proceed()
            val observer = chain.thisObject ?: return chain.proceed()
            val progress = chain.args.getOrNull(0) ?: return chain.proceed()
            val holder = api.getObserverHolder(observer) ?: return chain.proceed()
            val common = api.common ?: return chain.proceed()
            return if (common.updateProgress(holder, progress, isIsland = false)) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    private class IslandProgressHook(private val api: IslandApi) : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!MediaCardRuntimeConfig.current.enabled) return chain.proceed()
            val progress = chain.args.getOrNull(1) ?: return chain.proceed()
            val holder = chain.args.getOrNull(2) ?: return chain.proceed()
            return if (api.common.updateProgress(holder, progress, isIsland = true)) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    private class NotificationControllerHook(
        private val api: NotificationApi,
        private val action: Action
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result
            val controller = chain.thisObject ?: return result
            runCatching {
                when (action) {
                    Action.ATTACH -> api.attachWaveListener(controller)
                    Action.DETACH -> api.detachProgress(controller)
                    Action.NATIVE_GLOW_ATTACH -> api.attachNativeGlow(controller)

                    Action.FULL_AOD -> {
                        val toFullAod = chain.args.getOrNull(0) as? Boolean ?: false
                        val keepExpanded =
                            NotificationMediaCoverStyleHooker.shouldKeepExpandedInFullAod()
                        api.getHolder(controller)?.let { holder ->
                            waveSeekBars[holder]?.visibility =
                                if (toFullAod && !keepExpanded) View.GONE else View.VISIBLE
                        }
                    }

                    Action.FOREGROUND -> api.updateProgressColor(controller)
                }
            }.onFailure {
                HookLogger.e(TAG, "同步通知中心波浪进度条状态失败: action=$action", it)
            }
            return result
        }
    }

    private class IslandBinderHook(
        private val api: IslandApi,
        private val action: Action
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result
            val binder = chain.thisObject ?: return result
            runCatching {
                when (action) {
                    Action.ATTACH -> api.attachWaveListeners(binder)
                    Action.DETACH -> api.getHolders(binder).forEach { holder ->
                        api.detachWave(holder)
                    }

                    else -> Unit
                }
            }.onFailure {
                HookLogger.e(TAG, "同步超级岛波浪进度条状态失败: action=$action", it)
            }
            return result
        }
    }

    private enum class Action {
        ATTACH,
        DETACH,
        NATIVE_GLOW_ATTACH,
        FULL_AOD,
        FOREGROUND
    }

    private class CommonApi(
        private val listeningField: Field,
        private val seekAvailableField: Field,
        private val playingField: Field,
        private val scrubbingField: Field,
        private val enabledField: Field,
        private val durationField: Field,
        private val elapsedTimeField: Field,
        private val falsingManagerField: Field,
        private val seekBarChangeListenerConstructor: Constructor<*>
    ) {
        @SuppressLint("SetTextI18n")
        fun updateProgress(holder: Any, progress: Any, isIsland: Boolean): Boolean {
            val seekBar = waveSeekBars[holder] ?: return false
            val holderApi = if (isIsland) {
                IslandApi.currentHolderFields
            } else {
                NotificationApi.currentHolderFields
            } ?: return false
            val elapsedTimeView = holderApi.elapsedTimeField.get(holder) as? TextView
            val totalTimeView = holderApi.totalTimeField.get(holder) as? TextView
            val listening = listeningField.getBoolean(progress)
            val seekAvailable = seekAvailableField.getBoolean(progress)
            val playing = playingField.getBoolean(progress)
            val scrubbing = scrubbingField.getBoolean(progress)
            val enabled = enabledField.getBoolean(progress)
            val duration = durationField.getInt(progress)
            val elapsedTime = elapsedTimeField.get(progress) as? Int
            if (enabled) {
                totalTimeView?.text = DateUtils.formatElapsedTime(duration / 1000L)
                seekBar.isEnabled = seekAvailable
                seekBar.max = duration
                elapsedTime?.let {
                    elapsedTimeView?.text = DateUtils.formatElapsedTime(it / 1000L)
                    if (!scrubbing) seekBar.progress = it
                }
                seekBar.animate = playing && !scrubbing && listening
                seekBar.transitionEnabled = !seekAvailable
            } else {
                seekBar.isEnabled = false
                seekBar.progress = 0
                seekBar.contentDescription = ""
                seekBar.animate = false
                elapsedTimeView?.text = "00:00"
                totalTimeView?.text = "00:00"
            }
            return true
        }

        fun createChangeListener(viewModel: Any): SeekBar.OnSeekBarChangeListener? {
            val falsingManager = falsingManagerField.get(viewModel) ?: return null
            return seekBarChangeListenerConstructor.newInstance(
                viewModel,
                falsingManager
            ) as? SeekBar.OnSeekBarChangeListener
        }

        companion object {
            fun create(classLoader: ClassLoader): CommonApi {
                val progressClass = classLoader.loadClass(PROGRESS_CLASS)
                val viewModelClass = classLoader.loadClass(SEEK_BAR_VIEW_MODEL_CLASS)
                val listenerClass = classLoader.loadClass(SEEK_BAR_CHANGE_LISTENER_CLASS)
                fun field(owner: Class<*>, name: String): Field {
                    return owner.getDeclaredField(name).apply { isAccessible = true }
                }
                val listenerConstructor = listenerClass.declaredConstructors.firstOrNull {
                    it.parameterCount == 2
                }?.apply { isAccessible = true }
                    ?: error("SeekBarChangeListener constructor unavailable")
                return CommonApi(
                    listeningField = field(progressClass, "listening"),
                    seekAvailableField = field(progressClass, "seekAvailable"),
                    playingField = field(progressClass, "playing"),
                    scrubbingField = field(progressClass, "scrubbing"),
                    enabledField = field(progressClass, "enabled"),
                    durationField = field(progressClass, "duration"),
                    elapsedTimeField = field(progressClass, "elapsedTime"),
                    falsingManagerField = field(viewModelClass, "falsingManager"),
                    seekBarChangeListenerConstructor = listenerConstructor
                )
            }
        }
    }

    private data class HolderFields(
        val seekBarField: Field,
        val elapsedTimeField: Field,
        val totalTimeField: Field
    )

    private data class WaveSeekBarState(
        val original: SeekBar,
        val replacement: SquigglySeekBar,
        val parent: ViewGroup,
        val originalIndex: Int
    )

    private data class NativeGlowState(
        val original: SeekBar,
        val replacement: SeekBar,
        val parent: ViewGroup,
        val originalIndex: Int
    )

    private class NotificationApi private constructor(
        val common: CommonApi?,
        val holderConstructors: List<Constructor<*>>,
        private val holderFields: HolderFields,
        private val hyperSeekBarConstructor: Constructor<*>?,
        private val runtimeShaderField: Field?,
        private val observerOuterField: Field?,
        private val controllerHolderField: Field,
        private val controllerSeekBarViewModelField: Field,
        val observerOnChanged: Method?,
        val attachMethod: Method?,
        val detachMethod: Method?,
        val fullAodMethod: Method?,
        val updateForegroundColorsMethod: Method?
    ) {
        fun replaceWithWave(holder: Any): SeekBar? {
            currentHolderFields = holderFields
            return replaceWithWaveSeekBar(
                holder,
                holderFields.seekBarField,
                MediaCardRuntimeConfig.current.notification.thumbStyle
            )
        }

        fun replaceWithNativeGlow(holder: Any) {
            currentHolderFields = holderFields
            nativeGlowStates[holder]?.let { state ->
                if (holderFields.seekBarField.get(holder) !== state.replacement) {
                    holderFields.seekBarField.set(holder, state.replacement)
                }
                ensureNativeGlowAttached(state)
                nativeGlowSeekBars[holder] = state.replacement
                return
            }
            val original = holderFields.seekBarField.get(holder) as? SeekBar ?: return
            val parent = original.parent as? ViewGroup ?: return
            val originalIndex = parent.indexOfChild(original).takeIf { it >= 0 } ?: return
            var createdReplacement: SeekBar? = null
            try {
                val constructor = hyperSeekBarConstructor
                    ?: error("HyperProgressSeekBar(Context) constructor unavailable")
                val shaderField = runtimeShaderField
                    ?: error("HyperProgressSeekBar runtimeShader unavailable")
                val context = original.context
                val replacement = (constructor.newInstance(context) as? SeekBar ?: return)
                    .also { createdReplacement = it }
                if (shaderField.get(replacement) == null) {
                    HookLogger.w(TAG, "当前设备级别不支持原生光辉进度条，保留通知中心默认样式")
                    return
                }
                replacement.id = original.id
                replacement.layoutParams = copyLayoutParams(original)
                replacement.visibility = original.visibility
                replacement.alpha = original.alpha
                replacement.isEnabled = original.isEnabled
                replacement.max = original.max
                replacement.progress = original.progress
                replacement.secondaryProgress = original.secondaryProgress
                replacement.contentDescription = original.contentDescription
                replacement.thumbTintList = original.thumbTintList
                replacement.progressTintList = original.progressTintList
                replacement.progressBackgroundTintList =
                    original.progressBackgroundTintList
                replacement.setPadding(
                    original.paddingLeft,
                    original.paddingTop,
                    original.paddingRight,
                    original.paddingBottom
                )
                val state = NativeGlowState(original, replacement, parent, originalIndex)
                holderFields.seekBarField.set(holder, replacement)
                parent.addView(replacement, originalIndex.coerceIn(0, parent.childCount))
                parent.removeView(original)
                nativeGlowStates[holder] = state
                nativeGlowSeekBars[holder] = replacement
            } catch (error: Throwable) {
                holderFields.seekBarField.set(holder, original)
                createdReplacement?.let { replacement ->
                    if (replacement.parent === parent) parent.removeView(replacement)
                }
                nativeGlowStates.remove(holder)
                nativeGlowSeekBars.remove(holder)
                throw error
            }
        }

        fun getObserverHolder(observer: Any): Any? {
            val controller = observerOuterField?.get(observer) ?: return null
            return getHolder(controller)
        }

        fun getHolder(controller: Any): Any? = controllerHolderField.get(controller)

        fun attachWaveListener(controller: Any) {
            val holder = getHolder(controller) ?: return
            val seekBar = replaceWithWave(holder) ?: return
            val viewModel = controllerSeekBarViewModelField.get(controller) ?: return
            val listener = common?.createChangeListener(viewModel) ?: return
            seekBar.setOnSeekBarChangeListener(listener)
        }

        fun attachNativeGlow(controller: Any) {
            getHolder(controller)?.let { holder ->
                replaceWithNativeGlow(holder)
                updateProgressColor(controller)
            }
        }

        fun detachProgress(controller: Any) {
            getHolder(controller)?.let { holder ->
                restoreWaveSeekBar(holder)
                restoreNativeGlow(holder, holderFields.seekBarField)
            }
        }

        fun updateProgressColor(controller: Any) {
            val holder = getHolder(controller) ?: return
            if (
                NotificationMediaBackgroundController.isActive(controller) &&
                !NotificationMediaForegroundStyler.hasAppliedPalette(controller)
            ) {
                return
            }
            nativeGlowSeekBars[holder]?.let { seekBar ->
                NotificationMediaForegroundStyler.applyProgressColors(controller, seekBar)
                return
            }
            waveSeekBars[holder]?.let { seekBar ->
                NotificationMediaForegroundStyler.applyProgressColors(
                    controller = controller,
                    seekBar = seekBar
                )
            }
        }

        companion object {
            @Volatile
            var currentHolderFields: HolderFields? = null

            fun create(classLoader: ClassLoader, common: CommonApi?): NotificationApi {
                val holderClass = classLoader.loadClass(NOTIFICATION_HOLDER_CLASS)
                val controllerClass = classLoader.loadClass(NOTIFICATION_CONTROLLER_CLASS)
                val observerClass = runCatching {
                    classLoader.loadClass(NOTIFICATION_OBSERVER_CLASS)
                }.getOrNull()
                val seekBarClass = classLoader.loadClass(HYPER_PROGRESS_SEEK_BAR_CLASS)
                val holderFields = holderFields(holderClass)
                val observerOuter = observerClass?.let {
                    findField(it, "this\$0")
                }
                return NotificationApi(
                    common = common,
                    holderConstructors = holderClass.declaredConstructors.toList(),
                    holderFields = holderFields,
                    hyperSeekBarConstructor = runCatching {
                        seekBarClass.getDeclaredConstructor(Context::class.java)
                            .apply { isAccessible = true }
                    }.getOrNull(),
                    runtimeShaderField = runCatching {
                        seekBarClass.getDeclaredField("runtimeShader")
                            .apply { isAccessible = true }
                    }.getOrNull(),
                    observerOuterField = observerOuter,
                    controllerHolderField = controllerClass.getDeclaredField("holder")
                        .apply { isAccessible = true },
                    controllerSeekBarViewModelField =
                        controllerClass.getDeclaredField("seekBarViewModel")
                            .apply { isAccessible = true },
                    observerOnChanged = observerClass?.declaredMethods?.firstOrNull {
                        it.name == "onChanged" && it.parameterCount == 1
                    },
                    attachMethod = findMethod(controllerClass, "attach"),
                    detachMethod = findMethod(controllerClass, "detach"),
                    fullAodMethod = findMethod(controllerClass, "onFullAodStateChanged", 1),
                    updateForegroundColorsMethod =
                        findMethod(controllerClass, "updateForegroundColors", 0)
                )
            }
        }
    }

    private class IslandApi private constructor(
        val common: CommonApi,
        val holderConstructors: List<Constructor<*>>,
        private val holderFields: HolderFields,
        private val binderHolderField: Field,
        private val binderDummyHolderField: Field,
        private val binderSeekBarViewModelField: Field,
        val seekBarChangedMethod: Method,
        val attachMethod: Method?,
        val detachMethod: Method?
    ) {
        fun replaceWithWave(holder: Any): SeekBar? {
            currentHolderFields = holderFields
            return replaceWithWaveSeekBar(
                holder,
                holderFields.seekBarField,
                MediaCardRuntimeConfig.current.islandExpanded.thumbStyle
            )
        }

        fun getHolders(binder: Any): List<Any> = listOfNotNull(
            binderHolderField.get(binder),
            binderDummyHolderField.get(binder)
        )

        fun attachWaveListeners(binder: Any) {
            val viewModel = binderSeekBarViewModelField.get(binder) ?: return
            getHolders(binder).forEach { holder ->
                val seekBar = replaceWithWave(holder) ?: return@forEach
                common.createChangeListener(viewModel)?.let { listener ->
                    seekBar.setOnSeekBarChangeListener(listener)
                }
            }
        }

        fun detachWave(holder: Any) {
            restoreWaveSeekBar(holder)
        }

        companion object {
            @Volatile
            var currentHolderFields: HolderFields? = null

            fun create(classLoader: ClassLoader, common: CommonApi): IslandApi {
                val holderClass = classLoader.loadClass(ISLAND_HOLDER_CLASS)
                val binderClass = classLoader.loadClass(ISLAND_BINDER_CLASS)
                val seekBarChanged = binderClass.declaredMethods.firstOrNull { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.parameterCount == 3 &&
                        method.name.contains("seekBarChanged")
                } ?: error("Island seekBarChanged callback unavailable")
                return IslandApi(
                    common = common,
                    holderConstructors = holderClass.declaredConstructors.toList(),
                    holderFields = holderFields(holderClass),
                    binderHolderField = binderClass.getDeclaredField("holder")
                        .apply { isAccessible = true },
                    binderDummyHolderField = binderClass.getDeclaredField("dummyHolder")
                        .apply { isAccessible = true },
                    binderSeekBarViewModelField =
                        binderClass.getDeclaredField("seekBarViewModel")
                            .apply { isAccessible = true },
                    seekBarChangedMethod = seekBarChanged,
                    attachMethod = findMethod(binderClass, "attach"),
                    detachMethod = findMethod(binderClass, "detach")
                )
            }
        }
    }

    private fun replaceWithWaveSeekBar(
        holder: Any,
        seekBarField: Field,
        thumbStyle: Int
    ): SeekBar? {
        waveStates[holder]?.let { state ->
            ensureWaveAttached(state)
            waveSeekBars[holder] = state.replacement
            return state.replacement
        }
        val original = seekBarField.get(holder) as? SeekBar ?: return null
        if (original is SquigglySeekBar) {
            waveSeekBars[holder] = original
            return original
        }
        val parent = original.parent as? ViewGroup ?: return null
        val context = original.context
        val originalIndex = parent.indexOfChild(original).takeIf { it >= 0 } ?: return null
        val layoutParams = original.layoutParams?.let { params ->
            when (params) {
                is ViewGroup.MarginLayoutParams ->
                    ViewGroup.MarginLayoutParams(params).apply {
                        topMargin = 0
                        bottomMargin = 0
                    }

                else -> ViewGroup.LayoutParams(params)
            }
        }
        val replacement = SquigglySeekBar(context).apply {
            id = original.id
            this.layoutParams = layoutParams
            visibility = original.visibility
            alpha = original.alpha
            isEnabled = original.isEnabled
            max = original.max
            progress = original.progress
            secondaryProgress = original.secondaryProgress
            contentDescription = original.contentDescription
            thumbTintList = original.thumbTintList
            progressTintList = original.progressTintList
            progressBackgroundTintList = original.progressBackgroundTintList
            setPadding(
                original.paddingLeft,
                original.paddingTop,
                original.paddingRight,
                original.paddingBottom
            )
            this.thumbStyle = when (thumbStyle) {
                RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_VERTICAL -> ThumbStyle.VerticalBar
                RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN -> ThumbStyle.Hidden
                else -> ThumbStyle.Circle
            }
            waveLength = context.dp(20f)
            lineAmplitude = context.dp(1.5f)
            phaseSpeed = context.dp(8f)
            strokeWidth = context.dp(2f)
        }
        val state = WaveSeekBarState(original, replacement, parent, originalIndex)
        try {
            parent.addView(replacement, originalIndex.coerceIn(0, parent.childCount))
            parent.removeView(original)
            waveStates[holder] = state
            waveSeekBars[holder] = replacement
        } catch (error: Throwable) {
            if (replacement.parent === parent) parent.removeView(replacement)
            throw error
        }
        return replacement
    }

    private fun ensureWaveAttached(state: WaveSeekBarState) {
        if (state.replacement.parent === state.parent) return
        (state.replacement.parent as? ViewGroup)?.removeView(state.replacement)
        if (state.original.parent === state.parent) {
            state.parent.removeView(state.original)
        }
        state.parent.addView(
            state.replacement,
            state.originalIndex.coerceIn(0, state.parent.childCount)
        )
    }

    private fun ensureNativeGlowAttached(state: NativeGlowState) {
        if (state.replacement.parent === state.parent) return
        (state.replacement.parent as? ViewGroup)?.removeView(state.replacement)
        if (state.original.parent === state.parent) {
            state.parent.removeView(state.original)
        }
        state.parent.addView(
            state.replacement,
            state.originalIndex.coerceIn(0, state.parent.childCount)
        )
    }

    private fun restoreWaveSeekBar(holder: Any) {
        val state = waveStates.remove(holder) ?: run {
            waveSeekBars.remove(holder)
            return
        }
        state.replacement.setOnSeekBarChangeListener(null)
        val currentParent = state.replacement.parent as? ViewGroup
        if (currentParent != null) {
            currentParent.removeView(state.replacement)
            if (state.original.parent == null) {
                state.parent.addView(
                    state.original,
                    state.originalIndex.coerceIn(0, state.parent.childCount)
                )
            }
        } else if (state.original.parent == null) {
            state.parent.addView(
                state.original,
                state.originalIndex.coerceIn(0, state.parent.childCount)
            )
        }
        waveSeekBars.remove(holder)
    }

    private fun restoreNativeGlow(holder: Any, seekBarField: Field) {
        val state = nativeGlowStates.remove(holder) ?: run {
            nativeGlowSeekBars.remove(holder)
            return
        }
        if (seekBarField.get(holder) === state.replacement) {
            seekBarField.set(holder, state.original)
        }
        val currentParent = state.replacement.parent as? ViewGroup
        if (currentParent != null) {
            currentParent.removeView(state.replacement)
            if (state.original.parent == null) {
                state.parent.addView(
                    state.original,
                    state.originalIndex.coerceIn(0, state.parent.childCount)
                )
            }
        } else if (state.original.parent == null) {
            state.parent.addView(
                state.original,
                state.originalIndex.coerceIn(0, state.parent.childCount)
            )
        }
        nativeGlowSeekBars.remove(holder)
    }

    private fun copyLayoutParams(view: View): ViewGroup.LayoutParams? {
        val params = view.layoutParams ?: return null
        return when (params) {
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(params)
            else -> ViewGroup.LayoutParams(params)
        }
    }

    private fun holderFields(holderClass: Class<*>): HolderFields {
        return HolderFields(
            seekBarField = holderClass.getDeclaredField("seekBar").apply {
                isAccessible = true
            },
            elapsedTimeField = holderClass.getDeclaredField("elapsedTimeView").apply {
                isAccessible = true
            },
            totalTimeField = holderClass.getDeclaredField("totalTimeView").apply {
                isAccessible = true
            }
        )
    }

    private fun findField(owner: Class<*>, name: String): Field? {
        return generateSequence(owner as Class<*>?) { it.superclass }
            .firstNotNullOfOrNull { type ->
                runCatching { type.getDeclaredField(name) }.getOrNull()
            }
            ?.apply { isAccessible = true }
    }

    private fun findMethod(owner: Class<*>, name: String, parameterCount: Int): Method? {
        return generateSequence(owner as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == name && it.parameterCount == parameterCount }
            ?.apply { isAccessible = true }
    }

    private fun findMethod(owner: Class<*>, name: String): Method? {
        return generateSequence(owner as Class<*>?) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { it.name == name }
            ?.apply { isAccessible = true }
    }

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
}
