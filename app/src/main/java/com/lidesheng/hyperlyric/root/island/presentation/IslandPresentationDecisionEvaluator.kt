package com.lidesheng.hyperlyric.root.island.presentation

import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils

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
        if (data == null) return IslandRenderPolicy.OwnerEvidence.Pending
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(data)
        if (mediaInfo != null) {
            return IslandRenderPolicy.OwnerEvidence.Media(mediaInfo.packageName)
        }
        return if (IslandProbeUtils.isMediaIsland(data)) {
            IslandRenderPolicy.OwnerEvidence.Pending
        } else {
            IslandRenderPolicy.OwnerEvidence.NotMedia
        }
    }

    fun isCurrentLyricOwner(mediaInfo: IslandProbeUtils.MediaIslandInfo): Boolean {
        val lyricPackageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf(String::isNotEmpty)
            ?: return false
        return mediaInfo.packageName == lyricPackageName
    }

    /**
     * Long press remains bound to the current lyric island while playback is paused. The
     * presentation policy may suppress injected views after a pause, but it must not make the
     * still-visible native island lose its drag-share or playback-toggle action.
     */
    fun isCurrentLyricLongPressTarget(data: Any?): Boolean {
        if (!IslandProbeUtils.isSuperIslandEnabled()) return false
        if (!LyriconDataBridge.hasLyricsForPresentation()) return false
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(data) ?: return false
        return isCurrentLyricOwner(mediaInfo)
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
