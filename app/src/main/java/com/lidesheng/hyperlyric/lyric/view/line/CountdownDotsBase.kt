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

import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

/**
 * 倒计时圆点渲染器的共享骨架。
 *
 * 承载两种圆点样式（原有【倒计时圆点】与【倒计时圆点（AMLL 样式）】）在视图层语义上
 * 完全一致的部分：以 `textSize` 为基准的几何、水平对齐、字号/颜色下发、暂停冻结与
 * 行状态复位。具体演出编排由子类实现。
 *
 * **兼容性约束**：原有 [CountdownDotsRenderer] 的视觉与时间行为必须逐像素不变，
 * 因此它保持独立实现、不继承本类（其源码零改动）。视图层统一通过
 * [isCountdownDotsRenderer]、[freezeCountdownDots]、[syncCountdownDotsFrom] 三个
 * 共享入口判断，避免在两个视图里各写一套重复分支。
 */
internal abstract class CountdownDotsBase : LineRenderer {

    /** 当前字号，同时作为圆点几何的 `1em` 基准。 */
    protected var baseTextSize: Float = 0f

    override var centerIfPossible: Boolean = false
    override var rightIfPossible: Boolean = false

    /** 下发字号。子类可覆写以清理自身缓存的几何相关资源。 */
    open fun setTextSize(size: Float) {
        baseTextSize = size
    }

    /** 下发颜色。`background` 与 `highlight` 的语义由子类决定。 */
    open fun setColors(background: IntArray, highlight: IntArray) = Unit

    /** 暂停时冻结当前帧。 */
    open fun freeze() = Unit

    /** 把另一棵视图树（Master）的演出状态同步过来。 */
    open fun syncFrom(other: CountdownDotsBase) = Unit

    /** 本渲染器占用的行宽度（像素），用于行测量与对齐。 */
    abstract fun contentWidth(): Float

    /** 清空演出状态，供行被替换或视图回收时调用。 */
    protected abstract fun resetPerformance()

    override fun reset(state: LineState) {
        resetPerformance()
        state.reset()
    }

    /**
     * 计算内容盒左边界，规则与原有圆点渲染器一致：
     * 右对齐优先，其次居中，否则左对齐；结果不小于 0。
     */
    protected fun alignedStart(width: Float, model: LyricModel, viewWidth: Int): Float = when {
        model.isAlignedRight || rightIfPossible -> viewWidth - width
        centerIfPossible -> (viewWidth - width) / 2f
        else -> 0f
    }.coerceAtLeast(0f)
}

/**
 * 该渲染器是否为倒计时圆点渲染器（两种样式之一）。
 *
 * 原有渲染器不继承 [CountdownDotsBase]（其行为必须逐像素不变），
 * 因此这里同时识别两个实现，让视图层只写一处判断。
 */
internal val LineRenderer.isCountdownDotsRenderer: Boolean
    get() = this is CountdownDotsBase || this is CountdownDotsRenderer

/** 冻结倒计时圆点演出；非圆点渲染器无操作。 */
internal fun LineRenderer.freezeCountdownDots() {
    when (this) {
        is CountdownDotsBase -> freeze()
        is CountdownDotsRenderer -> freeze()
    }
}

/** 双树同步：把 Master 的圆点演出状态拷到 Slave；样式不同则不同步。 */
internal fun LineRenderer.syncCountdownDotsFrom(master: LineRenderer) {
    when {
        this is CountdownDotsBase && master is CountdownDotsBase -> syncFrom(master)
        this is CountdownDotsRenderer && master is CountdownDotsRenderer -> syncFrom(master)
    }
}
