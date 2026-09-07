package com.lidesheng.hyperlyric.root.island.config

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.sizing.IslandSlotGeometryConfig

internal data class IslandSlotRuntimeConfig(
    val activeMode: Int,
    val leftMode: Int,
    val rightMode: Int,
    val geometry: IslandSlotGeometryConfig,
    val dynamicWidthBasis: Int,
    val pauseBehavior: Int,
    val textSizeSp: Int,
    val textSizeRatio: Float,
    val fontWeight: Int,
    val fontItalic: Boolean,
    val fadingEdgeLength: Int,
    val gradientProgress: Boolean,
    val centerLyric: Boolean,
    val centerMusicInfo: Boolean,
    val rightLyric: Boolean,
    val lyricAnimationEnabled: Boolean,
    val lyricAnimationId: String,
    val lyricAnimationSpeedRate: Int,
    val lyricMarqueeEnabled: Boolean,
    val lyricMarqueeSpeed: Int,
    val lyricMarqueeDelay: Int,
    val lyricMarqueeLoopDelay: Int,
    val lyricMarqueeInfinite: Boolean,
    val lyricMarqueeStopEnd: Boolean,
    val metadataMarqueeEnabled: Boolean,
    val metadataMarqueeSpeed: Int,
    val metadataMarqueeDelay: Int,
    val metadataMarqueeLoopDelay: Int,
    val metadataMarqueeInfinite: Boolean,
    val syllableRelative: Boolean,
    val syllableHighlight: Boolean,
    val syllableLineDisplay: Boolean,
    val disableTranslation: Boolean,
    val translationOnly: Boolean,
    val swapTranslation: Boolean,
    val nextLyricLine: Boolean,
    val autoSwitchTranslation: Boolean,
    val textColorStyle: Int,
    val customFontPath: String,
    val narrowLatinFont: Boolean,
    val wordMotionEnabled: Boolean,
    val wordMotionCjkLift: Float,
    val wordMotionCjkWave: Float,
    val wordMotionLatinByCharacter: Boolean,
    val wordMotionLatinLift: Float,
    val wordMotionLatinWave: Float
) {
    val isSplitMode: Boolean
        get() = activeMode == 1

    val extractCoverTextColor: Boolean
        get() = LyricTextColorStylePolicy.usesCoverColor(textColorStyle)

    val extractCoverTextGradient: Boolean
        get() = LyricTextColorStylePolicy.usesCoverGradient(textColorStyle)

    val followStatusBarTextColor: Boolean
        get() = LyricTextColorStylePolicy.followsStatusBar(textColorStyle)

    val styleSignature: String = listOf(
        activeMode,
        textSizeSp,
        textSizeRatio,
        fontWeight,
        fontItalic,
        fadingEdgeLength,
        gradientProgress,
        centerLyric,
        centerMusicInfo,
        rightLyric,
        lyricAnimationEnabled,
        lyricAnimationId,
        lyricAnimationSpeedRate,
        lyricMarqueeEnabled,
        lyricMarqueeSpeed,
        lyricMarqueeDelay,
        lyricMarqueeLoopDelay,
        lyricMarqueeInfinite,
        lyricMarqueeStopEnd,
        metadataMarqueeEnabled,
        metadataMarqueeSpeed,
        metadataMarqueeDelay,
        metadataMarqueeLoopDelay,
        metadataMarqueeInfinite,
        syllableRelative,
        syllableHighlight,
        syllableLineDisplay,
        disableTranslation,
        translationOnly,
        swapTranslation,
        nextLyricLine,
        autoSwitchTranslation,
        customFontPath,
        narrowLatinFont,
        wordMotionEnabled,
        wordMotionCjkLift,
        wordMotionCjkWave,
        wordMotionLatinByCharacter,
        wordMotionLatinLift,
        wordMotionLatinWave
    ).joinToString("|")

    fun modeForTag(tag: String): Int {
        return if (tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG) leftMode else rightMode
    }

    fun isLeftTag(tag: String): Boolean {
        return tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
    }

    companion object {
        fun from(prefs: SharedPreferences): IslandSlotRuntimeConfig =
            IslandSlotRuntimeConfigReader.read(prefs)
    }
}
