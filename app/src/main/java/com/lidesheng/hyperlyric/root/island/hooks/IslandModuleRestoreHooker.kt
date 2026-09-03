package com.lidesheng.hyperlyric.root.island.hooks

import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport.TAG
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

internal object IslandModuleRestoreHooker {

    /**
     * The first big-island build is asynchronous on newer SystemUI versions.
     * bindData is the first synchronous point after a module holder has been
     * created, added to the template, initialized, and bound. The same adapter
     * lifecycle is used by both the real and fake template trees.
     */
    class AdapterBindDataHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()

            runCatching {
                val moduleType = chain.args.getOrNull(0) as? String
                val data = chain.args.getOrNull(1)
                val holderRoot = IslandProbeUtils.getHolderRootView(
                    IslandProbeUtils.getHolder(chain.thisObject, moduleType)
                ) ?: return@runCatching
                val isFake = IslandProbeUtils.isFakeBigIslandModuleArea(holderRoot)
                if (!isFake && !IslandProbeUtils.isRealBigIslandModuleArea(holderRoot)) {
                    return@runCatching
                }

                IslandPresentationCoordinator.onModuleBound(
                    holderRoot = holderRoot,
                    moduleType = moduleType,
                    owner = IslandPresentationCoordinator.ownerEvidence(data),
                    isFake = isFake
                )
            }.onFailure { e ->
                HookLogger.e(TAG, "adapter.bindData 后准备歌词视图失败", e)
            }

            return result
        }
    }

    class AdapterUpdateViewHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()

            runCatching {
                val moduleType = chain.args.getOrNull(0) as? String
                val data = chain.args.getOrNull(2)
                val holderRoot = IslandProbeUtils.getHolderRootView(
                    IslandProbeUtils.getHolder(chain.thisObject, moduleType)
                ) ?: return@runCatching
                val isFake = IslandProbeUtils.isFakeBigIslandModuleArea(holderRoot)
                if (!isFake && !IslandProbeUtils.isRealBigIslandModuleArea(holderRoot)) {
                    return@runCatching
                }
                IslandPresentationCoordinator.onModuleUpdated(
                    holderRoot = holderRoot,
                    moduleType = moduleType,
                    owner = IslandPresentationCoordinator.ownerEvidence(data),
                    isFake = isFake
                )
            }.onFailure { e ->
                HookLogger.e(TAG, "adapter.updateView 后恢复歌词视图失败", e)
            }

            return result
        }
    }

}
