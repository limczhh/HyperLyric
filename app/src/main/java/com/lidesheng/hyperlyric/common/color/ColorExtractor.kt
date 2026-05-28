/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.common.color

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 主色主导级颜色提取器（Theme-Adaptive Edition）。
 * 优化点：支持弹性颜色数量返回，自动生成适配深/浅背景的两套方案。
 */
object ColorExtractor {

    private const val DEFAULT_MAX_COLORS = 4
    private const val MAX_SAMPLE_PIXELS = 100 * 100
    private const val KMEANS_ITERATIONS = 15
    private const val SIGMA_SQ_2 = 2.0f * 25.0f * 25.0f

    private const val HUE_THRESHOLD = 45f
    private const val DIST_THRESHOLD = 20.0

    /**
     * 提取具备背景适配能力的颜色套件。
     * @param bitmap 输入图片
     * @param maxColors 提取颜色的最大数量
     */
    fun extractThemePalette(bitmap: Bitmap, maxColors: Int = DEFAULT_MAX_COLORS): ThemePalette {
        val raw = extract(bitmap, maxColors)

        return ThemePalette(
            rawColors = raw,
            onWhiteBackground = raw.map { adaptForBackground(it, isDarkBg = false) },
            onBlackBackground = raw.map { adaptForBackground(it, isDarkBg = true) }
        )
    }

    /**
     * 核心提取逻辑（返回原始代表色）
     */
    fun extract(bitmap: Bitmap, maxColors: Int = DEFAULT_MAX_COLORS): List<Int> {
        if (bitmap.isRecycled) return emptyList()

        val scaled = scaleBitmap(bitmap, MAX_SAMPLE_PIXELS)
        val size = scaled.width * scaled.height
        val rawPixels = IntArray(size)
        scaled.getPixels(rawPixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled != bitmap) scaled.recycle()

        val lArr = FloatArray(size); val aArr = FloatArray(size); val bArr = FloatArray(size); val wArr = FloatArray(size)
        val outLab = DoubleArray(3)
        var sumL = 0f; var sumA = 0f; var sumB = 0f; var totalW = 0f

        for (i in 0 until size) {
            ColorUtils.colorToLAB(rawPixels[i], outLab)
            val l = outLab[0].toFloat(); val a = outLab[1].toFloat(); val b = outLab[2].toFloat()
            val chroma = sqrt(a * a + b * b)
            lArr[i] = l; aArr[i] = a; bArr[i] = b
            val weight = if (chroma > 5.0f) chroma * chroma else 0.1f
            wArr[i] = weight
            sumL += l * weight; sumA += a * weight; sumB += b * weight; totalW += weight
        }

        if (totalW == 0f) return emptyList()

        val anchorL = sumL / totalW; val anchorA = sumA / totalW; val anchorB = sumB / totalW
        for (i in 0 until size) {
            val distSq = (lArr[i] - anchorL).let { it * it } +
                    (aArr[i] - anchorA).let { it * it } +
                    (bArr[i] - anchorB).let { it * it }
            wArr[i] *= (0.3f + 0.7f * exp(-distSq / SIGMA_SQ_2))
        }

        val k = (maxColors * 3).coerceAtMost(size)
        val clusters = kMeansLabOptimized(lArr, aArr, bArr, wArr, k)

        return filterRepresentations(clusters, maxColors)
    }

    /**
     * 针对背景进行亮度自适应调整
     * @param color 原始颜色
     * @param isDarkBg 目标背景是否为深色
     */
    private fun adaptForBackground(color: Int, isDarkBg: Boolean): Int {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        val currentL = lab[0]

        val targetL = if (isDarkBg) {
            // 深色背景：亮度应在 70-85 之间
            if (currentL < 70.0) 70.0 else if (currentL > 85.0) 85.0 else currentL
        } else {
            // 浅色背景：亮度应在 30-45 之间
            if (currentL > 45.0) 45.0 else if (currentL < 30.0) 30.0 else currentL
        }

        return ColorUtils.LABToColor(targetL, lab[1], lab[2])
    }

