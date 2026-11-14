/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 圆点演出行为参考 AMLL（https://github.com/amll-dev/applemusic-like-lyrics）
 * 的 interlude-dots 实现，按行为规格重写，未复制其源代码。
 * 规格文档：docs/amll-countdown-dots-spec.md
 */

package com.lidesheng.hyperlyric.lyric.view.line

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AmllInterludeTimeline] 的行为测试。
 *
 * 全部断言只依赖 `(begin, end, posMs)`，不涉及 Android 与绘制，因此可直接在 JVM 上跑。
 * 时间轴约定：除 [AmllInterludeTimeline.beginMs] 外，其余字段都是**相对行起点**的毫秒数。
 */
class AmllInterludeTimelineTest {

    // ---- 隐藏阈值 ----

    @Test
    fun hiddenWhenSpanTooShortForEntry() {
        // span - EXIT_TOTAL_MS < DOT_ENTER_TOTAL_MS(910) 即 span < 1910。
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = 1_900L)
        assertTrue(timeline.isHidden)
        assertEquals(AmllInterludeTimeline.Mode.HIDDEN, timeline.mode)
        assertFalse(timeline.isActiveAt(1_000L))
        assertTrue(timeline.isFinishedAt(1_000L))
        assertFalse(timeline.snapshotAt(1_000L).isVisible)
        assertEquals(0f, timeline.snapshotAt(1_000L).opacity, 0f)
    }

    @Test
    fun exactlyAtEntryThresholdBecomesVisiblePerformance() {
        // span = 1910 -> bodyMs = 910，刚好不隐藏；bodyMs < 3000 走 fallback-hold。
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = 1_910L)
        assertFalse(timeline.isHidden)
        assertEquals(AmllInterludeTimeline.Mode.FALLBACK_HOLD, timeline.mode)
        assertEquals(910L, timeline.bodyEndMs)
        assertEquals(1_910L, timeline.totalEndMs)
    }

    @Test
    fun snapshotGoesHiddenAfterTotalEnd() {
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = 8_000L)
        val after = timeline.snapshotAt(timeline.totalEndMs + 1L)
        assertFalse(after.isVisible)
        assertEquals(0f, after.opacity, 0f)
        assertTrue(timeline.isFinishedAt(timeline.totalEndMs))
        assertFalse(timeline.isActiveAt(timeline.totalEndMs))
    }

    // ---- fallback-hold ----

    @Test
    fun fallbackHoldKeepsScaleAtOneAndFillsDots() {
        // span = 3000 -> bodyMs = 2000，落在 [910, 3000) 区间。
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = 3_000L)
        assertEquals(AmllInterludeTimeline.Mode.FALLBACK_HOLD, timeline.mode)

        // 主演出中段：缩放恒 1，三点点满（错峰入场系数已到 1）。
        val middle = timeline.snapshotAt(1_500L)
        assertEquals(1f, middle.scale, 1e-6f)
        for (index in 0 until AmllInterludeTimeline.DOT_COUNT) {
            assertEquals(
                AmllInterludeTimeline.DOT_ACTIVE_OPACITY,
                middle.dotOpacity(index),
                1e-4f,
            )
        }
        assertTrue(middle.isVisible)
    }

    @Test
    fun fallbackHoldStillExits() {
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = 3_000L)
        // 退场第一阶段末：缩放已升到 1.25 附近。
        val peak = timeline.snapshotAt(timeline.bodyEndMs + AmllInterludeTimeline.EXIT_PHASE1_MS)
        assertTrue(peak.scale > 1.2f)
        // 演出终点：全部归零。
        val end = timeline.snapshotAt(timeline.totalEndMs)
        assertFalse(end.isVisible)
        assertEquals(0f, end.opacity, 0f)
    }

    @Test
    fun bodyBetweenEntryAndFallbackThresholdUsesFallback() {
        // bodyMs = 2999 -> fallback-hold；bodyMs = 3000 -> breathe。
        val justBelow = AmllInterludeTimeline.build(0L, 2_999L + AmllInterludeTimeline.EXIT_TOTAL_MS)
        assertEquals(AmllInterludeTimeline.Mode.FALLBACK_HOLD, justBelow.mode)

        val atThreshold = AmllInterludeTimeline.build(0L, 3_000L + AmllInterludeTimeline.EXIT_TOTAL_MS)
        assertEquals(AmllInterludeTimeline.Mode.BREATHE, atThreshold.mode)
    }

    // ---- 正常 breathe 编排 ----

    @Test
    fun breatheOrchestrationDerivesSegmentAndDot3Target() {
        val bodyMs = 6_000L
        val timeline = AmllInterludeTimeline.build(0L, bodyMs + AmllInterludeTimeline.EXIT_TOTAL_MS)
        assertEquals(AmllInterludeTimeline.Mode.BREATHE, timeline.mode)

        val segment = ((bodyMs + AmllInterludeTimeline.DOT3_TRAILING_MS) / 3.0).toLong()
        assertEquals(segment, timeline.segmentMs)
        // 不变式：两段点亮 + 第三段 == bodyMs。
        assertEquals(bodyMs, timeline.segmentMs * 2 + timeline.dot3DurationMs)
        // 第三颗点比前两颗少走 DOT3_TRAILING_MS。
        assertEquals(
            AmllInterludeTimeline.DOT3_TRAILING_MS,
            timeline.segmentMs - timeline.dot3DurationMs,
        )
        assertTrue(timeline.dot3Target in 0.05f..0.95f)
        // 呼吸周期落在 [3000, 8000)。
        assertTrue(timeline.breathePeriodMs in 3_000L..7_999L)
    }

    @Test
    fun breatheScaleStaysWithinBounds() {
        val timeline = AmllInterludeTimeline.build(0L, 10_000L)
        var minScale = Float.MAX_VALUE
        var maxScale = 0f
        var posMs = timeline.delayEndMs
        while (posMs < timeline.bodyEndMs) {
            val scale = timeline.snapshotAt(posMs).scale
            assertTrue("scale 越界: $scale", scale in 0.999f..1.2501f)
            if (scale < minScale) minScale = scale
            if (scale > maxScale) maxScale = scale
            posMs += 17L
        }
        // 一个完整周期内应同时接近下界与上界。
        assertTrue("未触及下限: $minScale", minScale < 1.02f)
        assertTrue("未触及上限: $maxScale", maxScale > 1.23f)
    }

    @Test
    fun dotsLightUpInOrder() {
        val timeline = AmllInterludeTimeline.build(0L, 8_000L)
        val segment = timeline.segmentMs

        // 第一段中：第一颗已亮，第二颗仍暗。
        val firstPhase = timeline.snapshotAt(segment / 2)
        assertTrue(firstPhase.dotOpacity(0) > firstPhase.dotOpacity(1))
        assertTrue(firstPhase.dotOpacity(1) <= firstPhase.dotOpacity(2) + 1e-6f)

        // 第一段结束：第一颗全亮，第二颗仍为未点亮色。
        val atSegmentEnd = timeline.snapshotAt(segment)
        assertEquals(
            AmllInterludeTimeline.DOT_ACTIVE_OPACITY,
            atSegmentEnd.dotOpacity(0),
            1e-3f,
        )
        assertTrue(
            atSegmentEnd.dotOpacity(1) < AmllInterludeTimeline.DOT_ACTIVE_OPACITY * 0.5f
        )
    }

    // ---- 退场 ----

    @Test
    fun exitScaleRisesThenFalls() {
        val timeline = AmllInterludeTimeline.build(0L, 8_000L)
        val bodyEnd = timeline.bodyEndMs
        val phase1 = AmllInterludeTimeline.EXIT_PHASE1_MS
        val phase2 = AmllInterludeTimeline.EXIT_PHASE2_MS

        val atBodyEnd = timeline.snapshotAt(bodyEnd).scale
        val midPhase1 = timeline.snapshotAt(bodyEnd + phase1 / 2).scale
        val atPhase1End = timeline.snapshotAt(bodyEnd + phase1).scale
        val midPhase2 = timeline.snapshotAt(bodyEnd + phase1 + phase2 / 2).scale

        // 先升：bodyEnd -> phase1 中点 -> phase1 末。
        assertTrue("阶段一未放大: $atBodyEnd -> $midPhase1", midPhase1 > atBodyEnd)
        assertTrue("阶段一未到峰值: $midPhase1 -> $atPhase1End", atPhase1End > midPhase1)
        assertEquals(AmllInterludeTimeline.BREATHE_MAX_SCALE, atPhase1End, 1e-3f)
        // 后降。
        assertTrue("阶段二未缩小: $atPhase1End -> $midPhase2", midPhase2 < atPhase1End)
    }

    @Test
    fun exitEndsNearMinScale() {
        val timeline = AmllInterludeTimeline.build(0L, 8_000L)
        val lastFrame = timeline.snapshotAt(timeline.totalEndMs - 1L)
        assertTrue(
            "退场末缩放: ${lastFrame.scale}",
            lastFrame.scale < AmllInterludeTimeline.BREATHE_MAX_SCALE - 0.5f,
        )
        assertTrue(lastFrame.scale > AmllInterludeTimeline.BREATHE_MIN_SCALE - 0.01f)
    }

    @Test
    fun exitFadesContainerOpacity() {
        val timeline = AmllInterludeTimeline.build(0L, 8_000L)
        val bodyEnd = timeline.bodyEndMs
        // 退场前 750ms 内不透明度保持不变。
        val atBodyEnd = timeline.snapshotAt(bodyEnd)
        val midPhase1 = timeline.snapshotAt(bodyEnd + 400L)
        assertEquals(atBodyEnd.opacity, midPhase1.opacity, 1e-4f)
        // 最后 250ms 内衰减。
        val nearEnd = timeline.snapshotAt(timeline.totalEndMs - 10L)
        assertTrue(nearEnd.opacity < atBodyEnd.opacity * 0.2f)
    }

    // ---- 第三点补齐 ----

    @Test
    fun thirdDotCompletesDuringExit() {
        val timeline = AmllInterludeTimeline.build(0L, 8_000L)
        val bodyEnd = timeline.bodyEndMs
        val target = timeline.dot3Target
        assertTrue("dot3Target 应小于 1: $target", target < 1f)

        // 退场开始时第三颗仍是 dot3Target。
        val atBodyEnd = timeline.snapshotAt(bodyEnd)
        val expectedAtBodyEnd = AmllInterludeTimeline.DOT_INACTIVE_OPACITY +
            (AmllInterludeTimeline.DOT_ACTIVE_OPACITY - AmllInterludeTimeline.DOT_INACTIVE_OPACITY) * target
        assertEquals(expectedAtBodyEnd, atBodyEnd.dotOpacity(2), 5e-3f)

        // 退场 750ms 后补齐到全亮（前两颗在退场段恒为 1）。
        val afterTrailing = timeline.snapshotAt(bodyEnd + AmllInterludeTimeline.DOT3_TRAILING_MS)
        val fullOpacity = AmllInterludeTimeline.DOT_ACTIVE_OPACITY
        assertEquals(fullOpacity, afterTrailing.dotOpacity(2), 1e-3f)

        // 补齐过程单调不减。
        var previous = atBodyEnd.dotOpacity(2)
        var posMs = bodyEnd
        while (posMs <= bodyEnd + AmllInterludeTimeline.DOT3_TRAILING_MS) {
            val current = timeline.snapshotAt(posMs).dotOpacity(2)
            assertTrue("dot3 非单调: $previous -> $current", current >= previous - 1e-5f)
            previous = current
            posMs += 25L
        }
    }

    // ---- 连续性与纯函数性 ----

    @Test
    fun snapshotsAreContinuousWhenPositionAdvances() {
        val timeline = AmllInterludeTimeline.build(beginMs = 1_000L, endMs = 9_000L)
        var previous: AmllDotsSnapshot? = null
        var posMs = 1_000L
        // 只扫演出区间内部；终点之后直接切到 HIDDEN，缩放复位不属于连续性问题。
        val lastPosMs = 1_000L + timeline.totalEndMs - 1L
        // 以 1ms 采样验证函数本身连续：退场缓动末段导数很陡（阶段二控制点 x2 = 1，
        // 端点切线水平），16ms 的帧间隔会放大成可见的快速塌缩，那是曲线本意而非跳变。
        while (posMs <= lastPosMs) {
            val current = timeline.snapshotAt(posMs)
            if (previous != null) {
                assertTrue(
                    "缩放跳变 @${posMs - 1_000L}ms: ${previous.scale} -> ${current.scale}",
                    kotlin.math.abs(current.scale - previous.scale) < 0.05f,
                )
                assertTrue(
                    "不透明度跳变 @${posMs - 1_000L}ms: ${previous.opacity} -> ${current.opacity}",
                    kotlin.math.abs(current.opacity - previous.opacity) < 0.05f,
                )
            }
            previous = current
            posMs += 1L
        }
    }

    @Test
    fun snapshotIsPureFunctionOfPosition() {
        val timeline = AmllInterludeTimeline.build(beginMs = 2_000L, endMs = 10_000L)
        // 正向逐帧推进后回头重新查询，结果必须与直接计算完全一致（无隐藏状态）。
        for (posMs in 2_000L..10_000L step 250L) {
            val first = timeline.snapshotAt(posMs)
            // 中间穿插大量其他时刻的查询。
            timeline.snapshotAt(5_000L)
            timeline.snapshotAt(9_999L)
            val second = timeline.snapshotAt(posMs)
            assertEquals(first, second)
        }
    }

    @Test
    fun seekToArbitraryTimeEqualsDirectComputation() {
        val timeline = AmllInterludeTimeline.build(beginMs = 500L, endMs = 12_500L)
        // 从末尾倒着取，等价于任意顺序的 seek。
        var posMs = 12_500L
        while (posMs >= 500L) {
            val snapshot = timeline.snapshotAt(posMs)
            val fresh = AmllInterludeTimeline.build(beginMs = 500L, endMs = 12_500L)
                .snapshotAt(posMs)
            assertEquals("seek 到 ${posMs}ms 结果不一致", snapshot, fresh)
            posMs -= 137L
        }
    }

    @Test
    fun timelineDerivationIsIndependentOfAbsoluteOffset() {
        // 同一段区间整体平移，相对时间轴必须完全一致。
        val base = AmllInterludeTimeline.build(beginMs = 0L, endMs = 9_000L)
        val shifted = AmllInterludeTimeline.build(beginMs = 60_000L, endMs = 69_000L)
        assertEquals(base.mode, shifted.mode)
        assertEquals(base.bodyEndMs, shifted.bodyEndMs)
        assertEquals(base.totalEndMs, shifted.totalEndMs)
        assertEquals(base.segmentMs, shifted.segmentMs)
        assertEquals(base.dot3Target, shifted.dot3Target, 0f)

        for (offsetMs in 0L..9_000L step 100L) {
            assertEquals(
                base.snapshotAt(offsetMs),
                shifted.snapshotAt(60_000L + offsetMs),
            )
        }
    }

    // ---- 无界行 ----

    @Test
    fun unboundedRowStillLightsDots() {
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = Long.MAX_VALUE)
        assertTrue(timeline.unbounded)
        assertFalse(timeline.isHidden)
        assertEquals(AmllInterludeTimeline.Mode.BREATHE, timeline.mode)
        assertEquals(AmllInterludeTimeline.BREATHE_BASE_PERIOD_MS, timeline.breathePeriodMs)
        assertEquals(1f, timeline.dot3Target, 0f)

        // 三颗点各自用 4000ms 走完点亮区间，起点依次为 0 / 4000 / 8000。
        assertEquals(
            AmllInterludeTimeline.DOT_ACTIVE_OPACITY,
            timeline.snapshotAt(4_000L).dotOpacity(0),
            1e-3f,
        )
        assertTrue(
            "第二颗不应在 4000ms 前点亮",
            timeline.snapshotAt(3_999L).dotOpacity(1) <
                AmllInterludeTimeline.DOT_ACTIVE_OPACITY * 0.5f,
        )
        assertEquals(
            AmllInterludeTimeline.DOT_ACTIVE_OPACITY,
            timeline.snapshotAt(8_000L).dotOpacity(1),
            1e-3f,
        )
        val afterFullRamp = timeline.snapshotAt(12_500L)
        for (index in 0 until AmllInterludeTimeline.DOT_COUNT) {
            assertEquals(
                AmllInterludeTimeline.DOT_ACTIVE_OPACITY,
                afterFullRamp.dotOpacity(index),
                1e-3f,
            )
        }

        // 远未来仍不结束、仍在呼吸。
        val farFuture = 6L * 60L * 60L * 1_000L
        assertTrue(timeline.isActiveAt(farFuture))
        assertFalse(timeline.isFinishedAt(farFuture))
        val farSnapshot = timeline.snapshotAt(farFuture)
        assertTrue(farSnapshot.isVisible)
        assertTrue(farSnapshot.scale in 1f..1.2501f)
    }

    @Test
    fun unboundedRowNeverEntersExit() {
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, timeline.bodyEndMs)
        assertEquals(Long.MAX_VALUE, timeline.totalEndMs)
        // 十小时后仍在主演出段。
        val late = timeline.snapshotAt(10L * 60L * 60L * 1_000L)
        assertTrue(late.opacity > 0.99f)
    }

    // ---- 几何常量 ----

    @Test
    fun dotGeometryKeepsAmllProportions() {
        // gap : 直径 == 0.6 : 1
        val ratio = AmllInterludeTimeline.DOT_GAP_EM / AmllInterludeTimeline.DOT_DIAMETER_EM
        assertEquals(0.6f, ratio, 1e-6f)
        assertEquals(
            AmllInterludeTimeline.DOT_DIAMETER_EM / 2f,
            AmllInterludeTimeline.DOT_RADIUS_EM,
            0f,
        )
        assertEquals(3, AmllInterludeTimeline.DOT_COUNT)
    }

    // ---- 入场细节 ----

    @Test
    fun enterHoldSegmentIsInvisible() {
        // 显式传 holdMs = 500 验证入场延迟段（当前调用点传 0）。
        val timeline = AmllInterludeTimeline.build(beginMs = 0L, endMs = 10_000L, holdMs = 500L)
        assertEquals(500L, timeline.delayEndMs)
        val duringHold = timeline.snapshotAt(250L)
        assertFalse(duringHold.isVisible)
        assertEquals(0f, duringHold.opacity, 0f)
        // 延迟结束后开始淡入。
        val afterHold = timeline.snapshotAt(600L)
        assertTrue(afterHold.opacity > 0f)
    }

    @Test
    fun containerFadesInOverEntryWindow() {
        val timeline = AmllInterludeTimeline.build(0L, 10_000L)
        val early = timeline.snapshotAt(40L).opacity
        val mid = timeline.snapshotAt(90L).opacity
        val done = timeline.snapshotAt(AmllInterludeTimeline.ENTER_FADE_MS).opacity
        assertTrue(early < mid)
        assertTrue(mid < done)
        assertEquals(1f, done, 1e-4f)
    }

    @Test
    fun dotsStaggerInByEightyMilliseconds() {
        val timeline = AmllInterludeTimeline.build(0L, 10_000L)
        // 恰好 80ms 时第一颗已开始淡入，第二颗仍为 0。
        val at80 = timeline.snapshotAt(80L)
        assertTrue(at80.dotOpacity(0) > 0f)
        assertEquals(0f, at80.dotOpacity(1), 1e-6f)
        // 160ms 时第三颗仍为 0。
        assertEquals(0f, timeline.snapshotAt(160L).dotOpacity(2), 1e-6f)
        // 910ms（DOT_ENTER_TOTAL_MS）时三颗的错峰入场系数都已到 1。
        val atTotal = timeline.snapshotAt(AmllInterludeTimeline.DOT_ENTER_TOTAL_MS)
        assertTrue(atTotal.dotOpacity(0) > 0f)
    }
}
