/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

internal const val METADATA_TITLE_LINE = "TitleLine"
internal const val METADATA_COUNTDOWN_LINE = "CountdownLine"

/**
 * 倒计时占位行样式标记。
 *
 * 标记随行的元数据一路带到 [LyricModel]，视图层据此选择渲染器，
 * 因此渲染器选择不依赖偏好设置，跨进程/跨视图树都稳定。
 */
internal const val METADATA_COUNTDOWN_STYLE = "CountdownStyle"

/**
 * 倒计时圆点的渲染样式。
 *
 * [LEGACY] 是原有的【倒计时圆点】选项，行为与观感必须保持不变；
 * [AMLL] 是新增的【倒计时圆点（AMLL 样式）】。
 */
internal enum class CountdownDotStyle {
    LEGACY,
    AMLL,
}

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_TITLE_LINE, false) == true

fun IRichLyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricModel.isCountdownLine(): Boolean =
    metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

/** 该行是否使用 AMLL 样式的倒计时圆点。 */
internal fun LyricModel.isAmllCountdownLine(): Boolean =
    isCountdownLine() && countdownDotStyle() == CountdownDotStyle.AMLL

/** 读取该行的倒计时样式标记，缺省为 [CountdownDotStyle.LEGACY]。 */
internal fun LyricModel.countdownDotStyle(): CountdownDotStyle =
    metadata?.get(METADATA_COUNTDOWN_STYLE)
        ?.let { value -> CountdownDotStyle.entries.firstOrNull { it.name == value } }
        ?: CountdownDotStyle.LEGACY

/**
 * 构造无文本的倒计时占位行。
 *
 * 该行同时带有标题行与倒计时行标记，渲染层据此改用倒计时圆点渲染器；
 * 行内容为空，宿主校验与内容选择都会把它当作占位内容处理。
 *
 * @param style 圆点样式，默认沿用原有的【倒计时圆点】。
 */
internal fun countdownPlaceholderLine(
    begin: Long,
    end: Long,
    style: CountdownDotStyle = CountdownDotStyle.LEGACY,
): RichLyricLine =
    RichLyricLine(begin = begin, end = end, duration = end - begin).apply {
        metadata = lyricMetadataOf(
            METADATA_TITLE_LINE to "true",
            METADATA_COUNTDOWN_LINE to "true",
            METADATA_COUNTDOWN_STYLE to style.name
        )
    }
