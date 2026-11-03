package com.lidesheng.hyperlyric.lyric.view.line

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UnsungOpacityTest {

    @Test
    fun opacityMapsToAbsoluteAlpha() {
        assertEquals(0, UnsungOpacity.toAlpha(0f))
        assertEquals(128, UnsungOpacity.toAlpha(0.5f))
        assertEquals(255, UnsungOpacity.toAlpha(1f))
    }

    @Test
    fun opacityOutOfRangeIsClamped() {
        assertEquals(0, UnsungOpacity.toAlpha(-0.5f))
        assertEquals(255, UnsungOpacity.toAlpha(1.5f))
    }

    @Test
    fun nonFiniteOpacityFallsBackToOpaque() {
        assertEquals(255, UnsungOpacity.toAlpha(Float.NaN))
        assertEquals(255, UnsungOpacity.toAlpha(Float.POSITIVE_INFINITY))
        assertEquals(255, UnsungOpacity.toAlpha(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun tintKeepsRgbAndOverridesAlpha() {
        assertEquals(0x80123456.toInt(), UnsungOpacity.tint(0xFF123456.toInt(), 0.5f))
        assertEquals(0x00123456, UnsungOpacity.tint(0xFF123456.toInt(), 0f))
        assertEquals(0xFF123456.toInt(), UnsungOpacity.tint(0x80123456.toInt(), 1f))
    }

    @Test
    fun defaultOpacityKeepsDefaultThemeLook() {
        // 默认主题的未演唱色是 0x80FFFFFF；默认不透明度 0.5 换算后仍是 0x80FFFFFF（外观不变）。
        assertEquals(0x80FFFFFF.toInt(), UnsungOpacity.tint(0x80FFFFFF.toInt(), 0.5f))
    }

    @Test
    fun sungOpacityOneKeepsOriginalHighlightColor() {
        // 已演唱不透明度 = 1：烘出的高亮色与原始颜色完全一致，默认外观不变。
        assertEquals(0xFFFFFFFF.toInt(), UnsungOpacity.tint(0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF00FF00.toInt(), UnsungOpacity.tint(0xFF00FF00.toInt(), 1f))
    }

    @Test
    fun sungOpacityZeroMakesHighlightFullyTransparent() {
        // 已演唱不透明度 = 0：高亮色 RGB 保留但 alpha 为 0（绘制结果全透明）。
        assertEquals(0x00FFFFFF, UnsungOpacity.tint(0xFFFFFFFF.toInt(), 0f))
        assertEquals(0x0000FF00, UnsungOpacity.tint(0xFF00FF00.toInt(), 0f))
    }

    @Test
    fun tintMapsWholeArrayWithoutMutatingSource() {
        val source = intArrayOf(0xFFFFFFFF.toInt(), 0xFF00FF00.toInt())
        assertArrayEquals(
            intArrayOf(0x80FFFFFF.toInt(), 0x8000FF00.toInt()),
            UnsungOpacity.tint(source, 0.5f)
        )
        assertEquals(0xFFFFFFFF.toInt(), source[0])
    }
}
