package com.lidesheng.hyperlyric.root.island

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import com.lidesheng.hyperlyric.root.utils.HookLogger

internal object IslandTextHookerSupport {
    const val TAG = "IslandTextHooker"

    fun extractMediaInfoFromContentOrReal(contentView: ViewGroup): IslandProbeUtils.MediaIslandInfo? {
        val currentData = IslandProbeUtils.getCurrentIslandData(contentView)
        val currentInfo = IslandProbeUtils.extractMediaIslandInfo(currentData)
        if (currentInfo != null) return currentInfo

        val realView = callNoArgMethodResult(contentView, "getRealView")
        val realData = IslandProbeUtils.getCurrentIslandData(realView)
        return IslandProbeUtils.extractMediaIslandInfo(realData)
    }

    fun prepareFrozenFakeIslandForTransition(fakeView: ViewGroup, source: String) {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return
        val mediaInfo = extractMediaInfoFromContentOrReal(fakeView) ?: return

        if (!isCurrentLyricIsland(mediaInfo)) {
            return
        }
        if (!shouldRenderInjectedIsland(fakeView, mediaInfo)) {
            IslandHostFacade.clearInjectedViews(fakeView)
            return
        }

        if (IslandLyricTextInjector.hasInjectedLyricText(fakeView)) {
            IslandLyricTextInjector.restoreExistingSlotsLightweight(fakeView)
        } else {
            IslandLyricTextInjector.injectSlots(fakeView, reconfigureExisting = false, suppressAnimation = true)
        }
        IslandLyricTextInjector.refreshCurrentContent(fakeView, includeLyricSlots = true, force = true, suppressAnimation = true)
        IslandLyricTextInjector.freezeInjectedLyricProgress(fakeView, LyriconDataBridge.currentPosition)
        fakeView.alpha = 1f
        HookLogger.d(TAG, "已准备过渡冻结 fake view: 来源=$source")
    }

    fun restoreRealIslandAfterFakeTransition(fakeView: ViewGroup, source: String) {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return
        val mediaInfo = extractMediaInfoFromContentOrReal(fakeView) ?: return
        if (!isCurrentLyricIsland(mediaInfo)) return
        if (!shouldRenderInjectedIsland(fakeView, mediaInfo)) return

        val realView = callNoArgMethodResult(fakeView, "getRealView") as? ViewGroup ?: return
        IslandViewRegistry.register(realView, mediaInfo.packageName)
        val changed = IslandLyricTextInjector.restoreExistingSlotsLightweight(realView)
        IslandLyricTextInjector.refreshCurrentContent(realView)
        realView.visibility = View.VISIBLE
        (callNoArgMethodResult(realView, "getBackgroundView") as? View)?.visibility = View.VISIBLE
        if (changed) {
            IslandHostFacade.triggerSystemRelayout(realView)
        }
        HookLogger.d(TAG, "fake view 过渡结束后已恢复真实岛: 来源=$source, 重新布局=$changed")
    }

    fun isCurrentLyricIsland(mediaInfo: IslandProbeUtils.MediaIslandInfo): Boolean {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        return !lyricPkg.isNullOrEmpty() && mediaInfo.packageName == lyricPkg
    }

    fun clearOnlyWhenPackageIsDefinitelyDifferent(viewGroup: ViewGroup, mediaInfo: IslandProbeUtils.MediaIslandInfo) {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        if (lyricPkg.isNullOrEmpty()) {
            HookLogger.d(TAG, "歌词包名暂时为空，保留已注入岛: 岛包名=${mediaInfo.packageName}")
            return
        }
        hardClearInjectedIsland(viewGroup)
    }

    fun shouldRenderInjectedIsland(
        viewGroup: ViewGroup?,
        mediaInfo: IslandProbeUtils.MediaIslandInfo?
    ): Boolean {
        if (viewGroup == null || mediaInfo == null) {
            return BaseIslandRenderer.shouldRenderInjectedIsland()
        }
        return BaseIslandRenderer.shouldRenderInjectedIsland(viewGroup.context, mediaInfo.packageName)
    }

    fun hardClearInjectedIsland(viewGroup: ViewGroup, suppressRelayout: Boolean = false) {
        IslandViewRegistry.unregister(viewGroup)
        IslandHostFacade.clearInjectedViews(viewGroup)
        if (!suppressRelayout) {
            IslandHostFacade.triggerSystemRelayout(viewGroup)
        }
    }

    fun restoreAdapterModule(adapter: Any?, moduleType: String?, source: String) {
        val holderRoot = IslandProbeUtils.getHolderRootView(
            IslandProbeUtils.getHolder(adapter, moduleType)
        ) ?: return

        if (IslandLyricTextInjector.restoreExistingModuleSlotLightweight(holderRoot, moduleType)) {
            IslandLyricTextInjector.refreshCurrentContent(holderRoot)
        HookLogger.d(TAG, "已轻量恢复歌词视图: 来源=$source，模块=$moduleType")
        }
    }

    fun callNoArgMethodResult(receiver: Any, name: String): Any? {
        return runCatching {
            receiver.javaClass.methods.find {
                it.name == name && it.parameterTypes.isEmpty()
            }?.invoke(receiver)
        }.getOrNull()
    }

    fun findFieldValue(receiver: Any?, name: String): Any? {
        val target = receiver ?: return null
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val field = current.declaredFields.find { it.name == name }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }
}
