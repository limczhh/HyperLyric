package com.lidesheng.hyperlyric.root.island.content

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import java.util.WeakHashMap
import java.util.Collections
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Applies one scheduled presentation to a slot and owns transitions between renderers.
 * Lyric and metadata assemblers only update content within their own display mode.
 */
internal object SlotContentCoordinator {
    private val renderedModes = Collections.synchronizedMap(WeakHashMap<View, Int>())

    fun invalidate(view: View? = null) {
        if (view == null) renderedModes.clear() else renderedModes.remove(view)
        view?.let(IslandLyricViewController::stopRecursively)
        IslandMetadataContentAssembler.invalidate(view)
        IslandSlotStyleAssembler.invalidate(view)
    }

    fun configureView(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context),
        force: Boolean = false,
        coverPaletteBackgroundIsDark: Boolean = true
    ) {
        val resolvedMode = renderedModes[view] ?: config.resolveContentMode(mode)
        val usePlainTextLyricClock = resolvedMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC &&
                config.lyricMarqueeEnabled && LyriconDataBridge.isTextMode
        IslandLyricViewController.useSharedMarqueeClock(
            view,
            resolvedMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO ||
                    usePlainTextLyricClock,
            if (usePlainTextLyricClock) {
                LyriconDataBridge.currentPlainTextMarqueeOriginActiveTimeMs()
            } else {
                0L
            }
        )
        IslandSlotStyleAssembler.configureView(
            view = view,
            prefs = prefs,
            config = config,
            mode = resolvedMode,
            mediaInfo = mediaInfo,
            force = force,
            coverPaletteBackgroundIsDark = coverPaletteBackgroundIsDark
        )
    }

    fun applySlotContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        force: Boolean = false,
        playbackActive: Boolean,
        suppressAnimation: Boolean = false,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context),
        playbackClock: LyriconDataBridge.PlaybackClockReading =
            LyriconDataBridge.currentPlaybackClock(),
        playbackDuration: Long = mediaInfo.duration,
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null,
        forceUnsplit: Boolean = false,
        allowNoLyricsPlaceholder: Boolean = false,
        coverPaletteBackgroundIsDark: Boolean = true
    ): Boolean {
        val resolvedMode = config.resolveContentMode(mode)
        if (renderedModes[view] != resolvedMode) {
            IslandLyricViewController.stopRecursively(view)
            IslandMetadataContentAssembler.invalidate(view)
            renderedModes[view] = resolvedMode
        }
        configureView(
            view = view,
            prefs = prefs,
            config = config,
            mode = resolvedMode,
            mediaInfo = mediaInfo,
            force = force,
            coverPaletteBackgroundIsDark = coverPaletteBackgroundIsDark
        )
        return if (resolvedMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            IslandLyricContentAssembler.apply(
                view = view,
                config = config,
                force = force,
                playbackActive = playbackActive,
                playbackClock = playbackClock,
                suppressAnimation = suppressAnimation,
                onLineWillApply = onLineWillApply,
                onLineApplied = onLineApplied,
                onLineCancelled = onLineCancelled,
                forceUnsplit = forceUnsplit,
                allowNoLyricsPlaceholder = allowNoLyricsPlaceholder && config.presentation.hasPlaceholder
            )
        } else if (resolvedMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO) {
            val changed = IslandMetadataContentAssembler.apply(
                view = view,
                prefs = prefs,
                config = config,
                mode = resolvedMode,
                force = force,
                mediaInfo = mediaInfo,
                playbackPosition = playbackClock.positionMs,
                playbackDuration = playbackDuration
            )
            IslandLyricViewController.applyPlaybackSnapshot(
                view = view,
                position = playbackClock.positionMs,
                playbackSpeed = playbackClock.playbackSpeed,
                activeTimeMs = playbackClock.activeTimeMs,
                active = playbackActive
            )
            changed
        } else {
            when (view) {
                is RichLyricLineView -> view.setMetadataLine(null)
                is SpaceGateRichLyricLineView -> view.setMetadataLine(null)
            }
            false
        }
    }

    fun updatePlaybackProgress(view: View, position: Long): Boolean {
        return IslandMetadataContentAssembler.updatePlaybackProgress(view, position)
    }

    private fun currentMediaInfo(context: Context): MediaMetadataHelper.MediaInfo {
        val targetPkg = LyriconDataBridge.currentLyricPackageName ?: ""
        return CurrentMediaInfoResolver.getMediaInfo(context, targetPkg, HookLogger)
    }
}
