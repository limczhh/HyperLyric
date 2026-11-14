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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * 一次倒计时圆点演出的不可变帧快照。
 *
 * @property scale 容器缩放，作用于三个圆点围绕内容盒中心的整体缩放。
 * @property opacity 容器不透明度，绘制时必须与每个圆点的不透明度相乘。
 * @property dotOpacities 三个圆点的最终不透明度（已含容器不透明度）。
 * @property isVisible 本帧是否有可见墨迹；为 `false` 时绘制方可直接跳过。
 */
internal class AmllDotsSnapshot(
    val scale: Float,
    val opacity: Float,
    val dotOpacities: FloatArray,
    val isVisible: Boolean,
) {

    fun dotOpacity(index: Int): Float = dotOpacities.getOrElse(index) { 0f }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AmllDotsSnapshot) return false
        return scale == other.scale &&
            opacity == other.opacity &&
            isVisible == other.isVisible &&
            dotOpacities.contentEquals(other.dotOpacities)
    }

    override fun hashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + dotOpacities.contentHashCode()
        return result
    }

    override fun toString(): String =
        "AmllDotsSnapshot(scale=$scale, opacity=$opacity, " +
            "dots=${dotOpacities.joinToString(prefix = "[", postfix = "]")}, visible=$isVisible)"

    companion object {
        /** 无墨迹快照：缩放复位、不透明度归零、三点全暗。 */
        val HIDDEN: AmllDotsSnapshot =
            AmllDotsSnapshot(scale = 1f, opacity = 0f, dotOpacities = floatArrayOf(0f, 0f, 0f), isVisible = false)
    }
}

/**
 * 倒计时圆点演出的时间编排：把 `(beginMs, endMs, holdMs)` 编译成一条固定的时间线，
 * 再由 [snapshotAt] 把任意媒体时刻映射成帧快照。
 *
 * 设计要点（见规格 6.3）：演出是**媒体时间的纯函数**。本类不持有跨帧动画状态，
 * 不累加帧间隔，也没有目标值与重锚阈值；同一 `posMs` 永远得到同一快照，因此
 * 倍速播放、拖动进度、暂停冻结都天然正确。唯一的时变输入由渲染器在每次位置推送时
 * 硬覆盖。
 *
 * 本类不依赖 Android，可被 JVM 单元测试直接驱动。
 */
