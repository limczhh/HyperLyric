/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.common.lyric.METADATA_KEY_AMLL_TTML_SOURCE
import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

internal const val METADATA_TITLE_LINE = "TitleLine"
internal const val METADATA_COUNTDOWN_LINE = "CountdownLine"

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_TITLE_LINE, false) == true

fun IRichLyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricModel.isCountdownLine(): Boolean =
    metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

/**
 * 构造无文本的倒计时占位行。
 *
 * 该行同时带有标题行与倒计时行标记，渲染层据此改用倒计时圆点渲染器；
 * 行内容为空，宿主校验与内容选择都会把它当作占位内容处理。
 */
internal fun countdownPlaceholderLine(begin: Long, end: Long): RichLyricLine =
    RichLyricLine(begin = begin, end = end, duration = end - begin).apply {
        metadata = lyricMetadataOf(
            METADATA_TITLE_LINE to "true",
            METADATA_COUNTDOWN_LINE to "true"
        )
    }

/** 当前歌词行是否由 AMLL TTML 解析器产出，即歌词是否来自 AMLL TTML Database。 */
internal fun IRichLyricLine?.isAmllTtmlLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_KEY_AMLL_TTML_SOURCE, false) == true
