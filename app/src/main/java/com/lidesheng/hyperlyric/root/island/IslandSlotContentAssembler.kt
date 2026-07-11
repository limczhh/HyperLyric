package com.lidesheng.hyperlyric.root.island

import android.content.Context
import android.content.SharedPreferences
import android.text.TextPaint
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.RichLyricLineSplitter
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.METADATA_NEXT_LINE_PREVIEW
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.yoyo.YoYoPresets
import com.lidesheng.hyperlyric.lyric.view.yoyo.animateUpdate
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.LyricStyleHelper
import com.lidesheng.hyperlyric.root.utils.TranslationHelper
import java.util.WeakHashMap

internal object IslandSlotContentAssembler {
    private val lastContentSignatures = WeakHashMap<View, String>()
    private val lastStyleSignatures = WeakHashMap<View, String>()

    fun invalidate(view: View? = null) {
        if (view == null) {
            synchronized(lastContentSignatures) { lastContentSignatures.clear() }
            synchronized(lastStyleSignatures) { lastStyleSignatures.clear() }
            return
        }
        synchronized(lastContentSignatures) { lastContentSignatures.remove(view) }
        synchronized(lastStyleSignatures) { lastStyleSignatures.remove(view) }
    }

    fun configureView(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context),
        force: Boolean = false
    ) {
        val nextLinePreview = isNextLinePreviewEnabled(prefs, config)
        val disableAll = TranslationHelper.isTranslationDisabled(prefs) || nextLinePreview
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
        val signature = listOf(
            config.styleSignature,
            mode,
            mediaInfo.title,
            mediaInfo.artist,
            mediaInfo.album,
            mediaInfo.albumArt?.generationId ?: 0,
            LyriconDataBridge.isDisplayTranslation,
            LyriconDataBridge.isDisplayRoma
        ).joinToString("|")

        if (!force && lastStyleSignatures[view] == signature) return
        val mediaColorKey = if (mediaInfo.albumArt != null || CoverColorHelper.currentMediaKey() == null) {
            CoverColorHelper.updateMediaSession(
                packageName = LyriconDataBridge.currentLyricPackageName.orEmpty(),
                title = mediaInfo.title,
                artist = mediaInfo.artist,
                album = mediaInfo.album
            )
        } else {
            CoverColorHelper.currentMediaKey()
        }
        val style = LyricStyleHelper.buildStyle(
            prefs = prefs,
            res = view.resources,
            mode = mode,
            albumBitmap = mediaInfo.albumArt,
            mediaColorKey = mediaColorKey
        )
        when (view) {
            is RichLyricLineView -> {
                view.displayTranslation = LyriconDataBridge.isDisplayTranslation && !disableAll
                view.displayRoma = LyriconDataBridge.isDisplayRoma && !disableAll && !translationOnly
                view.setStyle(style)
            }
            is SpaceGateRichLyricLineView -> {
                view.displayTranslation = LyriconDataBridge.isDisplayTranslation && !disableAll
                view.displayRoma = LyriconDataBridge.isDisplayRoma && !disableAll && !translationOnly
                view.setStyle(style)
            }
        }
        lastStyleSignatures[view] = signature
    }

    fun applySlotContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        lineOverride: IRichLyricLine? = null,
        force: Boolean = false,
        playbackActive: Boolean = true,
        suppressAnimation: Boolean = false,
        mediaInfo: MediaMetadataHelper.MediaInfo = currentMediaInfo(view.context)
    ): Boolean {
        configureView(view, prefs, config, mode, mediaInfo, force)
        return if (mode == 7) {
            applyLyricContent(view, prefs, config, lineOverride, force, playbackActive, suppressAnimation)
        } else {
            applyMetadataContent(view, config, mode, force, mediaInfo)
        }
    }

    fun applyLyricLineContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        playbackActive: Boolean = true
    ): Boolean = applyLyricContent(
        view = view,
        prefs = prefs,
        config = config,
        lineOverride = lineOverride,
        force = false,
        playbackActive = playbackActive,
        suppressAnimation = false
    )

    fun buildSlotLyricLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): IRichLyricLine? {
        val rawLine = processedRawLine(prefs, config)
        if (!config.isSplitMode || rawLine == null) return rawLine
        if (rawLine.text.isNullOrEmpty()) return rawLine

        val density = view.resources.displayMetrics.density
        val leftMaxPx = config.leftMaxWidthDp * density
        val textPaint = TextPaint().apply {
            textSize = config.textSizeSp.toFloat() * density
        }
        val splitPx = if (config.centerLyric) {
            val textWidth = textPaint.measureText(rawLine.text ?: "")
            (textWidth / 2f).coerceAtMost(leftMaxPx)
        } else {
            leftMaxPx
        }
        val splitResult = RichLyricLineSplitter.split(
            rawLine,
            textPaint,
            splitPx,
            config.textSizeRatio,
            config.centerLyric
        )
        return if (isLeft) splitResult.left else splitResult.right
    }

    fun processedRawLine(prefs: SharedPreferences, config: IslandSlotRuntimeConfig? = null): IRichLyricLine? {
        val songName = LyriconDataBridge.currentSongName?.takeIf { it.isNotEmpty() } ?: ""
        var rawLine = LyriconDataBridge.currentLyricLine
            ?: RichLyricLine(text = songName, words = emptyList())

        if (config != null && isNextLinePreviewEnabled(prefs, config)) {
            return rawLine.withNextLinePreview(LyriconDataBridge.currentNextLyricLine)
        }

        if (TranslationHelper.isTranslationOnly(prefs)) {
            rawLine = TranslationHelper.applyTranslationOnly(rawLine)
        } else if (TranslationHelper.isSwapTranslation(prefs)) {
            rawLine = TranslationHelper.swapTranslation(rawLine)
        }
        return rawLine
    }

    private fun applyLyricContent(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        force: Boolean,
        playbackActive: Boolean,
        suppressAnimation: Boolean
    ): Boolean {
        val targetLine = lineOverride ?: buildSlotLyricLine(
            view = view,
            prefs = prefs,
            config = config,
            isLeft = view.tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
        )
        val signature = "lyric|${lineContentSignature(targetLine)}|${config.styleSignature}"
        if (!force && lastContentSignatures[view] == signature) {
            applyPlaybackActive(view, playbackActive)
            return false
        }

        val applyLine: (View) -> Unit = { target ->
            when (target) {
                is RichLyricLineView -> {
                    target.line = targetLine
                    target.setPlaybackActive(playbackActive)
                    if (config.lyricMarqueeEnabled) target.post { target.requestStartMarquee() }
                }
                is SpaceGateRichLyricLineView -> {
                    target.line = targetLine
                    target.setPlaybackActive(playbackActive)
                    if (config.lyricMarqueeEnabled) target.post { target.requestStartMarquee() }
                }
            }
        }

        val suppressContentAnimation = suppressAnimation || isNextLinePreviewEnabled(prefs, config)
        if (config.lyricAnimationEnabled && !suppressContentAnimation) {
            val preset = YoYoPresets.getById(config.lyricAnimationId) ?: YoYoPresets.Default
            when (view) {
                is RichLyricLineView -> view.animateUpdate(preset) { applyLine(this) }
                is SpaceGateRichLyricLineView -> view.animateUpdate(preset) { applyLine(this) }
                else -> applyLine(view)
            }
        } else {
            applyLine(view)
        }
        lastContentSignatures[view] = signature
        return true
    }

    private fun applyMetadataContent(
        view: View,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        force: Boolean,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): Boolean {
        val songName = LyriconDataBridge.currentSongName?.takeIf { it.isNotEmpty() } ?: mediaInfo.title
        val artistName = mediaInfo.artist
        val albumName = mediaInfo.album

        val signature = listOf(
            "metadata",
            mode,
            songName,
            artistName,
            albumName,
            config.metadataMarqueeEnabled,
            config.metadataMarqueeSpeed,
            config.metadataMarqueeDelay,
            config.metadataMarqueeLoopDelay,
            config.metadataMarqueeInfinite
        ).joinToString("|")
        if (!force && lastContentSignatures[view] == signature) return false

        val singleModeText = when (mode) {
            1 -> songName
            2 -> artistName
            3 -> albumName
            4 -> "$songName - $artistName"
            else -> ""
        }
        val newLine = when (mode) {
            1, 2, 3, 4 -> RichLyricLine(text = singleModeText, words = emptyList())
            5 -> RichLyricLine(text = songName, words = emptyList(), secondary = artistName, secondaryWords = emptyList())
            6 -> {
                val secondary = if (albumName.isEmpty()) artistName else "$artistName - $albumName"
                RichLyricLine(text = songName, words = emptyList(), secondary = secondary, secondaryWords = emptyList())
            }
            else -> null
        }

        when (view) {
            is RichLyricLineView -> {
                view.line = newLine
                applyMetadataMarquee(view, config)
            }
            is SpaceGateRichLyricLineView -> {
                view.line = newLine
                applyMetadataMarquee(view, config)
            }
        }
        lastContentSignatures[view] = signature
        return true
    }

    private fun applyPlaybackActive(view: View, playbackActive: Boolean) {
        when (view) {
            is RichLyricLineView -> view.setPlaybackActive(playbackActive)
            is SpaceGateRichLyricLineView -> view.setPlaybackActive(playbackActive)
        }
    }

    private fun applyMetadataMarquee(view: RichLyricLineView, config: IslandSlotRuntimeConfig) {
        if (!config.metadataMarqueeEnabled) return
        view.setMetadataMarqueeConfig(
            config.metadataMarqueeSpeed.toFloat(),
            config.metadataMarqueeDelay,
            config.metadataMarqueeLoopDelay,
            if (config.metadataMarqueeInfinite) -1 else 1,
            true
        )
        view.main.setPeerLineWidth(view.secondary.lineWidth)
        view.secondary.setPeerLineWidth(view.main.lineWidth)
        view.post { view.requestStartMarquee() }
    }

    private fun applyMetadataMarquee(view: SpaceGateRichLyricLineView, config: IslandSlotRuntimeConfig) {
        if (!config.metadataMarqueeEnabled) return
        view.setMetadataMarqueeConfig(
            config.metadataMarqueeSpeed.toFloat(),
            config.metadataMarqueeDelay,
            config.metadataMarqueeLoopDelay,
            if (config.metadataMarqueeInfinite) -1 else 1,
            true
        )
        view.main.setPeerLineWidth(view.secondary.lineWidth)
        view.secondary.setPeerLineWidth(view.main.lineWidth)
        view.post { view.requestStartMarquee() }
    }

    private fun currentMediaInfo(context: Context): MediaMetadataHelper.MediaInfo {
        val targetPkg = LyriconDataBridge.currentLyricPackageName ?: ""
        return MediaMetadataHelper.getMediaInfo(context, targetPkg, HookLogger)
    }

    private fun lineContentSignature(line: IRichLyricLine?): Int {
        if (line == null) return 0
        return listOf(
            line.begin,
            line.end,
            line.duration,
            line.text,
            line.words,
            line.secondary,
            line.secondaryWords,
            line.translation,
            line.translationWords,
            line.roma,
            line.isAlignedRight
        ).hashCode()
    }

    private fun isNextLinePreviewEnabled(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig
    ): Boolean {
        if (!config.nextLyricLine || config.isSplitMode) return false
        val source = prefs.getString(RootConstants.KEY_HOOK_LYRIC_SOURCE, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE)
        return source == "lyricon" || source == "lyricinfo"
    }

    private fun IRichLyricLine.withNextLinePreview(nextLine: IRichLyricLine?): IRichLyricLine {
        val nextText = nextLine?.text?.takeIf { it.isNotBlank() }
        return RichLyricLine(
            begin = begin,
            end = end,
            duration = duration,
            isAlignedRight = isAlignedRight,
            metadata = lyricMetadataOf(
                *(metadata?.entries?.map { it.key to it.value } ?: emptyList()).toTypedArray(),
                METADATA_NEXT_LINE_PREVIEW to "true"
            ),
            text = text,
            words = words,
            secondary = nextText,
            secondaryWords = emptyList(),
            translation = null,
            translationWords = null,
            roma = null
        )
    }
}
