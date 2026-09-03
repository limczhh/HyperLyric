package com.lidesheng.hyperlyric.root.island.content

import android.content.SharedPreferences
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.sizing.IslandDynamicWidthCoordinator
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Refreshes the content of already injected lyric and metadata slots.
 *
 * Slot structure is owned by [com.lidesheng.hyperlyric.root.island.structure.IslandSlotStructureInjector].
 * Width preflight remains attached to the line-application callbacks so a new lyric line is
 * measured before it becomes visible.
 */
internal object IslandLyricContentRefresher {

    fun refreshCurrentContent(
        rootView: ViewGroup,
        playbackActive: Boolean
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: run {
            HookLogger.dState(
                stateId = "IslandLyricContentRefresher:${System.identityHashCode(rootView)}:prefs",
                tag = "IslandLyricContentRefresher",
                state = "missing"
            ) {
                "歌词内容未刷新: reason=remote_preferences_missing, root=${System.identityHashCode(rootView)}"
            }
            return false
        }
        val config = IslandSlotRuntimeConfig.from(prefs)
        val packageName = LyriconDataBridge.currentLyricPackageName.orEmpty()
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(rootView.context, packageName, HookLogger)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = refreshSlotContent(
                rootView,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config.leftMode,
                prefs,
                config,
                playbackActive,
                mediaInfo
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = refreshSlotContent(
                rootView,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config.rightMode,
                prefs,
                config,
                playbackActive,
                mediaInfo
            ) || changed
        }

        IslandDynamicWidthCoordinator.requestRefresh(rootView)
        return changed
    }

    private fun refreshSlotContent(
        rootView: ViewGroup,
        viewTag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): Boolean {
        val view = rootView.findViewWithTag<android.view.View>(viewTag) ?: run {
            HookLogger.dState(
                stateId = "IslandLyricContentRefresher:${System.identityHashCode(rootView)}:$viewTag",
                tag = "IslandLyricContentRefresher",
                state = "missing|$mode"
            ) {
                "歌词内容未刷新: root=${System.identityHashCode(rootView)}, tag=$viewTag, " +
                        "mode=$mode, reason=tagged_view_missing"
            }
            return false
        }
        val contentChanged = IslandSlotContentFacade.applySlotContent(
            view,
            prefs,
            config,
            mode,
            playbackActive = playbackActive,
            suppressAnimation = true,
            mediaInfo = mediaInfo,
            onLineWillApply = { contentWidthPx ->
                IslandDynamicWidthCoordinator.prepareLyricWidth(rootView, viewTag, contentWidthPx)
            },
            onLineApplied = {
                IslandDynamicWidthCoordinator.clearPreflight(rootView, viewTag)
                IslandDynamicWidthCoordinator.requestRefresh(rootView)
            },
            onLineCancelled = {
                IslandDynamicWidthCoordinator.clearPreflight(rootView, viewTag)
            }
        )
        if (mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
            IslandDynamicWidthCoordinator.cacheMetadataWidth(rootView, viewTag)
        ) {
            return true
        }
        return contentChanged
    }
}
