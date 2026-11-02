/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import kotlin.math.roundToInt

/**
 * 未演唱歌词不透明度的换算工具。
 *
 * 语义是“绝对值覆盖”：未演唱部分的 alpha 直接等于 round(255 × opacity)，
 * 不再与颜色自带的 alpha 相乘。因此 opacity = 1f 时未演唱与已演唱完全一致，
 * opacity = 0f 时未演唱部分完全透明。
 *
 * 这里刻意不依赖 android.graphics（颜色运算全部用位运算完成），
 * 以便在纯 JVM 单测中直接验证换算结果。
 */
internal object UnsungOpacity {
    private const val FULL_ALPHA = 255
    private const val RGB_MASK = 0x00FFFFFF

    /** 把 0f..1f 的不透明度换算成 0..255 的 alpha；非有限值（NaN/Inf）回退为完全不透明。 */
    fun toAlpha(opacity: Float): Int {
        if (!opacity.isFinite()) return FULL_ALPHA
        return (opacity.coerceIn(0f, 1f) * FULL_ALPHA).roundToInt()
    }

    /** 保留 [color] 的 RGB，仅用 [opacity] 覆盖 alpha。 */
    fun tint(color: Int, opacity: Float): Int =
        (color and RGB_MASK) or (toAlpha(opacity) shl 24)

    /** 对整组颜色逐个 [tint] 并返回新数组；只应在配置变化时调用，不要放进绘制路径。 */
    fun tint(colors: IntArray, opacity: Float): IntArray =
        IntArray(colors.size) { tint(colors[it], opacity) }
}
