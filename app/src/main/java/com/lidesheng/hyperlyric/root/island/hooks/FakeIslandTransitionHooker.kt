package com.lidesheng.hyperlyric.root.island.hooks

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport.TAG
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

internal object FakeIslandTransitionHooker {
    class FreeformFakeViewCallbackHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val fakeView = chain.args.firstOrNull() as? ViewGroup
                    ?: return@runCatching
                if (
                    IslandTextHookerSupport.callNoArgMethodResult(
                        fakeView,
                        "getClosingAppFromFreeform"
                    ) == true
                ) {
                    IslandExpandedMediaAmbientFlowHooker.resetMiniWindowBackgroundTransform()
                }
            }.onFailure { error ->
                HookLogger.e(TAG, "自由小窗过渡回调处理失败", error)
            }
            return chain.proceed()
        }
    }

    class VisibilityHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val visibility = (chain.args.getOrNull(0) as? Number)?.toInt()
            val fakeView = chain.thisObject as? ViewGroup
            if (fakeView != null && visibility == View.VISIBLE) {
                runCatching {
                    IslandExpandedMediaAmbientFlowHooker.applyFakeTransitionTheme(fakeView)
                }.onFailure { error ->
                    HookLogger.e(TAG, "FakeView 显示前应用媒体背景主题失败", error)
                }
            }
            val result = chain.proceed()

            if (fakeView != null && visibility == View.VISIBLE) {
                fakeView.postOnAnimation {
                    runCatching {
                        IslandExpandedMediaAmbientFlowHooker.applyFakeTransitionTheme(fakeView)
                    }.onFailure { error ->
                        HookLogger.e(TAG, "FakeView 显示后应用媒体背景主题失败", error)
                    }
                }
            }

            return result
        }
    }

    class ExpandedViewTransitionHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val realView = chain.args.firstOrNull() as? View
                    ?: return@runCatching
                val fakeView = IslandTextHookerSupport.callNoArgMethodResult(
                    realView,
                    "getFakeView"
                ) as? ViewGroup ?: return@runCatching
                IslandExpandedMediaAmbientFlowHooker.applyFakeTransitionTheme(fakeView)
            }.onFailure { error ->
                HookLogger.e(TAG, "FakeView 展开后应用媒体背景主题失败", error)
            }
            return result
        }
    }
}
