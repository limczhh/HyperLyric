package com.lidesheng.hyperlyric.root.island.hooks

import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.content.IslandMetadataContentAssembler
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * Supplies drag-share data only for the current lyric long-press target. Xiaomi still owns the
 * drag card, Intent and ClipData creation; this hook only supplies the missing template data for
 * the current long-press invocation.
 */
internal object IslandLyricShareHooker {
    private const val TAG = "IslandDragShareHooker"
    private const val CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentViewController"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val DATA_CLASS =
        "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"
    private const val SHARE_DATA_CLASS =
        "miui.systemui.dynamicisland.model.ShareData"

    fun hook(module: XposedModule, cl: ClassLoader) {
        val controllerClass = cl.loadClass(CONTROLLER_CLASS)
        val method = controllerClass.declaredMethods.firstOrNull {
            it.name == "onLongPressed" &&
                    it.parameterTypes.size == 3 &&
                    it.parameterTypes[0].name == BASE_CONTENT_VIEW_CLASS &&
                    it.parameterTypes[1].name == DATA_CLASS &&
                    it.parameterTypes[2] == Float::class.javaPrimitiveType
        }
        if (method == null) {
            HookLogger.w(TAG, "未找到长按回调，跳过拖拽分享 Hook")
            return
        }

        method.isAccessible = true
        module.deoptimize(method)
        module.hook(method).intercept(LongPressedHook())
        HookLogger.i(TAG, "拖拽分享 Hook 已初始化")
    }

    internal class LongPressedHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.args.getOrNull(0) ?: return chain.proceed()
            val data = chain.args.getOrNull(1)
            val shouldHandle = runCatching {
                isEnabled() && IslandPresentationCoordinator.isCurrentLyricLongPressTarget(data)
            }.onFailure { error ->
                HookLogger.w(TAG, "判断拖拽分享目标失败，保留原生行为", error)
            }.getOrDefault(false)
            if (!shouldHandle) {
                return chain.proceed()
            }

            val systemUiView = view as? View ?: return chain.proceed()
            val payload = IslandLyricSharePayloadBuilder.build(
                view = systemUiView,
                prefs = HookEntry.instance?.prefs ?: return chain.proceed()
            )
                ?: return chain.proceed()
            val replacement = runCatching {
                TemplateShareDataAdapter.apply(view, payload)
            }.onFailure { error ->
                HookLogger.w(TAG, "准备拖拽分享数据失败", error)
            }.getOrNull() ?: return chain.proceed()

            HookLogger.d(
                TAG,
                "长按补充拖拽分享: title=${payload.title.hashCode()}, " +
                        "contentLength=${payload.shareContent.length}"
            )
            return try {
                chain.proceed()
            } finally {
                replacement.restore()
            }
        }

        private fun isEnabled(): Boolean {
            return runCatching {
                HookEntry.instance?.prefs?.getInt(
                    RootConstants.KEY_HOOK_ISLAND_LONG_PRESS_BEHAVIOR,
                    RootConstants.DEFAULT_HOOK_ISLAND_LONG_PRESS_BEHAVIOR
                ) == RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_LYRIC_SHARE
            }.getOrDefault(false)
        }
    }

    private object TemplateShareDataAdapter {
        private const val COPY_PARAMETER_COUNT = 14

        fun apply(view: Any, payload: IslandLyricSharePayload): AppliedTemplate? {
            val template = invokeGetter(view, "getTemplate") ?: return null
            val existingShareData = invokeGetter(template, "getShareData")
            val existingShareContent = invokeGetter(existingShareData, "getShareContent") as? String
            if (!existingShareContent.isNullOrBlank()) return null

            val shareDataClass = Class.forName(
                SHARE_DATA_CLASS,
                true,
                view.javaClass.classLoader
            )
            val shareData = shareDataClass.getDeclaredConstructor().apply {
                isAccessible = true
            }.newInstance()
            setString(shareData, "setTitle", payload.title)
            setString(shareData, "setContent", payload.content)
            setString(shareData, "setShareContent", payload.shareContent)

            val copyMethod = template.javaClass.methods.firstOrNull {
                it.name == "copy" &&
                        it.parameterTypes.size == COPY_PARAMETER_COUNT &&
                        it.parameterTypes[2].name == SHARE_DATA_CLASS
            } ?: return null
            copyMethod.isAccessible = true
            val copiedTemplate = copyMethod.invoke(
                template,
                invokeGetter(template, "getBigIslandArea"),
                invokeGetter(template, "getSmallIslandArea"),
                shareData,
                invokeGetter(template, "getBusiness"),
                invokeRequiredGetter(template, "getDismissIsland"),
                invokeRequiredGetter(template, "getIslandTimeout"),
                invokeGetter(template, "getHighlightColor"),
                invokeGetter(template, "getIslandProperty"),
                invokeGetter(template, "getIslandPriority"),
                invokeRequiredGetter(template, "getIslandOrder"),
                invokeGetter(template, "getNeedCloseAnimation"),
                invokeRequiredGetter(template, "getExpandedTime"),
                invokeGetter(template, "getMaxSize"),
                invokeGetter(template, "getAppContentDescription")
            ) ?: return null

            val setTemplate = view.javaClass.methods.firstOrNull {
                it.name == "setTemplate" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0].name == template.javaClass.name
            } ?: return null
            setTemplate.isAccessible = true
            setTemplate.invoke(view, copiedTemplate)
            return AppliedTemplate(view, setTemplate, template)
        }

        private fun invokeGetter(receiver: Any?, name: String): Any? {
            receiver ?: return null
            val method = receiver.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.isEmpty()
            } ?: return null
            method.isAccessible = true
            return method.invoke(receiver)
        }

        private fun invokeRequiredGetter(receiver: Any, name: String): Any {
            return invokeGetter(receiver, name)
                ?: throw NoSuchMethodException("$name on ${receiver.javaClass.name}")
        }

        private fun setString(receiver: Any, name: String, value: String) {
            val method = receiver.javaClass.methods.firstOrNull {
                it.name == name &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == String::class.java
            } ?: throw NoSuchMethodException("$name on ${receiver.javaClass.name}")
            method.isAccessible = true
            method.invoke(receiver, value)
        }

        class AppliedTemplate(
            private val view: Any,
            private val setTemplate: Method,
            private val originalTemplate: Any
        ) {
            fun restore() {
                runCatching {
                    setTemplate.invoke(view, originalTemplate)
                }.onFailure { error ->
                    HookLogger.w(TAG, "恢复原生超级岛模板失败", error)
                }
            }
        }
    }
}
