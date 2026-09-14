/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplayPolicy
import com.lidesheng.hyperlyric.common.lyric.METADATA_RESOLVED_SECONDARY_CONTENT
import com.lidesheng.hyperlyric.common.lyric.METADATA_SWAPPED_ORIGINAL
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContent
import com.lidesheng.hyperlyric.common.lyric.hasContent
import com.lidesheng.hyperlyric.common.lyric.textOf
import com.lidesheng.hyperlyric.common.lyric.wordsOf
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf

internal const val METADATA_NEXT_LINE_PREVIEW = "nextLinePreview"

internal class LyricLineAssembler(
    private var displayTranslation: Boolean = true,
    private var displayRoma: Boolean = true,
    private var enableRelativeProgress: Boolean = false,
    private var enableRelativeHighlight: Boolean = false,
    private var displayLineByLine: Boolean = false,
    private var secondaryContentOrder: List<LyricSecondaryContent> =
        LyricSecondaryContent.DEFAULT_ORDER,
) {
    private val wordBuilder = RelativeWordBuilder()

    fun updateFlags(displayTranslation: Boolean, displayRoma: Boolean,
                    enableRelativeProgress: Boolean, enableRelativeHighlight: Boolean,
                    displayLineByLine: Boolean,
                    secondaryContentOrder: List<LyricSecondaryContent>) {
        this.displayTranslation = displayTranslation
        this.displayRoma = displayRoma
        this.enableRelativeProgress = enableRelativeProgress
        this.enableRelativeHighlight = enableRelativeHighlight
        this.displayLineByLine = displayLineByLine
        this.secondaryContentOrder = LyricContentDisplayPolicy.normalizeOrder(secondaryContentOrder)
    }

    data class MainResult(
        val line: LyricLine,
        val isScrollOnly: Boolean,
        val sustainAwareProgress: Boolean,
        val isLineTimeline: Boolean
    )

    fun buildMain(source: IRichLyricLine?): MainResult {
        if (source == null) return MainResult(LyricLine(), false, false, false)

        val hasOriginalWords = !source.words.isNullOrEmpty()
        val shouldGen = enableRelativeProgress && source.isTitleLine().not()
        val lineText = textForLine(source.text, source.words)
        val useLineTimeline = shouldUseLineTimeline(source, source.words, lineText)
        val words = when {
            useLineTimeline -> emptyList()
            shouldGen -> wordBuilder.build(source, source.text, source.words)
            else -> source.words
        }

        val generated = !useLineTimeline && !hasOriginalWords && words !== source.words
        val line = LyricLine(
            begin = source.begin, end = source.end, duration = source.duration,
            isAlignedRight = source.isAlignedRight, metadata = source.metadata,
            text = if (useLineTimeline) lineText else source.text,
            words = words
        )
        return MainResult(
            line = line,
            isScrollOnly = generated && !enableRelativeHighlight,
            sustainAwareProgress = hasOriginalWords && !useLineTimeline,
            isLineTimeline = useLineTimeline
        )
    }

    data class SecondaryResult(
        val line: LyricLine,
        val alwaysShow: Boolean,
        val isScrollOnly: Boolean,
        val isNextLinePreview: Boolean,
        val sustainAwareProgress: Boolean,
        val isLineTimeline: Boolean
    )

    fun buildSecondary(source: IRichLyricLine?): SecondaryResult {
        if (source == null) return SecondaryResult(LyricLine(), false, false, false, false, false)

        var generated = false
        var hasOriginalWords = false
        var lineTimelineGenerated = false
        val isNextLinePreview = source.metadata?.getBoolean(METADATA_NEXT_LINE_PREVIEW) == true
        val isSwappedOriginal = source.metadata?.getBoolean(METADATA_SWAPPED_ORIGINAL) == true
        val line = LyricLine().apply {
            begin = source.begin; end = source.end; duration = source.duration
            isAlignedRight = source.isAlignedRight

            // Next-line preview and the original lyric moved by the swap option are explicit
            // secondary-row content. They must not be filtered by the generic content selection.
            val hasSourceSecondary = !source.secondary.isNullOrBlank() ||
                    !source.secondaryWords.isNullOrEmpty()
            val hasExplicitSecondary = isNextLinePreview || isSwappedOriginal || hasSourceSecondary
            val resolvedContent = source.metadata
                ?.getString(METADATA_RESOLVED_SECONDARY_CONTENT)
                ?.let(LyricSecondaryContent::fromPreferenceValue)
                ?.takeUnless { it == LyricSecondaryContent.NEXT_LINE }
            val selectedContent = if (hasExplicitSecondary) {
                null
            } else if (resolvedContent != null) {
                // The content type was resolved at song level. It intentionally remains selected
                // even when this particular line has no value in that lane, so lower-priority
                // content cannot make the second row change meaning mid-song.
                resolvedContent
            } else {
                secondaryContentOrder.firstOrNull { content ->
                    when (content) {
                        LyricSecondaryContent.TRANSLATION -> displayTranslation
                        LyricSecondaryContent.ROMA -> displayRoma
                        LyricSecondaryContent.NEXT_LINE -> false
                    } && content.hasContent(source)
                }
            }
            val selectedText = if (hasExplicitSecondary) {
                source.secondary
            } else {
                selectedContent?.textOf(source)
            }
            val selectedWords = if (hasExplicitSecondary) {
                source.secondaryWords
            } else {
                selectedContent?.wordsOf(source)
            }
            val hasSelectedContent = if (hasExplicitSecondary) {
                !selectedText.isNullOrBlank() || !selectedWords.isNullOrEmpty()
            } else {
                selectedContent != null
            }
            if (hasSelectedContent) {
                val selectedTextForLine = textForLine(selectedText, selectedWords)
                text = selectedText
                if (isNextLinePreview) {
                    // 下一句只是预览文本，不能继承当前行时间轴或生成相对时间轴。
                    words = emptyList()
                    metadata = lyricMetadataOf(METADATA_NEXT_LINE_PREVIEW to "true")
                } else {
                    val useLineTimeline = shouldUseLineTimeline(
                        source,
                        selectedWords,
                        selectedTextForLine
                    )
                    val builtWords = if (useLineTimeline) {
                        emptyList()
                    } else {
                        wordBuilder.build(source, selectedText, selectedWords)
                    }
                    words = builtWords
                    metadata = when {
                        isSwappedOriginal -> null
                        selectedContent == LyricSecondaryContent.TRANSLATION ->
                            lyricMetadataOf("translation" to "true")
                        selectedContent == LyricSecondaryContent.ROMA ->
                            lyricMetadataOf("roma" to "true")
                        else -> null
                    }
                    lineTimelineGenerated = useLineTimeline
                    generated = !useLineTimeline && words !== selectedWords
                    hasOriginalWords = !useLineTimeline && !selectedWords.isNullOrEmpty()
                    if (useLineTimeline) text = selectedTextForLine
                }
            }
        }

        val hasContent = line.text?.isNotBlank() == true || !line.words.isNullOrEmpty()
        val isPlain = line.words?.isEmpty() == true
        val alwaysShow = hasContent && (
                isPlain || line.metadata?.getBoolean("translation") == true
                        || line.metadata?.getBoolean("roma") == true
                        || line.words?.firstOrNull()?.begin?.let { (it - source.begin) < 500 } == true
                )

        return SecondaryResult(
            line = line,
            alwaysShow = alwaysShow,
            isScrollOnly = generated && !enableRelativeHighlight,
            isNextLinePreview = isNextLinePreview,
            sustainAwareProgress = hasOriginalWords && !lineTimelineGenerated,
            isLineTimeline = lineTimelineGenerated
        )
    }

    /**
     * A word-timed line already carries the line-level timeline in [source].
     * The line-display mode intentionally collapses only that kind of line into
     * one timed text unit; lines without any word list keep their existing path.
     */
    private fun shouldUseLineTimeline(
        source: IRichLyricLine,
        contentWords: List<LyricWord>?,
        contentText: String?
    ): Boolean {
        val lineDuration = (source.end - source.begin).takeIf { it > 0L } ?: source.duration
        return displayLineByLine && !source.isTitleLine() &&
                source.begin >= 0 && lineDuration > 0L &&
                !contentText.isNullOrBlank() &&
                (!contentWords.isNullOrEmpty() || !source.words.isNullOrEmpty())
    }

    private fun textForLine(text: String?, words: List<LyricWord>?): String? =
        text?.takeIf { it.isNotBlank() } ?: words?.joinToString("") { it.text.orEmpty() }
}


