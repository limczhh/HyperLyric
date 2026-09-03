package com.lidesheng.hyperlyric.root.island.content

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Compatibility facade for injected slot content.
 *
 * Slot routing stays here while lyric content, metadata content and style application keep their
 * own implementation and caches in the content package.
 */
internal object IslandSlotContentFacade {

    fun invalidate(view: View? = null) {
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
        force: Boolean = false
    ) {
        IslandSlotStyleAssembler.configureView(
            view = view,
            prefs = prefs,
            config = config,
            mode = mode,
            mediaInfo = mediaInfo,
            nextLinePreviewEnabled = IslandLyricContentAssembler.isNextLinePreviewEnabled(
                prefs,
                config
            ),
            force = force
        )
    }

    fun applySlotContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        lineOverride: IRichLyricLine? = null,
        force: Boolean = false,
        playbackActive: Boolean,
        suppressAnimation: Boolean = false,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context),
        playbackClock: LyriconDataBridge.PlaybackClockReading =
            LyriconDataBridge.currentPlaybackClock(),
        playbackDuration: Long = mediaInfo.duration,
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null
    ): Boolean {
        configureView(view, prefs, config, mode, mediaInfo, force)
        return if (mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
            IslandMetadataContentAssembler.clearState(view)
            IslandLyricContentAssembler.apply(
                view = view,
                prefs = prefs,
                config = config,
                lineOverride = lineOverride,
                force = force,
                playbackActive = playbackActive,
                playbackClock = playbackClock,
                suppressAnimation = suppressAnimation,
                onLineWillApply = onLineWillApply,
                onLineApplied = onLineApplied,
                onLineCancelled = onLineCancelled
            )
        } else {
            IslandMetadataContentAssembler.apply(
                view = view,
                prefs = prefs,
                config = config,
                mode = mode,
                force = force,
                mediaInfo = mediaInfo,
                playbackPosition = playbackClock.positionMs,
                playbackDuration = playbackDuration
            )
        }
    }

    fun clearMetadataState(view: View) {
        IslandMetadataContentAssembler.clearState(view)
    }

    fun updatePlaybackProgress(view: View, position: Long): Boolean {
        return IslandMetadataContentAssembler.updatePlaybackProgress(view, position)
    }

    fun applyLyricLineContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        playbackActive: Boolean,
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null
    ): Boolean = IslandLyricContentAssembler.applyLine(
        view = view,
        prefs = prefs,
        config = config,
        lineOverride = lineOverride,
        playbackActive = playbackActive,
        onLineWillApply = onLineWillApply,
        onLineApplied = onLineApplied,
        onLineCancelled = onLineCancelled
    )

    fun buildSlotLyricLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): IRichLyricLine? = IslandLyricContentAssembler.buildSlotLyricLine(
        view = view,
        prefs = prefs,
        config = config,
        isLeft = isLeft
    )

    fun processedRawLine(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig? = null
    ): IRichLyricLine? = IslandLyricContentAssembler.processedRawLine(prefs, config)

    private fun currentMediaInfo(context: Context): MediaMetadataHelper.MediaInfo {
        val targetPkg = LyriconDataBridge.currentLyricPackageName ?: ""
        return CurrentMediaInfoResolver.getMediaInfo(context, targetPkg, HookLogger)
    }
}
