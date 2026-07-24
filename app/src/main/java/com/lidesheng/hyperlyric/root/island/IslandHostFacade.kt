package com.lidesheng.hyperlyric.root.island

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.HookIslandGlow
import com.lidesheng.hyperlyric.root.utils.HookLogger

internal object IslandHostFacade {
    private var loggedCutoutInfo = false

    fun logCameraCutoutInfo(rootView: ViewGroup) {
        if (loggedCutoutInfo) return
        val cutoutView = IslandViewHelper.findViewByName(rootView, "area_cutout")
        if (cutoutView != null) {
            val location = IntArray(2)
            cutoutView.getLocationOnScreen(location)
            HookLogger.d(
                "IslandHostFacade",
                "摄像头挖孔宽度=${cutoutView.width}px，x=${location[0]}"
            )
        } else {
            HookLogger.d("IslandHostFacade", "未找到摄像头挖孔视图")
        }
        loggedCutoutInfo = true
    }

    fun applyHostSettings(rootView: ViewGroup, prefs: SharedPreferences) {
        val showAlbum = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM,
            RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM
        )
        val showRhythm = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON,
            RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON
        )

        IslandViewHelper.toggleContainer(rootView, IslandProbeUtils.LEFT_PARENT_NAME, "island_container_module_icon", showAlbum)
        IslandViewHelper.toggleContainer(rootView, IslandProbeUtils.RIGHT_PARENT_NAME, "island_container_module_icon", showRhythm)
        IslandViewHelper.toggleContainer(rootView, IslandProbeUtils.LEFT_PARENT_NAME, IslandProbeUtils.TEXT_CONTAINER_NAME, true)
        IslandViewHelper.toggleContainer(rootView, IslandProbeUtils.RIGHT_PARENT_NAME, IslandProbeUtils.TEXT_CONTAINER_NAME, true)

        if (!showAlbum) {
            IslandViewHelper.clearTextContainerMargin(rootView, IslandProbeUtils.LEFT_PARENT_NAME, clearStart = true, clearEnd = false)
        }
        if (!showRhythm) {
            IslandViewHelper.clearTextContainerMargin(rootView, IslandProbeUtils.RIGHT_PARENT_NAME, clearStart = false, clearEnd = true)
        }
    }

    fun clearAndRefresh(rootView: ViewGroup) {
        val changed = IslandViewHelper.clearInjectedViews(rootView)
        IslandProgressGlowController.clear(rootView)
        if (changed) {
            IslandViewHelper.triggerSystemRelayout(rootView)
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

    fun injectHostGlow(viewGroup: ViewGroup, islandData: Any?, prefs: SharedPreferences) {
        HookIslandGlow.injectAndTriggerGlow(viewGroup, islandData, prefs)
    }

    fun updateHostGlow(rootView: ViewGroup, albumArt: Bitmap?, prefs: SharedPreferences) {
        HookIslandGlow.updateMusicGlow(rootView, albumArt, prefs)
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
