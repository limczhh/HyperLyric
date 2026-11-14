/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import kotlin.math.abs

/**
 * 三次贝塞尔缓动求解器。
 *
 * 曲线固定经过 `P0 = (0, 0)` 与 `P3 = (1, 1)`，由两个控制点 `(x1, y1)`、`(x2, y2)` 决定，
 * 与 CSS `cubic-bezier(x1, y1, x2, y2)` 语义一致：给定横轴进度 `x`，求纵轴进度 `y`。
 *
 * 求解策略（与浏览器实现同类）：
 * 1. 11 个等距采样点构成 `x -> t` 的初值表，线性插值给出 Newton 初值；
 * 2. 最多 8 次 Newton-Raphson 迭代（需要 `x(t)` 单调且导数不退化）；
 * 3. 导数过小、迭代越界或收敛失败时退回 `[0, 1]` 区间二分，最多 20 次。
 *
 * 契约：
 * - 端点精确：`solve(x <= 0) == 0`、`solve(x >= 1) == 1`；
 * - 在 `[0, 1]` 上单调不减（控制点横坐标均落在 `[0, 1]` 时成立）；
 * - 精度 `|solve(x) - 精确值| <= 1e-4`；
 * - 实例无状态，可安全跨线程共享，**应在构造期一次性建好并缓存**，不要在每帧绘制时新建。
 */
internal class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float,
) {

    /** 恒等缓动（线性）无需迭代求解。 */
    private val isLinear: Boolean = x1 == y1 && x2 == y2

    /** `x(t)` 的等距采样表，仅用于给 Newton 迭代提供初值。 */
    private val sampleXs: FloatArray = FloatArray(SAMPLE_COUNT + 1) { index ->
        axis(index.toFloat() / SAMPLE_COUNT, x1, x2)
    }

    /** 求 `y = f(x)`。 */
    fun solve(x: Float): Float {
        if (x.isNaN()) return 0f
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        if (isLinear) return x
        return axis(solveParameter(x), y1, y2)
    }

    /** 求参数 `t` 使 `x(t) == x`，再代入纵轴曲线。 */
    private fun solveParameter(x: Float): Float {
        var t = guessParameter(x)

        var iteration = 0
        while (iteration < NEWTON_ITERATIONS) {
            val currentX = axis(t, x1, x2)
            val error = currentX - x
            if (abs(error) < PRECISION) return t
            val derivative = axisDerivative(t, x1, x2)
            if (derivative < MIN_DERIVATIVE) break
            val next = t - error / derivative
            if (next < 0f || next > 1f) break
            t = next
            iteration++
        }

        var low = 0f
        var high = 1f
        var middle = t.coerceIn(0f, 1f)
        repeat(BISECTION_ITERATIONS) {
            val value = axis(middle, x1, x2)
            if (abs(value - x) < PRECISION) return middle
            if (value < x) low = middle else high = middle
            middle = (low + high) * 0.5f
        }
        return middle
    }

    /** 采样表线性插值，给出 `t` 的初值。 */
    private fun guessParameter(x: Float): Float {
        var lowIndex = 0
        var highIndex = SAMPLE_COUNT
        while (lowIndex < highIndex) {
            val middle = (lowIndex + highIndex) / 2
            if (sampleXs[middle] < x) lowIndex = middle + 1 else highIndex = middle
        }
        if (lowIndex == 0) return 0f
        val previousX = sampleXs[lowIndex - 1]
        val currentX = sampleXs[lowIndex]
        if (currentX <= previousX) return (lowIndex - 1).toFloat() / SAMPLE_COUNT
        val ratio = (x - previousX) / (currentX - previousX)
        return ((lowIndex - 1) + ratio.coerceIn(0f, 1f)) / SAMPLE_COUNT
    }

    private companion object {
        const val SAMPLE_COUNT = 10
        const val NEWTON_ITERATIONS = 8
        const val BISECTION_ITERATIONS = 20
        const val PRECISION = 1e-6f
        const val MIN_DERIVATIVE = 1e-4f

        /** 一维三次贝塞尔：`3(1-t)²t·a1 + 3(1-t)t²·a2 + t³`，端点固定为 0 与 1。 */
        fun axis(t: Float, a1: Float, a2: Float): Float {
            val inverse = 1f - t
            return 3f * inverse * inverse * t * a1 +
                3f * inverse * t * t * a2 +
                t * t * t
        }

        /** `axis` 对 `t` 的导数。 */
        fun axisDerivative(t: Float, a1: Float, a2: Float): Float {
            val inverse = 1f - t
            return 3f * inverse * inverse * a1 +
                6f * inverse * t * (a2 - a1) +
                3f * t * t * (1f - a2)
        }
    }
}
