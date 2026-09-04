package com.lidesheng.hyperlyric.root.island.renderer

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.hooks.IslandWidthLimitHooker
import com.lidesheng.hyperlyric.root.island.presentation.IslandNativeRefreshCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandReconcileReason

/**
 * Coordinates settings changes that affect Xiaomi's media-island layout or native artwork views.
 * Xiaomi owns the rebind/measure/layout step; HyperLyric only reapplies its content afterward.
 */
internal object IslandSettingsRefreshCoordinator {
    fun request() {
        val presentationRevision = IslandPresentationCoordinator.invalidatePresentation()
        IslandNativeRefreshCoordinator.request(
            onComplete = { root ->
                IslandMusicWaveColorHooker.refresh()
                refreshHyperLyricContentIfNeeded(root, presentationRevision)
            }
        )
    }

    private fun refreshHyperLyricContentIfNeeded(
        root: ViewGroup,
        presentationRevision: Long
    ) {
        if (!IslandPresentationCoordinator.isCurrentPresentation(presentationRevision)) return
        val packageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return
        IslandWidthLimitHooker.refresh(root)
        val lyricVersion = LyriconDataBridge.versionCounter.get()
        IslandPresentationCoordinator.snapshotAttachedHosts(packageName).forEach { token ->
            if (!IslandPresentationCoordinator.isCurrentHost(token) ||
                !IslandPresentationCoordinator.isCurrentPresentation(presentationRevision) ||
                LyriconDataBridge.versionCounter.get() != lyricVersion ||
                LyriconDataBridge.currentLyricPackageName != packageName
            ) return@forEach

            val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                token = token,
                reason = IslandReconcileReason.SETTINGS_CHANGED,
                expectedPresentationRevision = presentationRevision
            )
            if (!result.isTarget) return@forEach

            val prefs = HookEntry.instance?.prefs ?: return@forEach
            IslandContentUpdateCoordinator.updateContentForView(
                view = token.root,
                packageName = packageName,
                prefs = prefs,
                config = IslandSlotRuntimeConfig.from(prefs),
                playbackActive = IslandPresentationCoordinator.isPlaybackActive(),
                hostKind = token.kind
            )
        }
    }
}
