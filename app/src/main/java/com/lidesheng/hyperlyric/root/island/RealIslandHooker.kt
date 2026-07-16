package com.lidesheng.hyperlyric.root.island

import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.IslandTextHookerSupport.TAG
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

internal object RealIslandHooker {

    class UpdateBigIslandViewHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            var mediaInfo: IslandProbeUtils.MediaIslandInfo? = null
            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                val data = chain.args.getOrNull(0)
                if (IslandProbeUtils.isSuperIslandEnabled()) {
                    mediaInfo = IslandProbeUtils.extractMediaIslandInfo(data)
                    if (mediaInfo?.let(IslandTextHookerSupport::isCurrentLyricIsland) == true) {
                        if (!IslandTextHookerSupport.shouldRenderInjectedIsland(contentView, mediaInfo)) {
                            IslandTextHookerSupport.hardClearInjectedIsland(contentView)
                            return@runCatching
                        }
                        if (IslandLyricTextInjector.restoreExistingSlotsLightweight(contentView)) {
                            IslandLyricTextInjector.refreshCurrentContent(contentView)
                            IslandHostFacade.triggerSystemRelayout(contentView)
                HookLogger.d(TAG, "updateBigIslandView 前已轻量恢复歌词视图并重新布局")
                        }
                    }
                }
            }.onFailure { e ->
            HookLogger.e(TAG, "预恢复歌词视图失败", e)
            }

            val result = chain.proceed()

            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                val prefs = HookEntry.instance?.prefs ?: return@runCatching
                if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
                    return@runCatching
                }

                val data = chain.args.getOrNull(0)
                val info = mediaInfo ?: IslandProbeUtils.extractMediaIslandInfo(data)

                if (info == null) {
                    IslandTextHookerSupport.hardClearInjectedIsland(contentView)
                    return@runCatching
                }

                if (!IslandTextHookerSupport.isCurrentLyricIsland(info)) {
                    IslandTextHookerSupport.clearOnlyWhenPackageIsDefinitelyDifferent(contentView, info)
                    return@runCatching
                }

                IslandViewRegistry.register(contentView, info.packageName)
                if (!IslandTextHookerSupport.shouldRenderInjectedIsland(contentView, info)) {
                    IslandTextHookerSupport.hardClearInjectedIsland(contentView)
                    return@runCatching
                }

                if (IslandLyricTextInjector.injectSlots(contentView, reconfigureExisting = false)) {
                    IslandViewHelper.triggerSystemRelayout(contentView)
                }
                IslandLyricTextInjector.refreshCurrentContent(contentView)

                IslandHostFacade.injectHostGlow(contentView, data, prefs)
            }.onFailure { e ->
            HookLogger.e(TAG, "注入歌词视图失败", e)
            }

            return result
        }
    }

    class LayoutVisibilityHook(
        private val eventName: String
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()

            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                if (!IslandProbeUtils.isSuperIslandEnabled()) return@runCatching
                val currentData = IslandProbeUtils.getCurrentIslandData(contentView)
                val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(currentData) ?: return@runCatching

                if (!IslandTextHookerSupport.isCurrentLyricIsland(mediaInfo)) {
                    IslandTextHookerSupport.clearOnlyWhenPackageIsDefinitelyDifferent(contentView, mediaInfo)
                    return@runCatching
                }
                if (!IslandTextHookerSupport.shouldRenderInjectedIsland(contentView, mediaInfo)) {
                    IslandTextHookerSupport.hardClearInjectedIsland(contentView)
                    return@runCatching
                }

                if (IslandLyricTextInjector.restoreExistingSlotsLightweight(contentView)) {
                    IslandLyricTextInjector.refreshCurrentContent(contentView)
                    IslandHostFacade.triggerSystemRelayout(contentView)
                HookLogger.d(TAG, "$eventName 后已轻量恢复歌词视图并重新布局")
                }
            }.onFailure { e ->
            HookLogger.e(TAG, "$eventName 后恢复歌词视图失败", e)
            }

            return result
        }
    }
}
