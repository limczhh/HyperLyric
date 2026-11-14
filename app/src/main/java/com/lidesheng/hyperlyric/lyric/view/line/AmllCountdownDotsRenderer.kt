/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 圆点演出行为参考 AMLL（https://github.com/amll-dev/applemusic-like-lyrics）
 * 的 interlude-dots 实现，按行为规格重写，未复制其源代码。
 */

package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

/**
 * 【倒计时圆点（AMLL 样式）】渲染器。
 *
 * 演出编排由 [AmllInterludeTimeline] 完全决定：给定行区间与媒体时刻即可算出
 * `(scale, containerOpacity, dot0..2 opacity)`。本类只负责两件事：
 *
 * 1. **维持帧内平滑**：位置推送被节流到约 33ms，而 `step()` / `draw()` 的签名里没有
 *    `posMs`，因此需要一个可推进的镜像时钟 [clockPosMs]。它每次 [update] 都被
 *    `posMs` **无条件硬覆盖**，只是 `posMs` 的线性外推镜像——没有目标值、没有缓动进度、
 *    没有重锚阈值、不跨行累积，与 [ProgressAnimator] 无关。
 * 2. **按 AMLL 几何绘制**：三个圆点按 [AmllInterludeTimeline] 的几何常量排布，
 *    以内容盒中心为原点整体缩放。
 *
 * 颜色为单色：取 `highlightColors` 首色，未点亮与点亮只靠不透明度区分。
 * `setColors` 保留与原有渲染器相同的签名，`background` 参数接收但忽略，
 * 使视图层的颜色下发调用点无需改动。
 */
internal class AmllCountdownDotsRenderer(
    /** 入场延迟。恒为 0：延迟由占位行的起点承担，渲染器不叠加。 */
    private val holdMs: Long = 0L,
) : CountdownDotsBase() {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var highlightColor: Int = Color.WHITE

    /** 权威时钟：绝对媒体时间，每次 [update] / [seek] 被硬覆盖。 */
    private var clockPosMs: Long = 0L

    /** 暂停冻结标记。冻结时 `isPlaying` 为假、[step] 不推进时钟，保留当前画面。 */
    private var frozen: Boolean = false

    private var playbackSpeed: Float = 1f

    private var timeline: AmllInterludeTimeline? = null

    /** 最近一次解析出的帧快照，供 [draw] 使用。 */
    private var snapshot: AmllDotsSnapshot = AmllDotsSnapshot.HIDDEN

    override val isPlaying: Boolean
        get() {
            if (frozen) return false
            val current = timeline ?: return false
            return current.isActiveAt(clockPosMs)
        }

    override val isFinished: Boolean
        get() = timeline?.isFinishedAt(clockPosMs) ?: true

    override val isStarted: Boolean
        get() = timeline?.isStartedAt(clockPosMs) ?: false

    override fun setColors(background: IntArray, highlight: IntArray) {
        // 单色：只取高亮首色。background 接收但不使用。
        val resolved = highlight.firstOrNull() ?: return
        if (highlightColor == resolved) return
        highlightColor = resolved
    }

    override fun contentWidth(): Float = AmllInterludeTimeline.CONTENT_WIDTH_EM * baseTextSize

    override fun freeze() {
        frozen = true
    }

    override fun syncFrom(other: CountdownDotsBase) {
        val source = other as? AmllCountdownDotsRenderer ?: return
        // 派生时间线只由 (begin, end, holdMs) 决定，无需同步。
        clockPosMs = source.clockPosMs
        frozen = source.frozen
        playbackSpeed = source.playbackSpeed
        timeline = source.timeline
        snapshot = source.snapshot
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        clockPosMs = posMs
        rebuildTimeline(model)
        resolve()
    }

    override fun update(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playbackSpeed: Float,
    ) {
        clockPosMs = posMs
        frozen = false
        this.playbackSpeed = normalizePlaybackSpeed(playbackSpeed)
        rebuildTimeline(model)
        resolve()
    }

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int,
    ): Boolean {
        if (!isPlaying) return false
        if (deltaNanos <= 0L) return false
        // 镜像时钟线性外推；下一次 update() 会立即抹平累计误差。
        clockPosMs += (deltaNanos / NANOS_PER_MILLI * playbackSpeed).toLong()
        resolve()
        return true
    }

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        if (baseTextSize <= 0f || viewWidth <= 0 || viewHeight <= 0) return
        val frame = snapshot
        if (!frame.isVisible) return

        val boxWidth = contentWidth()
        val boxLeft = alignedStart(boxWidth, model, viewWidth)
        val centerY = viewHeight / 2f
        val scale = frame.scale

        dotPaint.color = highlightColor
        val baseAlpha = dotPaint.alpha
        repeat(AmllInterludeTimeline.DOT_COUNT) { index ->
            val opacity = frame.dotOpacity(index)
            if (opacity <= 0f) return@repeat
            val centerX = boxLeft + AmllInterludeTimeline.dotCenterXEm(index, scale) * baseTextSize
            val radius = AmllInterludeTimeline.dotRadiusEm(scale) * baseTextSize
            dotPaint.alpha = (baseAlpha * opacity).toInt().coerceIn(0, 255)
            canvas.drawCircle(centerX, centerY, radius, dotPaint)
        }
        dotPaint.alpha = baseAlpha
    }

    override fun resetPerformance() {
        clockPosMs = 0L
        frozen = false
        playbackSpeed = 1f
        timeline = null
        snapshot = AmllDotsSnapshot.HIDDEN
    }

    /** 时间线只依赖行区间与入场延迟，仅在 [update] / [seek] 中重建。 */
    private fun rebuildTimeline(model: LyricModel) {
        timeline = AmllInterludeTimeline.build(model.begin, model.end, holdMs)
    }

    private fun resolve() {
        snapshot = timeline?.snapshotAt(clockPosMs) ?: AmllDotsSnapshot.HIDDEN
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
