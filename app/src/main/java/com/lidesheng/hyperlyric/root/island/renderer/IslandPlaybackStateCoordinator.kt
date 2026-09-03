package com.lidesheng.hyperlyric.root.island.renderer

import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowController
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandReconcileReason
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController
import com.lidesheng.hyperlyric.root.utils.HookLogger

/** Applies one playback policy to every registered Real/Fake projection. */
internal object IslandPlaybackStateCoordinator {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clearedByPause = false

    fun markClearedByPause() {
        clearedByPause = true
    }

    fun onPlaybackStateChanged(
        isPlaying: Boolean,
        onRefreshRequested: () -> Unit,
        onClearRequested: () -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                onPlaybackStateChanged(isPlaying, onRefreshRequested, onClearRequested)
            }
            return
        }
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        ) {
            HookLogger.dState(
                stateId = "IslandPlaybackStateCoordinator.policy",
                tag = "IslandPlaybackStateCoordinator",
                state = "disabled|$isPlaying"
            ) {
                "播放策略未执行: reason=super_island_disabled, isPlaying=$isPlaying"
            }
            onClearRequested()
            return
        }

        val stateChanged = IslandPresentationCoordinator.updatePlaybackState(isPlaying)
        if (stateChanged) {
            IslandProgressGlowController.onPlaybackStateChanged(isPlaying)
        }
        val behavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )
        val wasClearedByPause = clearedByPause

        when {
            isPlaying && clearedByPause -> {
                clearedByPause = false
                restoreActiveViewsAfterPause(onRefreshRequested)
            }

            isPlaying && stateChanged -> applyPlaybackStateToActiveViews(true)

            !isPlaying && behavior == 0 && !clearedByPause -> {
                applyPlaybackStateToActiveViews(false)
                clearActiveViewsForPauseInternal()
            }

            !isPlaying && stateChanged -> applyPlaybackStateToActiveViews(false)
        }

        val action = when {
            isPlaying && wasClearedByPause -> "restore_after_pause"
            isPlaying && stateChanged -> "resume_active_views"
            !isPlaying && behavior == 0 && !wasClearedByPause -> "clear_on_pause"
            !isPlaying && stateChanged -> "pause_active_views"
            else -> "no_change"
        }
        HookLogger.dState(
            stateId = "IslandPlaybackStateCoordinator.policy",
            tag = "IslandPlaybackStateCoordinator",
            state = "$isPlaying|$behavior|$stateChanged|$clearedByPause|$action"
        ) {
            "播放策略已处理: isPlaying=$isPlaying, pauseBehavior=$behavior, " +
                    "stateChanged=$stateChanged, clearedByPause=$clearedByPause, action=$action"
        }
    }

    private fun restoreActiveViewsAfterPause(onRefreshRequested: () -> Unit) {
        val lyricPackage = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val lyricVersion = LyriconDataBridge.versionCounter.get()
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()
        val hosts = IslandPresentationCoordinator.snapshotAttachedHosts(lyricPackage)
        if (hosts.isEmpty()) {
            HookLogger.dState(
                stateId = "IslandPlaybackStateCoordinator.restore",
                tag = "IslandPlaybackStateCoordinator",
                state = "no_hosts"
            ) {
                "暂停恢复未找到宿主: reason=no_attached_hosts, package=$lyricPackage"
            }
            onRefreshRequested()
            return
        }

        hosts.forEach { token ->
            if (!IslandPresentationCoordinator.shouldRenderInjectedIsland() ||
                !IslandPresentationCoordinator.isCurrentHost(token) ||
                !IslandPresentationCoordinator.isCurrentPresentation(presentationRevision) ||
                LyriconDataBridge.versionCounter.get() != lyricVersion ||
                LyriconDataBridge.currentLyricPackageName != lyricPackage
            ) return@forEach

            val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                token = token,
                reason = IslandReconcileReason.PLAYBACK_RESUME,
                expectedPresentationRevision = presentationRevision
            )
            if (!result.isTarget) return@forEach

            val currentPrefs = HookEntry.instance?.prefs ?: return@forEach
            val config = IslandSlotRuntimeConfig.from(currentPrefs)
            val expectsInjectedView =
                config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE ||
                        config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE
            if (expectsInjectedView && result.mutation.injectedSlotsPresent == false) {
                onRefreshRequested()
                return@forEach
            }

            IslandContentUpdateCoordinator.updateContentForView(
                view = token.root,
                packageName = lyricPackage,
                prefs = currentPrefs,
                config = config,
                playbackActive = true,
                hostKind = token.kind
            )
        }
    }

    fun clearActiveViewsForPause() {
        if (clearedByPause) return
        applyPlaybackStateToActiveViews(false)
        clearActiveViewsForPauseInternal()
    }

    fun deactivateAllHosts() {
        IslandPresentationCoordinator.snapshotAttachedInjectedHosts().forEach { snapshot ->
            IslandLyricViewController.setPlaybackActiveRecursively(snapshot.host.root, false)
        }
    }

    private fun clearActiveViewsForPauseInternal() {
        val lyricPackage = LyriconDataBridge.currentLyricPackageName
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()
        IslandPresentationCoordinator.snapshotAttachedHosts()
            .filter { token -> lyricPackage == null || token.packageName == lyricPackage }
            .forEach { token ->
                IslandPresentationCoordinator.clearRegisteredHostIfSuppressed(
                    token,
                    presentationRevision
                )
            }
        clearedByPause = true
    }

    private fun applyPlaybackStateToActiveViews(isPlaying: Boolean) {
        val lyricPackage = LyriconDataBridge.currentLyricPackageName
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()
        val playbackClock = LyriconDataBridge.currentPlaybackClock()
        IslandPresentationCoordinator.snapshotAttachedInjectedHosts(lyricPackage)
            .forEach { snapshot ->
                val token = snapshot.host
                val currentPackage = LyriconDataBridge.currentLyricPackageName
                if (!IslandPresentationCoordinator.isCurrentHost(token) ||
                    !IslandPresentationCoordinator.isCurrentPresentation(presentationRevision) ||
                    IslandPresentationCoordinator.isPlaybackActive() != isPlaying ||
                    (currentPackage != null && currentPackage != token.packageName)
                ) return@forEach

                if (snapshot.injectedViews.isEmpty()) {
                    IslandLyricViewController.applyPlaybackSnapshotRecursively(
                        token.root,
                        playbackClock.positionMs,
                        playbackClock.playbackSpeed,
                        isPlaying
                    )
                    IslandPresentationCoordinator.refreshInjectedViewIndex(token)
                } else {
                    snapshot.injectedViews.forEach { view ->
                        IslandLyricViewController.applyPlaybackSnapshot(
                            view,
                            playbackClock.positionMs,
                            playbackClock.playbackSpeed,
                            isPlaying
                        )
                    }
                }
            }
    }
}
