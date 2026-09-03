package com.lidesheng.hyperlyric.root.island.host

import android.content.SharedPreferences
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.SuperIslandContentStylePolicy
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.island.effects.glow.HookIslandGlow
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowController
import com.lidesheng.hyperlyric.root.utils.HookLogger

internal object IslandHostFacade {
    fun applyHostSettings(rootView: ViewGroup, prefs: SharedPreferences) {
        val albumStyle = SuperIslandContentStylePolicy.readAlbumCoverStyle(prefs)
        val showAlbum = SuperIslandContentStylePolicy.isAlbumCoverVisible(
            albumStyle
        )
        val rhythmStyle = SuperIslandContentStylePolicy.readMusicWaveStyle(prefs)
        val showRhythm = SuperIslandContentStylePolicy.isMusicWaveVisible(
            rhythmStyle
        )
        val rootId = System.identityHashCode(rootView)
        HookLogger.dState(
            stateId = "IslandHostFacade:$rootId",
            tag = "IslandHostFacade",
            state = "$albumStyle|$rhythmStyle|$showAlbum|$showRhythm"
        ) {
            "宿主显示配置: root=$rootId, albumStyle=$albumStyle, showAlbum=$showAlbum, " +
                    "musicWaveStyle=$rhythmStyle, showRhythm=$showRhythm"
        }

        IslandViewHelper.toggleContainer(
            rootView,
            IslandProbeUtils.LEFT_PARENT_NAME,
            "island_container_module_icon",
            showAlbum
        )
        IslandViewHelper.toggleContainer(
            rootView,
            IslandProbeUtils.RIGHT_PARENT_NAME,
            "island_container_module_icon",
            showRhythm
        )
        IslandViewHelper.toggleContainer(
            rootView,
            IslandProbeUtils.LEFT_PARENT_NAME,
            IslandProbeUtils.TEXT_CONTAINER_NAME,
            true
        )
        IslandViewHelper.toggleContainer(
            rootView,
            IslandProbeUtils.RIGHT_PARENT_NAME,
            IslandProbeUtils.TEXT_CONTAINER_NAME,
            true
        )

        if (!showAlbum) {
            IslandViewHelper.clearTextContainerMargin(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                clearStart = true,
                clearEnd = false
            )
        }
        if (!showRhythm) {
            IslandViewHelper.clearTextContainerMargin(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                clearStart = false,
                clearEnd = true
            )
        }
    }

    fun clearInjectedViews(rootView: ViewGroup): Boolean {
        val changed = IslandViewHelper.clearInjectedViews(rootView)
        IslandProgressGlowController.clear(rootView)
        return changed
    }

    fun triggerSystemRelayout(rootView: ViewGroup) {
        IslandViewHelper.triggerSystemRelayout(rootView)
    }

    fun updateHostGlow(rootView: ViewGroup, prefs: SharedPreferences) {
        HookIslandGlow.updateMusicGlow(rootView, prefs)
    }

    fun updateProgressGlow(
        rootView: ViewGroup,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        prefs: SharedPreferences
    ) {
        IslandProgressGlowController.update(rootView, packageName, mediaInfo, prefs)
    }

    fun updateProgressGlow(
        rootView: ViewGroup,
        packageName: String,
        prefs: SharedPreferences
    ) {
        IslandProgressGlowController.update(rootView, packageName, null, prefs)
    }
}
