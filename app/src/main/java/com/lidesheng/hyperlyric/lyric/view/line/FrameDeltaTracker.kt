/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

/**
 * 帧间隔锚点。
 *
 * 位置样本会把渲染器时钟直接设为媒体时刻，而帧回调只拿到帧时间戳。
 * 锚点若停在上一次回调，下一帧的间隔会把样本已经覆盖的那段时长再累加一次，
 * 大间隔帧之后足以把演出推过终点而提前隐藏。
 * 样本落地后调用 [rebase] 把锚点前移到样本生效时刻，下一帧只累加其后的时长。
 */
internal class FrameDeltaTracker {

    private var anchorNanos = 0L

    /** 丢弃锚点，下一次 [deltaNanos] 返回 0。 */
    fun reset() {
        anchorNanos = 0L
    }

    /** 把锚点移到 [nowNanos]；尚无锚点时保持首次帧不推进。 */
    fun rebase(nowNanos: Long) {
        if (anchorNanos == 0L) return
        anchorNanos = nowNanos
    }

    /** 取本次帧间隔并推进锚点；迟于锚点的旧帧不推进也不返回负值。 */
    fun deltaNanos(frameTimeNanos: Long): Long {
        val previous = anchorNanos
        if (previous == 0L) {
            anchorNanos = frameTimeNanos
            return 0L
        }
        if (frameTimeNanos <= previous) return 0L
        anchorNanos = frameTimeNanos
        return frameTimeNanos - previous
    }
}