    private fun filterRepresentations(clusters: List<FloatArray>, maxColors: Int): List<Int> {
        val selected = mutableListOf<Int>()
        val selectedHues = mutableListOf<Float>()
        val outHsl = FloatArray(3)

        for (cluster in clusters) {
            if (selected.size >= maxColors) break
            val color = ColorUtils.LABToColor(cluster[0].toDouble(), cluster[1].toDouble(), cluster[2].toDouble())
            ColorUtils.colorToHSL(color, outHsl)
            val h = outHsl[0]; val s = outHsl[1]

            var isDistinct = true
            if (s > 0.15f) {
                for (sh in selectedHues) {
                    val diff = abs(h - sh)
                    if ((if (diff > 180) 360 - diff else diff) < HUE_THRESHOLD) {
                        isDistinct = false; break
                    }
                }
            } else {
                for (sc in selected) {
                    if (calculateLabDistance(color, sc) < DIST_THRESHOLD) {
                        isDistinct = false; break
                    }
                }
            }

            if (isDistinct) {
                selected.add(color)
                selectedHues.add(h)
            }
        }
        if (selected.isEmpty() && clusters.isNotEmpty()) {
            selected.add(ColorUtils.LABToColor(clusters[0][0].toDouble(), clusters[0][1].toDouble(), clusters[0][2].toDouble()))
        }
        return selected
    }

    private fun calculateLabDistance(c1: Int, c2: Int): Double {
        val lab1 = DoubleArray(3); val lab2 = DoubleArray(3)
        ColorUtils.colorToLAB(c1, lab1); ColorUtils.colorToLAB(c2, lab2)
        return sqrt((lab1[0] - lab2[0]).let { it * it } + (lab1[1] - lab2[1]).let { it * it } + (lab1[2] - lab2[2]).let { it * it })
    }

    private fun kMeansLabOptimized(lArr: FloatArray, aArr: FloatArray, bArr: FloatArray, wArr: FloatArray, k: Int): List<FloatArray> {
        val size = lArr.size
        val cL = FloatArray(k); val cA = FloatArray(k); val cB = FloatArray(k); val assignments = IntArray(size)
        repeat(k) { i -> val idx = Random.nextInt(size); cL[i] = lArr[idx]; cA[i] = aArr[idx]; cB[i] = bArr[idx] }
        repeat(KMEANS_ITERATIONS) {
            for (i in 0 until size) {
                var minDist = Float.MAX_VALUE; var closest = 0
                for (ci in 0 until k) {
                    val d = (lArr[i] - cL[ci]).let { it * it } + (aArr[i] - cA[ci]).let { it * it } + (bArr[i] - cB[ci]).let { it * it }
                    if (d < minDist) { minDist = d; closest = ci }
                }
                assignments[i] = closest
            }
            val nL = FloatArray(k); val nA = FloatArray(k); val nB = FloatArray(k); val nW = FloatArray(k)
            for (i in 0 until size) {
                val ci = assignments[i]; val w = wArr[i]
                nL[ci] += lArr[i] * w; nA[ci] += aArr[i] * w; nB[ci] += bArr[i] * w; nW[ci] += w
            }
            for (ci in 0 until k) if (nW[ci] > 0) { cL[ci] = nL[ci] / nW[ci]; cA[ci] = nA[ci] / nW[ci]; cB[ci] = nB[ci] / nW[ci] }
        }
        return List(k) { i ->
            var tw = 0f
            for (j in 0 until size) if (assignments[j] == i) tw += wArr[j]
            floatArrayOf(cL[i], cA[i], cB[i], tw)
        }.sortedByDescending { it[3] }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxPixels: Int): Bitmap {
        val totalPixels = bitmap.width * bitmap.height
        if (totalPixels <= maxPixels) return bitmap
        val scale = sqrt(maxPixels.toFloat() / totalPixels)
        return bitmap.scale((bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1))
    }

    /**
     * 主题调色板结果集
     */
    data class ThemePalette(
        val rawColors: List<Int>,          // 原始提取颜色
        val onWhiteBackground: List<Int>,  // 适合显示在白色背景上的颜色（高对比度暗色）
        val onBlackBackground: List<Int>   // 适合显示在黑色背景上的颜色（高对比度亮色）
    )
}
