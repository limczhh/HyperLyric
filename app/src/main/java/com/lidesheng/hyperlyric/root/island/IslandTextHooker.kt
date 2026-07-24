package com.lidesheng.hyperlyric.root.island

import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule

/**
 * Super Island hook installer.
 *
 * Behavior lives in small hooker groups so the verified real-island, fake-view,
 * adapter/module, and width paths can be reviewed independently.
 */
internal object IslandTextHooker {

    private const val TAG = IslandTextHookerSupport.TAG
    private const val CONTENT_VIEW_CLASS = "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val FAKE_CONTENT_VIEW_CLASS = "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val EXPANDED_VIEW_CLASS =
        "miui.systemui.dynamicisland.view.DynamicIslandExpandedView"
    private const val TEMPLATE_BUILDER_CLASS = "miui.systemui.dynamicisland.template.IslandTemplateBuilder"
    private const val ADAPTER_CLASS = "miui.systemui.dynamicisland.module.IslandModuleViewHolderAdapter"

    fun hook(module: XposedModule, cl: ClassLoader, includeMediaHooks: Boolean = true) {
        installFeature("真实岛") {
            val contentViewClass = cl.loadClass(CONTENT_VIEW_CLASS)

            contentViewClass.methods.filter { it.name == "updateBigIslandView" }.forEach { method ->
                module.deoptimize(method)
                module.hook(method).intercept(RealIslandHooker.UpdateBigIslandViewHook())
                HookLogger.d(TAG, "已 Hook updateBigIslandView: $method")
            }

            contentViewClass.methods
                .filter { it.name == "hideIslandLayout" || it.name == "showIslandLayout" }
                .filter { it.parameterTypes.isEmpty() }
                .forEach { method ->
                    module.deoptimize(method)
                    module.hook(method).intercept(RealIslandHooker.LayoutVisibilityHook(method.name))
                    HookLogger.d(TAG, "已 Hook ${method.name}: $method")
                }

        }

        installFeature("fake view 过渡") {
            val fakeViewClass = cl.loadClass(FAKE_CONTENT_VIEW_CLASS)
            fakeViewClass.declaredMethods
                .filter { it.name == "onTrackingFakeViewStart" && it.parameterTypes.isEmpty() }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(FakeIslandTransitionHooker.TrackingStartHook())
                    HookLogger.d(TAG, "已 Hook fake.onTrackingFakeViewStart: $method")
                }

            fakeViewClass.methods
                .filter { it.name == "updateViewStateWhenOpenAnimStart" && it.parameterTypes.isEmpty() }
                .forEach { method ->
                    module.deoptimize(method)
                    module.hook(method).intercept(FakeIslandTransitionHooker.PrepareVisibleHook())
                    HookLogger.d(TAG, "已 Hook fake.updateViewStateWhenOpenAnimStart: $method")
                }

            fakeViewClass.methods
                .filter {
                    it.name == "setVisibility" &&
                        it.parameterTypes.size == 1 &&
                        it.declaringClass.name == FAKE_CONTENT_VIEW_CLASS
                }
                .forEach { method ->
                    module.deoptimize(method)
                    module.hook(method).intercept(FakeIslandTransitionHooker.VisibilityHook())
                    HookLogger.d(TAG, "已 Hook fake.setVisibility: $method")
                }

        }

        installFeature("模块恢复") {
            if (includeMediaHooks) {
                installMiniBarHook(module, cl)
            }
            cl.loadClass(TEMPLATE_BUILDER_CLASS).declaredMethods
                .filter { it.name == "updateModuleView" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.UpdateModuleViewHook())
                    HookLogger.d(TAG, "已 Hook updateModuleView: $method")
                }

            cl.loadClass(ADAPTER_CLASS).declaredMethods
                .filter { it.name == "updateView" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.AdapterUpdateViewHook())
                    HookLogger.d(TAG, "已 Hook adapter.updateView: $method")
                }
        }
    }

    internal fun installMiniBarHook(module: XposedModule, cl: ClassLoader) {
        try {
            val baseContentViewClass = cl.loadClass(BASE_CONTENT_VIEW_CLASS)
            baseContentViewClass.declaredMethods
                .filter { it.name == "updateMiniBar" && it.parameterTypes.size == 1 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        IslandExpandedMediaAmbientFlowHooker.MiniBarUpdateHook()
                    )
                    HookLogger.d(TAG, "已 Hook updateMiniBar: $method")
                }
            installBackgroundUpdateHook(module, cl)
            installExpandedVisibilityHook(module, cl)
            installClosingToExpandedHook(module, cl)
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "跳过不支持的媒体 MiniBar Hook: reason=${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "安装媒体 MiniBar Hook 失败", e)
        }
    }

    internal fun installBackgroundUpdateHook(module: XposedModule, cl: ClassLoader) {
        val method = cl.loadClass(BASE_CONTENT_VIEW_CLASS).declaredMethods.single {
            it.name == "updateBackgroundBg" &&
                it.parameterCount == 2 &&
                it.parameterTypes[0] == android.view.View::class.java &&
                it.parameterTypes[1] == java.lang.Boolean.TYPE
        }.apply { isAccessible = true }
        module.deoptimize(method)
        module.hook(method).intercept(
            IslandExpandedMediaAmbientFlowHooker.BackgroundUpdateHook()
        )
        HookLogger.d(TAG, "安装展开态背景 Hook: method=$method")
    }

    internal fun installExpandedVisibilityHook(module: XposedModule, cl: ClassLoader) {
        val method = cl.loadClass(EXPANDED_VIEW_CLASS).getDeclaredMethod(
            "onVisibilityChanged",
            android.view.View::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        module.deoptimize(method)
        module.hook(method).intercept(
            IslandExpandedMediaAmbientFlowHooker.ExpandedVisibilityHook()
        )
        HookLogger.d(TAG, "安装展开态可见性 Hook: method=$method")
    }

    internal fun installClosingToExpandedHook(module: XposedModule, cl: ClassLoader) {
        val method = cl.loadClass(FAKE_CONTENT_VIEW_CLASS).getDeclaredMethod(
            "setClosingToExpanded",
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        module.deoptimize(method)
        module.hook(method).intercept(
            IslandExpandedMediaAmbientFlowHooker.ClosingToExpandedHook()
        )
        HookLogger.d(TAG, "安装过渡视图 Hook: method=$method")
    }

    private inline fun installFeature(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "跳过不支持的 $name Hook: reason=${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "安装 $name Hook 失败", e)
        }
    }
}
