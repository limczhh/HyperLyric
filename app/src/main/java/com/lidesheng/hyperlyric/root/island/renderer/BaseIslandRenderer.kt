package com.lidesheng.hyperlyric.root.island.renderer

import android.os.Handler
import android.os.Looper
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.host.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandReconcileReason
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController
import java.util.concurrent.atomic.AtomicLong

/**
 * Projects one lyric/playback state into every attached Super Island presentation.
 *
 * Real and Fake hosts are deliberately handled by the same loops. Xiaomi owns which tree is
 * visible; a visibility handoff is therefore not a lyric lifecycle event and performs no copy,
 * freeze, seek, or recovery work.
 */
object BaseIslandRenderer : IslandRenderer {

    private const val REFRESH_DEBOUNCE_MS = 32L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { performRefreshActiveIsland() }
    private val metadataRefreshRunnable = Runnable { performUpdateMetadata() }
    private val textColorRefreshRunnable = Runnable { performUpdateTextColors() }
    private val positionDispatchGeneration = AtomicLong(0L)
    private val contentDispatchGeneration = AtomicLong(0L)

    /**
     * Source lifecycle events are the authority for lyric rendering state.
     * Hook paths must not re-query MediaSession here: during a lyric refresh the source can
     * already be stopped while the player session still reports STATE_PLAYING.
     */
    fun shouldRenderInjectedIsland(): Boolean {
        return IslandPresentationCoordinator.shouldRenderInjectedIsland()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    override fun refreshActiveIsland() {
        mainHandler.removeCallbacks(metadataRefreshRunnable)
        IslandPresentationCoordinator.invalidatePresentation()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    private fun performRefreshActiveIsland() {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        ) {
            clearAllViews()
            return
        }
        if (!shouldRenderInjectedIsland()) {
            IslandPlaybackStateCoordinator.clearActiveViewsForPause()
            return
        }

        val lyricPackage =
            LyriconDataBridge.currentLyricPackageName?.takeIf { it.isNotEmpty() } ?: return
        val lyricVersion = LyriconDataBridge.versionCounter.get()
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()

        IslandContentUpdateCoordinator.invalidate()
        IslandPresentationCoordinator.snapshotAttachedHosts(lyricPackage).forEach { token ->
            if (!isDispatchCurrent(token, lyricPackage, lyricVersion, presentationRevision)) {
                return@forEach
            }
            val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                token = token,
                reason = IslandReconcileReason.STABLE_REFRESH,
                expectedPresentationRevision = presentationRevision
            )
            if (!result.isTarget) return@forEach

            val currentPrefs = HookEntry.instance?.prefs ?: return@forEach
            IslandContentUpdateCoordinator.updateContentForView(
                view = token.root,
                packageName = lyricPackage,
                prefs = currentPrefs,
                config = IslandSlotRuntimeConfig.from(currentPrefs),
                playbackActive = IslandPresentationCoordinator.isPlaybackActive(),
                hostKind = token.kind
            )
        }
    }

    override fun updateMetadata() {
        // MediaController callbacks can publish several intermediate snapshots. Resolve the
        // current source state once after they return to SystemUI's main queue.
        mainHandler.removeCallbacks(metadataRefreshRunnable)
        mainHandler.post(metadataRefreshRunnable)
    }

