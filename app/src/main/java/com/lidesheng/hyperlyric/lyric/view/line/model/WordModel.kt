/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.lidesheng.hyperlyric.lyric.view.line.model

import android.graphics.Paint
import android.graphics.Rect
import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.interfaces.ILyricTiming

/**
 * 表示歌词中的单词及其相关位置信息、时间信息和字符偏移。
 *
 * @property begin 单词开始时间，单位毫秒
 * @property end 单词结束时间，单位毫秒
 * @property duration 单词持续时间，单位毫秒
 * @property text 单词文本内容
 * @property previous 前一个单词模型，可为 null
 * @property next 下一个单词模型，可为 null
 * @property textWidth 单词文本的总宽度
 * @property startPosition 单词起始绘制位置
 * @property endPosition 单词结束绘制位置
 * @property chars 单词拆分后的字符数组
 * @property charWidths 各字符宽度数组
 * @property charStartPositions 各字符起始绘制位置数组
 * @property charEndPositions 各字符结束绘制位置数组
 */
data class WordModel(
    override var begin: Long,
    override var end: Long,
    override var duration: Long,
    val text: String,
    val metadata: LyricMetadata? = null,
) : ILyricTiming {

    /** 前一个单词 */
    var previous: WordModel? = null

    /** 下一个单词 */
    var next: WordModel? = null

    /** 单词文本总宽度 */
    var textWidth: Float = 0f
        private set

    /** 单词起始绘制位置 */
    var startPosition: Float = 0f
        private set

    /** 单词结束绘制位置 */
    var endPosition: Float = 0f
        private set

    /** 单词墨迹相对单词起点的左右边界，包含防止抗锯齿边缘被裁的少量余量。 */
    var inkStartOffset: Float = 0f
        private set
    var inkEndOffset: Float = 0f
        private set

    /** Strength of the optional sustain-aware highlight curve, in the range 0..1. */
    var sustainStrength: Float = 0f
        internal set

    /** Whether this word uses the CJK per-character motion profile. */
    val containsCjk: Boolean = text.any { it.isCjkTextUnit() }

    /** 拆分后的字符数组 */
    val chars: CharArray = text.toCharArray()

    /** 各字符宽度数组 */
    val charWidths: FloatArray = FloatArray(text.length)

    /** 各字符起始绘制位置数组 */
    val charStartPositions: FloatArray = FloatArray(text.length)

    /** 各字符结束绘制位置数组 */
    val charEndPositions: FloatArray = FloatArray(text.length)

    /** 各字符墨迹相对对应字符起点的左右边界。 */
    val charInkStartOffsets: FloatArray = FloatArray(text.length)
    val charInkEndOffsets: FloatArray = FloatArray(text.length)

    private val textBounds = Rect()
    private val charBounds = Rect()

    /**
     * 更新单词及其字符的尺寸和位置信息
     *
     * @param previous 上一个单词模型
     * @param paint 绘制文本的 Paint 对象
     */
    fun updateSizes(previous: WordModel?, paint: Paint) {
        paint.getTextWidths(chars, 0, chars.size, charWidths)
        paint.getTextBounds(text, 0, text.length, textBounds)
        inkStartOffset = textBounds.left - GLYPH_CLIP_PADDING
        inkEndOffset = textBounds.right + GLYPH_CLIP_PADDING

        for (i in chars.indices) {
            paint.getTextBounds(chars, i, 1, charBounds)
            charInkStartOffsets[i] = charBounds.left - GLYPH_CLIP_PADDING
            charInkEndOffsets[i] = charBounds.right + GLYPH_CLIP_PADDING
        }

        textWidth = charWidths.sum()
        startPosition = previous?.endPosition ?: 0f
        endPosition = startPosition + textWidth

        var currentPosition = startPosition
        for (i in chars.indices) {
            charStartPositions[i] = currentPosition
            currentPosition += charWidths[i]
            charEndPositions[i] = currentPosition
        }
    }

    private companion object {
        const val GLYPH_CLIP_PADDING = 1f
    }
}

internal fun List<WordModel>.toText(): String = joinToString("") { it.text }

private fun Char.isCjkTextUnit(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
}