internal class AmllInterludeTimeline private constructor(
    /** 演出起点，绝对媒体时间（毫秒）。 */
    val beginMs: Long,
    /** 入场延迟终点，**相对 [beginMs]**。 */
    val delayEndMs: Long,
    /** 主演出终点，相对 [beginMs]。 */
    val bodyEndMs: Long,
    /** 整段演出终点，相对 [beginMs]。 */
    val totalEndMs: Long,
    val mode: Mode,
    /** 呼吸周期，相对时间线内使用；[Mode.FALLBACK_HOLD] 时为 0。 */
    val breathePeriodMs: Long,
    /** 单颗圆点的点亮区间长度；[Mode.FALLBACK_HOLD] 时为 0。 */
    val segmentMs: Long,
    /** 第三颗圆点被压缩后的点亮区间长度。 */
    val dot3DurationMs: Long,
    /** 第三颗圆点在主演出结束时达到的点亮比例，缺口由退场段补齐。 */
    val dot3Target: Float,
    /** 无界行（跨度不小于 24 小时）标记，见规格 6.8。 */
    val unbounded: Boolean,
) {

    enum class Mode {
        /** 主演出伴随呼吸缩放。 */
        BREATHE,

        /** 主演出固定缩放 1，三点点满。 */
        FALLBACK_HOLD,

        /** 时长不足以完成入场，整段不演出。 */
        HIDDEN,
    }

    val isHidden: Boolean get() = mode == Mode.HIDDEN

    /** 相对 [beginMs] 的经过时间。时间线其余字段都在这个坐标系里。 */
    fun elapsedOf(posMs: Long): Long = posMs - beginMs

    /** 演出是否尚未结束（无界行恒为 `true`，直到行被替换）。 */
    fun isActiveAt(posMs: Long): Boolean {
        if (isHidden) return false
        if (unbounded) return true
        return elapsedOf(posMs) < totalEndMs
    }

    /** 演出是否已完整结束。 */
    fun isFinishedAt(posMs: Long): Boolean {
        if (isHidden) return true
        if (unbounded) return false
        return elapsedOf(posMs) >= totalEndMs
    }

    /** 是否已开始演出。 */
    fun isStartedAt(posMs: Long): Boolean = !isHidden && elapsedOf(posMs) > 0L

    /** 把绝对媒体时刻映射成帧快照。纯函数：无副作用、无状态。 */
    fun snapshotAt(posMs: Long): AmllDotsSnapshot {
        if (isHidden) return AmllDotsSnapshot.HIDDEN

        val elapsedMs = elapsedOf(posMs)
        if (!unbounded && elapsedMs >= totalEndMs) return AmllDotsSnapshot.HIDDEN
        if (elapsedMs < delayEndMs) {
            return AmllDotsSnapshot(scale = 1f, opacity = 0f, dotOpacities = ZERO_DOTS, isVisible = false)
        }

        val internalMs = elapsedMs - delayEndMs

        if (elapsedMs >= bodyEndMs) return exitSnapshot(internalMs, elapsedMs - bodyEndMs)

        val containerOpacity = enterOpacity(internalMs)
        if (mode == Mode.FALLBACK_HOLD) {
            return build(scale = 1f, opacity = containerOpacity, fractions = FULL_DOTS, internalMs = internalMs)
        }

        val cycleT = if (breathePeriodMs > 0L) {
            (internalMs % breathePeriodMs).toFloat() / breathePeriodMs.toFloat()
        } else {
            0f
        }
        val progress = breathingProgress(cycleT)
        val scale = if (progress <= 0.5f) {
            1f + (progress / 0.5f) * (BREATHE_MAX_SCALE - 1f)
        } else {
            BREATHE_MAX_SCALE - ((progress - 0.5f) / 0.5f) * (BREATHE_MAX_SCALE - 1f)
        }
        val fractions = floatArrayOf(
            computeDotFraction(startDelayMs = 0L, durationMs = segmentMs, internalMs = internalMs, target = 1f),
            computeDotFraction(startDelayMs = segmentMs, durationMs = segmentMs, internalMs = internalMs, target = 1f),
            computeDotFraction(
                startDelayMs = segmentMs * 2L,
                durationMs = dot3DurationMs,
                internalMs = internalMs,
                target = dot3Target,
            ),
        )
        return build(scale = scale, opacity = containerOpacity, fractions = fractions, internalMs = internalMs)
    }

    private fun exitSnapshot(internalMs: Long, exitElapsedMs: Long): AmllDotsSnapshot {
        // 容器不透明度只在退场最后 EXIT_FADE_MS 内衰减，前段保持不变。
        val fadeT = clamp01(
            (exitElapsedMs - (EXIT_TOTAL_MS - EXIT_FADE_MS)).toFloat() / EXIT_FADE_MS.toFloat()
        )
        val opacity = enterOpacity(internalMs) * (1f - EXIT_FADE_EASING.solve(fadeT))

        val scale = if (exitElapsedMs < EXIT_PHASE1_MS) {
            1f + EXIT_PHASE1_EASING.solve(exitElapsedMs.toFloat() / EXIT_PHASE1_MS.toFloat()) *
                (BREATHE_MAX_SCALE - 1f)
        } else {
            val phase2 = clamp01(
                (exitElapsedMs - EXIT_PHASE1_MS).toFloat() / EXIT_PHASE2_MS.toFloat()
            )
            BREATHE_MAX_SCALE -
                EXIT_PHASE2_EASING.solve(phase2) * (BREATHE_MAX_SCALE - BREATHE_MIN_SCALE)
        }

        // 第三颗圆点在主演出里少走 DOT3_TRAILING_MS，缺口由退场段线性补齐。
        val trailing = clamp01(exitElapsedMs.toFloat() / DOT3_TRAILING_MS.toFloat())
        val third = dot3Target + (1f - dot3Target) * trailing
        return build(
            scale = scale,
            opacity = opacity,
            fractions = floatArrayOf(1f, 1f, third),
            internalMs = internalMs,
        )
    }

    /** 把点亮分数折算成最终不透明度：分数 → 圆点不透明度 × 错峰入场 × 容器不透明度。 */
    private fun build(
        scale: Float,
        opacity: Float,
        fractions: FloatArray,
        internalMs: Long,
    ): AmllDotsSnapshot {
        val dots = FloatArray(DOT_COUNT) { index ->
            dotOpacity(fractions[index]) * dotEnterAlpha(index, internalMs) * opacity
        }
        val visible = opacity > 0f && dots.any { it > 0f }
        return AmllDotsSnapshot(scale = scale, opacity = opacity, dotOpacities = dots, isVisible = visible)
    }

    companion object {
        /** 圆点数量。 */
        const val DOT_COUNT = 3

        /**
         * 圆点直径，单位 em，以 textSize 为 1em 基准。
         *
         * 上游的容器字号基准与歌词行不同，照搬 0.3em 会明显偏小；
         * 放大到与原有倒计时圆点等大，两个选项切换时观感连续。
         */
        const val DOT_DIAMETER_EM = 0.5f

        /** 圆点半径，单位 em。 */
        const val DOT_RADIUS_EM = DOT_DIAMETER_EM / 2f

        /** 相邻圆点间距，单位 em；与直径保持 0.6 比 1，随直径等比放大。 */
        const val DOT_GAP_EM = 0.3f

        /**
         * 容器左右内边距，单位 em。
         *
         * 上游沿用歌词行自身的横向 padding，本渲染器已由视图层负责行内边距，
         * 这里只保留一个与圆点直径相称的留白，避免整行无谓变宽。
         */
        const val DOT_PADDING_X_EM = 0.25f

        /** 未点亮圆点不透明度。 */
        const val DOT_INACTIVE_OPACITY = 0.2f

        /** 完全点亮圆点不透明度。 */
        const val DOT_ACTIVE_OPACITY = 0.9f

        /** 入场延迟；当前所有调用点传 0，见规格 6.2。 */
        const val ENTER_HOLD_MS = 500L

        /** 容器淡入时长。 */
        const val ENTER_FADE_MS = 180L

        /** 单颗圆点自身淡入时长。 */
        const val DOT_ENTER_FADE_MS = 750L

        /** 相邻圆点错峰间隔。 */
        const val DOT_ENTER_STAGGER_MS = 80L

        /** 三颗圆点错峰完全淡入所需总时长，同时是主演出时长的下限。 */
        const val DOT_ENTER_TOTAL_MS =
            (DOT_COUNT - 1) * DOT_ENTER_STAGGER_MS + DOT_ENTER_FADE_MS

        /** 呼吸基准周期。 */
        const val BREATHE_BASE_PERIOD_MS = 4000L

        /** 主演出低于此长度时降级为固定缩放。 */
        const val FALLBACK_HOLD_THRESHOLD_MS = 3000L

        /** 呼吸缩放上限，contentWidth 必须按此预留。 */
        const val BREATHE_MAX_SCALE = 1.25f

        /**
         * 缩放下限，仅用于退场第二阶段。
         *
         * 上游为 0.4，按歌词行字号基准会让圆点缩到几乎不可见；
         * 提到 0.6 后呼吸仍有明显起伏，最小时也清晰可辨。
         */
        const val BREATHE_MIN_SCALE = 0.6f

        /** 退场第一阶段（放大蓄力）时长。 */
        const val EXIT_PHASE1_MS = 750L

        /** 退场第二阶段（缩小）时长。 */
        const val EXIT_PHASE2_MS = 250L

        /** 退场总时长。 */
        const val EXIT_TOTAL_MS = EXIT_PHASE1_MS + EXIT_PHASE2_MS

        /** 容器不透明度衰减时长。 */
        const val EXIT_FADE_MS = 250L

        /** 第三颗圆点在退场段补齐的时长，同时是它比前两颗少走的时长。 */
        const val DOT3_TRAILING_MS = 750L

        /** 跨度达到此值即视为无界行（纯音乐 / 无歌词占位）。 */
        const val UNBOUNDED_ROW_THRESHOLD_MS = 86_400_000L

        private val ZERO_DOTS = floatArrayOf(0f, 0f, 0f)
        private val FULL_DOTS = floatArrayOf(1f, 1f, 1f)

        // 五个缓动在类加载期一次性建好并复用，绘制期不再新建。
        private val LIGHTING_EASING = CubicBezierEasing(0.56f, 0.01f, 0.45f, 1f)
        private val ENTER_FADE_EASING = CubicBezierEasing(0.59f, 0.02f, 0.07f, 1f)
        private val EXIT_PHASE1_EASING = CubicBezierEasing(0.14f, 0.06f, 0.25f, 1f)
        private val EXIT_PHASE2_EASING = CubicBezierEasing(0.29f, 0.03f, 1f, 0.38f)
        private val EXIT_FADE_EASING = CubicBezierEasing(0.43f, 0.08f, 0.83f, 0.31f)

        /**
         * 编译时间线。
         *
         * @param beginMs 行起点，绝对媒体时间。
         * @param endMs 行终点，绝对媒体时间。
         * @param holdMs 入场延迟；按规格 6.2 恒为 0，保留参数便于日后调整。
         */
        fun build(beginMs: Long, endMs: Long, holdMs: Long = 0L): AmllInterludeTimeline {
            val span = saturatingSpan(beginMs, endMs)
            if (span >= UNBOUNDED_ROW_THRESHOLD_MS) return unbounded(beginMs)

            val delayEndMs = holdMs.coerceAtLeast(0L)
            val remainingMs = (span - delayEndMs).coerceAtLeast(0L)
            val bodyMs = remainingMs - EXIT_TOTAL_MS
            if (bodyMs < DOT_ENTER_TOTAL_MS) {
                return AmllInterludeTimeline(
                    beginMs = beginMs,
                    delayEndMs = 0L,
                    bodyEndMs = 0L,
                    totalEndMs = 0L,
                    mode = Mode.HIDDEN,
                    breathePeriodMs = 0L,
                    segmentMs = 0L,
                    dot3DurationMs = 0L,
                    dot3Target = 0f,
                    unbounded = false,
                )
            }

            val bodyEndMs = delayEndMs + bodyMs
            val totalEndMs = bodyEndMs + EXIT_TOTAL_MS

            if (bodyMs < FALLBACK_HOLD_THRESHOLD_MS) {
                return AmllInterludeTimeline(
                    beginMs = beginMs,
                    delayEndMs = delayEndMs,
                    bodyEndMs = bodyEndMs,
                    totalEndMs = totalEndMs,
                    mode = Mode.FALLBACK_HOLD,
                    breathePeriodMs = 0L,
                    segmentMs = 0L,
                    dot3DurationMs = 0L,
                    dot3Target = 1f,
                    unbounded = false,
                )
            }

            // 呼吸周期归一到不小于基准周期：周期永远落在 [3000, 8000)。
            val cycles = (bodyMs / BREATHE_BASE_PERIOD_MS).coerceAtLeast(1L)
            val breathePeriodMs = bodyMs / cycles
            val segmentMs = ((bodyMs + DOT3_TRAILING_MS).toDouble() / DOT_COUNT).roundToLong()
            val dot3DurationMs = bodyMs - segmentMs * 2L
            val dot3Target = if (segmentMs > 0L) {
                (dot3DurationMs.toFloat() / segmentMs.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }
            return AmllInterludeTimeline(
                beginMs = beginMs,
                delayEndMs = delayEndMs,
                bodyEndMs = bodyEndMs,
                totalEndMs = totalEndMs,
                mode = Mode.BREATHE,
                breathePeriodMs = breathePeriodMs,
                segmentMs = segmentMs,
                dot3DurationMs = dot3DurationMs,
                dot3Target = dot3Target,
                unbounded = false,
            )
        }

        /**
         * 无界行：纯音乐或无歌词占位行的跨度是 `Long.MAX_VALUE`。
         * 直接套编排公式会让点亮区间大到永远走不完，三颗圆点永远停在未点亮色，
         * 因此改用固定编排，见规格 6.8。
         */
        private fun unbounded(beginMs: Long): AmllInterludeTimeline = AmllInterludeTimeline(
            beginMs = beginMs,
            delayEndMs = 0L,
            bodyEndMs = Long.MAX_VALUE,
            totalEndMs = Long.MAX_VALUE,
            mode = Mode.BREATHE,
            breathePeriodMs = BREATHE_BASE_PERIOD_MS,
            segmentMs = BREATHE_BASE_PERIOD_MS,
            dot3DurationMs = BREATHE_BASE_PERIOD_MS,
            dot3Target = 1f,
            unbounded = true,
        )

        /** 行跨度，饱和到 `Long.MAX_VALUE` 以防溢出。 */
        private fun saturatingSpan(beginMs: Long, endMs: Long): Long {
            if (endMs <= beginMs) return 0L
            val span = endMs - beginMs
            return if (span < 0L) Long.MAX_VALUE else span
        }

        /** 容器入场淡入系数。 */
        private fun enterOpacity(internalMs: Long): Float =
            ENTER_FADE_EASING.solve(clamp01(internalMs.toFloat() / ENTER_FADE_MS.toFloat()))

        /** 单颗圆点的错峰入场系数，注意是平方而非缓动曲线。 */
        private fun dotEnterAlpha(index: Int, internalMs: Long): Float {
            val t = clamp01(
                (internalMs - index * DOT_ENTER_STAGGER_MS).toFloat() / DOT_ENTER_FADE_MS.toFloat()
            )
            return t * t
        }

        /** 单颗圆点的点亮分数。 */
        private fun computeDotFraction(
            startDelayMs: Long,
            durationMs: Long,
            internalMs: Long,
            target: Float,
        ): Float {
            if (internalMs <= startDelayMs) return 0f
            if (durationMs <= 0L) return clamp01(target)
            val localT = clamp01(
                (internalMs - startDelayMs).toFloat() / durationMs.toFloat()
            )
            return LIGHTING_EASING.solve(localT) * target
        }

        /** 点亮分数 → 圆点不透明度。 */
        private fun dotOpacity(fraction: Float): Float =
            DOT_INACTIVE_OPACITY + (DOT_ACTIVE_OPACITY - DOT_INACTIVE_OPACITY) * clamp01(fraction)

        /** 带轻微过冲的呼吸相位，值域 `[0, 1]`。 */
        private fun breathingProgress(t: Float): Float {
            if (t <= 0f) return 0f
            if (t >= 1f) return 1f
            val angle = 4.0 * PI * t
            val s = sin(angle).toFloat()
            val c = cos(angle).toFloat()
            return t - 0.084f * s + 0.008f * (1f - c) + 0.0046f * s * (c - s)
        }

        private fun clamp01(value: Float): Float = when {
            value.isNaN() -> 0f
            value <= 0f -> 0f
            value >= 1f -> 1f
            else -> value
        }
    }
}