    private fun performUpdateMetadata() {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            ) || !shouldRenderInjectedIsland()
        ) {
            refreshActiveIsland()
            return
        }

        val lyricPackage = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                refreshActiveIsland()
                return
            }
        val lyricVersion = LyriconDataBridge.versionCounter.get()
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()
        val snapshots = IslandPresentationCoordinator.snapshotAttachedInjectedHosts(lyricPackage)
        if (snapshots.isEmpty()) {
            refreshActiveIsland()
            return
        }

        snapshots.forEach { snapshot ->
            val token = snapshot.host
            if (!isDispatchCurrent(token, lyricPackage, lyricVersion, presentationRevision)) {
                return@forEach
            }
            val currentPrefs = HookEntry.instance?.prefs ?: return@forEach
            IslandContentUpdateCoordinator.updateMetadataForView(
                view = token.root,
                packageName = lyricPackage,
                prefs = currentPrefs,
                config = IslandSlotRuntimeConfig.from(currentPrefs),
                playbackActive = IslandPresentationCoordinator.isPlaybackActive(),
                hostKind = token.kind
            )
        }
    }

    override fun updateLyricLine() {
        if ((HookEntry.instance?.prefs?.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )) != true || !shouldRenderInjectedIsland()
        ) return
        val lyricPackage = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val lyricVersion = LyriconDataBridge.versionCounter.get()
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()
        val generation = contentDispatchGeneration.incrementAndGet()

        runOnMain {
            if (contentDispatchGeneration.get() != generation ||
                !isSourceCurrent(lyricPackage, lyricVersion, presentationRevision)
            ) return@runOnMain

            IslandPresentationCoordinator.snapshotAttachedHosts(lyricPackage).forEach { token ->
                if (contentDispatchGeneration.get() != generation ||
                    !isDispatchCurrent(token, lyricPackage, lyricVersion, presentationRevision)
                ) return@forEach

                // A registered projection can have been rebuilt or restored since the previous
                // line. Reconcile it idempotently before applying the new line; there is no
                // separate transition repair path.
                val result = IslandPresentationCoordinator.reconcileRegisteredHost(
                    token = token,
                    reason = IslandReconcileReason.LYRIC_SELF_HEAL,
                    expectedPresentationRevision = presentationRevision
                )
                if (!result.isTarget) return@forEach

                val currentPrefs = HookEntry.instance?.prefs ?: return@forEach
                IslandContentUpdateCoordinator.updateLyricContentForView(
                    view = token.root,
                    prefs = currentPrefs,
                    config = IslandSlotRuntimeConfig.from(currentPrefs),
                    playbackActive = IslandPresentationCoordinator.isPlaybackActive()
                )
            }
        }
    }

    override fun updateTextColors() {
        mainHandler.removeCallbacks(textColorRefreshRunnable)
        mainHandler.post(textColorRefreshRunnable)
    }

    private fun performUpdateTextColors() {
        IslandContentUpdateCoordinator.forEachActiveHost { view, packageName, prefs, config ->
            IslandContentUpdateCoordinator.updateTextColorsForView(
                view = view,
                packageName = packageName,
                prefs = prefs,
                config = config
            )
        }
    }

    override fun updatePosition(position: Long, playbackSpeed: Float) {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            ) || !shouldRenderInjectedIsland()
        ) return
        val lyricPackage = LyriconDataBridge.currentLyricPackageName ?: return
        val lyricVersion = LyriconDataBridge.versionCounter.get()
        val presentationRevision = IslandPresentationCoordinator.currentPresentationRevision()
        val generation = positionDispatchGeneration.incrementAndGet()

        runOnMain {
            if (positionDispatchGeneration.get() != generation ||
                !isSourceCurrent(lyricPackage, lyricVersion, presentationRevision)
            ) return@runOnMain

            val activeTimeMs = LyriconDataBridge.currentPlaybackClock().activeTimeMs
            IslandPresentationCoordinator.snapshotAttachedInjectedHosts(lyricPackage)
                .forEach { snapshot ->
                    val token = snapshot.host
                    if (positionDispatchGeneration.get() != generation ||
                        !isDispatchCurrent(
                            token,
                            lyricPackage,
                            lyricVersion,
                            presentationRevision
                        )
                    ) return@forEach

                    val playbackViews = if (snapshot.injectedViews.isEmpty()) {
                        listOfNotNull(
                            token.root.findViewWithTag<View>(
                                IslandProbeUtils.LEFT_TEST_VIEW_TAG
                            ),
                            token.root.findViewWithTag<View>(
                                IslandProbeUtils.RIGHT_TEST_VIEW_TAG
                            )
                        ).also {
                            IslandPresentationCoordinator.refreshInjectedViewIndex(token)
                        }
                    } else {
                        snapshot.injectedViews
                    }
                    playbackViews.forEach { view ->
                        IslandLyricViewController.synchronizePosition(
                            view,
                            position,
                            playbackSpeed,
                            activeTimeMs
                        )
                    }
                    IslandContentUpdateCoordinator.updatePlaybackProgressForViews(
                        rootView = token.root,
                        slotViews = playbackViews,
                        position = position
                    )
                    if (token.kind == IslandViewRegistry.HostKind.REAL) {
                        val currentPrefs = HookEntry.instance?.prefs ?: return@forEach
                        IslandHostFacade.updateProgressGlow(
                            token.root,
                            lyricPackage,
                            currentPrefs
                        )
                    }
                }
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        positionDispatchGeneration.incrementAndGet()
        IslandPlaybackStateCoordinator.onPlaybackStateChanged(
            isPlaying = isPlaying,
            onRefreshRequested = { refreshActiveIsland() },
            onClearRequested = { clearAllViews() }
        )
    }

    override fun clearAllViews() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { clearAllViews() }
            return
        }
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.removeCallbacks(metadataRefreshRunnable)
        mainHandler.removeCallbacks(textColorRefreshRunnable)
        positionDispatchGeneration.incrementAndGet()
        contentDispatchGeneration.incrementAndGet()

        val wasPlaybackActive = IslandPresentationCoordinator.isPlaybackActive()
        if (wasPlaybackActive) {
            IslandPresentationCoordinator.updatePlaybackState(false)
        }
        IslandPlaybackStateCoordinator.deactivateAllHosts()
        // A pause callback may already have restored the native slots. Preserve that revision so
        // source onStop() cannot cancel the first clear and create a second visible relayout.
        val presentationRevision = if (wasPlaybackActive) {
            IslandPresentationCoordinator.invalidatePresentation()
        } else {
            IslandPresentationCoordinator.currentPresentationRevision()
        }
        IslandPlaybackStateCoordinator.markClearedByPause()
        IslandPresentationCoordinator.snapshotAttachedHosts().forEach { token ->
            IslandPresentationCoordinator.clearRegisteredHost(token, presentationRevision)
        }
    }

    private fun isDispatchCurrent(
        token: IslandViewRegistry.HostToken,
        lyricPackage: String,
        lyricVersion: Int,
        presentationRevision: Long
    ): Boolean {
        return IslandPresentationCoordinator.isCurrentHost(token) &&
                isSourceCurrent(lyricPackage, lyricVersion, presentationRevision)
    }

    private fun isSourceCurrent(
        lyricPackage: String,
        lyricVersion: Int,
        presentationRevision: Long
    ): Boolean {
        return IslandPresentationCoordinator.isCurrentPresentation(presentationRevision) &&
                LyriconDataBridge.versionCounter.get() == lyricVersion &&
                LyriconDataBridge.currentLyricPackageName == lyricPackage &&
                shouldRenderInjectedIsland()
    }
}
