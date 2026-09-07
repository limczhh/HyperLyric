package com.lidesheng.hyperlyric.root.island.config

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.SuperIslandContentStylePolicy
import com.lidesheng.hyperlyric.common.SuperIslandWidthPolicy
import com.lidesheng.hyperlyric.common.SyllablePreferencePolicy
import com.lidesheng.hyperlyric.root.island.sizing.IslandSlotGeometryConfig
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Reads one immutable Super Island slot configuration snapshot from preferences.
 *
 * Callers should keep using [IslandSlotRuntimeConfig.from] so each lifecycle/update path obtains
 * one coherent snapshot instead of reading individual preferences at different times.
 */
internal object IslandSlotRuntimeConfigReader {
    fun read(prefs: SharedPreferences): IslandSlotRuntimeConfig {
        val syllableSettings = SyllablePreferencePolicy.read(prefs)
        val activeMode = prefs.getInt(
            RootConstants.KEY_HOOK_LYRIC_MODE,
            RootConstants.DEFAULT_HOOK_LYRIC_MODE
        )
        val showAlbum = SuperIslandContentStylePolicy.isAlbumCoverVisible(
            SuperIslandContentStylePolicy.readAlbumCoverStyle(prefs)
        )
        val showRhythm = SuperIslandContentStylePolicy.isMusicWaveVisible(
            SuperIslandContentStylePolicy.readMusicWaveStyle(prefs)
        )
        val disableWidthLimit = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
            RootConstants.DEFAULT_HOOK_ISLAND_DISABLE_WIDTH_LIMIT
        )
        val minIslandWidth = SuperIslandWidthPolicy.minIslandWidth(showAlbum, showRhythm)
        val maxIslandWidth = SuperIslandWidthPolicy.maxIslandWidth(
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        val fixedIslandWidth = SuperIslandWidthPolicy.normalizeIslandWidth(
            islandWidth = prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH,
                RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH
            ),
            showAlbum = showAlbum,
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        val widthMode = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
            RootConstants.DEFAULT_HOOK_ISLAND_WIDTH_MODE
        ).coerceIn(
            RootConstants.ISLAND_WIDTH_MODE_FIXED,
            RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
        )
        val dynamicWidthEnabled =
            widthMode == RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
        val dynamicMinWidth = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
            minIslandWidth.coerceAtLeast(RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_MIN_WIDTH)
        ).coerceIn(minIslandWidth, maxIslandWidth)
        val dynamicMaxWidth = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH,
            fixedIslandWidth.coerceIn(
                RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
                maxIslandWidth
            )
        ).coerceIn(dynamicMinWidth, maxIslandWidth)
        val effectiveMinWidth = if (dynamicWidthEnabled) {
            dynamicMinWidth
        } else {
            minIslandWidth
        }
        val effectiveMaxWidth = if (dynamicWidthEnabled) {
            dynamicMaxWidth
        } else {
            fixedIslandWidth
        }
        val dynamicWidthBasis = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS,
            RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS
        ).coerceIn(
            RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_ALL,
            RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY
        )
        val config = IslandSlotRuntimeConfig(
            activeMode = activeMode,
            leftMode = if (activeMode == 1) {
                RootConstants.ISLAND_CONTENT_MODE_LYRIC
            } else {
                readContentMode(
                    prefs,
                    RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
                    RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT
                )
            },
            rightMode = if (activeMode == 1) {
                RootConstants.ISLAND_CONTENT_MODE_LYRIC
            } else {
                readContentMode(
                    prefs,
                    RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
                    RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT
                )
            },
            geometry = IslandSlotGeometryConfig(
                isDynamicWidth = dynamicWidthEnabled,
                showAlbum = showAlbum,
                showRhythm = showRhythm,
                leftPaddingLeftDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
                    RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT
                ),
                leftPaddingRightDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
                    RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT
                ),
                rightPaddingLeftDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
                    RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT
                ),
                rightPaddingRightDp = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
                    RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT
                ),
                leftMinWidthDp = SuperIslandWidthPolicy.leftContentWidth(
                    islandWidth = effectiveMinWidth,
                    showAlbum = showAlbum,
                    showRhythm = showRhythm,
                    disableWidthLimit = disableWidthLimit
                ),
                rightMinWidthDp = effectiveMinWidth,
                leftMaxWidthDp = SuperIslandWidthPolicy.leftContentWidth(
                    islandWidth = effectiveMaxWidth,
                    showAlbum = showAlbum,
                    showRhythm = showRhythm,
                    disableWidthLimit = disableWidthLimit
                ),
                rightMaxWidthDp = effectiveMaxWidth
            ),
            dynamicWidthBasis = dynamicWidthBasis,
            pauseBehavior = prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
                RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
            ),
            textSizeSp = prefs.getInt(
                RootConstants.KEY_HOOK_TEXT_SIZE,
                RootConstants.DEFAULT_HOOK_TEXT_SIZE
            ),
            textSizeRatio = prefs.getFloat(
                RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
                RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO
            ),
            fontWeight = prefs.getInt(
                RootConstants.KEY_HOOK_FONT_WEIGHT,
                RootConstants.DEFAULT_HOOK_FONT_WEIGHT
            ),
            fontItalic = prefs.getBoolean(
                RootConstants.KEY_HOOK_FONT_ITALIC,
                RootConstants.DEFAULT_HOOK_FONT_ITALIC
            ),
            fadingEdgeLength = prefs.getInt(
                RootConstants.KEY_HOOK_FADING_EDGE_LENGTH,
                RootConstants.DEFAULT_HOOK_FADING_EDGE_LENGTH
            ),
            gradientProgress = prefs.getBoolean(
                RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
                RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS
            ),
            centerLyric = prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_LYRIC,
                RootConstants.DEFAULT_HOOK_CENTER_LYRIC
            ),
            centerMusicInfo = prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_CENTER_LYRIC,
                    RootConstants.DEFAULT_HOOK_CENTER_MUSIC_INFO
                )
            ),
            rightLyric = prefs.getBoolean(
                RootConstants.KEY_HOOK_RIGHT_LYRIC,
                RootConstants.DEFAULT_HOOK_RIGHT_LYRIC
            ),
            lyricAnimationEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_ANIM_ENABLE,
                RootConstants.DEFAULT_HOOK_ANIM_ENABLE
            ),
            lyricAnimationId = prefs.getString(
                RootConstants.KEY_HOOK_ANIM_ID,
                RootConstants.DEFAULT_HOOK_ANIM_ID
            ) ?: RootConstants.DEFAULT_HOOK_ANIM_ID,
            lyricAnimationSpeedRate = prefs.getInt(
                RootConstants.KEY_HOOK_ANIM_SPEED_RATE,
                RootConstants.DEFAULT_HOOK_ANIM_SPEED_RATE
            ).coerceIn(
                RootConstants.MIN_HOOK_ANIM_SPEED_RATE,
                RootConstants.MAX_HOOK_ANIM_SPEED_RATE
            ),
            lyricMarqueeEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_MODE,
                RootConstants.DEFAULT_HOOK_MARQUEE_MODE
            ),
            lyricMarqueeSpeed = prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_SPEED,
                RootConstants.DEFAULT_HOOK_MARQUEE_SPEED
            ),
            lyricMarqueeDelay = prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_DELAY
            ),
            lyricMarqueeLoopDelay = prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_LOOP_DELAY
            ),
            lyricMarqueeInfinite = prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_INFINITE,
                RootConstants.DEFAULT_HOOK_MARQUEE_INFINITE
            ),
            lyricMarqueeStopEnd = prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_STOP_END,
                RootConstants.DEFAULT_HOOK_MARQUEE_STOP_END
            ),
            metadataMarqueeEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE
            ),
            metadataMarqueeSpeed = prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_SPEED
            ),
            metadataMarqueeDelay = prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_DELAY
            ),
            metadataMarqueeLoopDelay = prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY
            ),
            metadataMarqueeInfinite = prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_INFINITE
            ),
            syllableRelative = syllableSettings.relativeProgress,
            syllableHighlight = syllableSettings.relativeHighlight,
            syllableLineDisplay = syllableSettings.lineDisplay,
            disableTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
                RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION
            ),
            translationOnly = prefs.getBoolean(
                RootConstants.KEY_HOOK_TRANSLATION_ONLY,
                RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY
            ),
            swapTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_SWAP_TRANSLATION,
                RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION
            ),
            nextLyricLine = prefs.getBoolean(
                RootConstants.KEY_HOOK_NEXT_LYRIC_LINE,
                RootConstants.DEFAULT_HOOK_NEXT_LYRIC_LINE
            ),
            autoSwitchTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_AUTO_SWITCH_TRANSLATION,
                RootConstants.DEFAULT_HOOK_AUTO_SWITCH_TRANSLATION
            ),
            textColorStyle = LyricTextColorStylePolicy.read(prefs),
            customFontPath = prefs.getString(RootConstants.KEY_HOOK_CUSTOM_FONT_PATH, null)
                .orEmpty(),
            narrowLatinFont = prefs.getBoolean(
                RootConstants.KEY_HOOK_NARROW_LATIN_FONT,
                RootConstants.DEFAULT_HOOK_NARROW_LATIN_FONT
            ),
            wordMotionEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED
            ),
            wordMotionCjkLift = prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT
            ),
            wordMotionCjkWave = prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE
            ),
            wordMotionLatinByCharacter = prefs.getBoolean(
                RootConstants.KEY_HOOK_WORD_MOTION_LATIN_BY_CHARACTER,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_BY_CHARACTER
            ),
            wordMotionLatinLift = prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT
            ),
            wordMotionLatinWave = prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE
            )
        )
        if (!HookLogger.isDebugEnabled) return config
        val summary = listOf(
            "mode=${config.activeMode}/${config.leftMode}/${config.rightMode}",
            "width=${config.geometry.isDynamicWidth}:${config.geometry.leftMinWidthDp}-" +
                    "${config.geometry.leftMaxWidthDp}/${config.geometry.rightMinWidthDp}-" +
                    "${config.geometry.rightMaxWidthDp}",
            "album=${config.geometry.showAlbum}",
            "rhythm=${config.geometry.showRhythm}",
            "pause=${config.pauseBehavior}",
            "text=${config.textSizeSp}/${config.textSizeRatio}/${config.fontWeight}/${config.fontItalic}",
            "animation=${config.lyricAnimationEnabled}:${config.lyricAnimationId}:${config.lyricAnimationSpeedRate}",
            "marquee=${config.lyricMarqueeEnabled}:${config.lyricMarqueeSpeed}",
            "metadataMarquee=${config.metadataMarqueeEnabled}:${config.metadataMarqueeSpeed}",
            "translation=${config.disableTranslation}/${config.translationOnly}/${config.swapTranslation}",
            "syllable=${config.syllableLineDisplay}/${config.syllableRelative}/${config.syllableHighlight}",
            "next=${config.nextLyricLine}/${config.autoSwitchTranslation}",
            "color=${config.textColorStyle}",
            "font=${config.customFontPath.isNotBlank()}/${config.narrowLatinFont}",
            "wordMotion=${config.wordMotionEnabled}"
        ).joinToString(",")
        HookLogger.dState(
            stateId = "IslandSlotRuntimeConfig",
            tag = "IslandSlotRuntimeConfig",
            state = summary
        ) {
            "超级岛歌词实际配置: $summary, styleSignature=${config.styleSignature.hashCode()}"
        }
        return config
    }

    private fun readContentMode(
        prefs: SharedPreferences,
        key: String,
        defaultValue: Int
    ): Int {
        return prefs.getInt(key, defaultValue).takeIf {
            it == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                    it == RootConstants.ISLAND_CONTENT_MODE_LYRIC ||
                    it == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
        } ?: defaultValue
    }
}
