package com.lidesheng.hyperlyric.root.island.presentation

import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.policy.IslandModificationTargetPolicy

/**
 * Builds the policy input from the current root-side presentation state.
 *
 * The policy itself remains pure in [IslandRenderPolicy]. This evaluator owns only the bridge
 * between live preferences/lyric state and that policy, keeping lifecycle reconciliation out of
 * the decision path.
 */
internal class IslandPresentationDecisionEvaluator(
    private val presentationState: IslandPresentationState
) {
    fun ownerEvidence(data: Any?): IslandRenderPolicy.OwnerEvidence {
        val target = IslandModificationTargetPolicy.resolve(data)
        return when (target.mediaState) {
            IslandModificationTargetPolicy.MediaState.MEDIA -> {
                IslandRenderPolicy.OwnerEvidence.Media(
                    target.mediaInfo?.packageName.orEmpty()
                )
            }

            IslandModificationTargetPolicy.MediaState.PENDING -> {
                IslandRenderPolicy.OwnerEvidence.Pending
            }

            IslandModificationTargetPolicy.MediaState.NOT_MEDIA -> {
                IslandRenderPolicy.OwnerEvidence.NotMedia
            }
        }
    }

    fun isCurrentLyricOwner(mediaInfo: IslandProbeUtils.MediaIslandInfo): Boolean {
        return IslandModificationTargetPolicy.isCurrentLyricOwner(mediaInfo)
    }

    /**
     * Long press remains bound to the current lyric island while playback is paused. The
     * presentation policy may suppress injected views after a pause, but it must not make the
     * still-visible native island lose its drag-share or playback-toggle action.
     */
    fun isCurrentLyricLongPressTarget(data: Any?): Boolean {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return false
        return IslandModificationTargetPolicy.isCurrentLyricPresentationTarget(data)
    }

    fun shouldRenderInjectedIsland(): Boolean {
        return IslandRenderPolicy.isPresentationAllowed(
            enabled = IslandProbeUtils.isSuperIslandEnabled(),
            playbackActive = presentationState.isPlaybackActive(),
            pauseBehavior = currentPauseBehavior()
        )
    }

    fun evaluate(owner: IslandRenderPolicy.OwnerEvidence): IslandRenderPolicy.Decision {
        return IslandRenderPolicy.evaluate(
            IslandRenderPolicy.Input(
                owner = owner,
                lyricPackageName = LyriconDataBridge.currentLyricPackageName,
                hasLyricsForPresentation = LyriconDataBridge.hasLyricsForPresentation(),
                enabled = IslandProbeUtils.isSuperIslandEnabled(),
                playbackActive = presentationState.isPlaybackActive(),
                pauseBehavior = currentPauseBehavior()
            )
        )
    }

    private fun currentPauseBehavior(): Int {
        return HookEntry.instance?.prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
    }
}
