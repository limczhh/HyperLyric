/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.lyric.view.line.model

import android.graphics.Paint
import io.github.proify.lyricon.lyric.model.LyricMetadata
import io.github.proify.lyricon.lyric.model.interfaces.ILyricTiming

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

    /** 拆分后的字符数组 */
    val chars: CharArray = text.toCharArray()

    /** 各字符宽度数组 */
    val charWidths: FloatArray = FloatArray(text.length)

    /** 各字符起始绘制位置数组 */
    val charStartPositions: FloatArray = FloatArray(text.length)

    /** 各字符结束绘制位置数组 */
    val charEndPositions: FloatArray = FloatArray(text.length)

    /**
     * 更新单词及其字符的尺寸和位置信息
     *
     * @param previous 上一个单词模型
     * @param paint 绘制文本的 Paint 对象
     */
    fun updateSizes(previous: WordModel?, paint: Paint) {
        paint.getTextWidths(chars, 0, chars.size, charWidths)
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
}

internal fun List<WordModel>.toText(): String = joinToString("") { it.text }