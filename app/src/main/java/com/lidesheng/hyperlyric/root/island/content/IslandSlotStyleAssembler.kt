package com.lidesheng.hyperlyric.root.island.content

import android.content.SharedPreferences
import android.view.View
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.effects.color.StatusBarTextColorHooker
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.utils.LyricStyleHelper
import com.lidesheng.hyperlyric.root.utils.TranslationHelper
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
        nextLinePreviewEnabled: Boolean,
        force: Boolean
    ) {
        val disableAll = TranslationHelper.isTranslationDisabled(prefs) || nextLinePreviewEnabled
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
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
        val textColorStyle = LyricTextColorStylePolicy.read(prefs)
        val useCoverColor = LyricTextColorStylePolicy.usesCoverColor(textColorStyle)
        val useCoverGradient = LyricTextColorStylePolicy.usesCoverGradient(textColorStyle)
        val cachedCoverPalette = if (statusBarTextColor == null && useCoverColor) {
            if (artworkRequest != null) {
                CoverColorHelper.getCachedColors(useCoverGradient, artworkRequest)
            } else {
                colorSession?.let { CoverColorHelper.getCachedColors(useCoverGradient, it) }
            }
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
            textColorOverride = statusBarTextColor
        )
        var dispatch = "unsupported"
        when (view) {
            is RichLyricLineView -> {
                if (styleChanged) {
                    view.displayTranslation = !disableAll
                    view.displayRoma = !disableAll && !translationOnly
                    view.setStyle(style)
                    dispatch = "set_style"
                } else {
                    view.updateColor(
                        style.primary.color,
                        style.highlight.background,
                        style.highlight.foreground
                    )
                    dispatch = "update_color"
                }
            }

            is SpaceGateRichLyricLineView -> {
                if (styleChanged) {
                    view.displayTranslation = !disableAll
                    view.displayRoma = !disableAll && !translationOnly
                    view.setStyle(
                        style,
                        isLeftSplitSide = config.isLeftTag(view.tag as? String ?: "")
                    )
                    dispatch = "set_style"
                } else {
                    view.updateColor(
                        style.primary.color,
                        style.highlight.background,
                        style.highlight.foreground
                    )
                    dispatch = "update_color"
                }
            }
        }
        lastStyleSignatures[view] = styleSignature
        lastColorSignatures[view] = colorSignature
        val appliedTextPaintColor = when (view) {
            is RichLyricLineView -> view.main.textPaint.color
            is SpaceGateRichLyricLineView -> view.main.textPaint.color
            else -> null
        }
        val colorSource = when {
            statusBarTextColor != null -> "status_bar"
            !useCoverColor -> "default"
            cachedCoverPalette?.second?.isNotEmpty() == true ->
                if (useCoverGradient) "cover_gradient" else "cover"

            else -> "cover_fallback"
        }
        val viewKey = view.tag?.toString() ?: view.javaClass.simpleName
        val debugState = listOf(
            colorSource,
            style.primary.color.contentToString(),
            style.highlight.background.contentToString(),
            style.highlight.foreground.contentToString(),
            appliedTextPaintColor,
            styleChanged,
            colorChanged,
            dispatch,
            view.visibility,
            view.isShown,
            view.isAttachedToWindow
        ).joinToString("|")
        HookLogger.dState(
            stateId = "IslandSlotStyleAssembler:$viewKey",
            tag = "IslandSlotStyleAssembler",
            state = debugState
        ) {
            "歌词样式已提交: tag=$viewKey, view=${view.javaClass.simpleName}, " +
                    "source=$colorSource, textStyle=$textColorStyle, " +
                    "sessionRevision=${colorSession?.revision}, " +
                    "artworkRevision=${artworkRequest?.revision}, " +
                    "albumArt=${albumBitmap != null}, " +
                    "palette=${cachedCoverPalette?.second?.size ?: 0}, " +
                    "primary=${style.primary.color.contentToString()}, " +
                    "background=${style.highlight.background.contentToString()}, " +
                    "highlight=${style.highlight.foreground.contentToString()}, " +
                    "paintColor=$appliedTextPaintColor, styleChanged=$styleChanged, " +
                    "colorChanged=$colorChanged, dispatch=$dispatch, " +
                    "attached=${view.isAttachedToWindow}, shown=${view.isShown}, alpha=${view.alpha}"
        }
    }
}
