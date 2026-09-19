package com.lidesheng.hyperlyric.root.mediacard.notification

import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.MediaCoverRotationController
import com.lidesheng.hyperlyric.root.mediacard.notification.aod.NotificationMediaFullAodAnimatedHeightHook
import com.lidesheng.hyperlyric.root.mediacard.notification.aod.NotificationMediaFullAodHook
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaBackgroundController
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutController
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutResourceIds
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.miui.NotificationMediaMiuiStyle
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.oneui.NotificationMediaOneUiStyle
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.pixel.NotificationMediaPixelStyle
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaCoverStyler
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaForegroundStyler
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object NotificationMediaCoverStyleHooker {
    private const val TAG = "NotificationMediaCoverStyleHooker"

    private lateinit var runtimeConfig: MediaCardRuntimeConfig.Snapshot

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val nativeApis =
        Collections.synchronizedMap(WeakHashMap<ClassLoader, NotificationMediaHostApi>())
    private val nativeUnavailableClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val colorOsAppIconStates =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, ColorOsAppIconState>())
    private val colorOsDeviceSwitchSources =
        Collections.synchronizedMap(WeakHashMap<ImageButton, ImageView>())
    private val boundSessionIdentities =
        Collections.synchronizedMap(WeakHashMap<Any, String>())

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

        if (!::runtimeConfig.isInitialized) {
            runtimeConfig = MediaCardRuntimeConfig.current
        }

        val api = resolveApi(classLoader) ?: run {
            hookedClassLoaders.remove(classLoader)
            return
        }
        NotificationMediaForegroundStyler.setAppliedListener { controller ->
            if (
                runtimeConfig.notification.layoutStyle ==
                    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                runtimeConfig.notification.layoutStyle ==
                    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI
            ) {
                runCatching { refreshAppNameColor(controller) }
                    .onFailure { HookLogger.e(TAG, "刷新媒体卡片应用名称颜色失败", it) }
            }
        }
        val methods = api.hookMethods.filter(::isHookRequired)
        val handles = mutableListOf<HookHandle>()
        methods.forEach { method ->
            runCatching {
                xposedModule.deoptimize(method)
                val hooker = hookerFor(method)
                    ?: error("No hooker for ${method.declaringClass.name}.${method.name}")
                handles += xposedModule.hook(method).intercept(hooker)
            }.onFailure { error ->
                HookLogger.e(
                    TAG,
                    "安装通知中心媒体卡片 Hook 失败: " +
                            "method=${method.declaringClass.simpleName}.${method.name}",
                    error
                )
            }
        }

        if (handles.size != methods.size) {
            handles.forEach(HookHandle::unhook)
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "通知中心媒体卡片 Hook 安装不完整")
        } else {
            HookLogger.d(TAG, "通知中心媒体卡片 Hook 已初始化: methods=${handles.size}")
        }
    }

    fun isTargetMethod(method: Method): Boolean {
        return when (method.declaringClass.name) {
            NotificationMediaHostClasses.VIEW_CONTROLLER -> when (method.name) {
                "attach", "bindMediaData", "setSeamless" -> method.parameterCount == 1
                "detach" -> method.parameterCount == 0
                "onFullAodStateChanged" ->
                    method.parameterCount == 1 &&
                            method.parameterTypes[0] == Boolean::class.javaPrimitiveType
                "updateForegroundColors" -> method.parameterCount == 0
                else -> false
            }

            NotificationMediaHostClasses.ACTION_BUTTON_UTILS -> when (method.name) {
                "setSemanticButton" -> method.parameterCount == 2
                "bindButtonsCommon" -> method.parameterCount == 3
                else -> false
            }

            NotificationMediaHostClasses.MEDIA_HEADER_VIEW ->
                method.name == "setAnimateHeight" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType

            NotificationMediaHostClasses.LAYOUT_CONTROLLER ->
                method.name == "loadLayout\$1" && method.parameterCount == 0

            else -> false
        }
    }

    fun hookerFor(executable: Executable): Hooker? {
        val method = executable as? Method ?: return null
        if (!isTargetMethod(method)) return null
        return when (method.declaringClass.name) {
            NotificationMediaHostClasses.VIEW_CONTROLLER -> when (method.name) {
                "onFullAodStateChanged" -> NotificationMediaFullAodHook(
                    keepExpanded = ::shouldKeepExpandedInFullAod,
                    onApplied = { controller -> applyStyle(controller, null) },
                    onFailure = { error ->
                        HookLogger.e(TAG, "应用通知中心 Full AOD 媒体样式失败", error)
                    }
                )

                else -> ControllerHook(method.name)
            }

            NotificationMediaHostClasses.ACTION_BUTTON_UTILS -> ActionButtonHook()
            NotificationMediaHostClasses.MEDIA_HEADER_VIEW ->
                NotificationMediaFullAodAnimatedHeightHook(
                    keepExpanded = ::shouldKeepExpandedInFullAod
                )
            NotificationMediaHostClasses.LAYOUT_CONTROLLER -> LayoutLoadHook()
            else -> null
        }
    }

    private fun isHookRequired(method: Method): Boolean {
        if (!runtimeConfig.enabled) return false
        val config = runtimeConfig.notification
        return when (method.declaringClass.name) {
            NotificationMediaHostClasses.VIEW_CONTROLLER -> when (method.name) {
                "attach", "bindMediaData" ->
                    config.coverStyle != RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT ||
                            config.layoutStyle != RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM ||
                            config.hideTime ||
                            config.hideCustomActions
                "detach" ->
                    config.coverStyle ==
                            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_ROTATING_CIRCLE ||
                            config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ||
                            config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                            config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                            config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
                "setSeamless" ->
                    config.hideDeviceSwitch ||
                        config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ||
                        config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                        config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL ||
                        config.backgroundStyle !=
                            RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT ||
                        config.ambientFlowMode !=
                            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
                "onFullAodStateChanged" -> shouldKeepExpandedInFullAod()
                "updateForegroundColors" ->
                    config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                        config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                        config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
                else -> false
            }

            NotificationMediaHostClasses.ACTION_BUTTON_UTILS ->
                config.hideCustomActions ||
                    config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                    config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                    config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
            NotificationMediaHostClasses.MEDIA_HEADER_VIEW -> shouldKeepExpandedInFullAod()
            NotificationMediaHostClasses.LAYOUT_CONTROLLER -> needsConstraintLayout(config)
            else -> false
        }
    }

    private fun needsConstraintLayout(config: MediaCardRuntimeConfig.Notification): Boolean {
        return config.layoutStyle != RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM ||
                config.coverStyle == RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN ||
                config.hideCoverSource ||
                config.hideDeviceSwitch ||
                config.hideCustomActions ||
                config.hideTime ||
                config.actionAlignLeft ||
                config.actionOrder != RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT
    }

    private class ControllerHook(private val methodName: String) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject ?: return chain.proceed()
            if (
                methodName == "setSeamless" &&
                runtimeConfig.notification.hideDeviceSwitch &&
                    runtimeConfig.notification.layoutStyle !=
                    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS &&
                    runtimeConfig.notification.layoutStyle !=
                    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI
            ) {
                return null
            }
            if (methodName == "detach") {
                boundSessionIdentities.remove(controller)
                cleanupStyle(controller)
                return chain.proceed()
            }

            val boundMediaData = if (methodName == "bindMediaData") {
                chain.args.firstOrNull()
            } else {
                null
            }
            val previousSessionIdentity = boundSessionIdentities[controller]
            val nextSessionIdentity = boundMediaData?.let(NotificationMediaDataIdentity::sessionOf)
            val result = chain.proceed()
            if (methodName == "attach" || methodName == "bindMediaData") {
                runCatching {
                    if (methodName == "bindMediaData" &&
                        boundMediaData != null &&
                        previousSessionIdentity != null &&
                        previousSessionIdentity != nextSessionIdentity
                    ) {
                        resolveApi(controller.javaClass.classLoader)
                            ?.refreshArtwork(controller, boundMediaData)
                    }
                    if (nextSessionIdentity != null) {
                        boundSessionIdentities[controller] = nextSessionIdentity
                    }
                    applyStyle(controller, boundMediaData)
                }.onFailure { HookLogger.e(TAG, "应用通知中心媒体卡片样式失败", it) }
            }
            if (methodName == "setSeamless" &&
                runtimeConfig.notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS
            ) {
                runCatching { applyColorOsAppIcon(controller) }
                    .onFailure { HookLogger.e(TAG, "应用 ColorOS 媒体卡片应用图标失败", it) }
            }
            if (methodName == "setSeamless" &&
                runtimeConfig.notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI
            ) {
                runCatching { applyOneUiStyle(controller) }
                    .onFailure { HookLogger.e(TAG, "应用 One UI 媒体卡片应用身份失败", it) }
            }
            if (methodName == "setSeamless" &&
                runtimeConfig.notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
            ) {
                runCatching { refreshPixelAppIcon(controller) }
                    .onFailure { HookLogger.e(TAG, "刷新 Pixel 媒体卡片应用图标失败", it) }
            }
            if (methodName == "setSeamless" && shouldApplyUnifiedForeground(controller)) {
                runCatching { NotificationMediaAmbientFlowHooker.applyUnifiedForeground(controller) }
                    .onFailure { HookLogger.e(TAG, "刷新媒体卡片统一前景色失败", it) }
            }
            if (
                methodName == "updateForegroundColors" &&
                    (runtimeConfig.notification.layoutStyle ==
                        RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                        runtimeConfig.notification.layoutStyle ==
                            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                        runtimeConfig.notification.layoutStyle ==
                            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL)
            ) {
                runCatching {
                    if (runtimeConfig.notification.layoutStyle ==
                        RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
                    ) {
                        refreshPixelAppIcon(controller)
                    } else {
                        refreshAppNameColor(controller)
                    }
                }.onFailure { HookLogger.e(TAG, "刷新媒体卡片应用身份失败", it) }
            }
            return result
        }
    }

    private fun shouldApplyUnifiedForeground(controller: Any): Boolean {
        return NotificationMediaBackgroundController.isActive(controller) ||
            runtimeConfig.notification.ambientFlowMode !=
            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
    }

    private class ActionButtonHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!shouldApplyActionButtonStyle()) return result
            (chain.args.firstOrNull() as? ImageButton)?.let { button ->
                when {
                    button.isColorOsDeviceSwitch() -> applyColorOsDeviceSwitchForButton(button)
                    runtimeConfig.notification.layoutStyle ==
                            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI -> {
                        NotificationMediaOneUiStyle.applyActionButton(button)
                        if (runtimeConfig.notification.hideCustomActions) {
                            applyCustomActionVisibility(button)
                        }
                    }

                    runtimeConfig.notification.layoutStyle ==
                            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI -> {
                        NotificationMediaMiuiStyle.applyActionButton(button)
                        if (runtimeConfig.notification.hideCustomActions) {
                            applyCustomActionVisibility(button)
                        }
                    }

                    runtimeConfig.notification.layoutStyle ==
                            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL -> {
                        NotificationMediaPixelStyle.applyActionButton(button)
                        if (runtimeConfig.notification.hideCustomActions) {
                            applyCustomActionVisibility(button)
                        }
                    }

                    else -> applyCustomActionVisibility(button)
                }
            }
            return result
        }
    }

    private fun shouldApplyActionButtonStyle(): Boolean {
        val config = runtimeConfig.notification
        return config.hideCustomActions ||
                config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                config.layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
    }

    private class LayoutLoadHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val controller = chain.thisObject ?: return result
            runCatching {
                resolveApi(controller.javaClass.classLoader)?.let { api ->
                    applyLoadedLayout(api, controller)
                }
            }.onFailure { HookLogger.e(TAG, "应用通知中心媒体卡片约束失败", it) }
            return result
        }
    }

    private fun applyStyle(controller: Any, mediaData: Any?) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        val albumView = api.getAlbumView(holder)
        val albumImage = api.getAlbumImage(holder)
        val config = runtimeConfig.notification
        val layoutStyle = config.layoutStyle

        api.getSeekBar(holder)?.let { seekBar ->
            if (
                layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS ||
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ||
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
            ) {
                api.removeSeekBarTrackInset(seekBar)
            }
            if (
                layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ||
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
            ) {
                applyExpandedMediaSeekBarPadding(seekBar)
            } else if (layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI) {
                applyMiuiMediaSeekBarPadding(seekBar)
            }
        }
        if (layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS) {
            applyColorOsTimeAlignment(api, holder)
            applyColorOsAppIcon(api, controller, holder)
        } else if (layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI) {
            NotificationMediaOneUiStyle.apply(api, controller, holder, mediaData)
        } else if (layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI) {
            NotificationMediaMiuiStyle.apply(api, controller, holder, mediaData)
        } else if (layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL) {
            NotificationMediaPixelStyle.apply(api, controller, holder, mediaData)
        }
        if (config.hideTime || layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL) {
            api.getElapsedTimeView(holder)?.visibility = View.GONE
            api.getTotalTimeView(holder)?.visibility = View.GONE
        }
        if (config.hideCustomActions) {
            api.getActionButtons(holder).forEach(::applyCustomActionVisibility)
        }

        if (
            layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
        ) {
            MediaCoverRotationController.detach(albumImage)
            albumView.visibility = View.GONE
        } else when (config.coverStyle) {
            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_CIRCLE -> {
                MediaCoverRotationController.detach(albumImage)
                NotificationMediaCoverStyler.applyCircle(albumView, albumImage)
            }

            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_ROTATING_CIRCLE -> {
                NotificationMediaCoverStyler.applyCircle(albumView, albumImage)
                val currentMediaData = api.getMediaData(controller) ?: mediaData
                MediaCoverRotationController.attach(
                    albumImage,
                    api.isPlaying(currentMediaData)
                )
            }

            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN -> {
                MediaCoverRotationController.detach(albumImage)
                albumView.visibility = View.GONE
            }
        }
    }

    private fun applyExpandedMediaSeekBarPadding(seekBar: View) {
        val verticalPadding = (16f * seekBar.resources.displayMetrics.density).roundToInt()
        if (
            seekBar.paddingTop == verticalPadding &&
                seekBar.paddingBottom == verticalPadding
        ) return
        seekBar.setPadding(
            seekBar.paddingLeft,
            verticalPadding,
            seekBar.paddingRight,
            verticalPadding
        )
    }

    private fun applyMiuiMediaSeekBarPadding(seekBar: View) {
        val verticalPadding = (16f * seekBar.resources.displayMetrics.density).roundToInt()
        if (
            seekBar.paddingTop == verticalPadding &&
                seekBar.paddingBottom == verticalPadding
        ) return
        seekBar.setPadding(
            seekBar.paddingLeft,
            verticalPadding,
            seekBar.paddingRight,
            verticalPadding
        )
    }

    private fun applyColorOsTimeAlignment(api: NotificationMediaHostApi, holder: Any) {
        api.getElapsedTimeView(holder)?.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        api.getTotalTimeView(holder)?.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    private fun applyColorOsAppIcon(controller: Any) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        applyColorOsAppIcon(api, controller, holder)
    }

    private fun applyColorOsAppIcon(
        api: NotificationMediaHostApi,
        controller: Any,
        holder: Any
    ) {
        val container = api.getSeamlessContainer(holder) ?: return
        val sourceIcon = api.getSeamlessIcon(holder) ?: return
        api.getAppIconDrawable(controller)?.let { drawable ->
            colorOsAppIconStates.getOrPut(container) {
                ColorOsAppIconState(container, sourceIcon)
            }.apply(drawable)
        }
        applyColorOsDeviceSwitch(api, holder)
    }

    private fun applyOneUiStyle(controller: Any) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        NotificationMediaOneUiStyle.apply(api, controller, holder, null)
    }

    private fun refreshAppNameColor(controller: Any) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        when (runtimeConfig.notification.layoutStyle) {
            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ->
                NotificationMediaMiuiStyle.refreshAppNameColor(api, holder)

            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ->
                NotificationMediaOneUiStyle.refreshAppNameColor(api, holder)
        }
    }

    private fun refreshPixelAppIcon(controller: Any) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        NotificationMediaPixelStyle.refreshAppIcon(api, controller, holder)
    }

    private fun applyColorOsDeviceSwitch(api: NotificationMediaHostApi, holder: Any) {
        val action4 = api.getAction4(holder) ?: return
        if (runtimeConfig.notification.hideDeviceSwitch) {
            colorOsDeviceSwitchSources.remove(action4)
            return
        }
        val sourceIcon = api.getSeamlessIcon(holder) ?: return
        colorOsDeviceSwitchSources[action4] = sourceIcon
        applyColorOsDeviceSwitch(action4, sourceIcon)
    }

    private fun applyColorOsDeviceSwitchForButton(button: ImageButton) {
        colorOsDeviceSwitchSources[button]?.let { sourceIcon ->
            applyColorOsDeviceSwitch(button, sourceIcon)
        }
    }

    private fun applyColorOsDeviceSwitch(button: ImageButton, sourceIcon: ImageView) {
        val drawable = sourceIcon.drawable ?: return
        val copy = runCatching {
            drawable.constantState
                ?.newDrawable(button.resources, button.context.theme)
                ?.mutate()
        }.getOrNull() ?: drawable
        button.setImageDrawable(InsetDrawable(copy, 0.05f))
        button.contentDescription = sourceIcon.contentDescription
        button.isEnabled = true
        button.visibility = View.VISIBLE
        button.setOnClickListener { sourceIcon.performClick() }
    }

    private fun cleanupStyle(controller: Any) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        NotificationMediaOneUiStyle.restore(api, holder)
        NotificationMediaMiuiStyle.restore(api, holder)
        NotificationMediaPixelStyle.restore(api, holder)
        api.getSeamlessContainer(holder)?.let { container ->
            colorOsAppIconStates.remove(container)?.restore()
        }
        api.getAction4(holder)?.let(colorOsDeviceSwitchSources::remove)
        MediaCoverRotationController.detach(api.getAlbumImage(holder))
    }

    private fun applyCustomActionVisibility(button: ImageButton) {
        if (button.isColorOsDeviceSwitch()) {
            button.visibility = View.VISIBLE
            return
        }
        if (button.isCustomActionSlot()) button.visibility = View.INVISIBLE
    }

    private fun ImageButton.isColorOsDeviceSwitch(): Boolean {
        return runCatching {
            runtimeConfig.notification.layoutStyle ==
                    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS &&
                    !runtimeConfig.notification.hideDeviceSwitch &&
                    resources.getResourceEntryName(id) == "action4" &&
                    colorOsDeviceSwitchSources.containsKey(this)
        }.getOrDefault(false)
    }

    private fun ImageButton.isCustomActionSlot(): Boolean {
        return runCatching {
            val name = resources.getResourceEntryName(id)
            name == "action0" || name == "action4"
        }.getOrDefault(false)
    }

    private fun applyLoadedLayout(api: NotificationMediaHostApi, controller: Any) {
        val config = runtimeConfig.notification
        val normalLayout = api.getNormalLayout(controller) ?: return
        val context = api.getLayoutContext(controller)
        NotificationMediaLayoutController.apply(
            bridge = api,
            normalLayout = normalLayout,
            normalAlbumLayout = api.getNormalAlbumLayout(controller),
            ids = NotificationMediaLayoutResourceIds.from(context),
            context = context,
            layoutStyle = config.layoutStyle,
            coverStyle = config.coverStyle,
            hideSource = config.hideCoverSource,
            hideDevice = config.hideDeviceSwitch,
            hideCustomActions = config.hideCustomActions,
            hideTime = config.hideTime,
            actionsLeftAligned = config.actionAlignLeft,
            actionsOrder = config.actionOrder
        )
    }

    private data class ColorOsAppIconState(
        val container: ViewGroup,
        val sourceIcon: ImageView,
        val originalSourceVisibility: Int = sourceIcon.visibility,
        val originalContainerVisibility: Int = container.visibility,
        val originalContainerBackground: Drawable? = container.background,
        var appIconView: ImageView? = null
    ) {
        fun apply(drawable: Drawable) {
            val iconView = appIconView ?: ImageView(container.context).also { view ->
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                view.scaleType = ImageView.ScaleType.FIT_CENTER
                view.isClickable = false
                view.isFocusable = false
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                container.addView(view)
                appIconView = view
            }
            val copy = runCatching {
                drawable.constantState
                    ?.newDrawable(iconView.resources, iconView.context.theme)
                    ?.mutate()
            }.getOrNull() ?: drawable
            iconView.setImageDrawable(copy)
            iconView.imageTintList = null
            iconView.alpha = 1f
            iconView.visibility = View.VISIBLE
            container.background = null
            sourceIcon.visibility = View.GONE
            container.visibility = View.VISIBLE
        }

        fun restore() {
            appIconView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
            appIconView = null
            container.background = originalContainerBackground
            sourceIcon.visibility = originalSourceVisibility
            container.visibility = originalContainerVisibility
        }
    }

    internal fun shouldKeepExpandedInFullAod(): Boolean {
        if (!runtimeConfig.enabled) return false
        val notification = runtimeConfig.notification
        return notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS ||
                notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ||
                notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ||
                notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ||
                notification.layoutStyle ==
                RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL ||
                runtimeConfig.alwaysOnDisplay.disableMediaCardCollapsing
    }

    private fun resolveApi(classLoader: ClassLoader?): NotificationMediaHostApi? {
        classLoader ?: return null
        nativeApis[classLoader]?.let { return it }
        return runCatching { NotificationMediaHostApi.create(classLoader) }
            .onSuccess {
                nativeApis[classLoader] = it
                nativeUnavailableClassLoaders.remove(classLoader)
            }
            .onFailure {
                if (nativeUnavailableClassLoaders.add(classLoader)) {
                    HookLogger.w(TAG, "通知中心媒体卡片接口不可用: reason=${it.message}")
                }
            }
            .getOrNull()
    }
}
