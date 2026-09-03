package com.lidesheng.hyperlyric.root.island.hooks

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport.TAG
import com.lidesheng.hyperlyric.root.island.presentation.IslandNativeRefreshCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandRenderPolicy
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker

internal object RealIslandHooker {

    class UpdateBigIslandViewHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            var owner: IslandRenderPolicy.OwnerEvidence? = null
            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                val data = chain.args.getOrNull(0)
                owner = IslandPresentationCoordinator.ownerEvidence(data)
                val mediaOwner = owner as? IslandRenderPolicy.OwnerEvidence.Media
                if (mediaOwner != null) {
                    IslandPresentationCoordinator.onRealBeforeSystemUpdate(
                        contentView,
                        mediaOwner
                    )
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "预恢复歌词视图失败", e)
            }

            val result = chain.proceed()
            // The old two-argument API returns Boolean directly. In the newer suspend API,
            // the extra continuation argument returns a non-Boolean marker while creation waits.
            if (chain.args.size >= 3 && result !is Boolean) return result

            runCatching {
                val contentView = chain.thisObject as? ViewGroup ?: return@runCatching
                val argumentData = chain.args.getOrNull(0)
                // Newer SystemUI versions resume this suspend method by invoking it again with
                // a null data argument. Resolve the data held by the content view before treating
                // the call as a real island clear.
                val argumentOwner = owner
                    ?: IslandPresentationCoordinator.ownerEvidence(argumentData)
                val fallbackData = if (
                    argumentData == null ||
                    argumentOwner == IslandRenderPolicy.OwnerEvidence.Pending
                ) {
                    IslandTextHookerSupport.extractIslandDataFromContentOrReal(contentView)
                } else {
                    null
                }
                val fallbackOwner = fallbackData?.let(
                    IslandPresentationCoordinator::ownerEvidence
                )
                val resolvedOwner = when {
                    argumentOwner is IslandRenderPolicy.OwnerEvidence.Media -> argumentOwner
                    fallbackOwner is IslandRenderPolicy.OwnerEvidence.Media -> fallbackOwner
                    argumentData != null -> argumentOwner
                    fallbackData != null -> fallbackOwner
                        ?: IslandRenderPolicy.OwnerEvidence.Pending

                    else -> IslandRenderPolicy.OwnerEvidence.NotMedia
                }
                IslandPresentationCoordinator.onRealSystemUpdateComplete(
                    root = contentView,
                    owner = resolvedOwner
                )
                IslandNativeRefreshCoordinator.onSystemUpdateComplete(contentView)
            }.onFailure { e ->
                HookLogger.e(TAG, "注入歌词视图失败", e)
            }

            return result
        }
    }
}
