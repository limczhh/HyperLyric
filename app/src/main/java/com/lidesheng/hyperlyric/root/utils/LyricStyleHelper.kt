package com.lidesheng.hyperlyric.root.utils

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.util.TypedValue
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.SyllablePreferencePolicy
import com.lidesheng.hyperlyric.lyric.view.Highlight
import com.lidesheng.hyperlyric.lyric.view.LyricViewStyle
import com.lidesheng.hyperlyric.lyric.view.Marquee
import com.lidesheng.hyperlyric.lyric.view.TextLook
import com.lidesheng.hyperlyric.lyric.view.TitleSlot
import com.lidesheng.hyperlyric.lyric.view.WordMotion

/**
 * 歌词样式构建助手
 * 负责根据用户配置和歌曲信息（如封面）生成 RichLyricLineView 所需的样式对象
 */
object LyricStyleHelper {
    private const val COVER_BACKGROUND_ALPHA = 144

    /**
     * 构建歌词样式对象
     */
    fun buildStyle(
        prefs: SharedPreferences,
        res: Resources,
        mode: Int,
        colorSession: CoverColorHelper.ColorSession? = null,
        artworkRequest: CoverColorHelper.ArtworkRequest? = null,
        textColorOverride: Int? = null
    ): LyricViewStyle {
        val syllableSettings = SyllablePreferencePolicy.read(prefs)
        val fontSize =
            prefs.getInt(RootConstants.KEY_HOOK_TEXT_SIZE, RootConstants.DEFAULT_HOOK_TEXT_SIZE)
        val font = FontHelper.loadFont(prefs)

        val textSizeRatio = prefs.getFloat(
            RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
            RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO
        )
        val primarySizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            fontSize.toFloat(),
            res.displayMetrics
        )

        // Style 层永远允许 secondary 显示；翻译开关通过 view.displayTranslation/displayRoma
        // 控制 assembler 选什么内容，无内容时 assembler 返回 alwaysShow=false → secondary GONE
        val showSecondary = mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC ||
                mode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO

