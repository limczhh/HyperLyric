package com.lidesheng.hyperlyric.root.island.content

import android.content.SharedPreferences
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.effects.color.StatusBarTextColorHooker
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.LyricStyleHelper
import java.util.WeakHashMap

internal object IslandSlotStyleAssembler {
    private val lastStyleSignatures = WeakHashMap<View, String>()
    private val lastColorSignatures = WeakHashMap<View, String>()

    fun invalidate(view: View? = null) {
        if (view == null) {
            synchronized(lastStyleSignatures) { lastStyleSignatures.clear() }
            synchronized(lastColorSignatures) { lastColorSignatures.clear() }
            return
        }
        synchronized(lastStyleSignatures) { lastStyleSignatures.remove(view) }
        synchronized(lastColorSignatures) { lastColorSignatures.remove(view) }
    }

    fun configureView(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        mediaInfo: MediaMetadataHelper.MediaInfo,
        force: Boolean
    ) {
        val lyricContentDisplay = config.lyricContentDisplay
        val colorSession = CoverColorHelper.currentSession(mediaInfo)
        val albumBitmap = mediaInfo.albumArt
        val artworkRequest = if (config.extractCoverTextColor) {
            CoverColorHelper.ensureArtworkColors(mediaInfo)
        } else {
            null
        }
        val statusBarTextColor = if (config.followStatusBarTextColor) {
            StatusBarTextColorHooker.currentTextColor()
        } else {
            null
        }
        val styleSignature = listOf(
            config.styleSignature,
            mode,
            mediaInfo.title,
            mediaInfo.artist,
            mediaInfo.album
        ).joinToString("|")
        val colorSignature = listOf(
            config.textColorStyle,
            statusBarTextColor,
            colorSession?.revision,
            colorSession?.mediaKey,
            artworkRequest?.revision,
            albumBitmap?.generationId ?: 0
        ).joinToString("|")

        val styleChanged = force || lastStyleSignatures[view] != styleSignature
        val colorChanged = force || lastColorSignatures[view] != colorSignature
        if (!styleChanged && !colorChanged) return

        val style = LyricStyleHelper.buildStyle(
            prefs = prefs,
            res = view.resources,
            mode = mode,
            colorSession = colorSession,
            artworkRequest = artworkRequest,
            textColorOverride = statusBarTextColor,
            lyricAlignmentOverride = config.lyricAlignment.takeIf {
                mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC
            }
        )
        when (view) {
            is RichLyricLineView -> {
                if (styleChanged) {
                    view.displayTranslation = lyricContentDisplay.showTranslation
                    view.displayRoma = lyricContentDisplay.showRoma
                    view.displayBackgroundVocal = lyricContentDisplay.showBackgroundVocal
                    view.secondaryContentOrder = lyricContentDisplay.order
                    view.setStyle(style)
                } else {
                    view.updateColor(
                        style.primary.color,
                        style.highlight.background,
                        style.highlight.foreground
                    )
                }
            }

            is SpaceGateRichLyricLineView -> {
                if (styleChanged) {
                    view.displayTranslation = lyricContentDisplay.showTranslation
                    view.displayRoma = lyricContentDisplay.showRoma
                    view.displayBackgroundVocal = lyricContentDisplay.showBackgroundVocal
                    view.secondaryContentOrder = lyricContentDisplay.order
                    view.setStyle(
                        style,
                        isLeftSplitSide = config.isLeftTag(view.tag as? String ?: "")
                    )
                } else {
                    view.updateColor(
                        style.primary.color,
                        style.highlight.background,
                        style.highlight.foreground
                    )
                }
            }
        }
        lastStyleSignatures[view] = styleSignature
        lastColorSignatures[view] = colorSignature
    }
}
