package com.lidesheng.hyperlyric.root.island.hooks

import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport
import io.github.libxposed.api.XposedModule

/**
 * Super Island hook installer.
 *
 * Behavior lives in small hooker groups so the verified real-island, fake-view,
 * adapter/module, and width paths can be reviewed independently.
 */
internal object IslandTextHooker {

    private const val TAG = IslandTextHookerSupport.TAG
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val FAKE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val EXPANDED_VIEW_CLASS =
        "miui.systemui.dynamicisland.view.DynamicIslandExpandedView"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val TEMPLATE_BUILDER_CLASS =
        "miui.systemui.dynamicisland.template.IslandTemplateBuilder"
    private const val ADAPTER_CLASS =
        "miui.systemui.dynamicisland.module.IslandModuleViewHolderAdapter"

    fun hook(module: XposedModule, cl: ClassLoader) {
        installFeature("超级岛长按行为") {
            IslandLyricShareHooker.hook(module, cl)
        }

        installFeature("解除超级岛长度限制") {
            IslandWidthLimitHooker.hook(module, cl)
        }

        installFeature("真实岛") {
            val contentViewClass = cl.loadClass(CONTENT_VIEW_CLASS)

            contentViewClass.methods.filter { it.name == "updateBigIslandView" }.forEach { method ->
                module.deoptimize(method)
                module.hook(method).intercept(RealIslandHooker.UpdateBigIslandViewHook())
            }

            contentViewClass.methods
                .filter { it.name == "hideIslandLayout" || it.name == "showIslandLayout" }
                .filter { it.parameterTypes.isEmpty() }
                .forEach { method ->
                    module.deoptimize(method)
                    module.hook(method)
                        .intercept(RealIslandHooker.LayoutVisibilityHook(method.name))
                }

        }

        installFeature("fake view 过渡") {
            val fakeViewClass = cl.loadClass(FAKE_CONTENT_VIEW_CLASS)
            fakeViewClass.methods
                .filter {
                    it.name == "setVisibility" &&
                            it.parameterTypes.size == 1 &&
                            it.declaringClass.name == FAKE_CONTENT_VIEW_CLASS
                }
                .forEach { method ->
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.VisibilityHook()
                    )
                }

        }

        installFeature("应用返回 fake view") {
            val animationDelegateClass = cl.loadClass(ANIMATION_DELEGATE_CLASS)
            animationDelegateClass.declaredMethods
                .filter {
                    it.name == "fakeViewToBigIsland" &&
                            it.parameterTypes.size == 2 &&
                            it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.AppReturnToBigIslandHook()
                    )
                }

            animationDelegateClass.declaredMethods
                .filter {
                    it.name == "fakeViewToExpanded" &&
                            it.parameterTypes.size == 2 &&
                            it.parameterTypes[0].name == CONTENT_VIEW_CLASS &&
                            it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.ExpandedViewTransitionHook()
                    )
                }

            cl.loadClass(EVENT_COORDINATOR_CLASS).declaredMethods
                .filter {
                    it.name == "updateFreeformFakeView" &&
                            it.parameterTypes.size == 3 &&
                            it.parameterTypes[0].name == FAKE_CONTENT_VIEW_CLASS &&
                            it.parameterTypes[1].name == CONTENT_VIEW_CLASS
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(
                        FakeIslandTransitionHooker.FreeformFakeViewCallbackHook()
                    )
                }

        }

        installFeature("模块首次绑定") {
            val adapterClass = cl.loadClass(ADAPTER_CLASS)

            adapterClass.declaredMethods
                .filter {
                    it.name == "bindData" &&
                            it.parameterTypes.size == 2 &&
                            it.parameterTypes[0] == String::class.java
                }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.AdapterBindDataHook())
                }
        }

        installFeature("模块恢复") {
            cl.loadClass(TEMPLATE_BUILDER_CLASS).declaredMethods
                .filter { it.name == "updateModuleView" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.UpdateModuleViewHook())
                }

            cl.loadClass(ADAPTER_CLASS).declaredMethods
                .filter { it.name == "updateView" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    method.isAccessible = true
                    module.deoptimize(method)
                    module.hook(method).intercept(IslandModuleRestoreHooker.AdapterUpdateViewHook())
                }
        }
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
