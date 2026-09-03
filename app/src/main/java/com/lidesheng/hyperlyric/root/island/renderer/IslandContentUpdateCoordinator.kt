package com.lidesheng.hyperlyric.root.island.renderer

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.SuperIslandContentStylePolicy
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.content.IslandSlotContentFacade
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.host.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.sizing.IslandDynamicWidthCoordinator
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Updates the content and visual style of already injected island slots.
 *
 * Host lifecycle and playback transitions stay in their own coordinators. This object only
 * translates the current lyric/media state into view updates and keeps the dynamic-width
 * preflight callbacks next to the content application that owns them.
 */
internal object IslandContentUpdateCoordinator {

    fun invalidate() {
        IslandSlotContentFacade.invalidate()
    }

    fun updateContentForView(
        view: ViewGroup,
        packageName: String,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean,
        hostKind: IslandViewRegistry.HostKind
    ) {
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        prepareSharedCoverPalette(packageName, mediaInfo, prefs)
        if (hostKind == IslandViewRegistry.HostKind.REAL) {
            IslandHostFacade.updateHostGlow(view, prefs)
            IslandHostFacade.updateProgressGlow(view, packageName, mediaInfo, prefs)
        }
        val leftChanged = updateSlot(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            mediaInfo,
            playbackActive
        )
        val rightChanged = updateSlot(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            mediaInfo,
            playbackActive
        )
        if ((config.leftMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    leftChanged) ||
            (config.rightMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    rightChanged)
        ) {
            IslandDynamicWidthCoordinator.requestRefresh(view)
        }
        if (hostKind == IslandViewRegistry.HostKind.REAL) {
            IslandMusicWaveColorHooker.refresh()
        }
    }

    /**
     * Refreshes only the user-configured music-information slots after a metadata callback.
     * Lyric slots stay untouched so a player changing its title metadata cannot restart lyric
     * content or its transition state.
     */
    fun updateMetadataForView(
        view: ViewGroup,
        packageName: String,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean,
        hostKind: IslandViewRegistry.HostKind
    ) {
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        prepareSharedCoverPalette(packageName, mediaInfo, prefs)
        if (hostKind == IslandViewRegistry.HostKind.REAL) {
            IslandHostFacade.updateHostGlow(view, prefs)
            IslandHostFacade.updateProgressGlow(view, packageName, mediaInfo, prefs)
        }
        val leftChanged = updateMetadataSlot(
            view = view,
            tag = IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            mode = config.leftMode,
            prefs = prefs,
            config = config,
            mediaInfo = mediaInfo,
            playbackActive = playbackActive
        )
        val rightChanged = updateMetadataSlot(
            view = view,
            tag = IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            mode = config.rightMode,
            prefs = prefs,
            config = config,
            mediaInfo = mediaInfo,
            playbackActive = playbackActive
        )
        if ((config.leftMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    leftChanged) ||
            (config.rightMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
                    rightChanged)
        ) {
            IslandDynamicWidthCoordinator.requestRefresh(view)
        }
        if (hostKind == IslandViewRegistry.HostKind.REAL) {
            IslandMusicWaveColorHooker.refresh()
        }
    }

    /**
     * Updates only dynamic playback fields in already-rendered music-information slots.
     * This is called from the high-frequency position path and must not touch lyric content.
     */
    fun updatePlaybackProgressForViews(
        rootView: ViewGroup,
        slotViews: Iterable<View>,
        position: Long
    ) {
        var contentChanged = false
        slotViews.forEach { slotView ->
            if (!IslandSlotContentFacade.updatePlaybackProgress(slotView, position)) {
                return@forEach
            }
            contentChanged = true
            slotView.tag?.toString()?.let { tag ->
                IslandDynamicWidthCoordinator.cacheMetadataWidth(rootView, tag)
            }
        }
        if (contentChanged) {
            IslandDynamicWidthCoordinator.requestRefresh(rootView)
        }
    }

    fun updateLyricContentForView(
        view: ViewGroup,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean
    ) {
        updateLyricSlot(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            playbackActive
        )
        updateLyricSlot(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            playbackActive
        )
    }

    fun updateTextColorsForView(
        view: ViewGroup,
        packageName: String,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig
    ) {
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(view.context, packageName, HookLogger)
        prepareSharedCoverPalette(packageName, mediaInfo, prefs)
        updateSlotColors(
            view,
            IslandProbeUtils.LEFT_TEST_VIEW_TAG,
            config.leftMode,
            prefs,
            config,
            mediaInfo
        )
        updateSlotColors(
            view,
            IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
            config.rightMode,
            prefs,
            config,
            mediaInfo
        )
    }

