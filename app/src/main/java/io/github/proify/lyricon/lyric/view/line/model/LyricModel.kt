/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line.model

import android.graphics.Paint
import android.graphics.Rect
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.model.LyricMetadata
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator

data class LyricModel(
    val begin: Long = 0,
    val end: Long = 0,
    val duration: Long = 0,
    val text: String,
    val words: List<WordModel>,
    val isAlignedRight: Boolean = false,
    var metadata: LyricMetadata? = null,
) {
    var width: Float = 0f
        private set

    val wordText: String by lazy { words.toText() }
    val wordTimingNavigator: TimingNavigator<WordModel> by lazy { TimingNavigator(words.toTypedArray()) }
    val isPlainText: Boolean = words.isEmpty()

    fun updateSizes(paint: Paint) {
        width = getTextFullWidth(paint, text)
        var previous: WordModel? = null
        words.forEach { word ->
            word.updateSizes(previous, paint)
            previous = word
        }
    }

    /**
     * 获取文字绘制所需的实际宽度
     */
    private fun getTextFullWidth(paint: Paint, text: String): Float {
        val measureWidth = paint.measureText(text)
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        // 如果 bounds.right 大于 measureWidth，说明文字向右侧溢出了
        return if (bounds.right > measureWidth) {
            bounds.right.toFloat()
        } else {
            measureWidth
        }
    }
}

internal fun emptyLyricModel(): LyricModel = LyricModel(
    words = emptyList(),
    text = ""
)

/**
 * 将 LyricLine 转换为 LyricModel
 */
internal fun LyricLine.createModel(): LyricModel = LyricModel(
    begin = begin,
    end = end,
    duration = duration,
    text = text.orEmpty(),
    words = words?.toWordModels() ?: emptyList(),
    isAlignedRight = isAlignedRight,
    metadata = metadata
)

/**
 * 将 LyricWord 列表转换为 WordModel 列表，并建立前后引用关系
 */
private fun List<LyricWord>.toWordModels(): List<WordModel> {
    val models = mutableListOf<WordModel>()
    var previousModel: WordModel? = null

    forEach { word ->
        val model = WordModel(
            begin = word.begin,
            end = word.end,
            duration = word.duration,
            text = word.text.orEmpty(),
            metadata = word.metadata
        )

        model.previous = previousModel
        previousModel?.next = model

        models.add(model)
        previousModel = model
    }
    return models
}