package com.lidesheng.hyperlyric.root.island

import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.effects.color.StatusBarTextColorHooker
import com.lidesheng.hyperlyric.root.island.effects.glow.HookIslandGlow
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowController
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowHooker
import com.lidesheng.hyperlyric.root.island.hooks.IslandMediaSwipeHooker
import com.lidesheng.hyperlyric.root.island.hooks.IslandWidthLimitHooker
import com.lidesheng.hyperlyric.root.island.hooks.SystemUIHookRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandNativeRefreshCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import com.lidesheng.hyperlyric.root.island.sizing.IslandDynamicWidthCoordinator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule

/**
 * Coordinates the Super Island side of an API 102 module handoff.
 *
 * This class owns ordering only.  Each renderer/effect still owns its resources, while
 * [com.lidesheng.hyperlyric.root.HookRuntimeRegistry] remains the owner of hook handles.  Media-card
 * hooks are deliberately absent from this coordinator and stay in their original generation.
 */
internal class SuperIslandHotReloadCoordinator(
    private val mainThread: MainThreadExecutor,
) {
    fun prepareForHotReload(): List<IslandPresentationCoordinator.HotReloadHostTransfer> {
        val transfers = mainThread.execute {
            BaseIslandRenderer.prepareForHotReload()
            val prepared = IslandPresentationCoordinator.prepareForHotReload()
            IslandDynamicWidthCoordinator.prepareForHotReload()
            check(StatusBarTextColorHooker.cleanupForHotReload()) {
                "DarkIconDispatcher receiver could not be detached"
            }
            HookIslandGlow.cleanupForHotReload()
            IslandProgressGlowController.clearAll()
            IslandProgressGlowHooker.cleanupForHotReload()
            IslandAlbumCoverStyleHooker.cleanup()
            IslandMusicWaveColorHooker.cleanup()
            IslandMediaSwipeHooker.prepareForHotReload()
            IslandNativeRefreshCoordinator.clear()
            prepared
        }

        // These registries contain class-loader and hooker-local state.  Clear them only after the
        // host tree has been returned to the native projection above.
        SystemUIHookRegistry.prepareForHotReload()
        IslandWidthLimitHooker.prepareForHotReload()
        return transfers
    }

    fun installPluginHooks(module: XposedModule, classLoaders: Iterable<ClassLoader>) {
        classLoaders.forEach { classLoader ->
            SystemUIHookRegistry.hook(module, classLoader)
        }
    }

    fun restoreStatusBarAfterHotReload(textColor: Int, dispatcher: Any?) {
        StatusBarTextColorHooker.restoreTextColor(textColor)
        mainThread.execute {
            StatusBarTextColorHooker.restoreDispatcherAfterHotReload(dispatcher)
        }
    }

    fun adoptHotReloadHosts(
        transfers: List<IslandPresentationCoordinator.HotReloadHostTransfer>,
    ): Int = mainThread.execute {
        IslandPresentationCoordinator.adoptHotReloadHosts(transfers)
    }

    fun refreshRestoredPresentation() {
        mainThread.execute {
            BaseIslandRenderer.updateLyricLine()
            BaseIslandRenderer.updateMetadata()
            BaseIslandRenderer.onPlaybackStateChanged(
                LyriconDataBridge.isPlaybackActive()
            )
            val clock = LyriconDataBridge.currentPlaybackClock()
            BaseIslandRenderer.updatePosition(clock.positionMs, clock.playbackSpeed)
            BaseIslandRenderer.refreshActiveIsland()
        }
    }

    /**
     * Clean a partially installed new Super Island generation.  This method intentionally does not
     * touch lyric sources or media-card state; those have different owners and are handled by the
     * module runtime and the retained media generation respectively.
     */
    fun cleanupFailedGeneration(): Boolean {
        val mainCleanupSucceeded = runCatching {
            mainThread.execute {
                var succeeded = StatusBarTextColorHooker.cleanupForHotReload()
                HookIslandGlow.cleanupForHotReload()
                IslandProgressGlowHooker.cleanupForHotReload()
                IslandAlbumCoverStyleHooker.cleanup()
                IslandMusicWaveColorHooker.cleanup()
                IslandMediaSwipeHooker.prepareForHotReload()
                IslandWidthLimitHooker.prepareForHotReload()
                IslandDynamicWidthCoordinator.prepareForHotReload()
                IslandNativeRefreshCoordinator.clear()
                BaseIslandRenderer.prepareForHotReload()
                IslandPresentationCoordinator.prepareForHotReload()
                succeeded
            }
        }.onFailure { error ->
            HookLogger.e(TAG, "清理失败的超级岛热重载代际失败", error)
        }.getOrDefault(false)
        SystemUIHookRegistry.prepareForHotReload()
        return mainCleanupSucceeded
    }

    internal interface MainThreadExecutor {
        fun <T> execute(block: () -> T): T
    }

    private companion object {
        private const val TAG = "SuperIslandHotReloadCoordinator"
    }
}