    fun forEachActiveHost(
        update: (
            ViewGroup,
            String,
            SharedPreferences,
            IslandSlotRuntimeConfig
        ) -> Unit
    ) {
        if (!IslandPresentationCoordinator.shouldRenderInjectedIsland()) {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.activeHosts",
                tag = "IslandContentUpdateCoordinator",
                state = "skip|presentation_not_target"
            ) {
                "颜色刷新未执行: reason=presentation_not_target"
            }
            return
        }
        val packageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                HookLogger.dState(
                    stateId = "IslandContentUpdateCoordinator.activeHosts",
                    tag = "IslandContentUpdateCoordinator",
                    state = "skip|no_lyric_package"
                ) {
                    "颜色刷新未执行: reason=no_lyric_package"
                }
                return
            }
        val expectedLyricVersion = LyriconDataBridge.versionCounter.get()
        val expectedPresentationRevision =
            IslandPresentationCoordinator.currentPresentationRevision()

        val hosts = IslandPresentationCoordinator.snapshotAttachedHosts(packageName)
        val realHostCount = hosts.count { it.kind == IslandViewRegistry.HostKind.REAL }
        val fakeHostCount = hosts.size - realHostCount
        HookLogger.dState(
            stateId = "IslandContentUpdateCoordinator.activeHosts",
            tag = "IslandContentUpdateCoordinator",
            state = "hosts|$packageName|$realHostCount|$fakeHostCount|" +
                    "$expectedLyricVersion|$expectedPresentationRevision"
        ) {
            "颜色刷新目标: package=$packageName, realHosts=$realHostCount, " +
                    "fakeHosts=$fakeHostCount, " +
                    "lyricVersion=$expectedLyricVersion, " +
                    "presentationRevision=$expectedPresentationRevision"
        }

        hosts.forEach { token ->
            if (!IslandPresentationCoordinator.isCurrentHost(token) ||
                !isColorUpdateCurrent(
                    packageName,
                    expectedLyricVersion,
                    expectedPresentationRevision
                )
            ) return@forEach
            val prefs = HookEntry.instance?.prefs ?: return@forEach
            update(
                token.root,
                packageName,
                prefs,
                IslandSlotRuntimeConfig.from(prefs)
            )
        }
    }

    private fun isColorUpdateCurrent(
        packageName: String,
        expectedLyricVersion: Int,
        expectedPresentationRevision: Long
    ): Boolean {
        return IslandPresentationCoordinator.isCurrentPresentation(
            expectedPresentationRevision
        ) &&
                LyriconDataBridge.versionCounter.get() == expectedLyricVersion &&
                LyriconDataBridge.currentLyricPackageName == packageName &&
                IslandPresentationCoordinator.shouldRenderInjectedIsland()
    }

    private fun updateLyricSlot(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        playbackActive: Boolean
    ) {
        if (mode != RootConstants.ISLAND_CONTENT_MODE_LYRIC) return
        val lyricView = view.findViewWithTag<View>(tag) ?: return
        val line = IslandSlotContentFacade.buildSlotLyricLine(
            view = lyricView,
            prefs = prefs,
            config = config,
            isLeft = tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
        )
        IslandSlotContentFacade.applyLyricLineContent(
            view = lyricView,
            prefs = prefs,
            config = config,
            lineOverride = line,
            playbackActive = playbackActive,
            onLineWillApply = { contentWidthPx ->
                IslandDynamicWidthCoordinator.prepareLyricWidth(view, tag, contentWidthPx)
            },
            onLineApplied = {
                IslandDynamicWidthCoordinator.clearPreflight(view, tag)
                IslandDynamicWidthCoordinator.requestRefresh(view)
            },
            onLineCancelled = {
                IslandDynamicWidthCoordinator.clearPreflight(view, tag)
            }
        )
    }

    private fun updateSlot(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        playbackActive: Boolean
    ): Boolean {
        if (mode == RootConstants.ISLAND_CONTENT_MODE_NONE) {
            view.findViewWithTag<View>(tag)?.let(IslandSlotContentFacade::clearMetadataState)
            return false
        }
        val slotView = view.findViewWithTag<View>(tag) ?: return false
        val contentChanged = IslandSlotContentFacade.applySlotContent(
            view = slotView,
            prefs = prefs,
            config = config,
            mode = mode,
            // Let applySlotContent configure the actual text paint first, then build the split
            // line from that paint. This keeps the split boundary in sync with custom fonts and
            // weight changes during a full island refresh.
            lineOverride = null,
            playbackActive = playbackActive,
            mediaInfo = mediaInfo,
            onLineWillApply = { contentWidthPx ->
                IslandDynamicWidthCoordinator.prepareLyricWidth(view, tag, contentWidthPx)
            },
            onLineApplied = {
                IslandDynamicWidthCoordinator.clearPreflight(view, tag)
                IslandDynamicWidthCoordinator.requestRefresh(view)
            },
            onLineCancelled = {
                IslandDynamicWidthCoordinator.clearPreflight(view, tag)
            }
        )
        if (mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO &&
            IslandDynamicWidthCoordinator.cacheMetadataWidth(view, tag)
        ) {
            return true
        }
        return contentChanged
    }

    private fun updateMetadataSlot(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        playbackActive: Boolean
    ): Boolean {
        if (mode != RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO) return false
        val slotView = view.findViewWithTag<View>(tag) ?: return false
        val contentChanged = IslandSlotContentFacade.applySlotContent(
            view = slotView,
            prefs = prefs,
            config = config,
            mode = mode,
            playbackActive = playbackActive,
            mediaInfo = mediaInfo
        )
        if (IslandDynamicWidthCoordinator.cacheMetadataWidth(view, tag)) {
            return true
        }
        return contentChanged
    }

    private fun updateSlotColors(
        view: ViewGroup,
        tag: String,
        mode: Int,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ) {
        if (mode == RootConstants.ISLAND_CONTENT_MODE_NONE) {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.slotColor:$tag",
                tag = "IslandContentUpdateCoordinator",
                state = "mode_none"
            ) {
                "歌词颜色未提交: tag=$tag, reason=content_mode_none"
            }
            return
        }
        val slotView = view.findViewWithTag<View>(tag) ?: run {
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.slotColor:$tag",
                tag = "IslandContentUpdateCoordinator",
                state = "slot_missing|$mode"
            ) {
                "歌词颜色未提交: tag=$tag, mode=$mode, reason=slot_view_missing"
            }
            return
        }
        IslandSlotContentFacade.configureView(
            view = slotView,
            prefs = prefs,
            config = config,
            mode = mode,
            mediaInfo = mediaInfo
        )
    }

    /**
     * The MediaSession artwork is the only color source. Populate the shared palette before
     * individual consumers render so their color lifecycle does not depend on MusicWave
     * callbacks, which disappear when an external-device icon occupies that island slot.
     */
    private fun prepareSharedCoverPalette(
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        prefs: SharedPreferences
    ) {
        val usesCoverPalette =
            LyricTextColorStylePolicy.usesCoverColor(
                LyricTextColorStylePolicy.read(prefs)
            ) ||
                    SuperIslandContentStylePolicy.usesMusicWaveCoverColor(
                        SuperIslandContentStylePolicy.readMusicWaveStyle(prefs)
                    ) ||
                    prefs.getBoolean(
                        RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                        RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
                    )
        if (!usesCoverPalette) return

        val rawAlbumArt = mediaInfo.albumArt
        val previousArtworkRevision = CoverColorHelper.currentArtworkRequest()?.revision
        val artworkRequest = CoverColorHelper.ensureArtworkColors(mediaInfo)
        rawAlbumArt?.takeUnless { it.isRecycled } ?: run {
            if (previousArtworkRevision != null &&
                CoverColorHelper.currentArtworkRequest() == null
            ) {
                IslandMusicWaveColorHooker.refresh()
            }
            HookLogger.dState(
                stateId = "IslandContentUpdateCoordinator.coverInput",
                tag = "IslandContentUpdateCoordinator",
                state = "no_art|${rawAlbumArt == null}|${rawAlbumArt?.isRecycled == true}"
            ) {
                        "共享取色未执行: reason=${if (rawAlbumArt == null) "album_art_missing" else "album_art_recycled"}, " +
                        "package=$packageName, artworkSource=${mediaInfo.artworkSource}, " +
                        "titlePresent=${mediaInfo.title.isNotBlank()}, " +
                        "artistPresent=${mediaInfo.artist.isNotBlank()}"
            }
            return
        }
        if (artworkRequest != null && artworkRequest.revision != previousArtworkRevision) {
            IslandMusicWaveColorHooker.refresh()
        }
    }
}