        val isLyricMode = mode == RootConstants.ISLAND_CONTENT_MODE_LYRIC
        val centerIfPossible = if (isLyricMode) {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_LYRIC,
                RootConstants.DEFAULT_HOOK_CENTER_LYRIC
            )
        } else {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_CENTER_LYRIC,
                    RootConstants.DEFAULT_HOOK_CENTER_MUSIC_INFO
                )
            )
        }
        val isMarqueeEnabled = if (isLyricMode) {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_MODE,
                RootConstants.DEFAULT_HOOK_MARQUEE_MODE
            )
        } else {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE
            )
        }
        val marqueeSpeed = if (isLyricMode) {
            prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_SPEED,
                RootConstants.DEFAULT_HOOK_MARQUEE_SPEED
            )
        } else {
            prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_SPEED
            )
        }
        val marqueeDelay = if (isLyricMode) {
            prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_DELAY
            )
        } else {
            prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_DELAY
            )
        }
        val marqueeLoopDelay = if (isLyricMode) {
            prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_LOOP_DELAY
            )
        } else {
            prefs.getInt(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY
            )
        }
        val infinite = if (isLyricMode) {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_INFINITE,
                RootConstants.DEFAULT_HOOK_MARQUEE_INFINITE
            )
        } else {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE,
                RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_INFINITE
            )
        }
        val stopAtEnd = if (isLyricMode) {
            prefs.getBoolean(
                RootConstants.KEY_HOOK_MARQUEE_STOP_END,
                RootConstants.DEFAULT_HOOK_MARQUEE_STOP_END
            )
        } else {
            true
        }

        val textColorStyle = LyricTextColorStylePolicy.read(prefs)
        val useCoverColor = LyricTextColorStylePolicy.usesCoverColor(textColorStyle)
        val useCoverGradient = LyricTextColorStylePolicy.usesCoverGradient(textColorStyle)

        val primaryColors: IntArray
        val bgColors: IntArray
        val hlColors: IntArray

        if (textColorOverride != null) {
            primaryColors = intArrayOf(textColorOverride)
            bgColors = intArrayOf(
                Color.argb(
                    128,
                    Color.red(textColorOverride),
                    Color.green(textColorOverride),
                    Color.blue(textColorOverride)
                )
            )
            hlColors = intArrayOf(textColorOverride)
        } else if (useCoverColor) {
            val palette = if (artworkRequest != null) {
                CoverColorHelper.getCachedColors(useCoverGradient, artworkRequest)
            } else {
                colorSession?.let {
                    CoverColorHelper.getCachedColors(useCoverGradient, it)
                }
            }
            val darkColors = palette?.second
            if (darkColors != null && darkColors.isNotEmpty()) {
                val backgroundCoverColors = darkColors.map {
                    Color.argb(
                        COVER_BACKGROUND_ALPHA,
                        Color.red(it),
                        Color.green(it),
                        Color.blue(it)
                    )
                }.toIntArray()
                primaryColors = darkColors   // 无逐字/标题 -> 封面颜色
                bgColors = backgroundCoverColors // 未唱到 -> 封面颜色(alpha 144)
                hlColors = darkColors        // 已唱到 -> 封面颜色
            } else {
                primaryColors = intArrayOf(Color.WHITE)
                bgColors = intArrayOf(Color.argb(128, 255, 255, 255))
                hlColors = intArrayOf(Color.WHITE)
            }
        } else {
            primaryColors = intArrayOf(Color.WHITE)
            bgColors = intArrayOf(Color.argb(128, 255, 255, 255))
            hlColors = intArrayOf(Color.WHITE)
        }

        return LyricViewStyle(
            primary = TextLook(
                color = primaryColors,
                size = primarySizePx,
                typeface = font.typeface,
                fontVariationSettings = font.variationSettings,
                relativeProgress = syllableSettings.relativeProgress,
                relativeHighlight = syllableSettings.relativeHighlight,
            ),
            secondary = TextLook(
                color = if (showSecondary) primaryColors else intArrayOf(Color.TRANSPARENT),
                size = if (showSecondary) primarySizePx * textSizeRatio else 0f,
                typeface = font.typeface,
                fontVariationSettings = font.variationSettings,
            ),
            highlight = Highlight(
                background = bgColors,
                foreground = hlColors,
            ),
            marquee = Marquee(
                speed = if (isMarqueeEnabled) marqueeSpeed.toFloat() else 0f,
                initialDelay = marqueeDelay,
                loopDelay = marqueeLoopDelay,
                repeatCount = if (!isMarqueeEnabled) 0 else if (infinite) -1 else 1,
                stopAtEnd = stopAtEnd,
            ),
            gradient = prefs.getBoolean(
                RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
                RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS
            ),
            lineDisplay = syllableSettings.lineDisplay,
            fadingEdge = prefs.getInt(
                RootConstants.KEY_HOOK_FADING_EDGE_LENGTH,
                RootConstants.DEFAULT_HOOK_FADING_EDGE_LENGTH
            ),
            wordMotion = WordMotion(
                enabled = prefs.getBoolean(
                    RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED
                ),
                cjkLiftFactor = prefs.getFloat(
                    RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT
                ),
                cjkWaveFactor = prefs.getFloat(
                    RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE
                ),
                latinByCharacter = prefs.getBoolean(
                    RootConstants.KEY_HOOK_WORD_MOTION_LATIN_BY_CHARACTER,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_BY_CHARACTER
                ),
                latinLiftFactor = prefs.getFloat(
                    RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT
                ),
                latinWaveFactor = prefs.getFloat(
                    RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE,
                    RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE
                ),
            ),
            placeholder = TitleSlot.NONE,
            centerIfPossible = centerIfPossible,
            rightIfPossible = isLyricMode && prefs.getBoolean(
                RootConstants.KEY_HOOK_RIGHT_LYRIC,
                RootConstants.DEFAULT_HOOK_RIGHT_LYRIC
            ),
        )
    }
}

