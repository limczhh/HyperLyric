package com.lidesheng.hyperlyric.root.island

import android.content.SharedPreferences
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants

internal data class IslandSlotRuntimeConfig(
    val activeMode: Int,
    val leftMode: Int,
    val rightMode: Int,
    val showAlbum: Boolean,
    val showRhythm: Boolean,
    val leftPaddingLeftDp: Int,
    val leftPaddingRightDp: Int,
    val rightPaddingLeftDp: Int,
    val rightPaddingRightDp: Int,
    val leftMaxWidthDp: Int,
    val rightMaxWidthDp: Int,
    val pauseBehavior: Int,
    val textSizeSp: Int,
    val textSizeRatio: Float,
    val fontWeight: Int,
    val fontItalic: Boolean,
    val fadingEdgeLength: Int,
    val gradientProgress: Boolean,
    val centerLyric: Boolean,
    val lyricAnimationEnabled: Boolean,
    val lyricAnimationId: String,
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
    val disableTranslation: Boolean,
    val translationOnly: Boolean,
    val swapTranslation: Boolean,
    val nextLyricLine: Boolean,
    val extractCoverTextColor: Boolean,
    val extractCoverTextGradient: Boolean,
    val customFontPath: String,
    val wordMotionEnabled: Boolean,
    val wordMotionCjkLift: Float,
    val wordMotionCjkWave: Float,
    val wordMotionLatinLift: Float,
    val wordMotionLatinWave: Float
) {
    val isSplitMode: Boolean
        get() = activeMode == 1

    val styleSignature: String = listOf(
        activeMode,
        textSizeSp,
        textSizeRatio,
        fontWeight,
        fontItalic,
        fadingEdgeLength,
        gradientProgress,
        centerLyric,
        lyricAnimationEnabled,
        lyricAnimationId,
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
        disableTranslation,
        translationOnly,
        swapTranslation,
        nextLyricLine,
        extractCoverTextColor,
        extractCoverTextGradient,
        customFontPath,
        wordMotionEnabled,
        wordMotionCjkLift,
        wordMotionCjkWave,
        wordMotionLatinLift,
        wordMotionLatinWave
    ).joinToString("|")

    fun modeForTag(tag: String): Int {
        return if (tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG) leftMode else rightMode
    }

    fun isLeftTag(tag: String): Boolean {
        return tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
    }

    fun isLeftParent(parentName: String): Boolean {
        return parentName.contains("1")
    }

    fun maxWidthDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftMaxWidthDp else rightMaxWidthDp
    }

    fun paddingLeftDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftPaddingLeftDp else rightPaddingLeftDp
    }

    fun paddingRightDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftPaddingRightDp else rightPaddingRightDp
    }

    fun widthPx(rootView: View, parentName: String): Int? {
        val maxWidthDp = maxWidthDp(parentName)
        if (maxWidthDp <= 0) return null
        return (maxWidthDp * rootView.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    fun paddingLeftPx(rootView: View, parentName: String): Int {
        return (paddingLeftDp(parentName) * rootView.resources.displayMetrics.density).toInt().coerceAtLeast(0)
    }

    fun paddingRightPx(rootView: View, parentName: String): Int {
        return (paddingRightDp(parentName) * rootView.resources.displayMetrics.density).toInt().coerceAtLeast(0)
    }

    companion object {
        fun from(prefs: SharedPreferences): IslandSlotRuntimeConfig {
            val activeMode = prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
            return IslandSlotRuntimeConfig(
                activeMode = activeMode,
                leftMode = if (activeMode == 1) 7 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT),
                rightMode = if (activeMode == 1) 7 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT),
                showAlbum = prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM),
                showRhythm = prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON),
                leftPaddingLeftDp = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT),
                leftPaddingRightDp = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT),
                rightPaddingLeftDp = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT),
                rightPaddingRightDp = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT),
                leftMaxWidthDp = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH),
                rightMaxWidthDp = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH),
                pauseBehavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE),
                textSizeSp = prefs.getInt(RootConstants.KEY_HOOK_TEXT_SIZE, RootConstants.DEFAULT_HOOK_TEXT_SIZE),
                textSizeRatio = prefs.getFloat(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO),
                fontWeight = prefs.getInt(RootConstants.KEY_HOOK_FONT_WEIGHT, RootConstants.DEFAULT_HOOK_FONT_WEIGHT),
                fontItalic = prefs.getBoolean(RootConstants.KEY_HOOK_FONT_ITALIC, RootConstants.DEFAULT_HOOK_FONT_ITALIC),
                fadingEdgeLength = prefs.getInt(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, RootConstants.DEFAULT_HOOK_FADING_EDGE_LENGTH),
                gradientProgress = prefs.getBoolean(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS),
                centerLyric = prefs.getBoolean(RootConstants.KEY_HOOK_CENTER_LYRIC, RootConstants.DEFAULT_HOOK_CENTER_LYRIC),
                lyricAnimationEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, RootConstants.DEFAULT_HOOK_ANIM_ENABLE),
                lyricAnimationId = prefs.getString(RootConstants.KEY_HOOK_ANIM_ID, RootConstants.DEFAULT_HOOK_ANIM_ID) ?: RootConstants.DEFAULT_HOOK_ANIM_ID,
                lyricMarqueeEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE),
                lyricMarqueeSpeed = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_SPEED),
                lyricMarqueeDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_DELAY),
                lyricMarqueeLoopDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_LOOP_DELAY),
                lyricMarqueeInfinite = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_INFINITE),
                lyricMarqueeStopEnd = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_STOP_END, RootConstants.DEFAULT_HOOK_MARQUEE_STOP_END),
                metadataMarqueeEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE),
                metadataMarqueeSpeed = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_SPEED),
                metadataMarqueeDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_DELAY),
                metadataMarqueeLoopDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY),
                metadataMarqueeInfinite = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_INFINITE),
                syllableRelative = prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE),
                syllableHighlight = prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT),
                disableTranslation = prefs.getBoolean(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION),
                translationOnly = prefs.getBoolean(RootConstants.KEY_HOOK_TRANSLATION_ONLY, RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY),
                swapTranslation = prefs.getBoolean(RootConstants.KEY_HOOK_SWAP_TRANSLATION, RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION),
                nextLyricLine = prefs.getBoolean(RootConstants.KEY_HOOK_NEXT_LYRIC_LINE, RootConstants.DEFAULT_HOOK_NEXT_LYRIC_LINE),
                extractCoverTextColor = prefs.getBoolean(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR, RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_COLOR),
                extractCoverTextGradient = prefs.getBoolean(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT, RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_GRADIENT),
                customFontPath = prefs.getString(RootConstants.KEY_HOOK_CUSTOM_FONT_PATH, null).orEmpty(),
                wordMotionEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED),
                wordMotionCjkLift = prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT),
                wordMotionCjkWave = prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE),
                wordMotionLatinLift = prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT),
                wordMotionLatinWave = prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE)
            )
        }
    }
}
