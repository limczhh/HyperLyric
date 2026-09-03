package com.lidesheng.hyperlyric.root.island.view

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.yoyo.YoYoAnimation

/**
 * Applies playback state to one injected island projection.
 *
 * Real and Fake hosts share this adapter so callers cannot accidentally use different seek,
 * pause, or hidden-clock semantics for the two Xiaomi trees.
 */
internal object IslandLyricViewController {

    fun configureProjection(view: View) {
        when (view) {
            is RichLyricLineView -> view.keepPlaybackClockRunningWhenHidden(true)
            is SpaceGateRichLyricLineView -> {
                // Island split slots are independent projections. Disable the standalone
                // master/slave gate before initial content is applied so both sides receive the
                // same creation-time media position.
                view.main.spaceGateEnabled = false
                view.secondary.spaceGateEnabled = false
                view.keepPlaybackClockRunningWhenHidden(true)
            }
        }
    }

    fun setPlaybackActive(view: View, active: Boolean) {
        when (view) {
            is RichLyricLineView -> view.setPlaybackActive(active)
            is SpaceGateRichLyricLineView -> view.setPlaybackActive(active)
        }
    }

    fun useSharedMarqueeClock(
        view: View,
        enabled: Boolean,
        originActiveTimeMs: Long = 0L
    ) {
        when (view) {
            is RichLyricLineView -> view.useSharedMarqueeClock(enabled, originActiveTimeMs)
            is SpaceGateRichLyricLineView -> view.useSharedMarqueeClock(
                enabled,
                originActiveTimeMs
            )
        }
    }

    fun synchronizePosition(
        view: View,
        position: Long,
        playbackSpeed: Float = 1f,
        activeTimeMs: Long = position
    ) {
        when (view) {
            is RichLyricLineView -> view.synchronizePosition(
                position,
                playbackSpeed,
                activeTimeMs
            )

            is SpaceGateRichLyricLineView -> view.synchronizePosition(
                position,
                playbackSpeed,
                activeTimeMs
            )
        }
    }

    fun applyPlaybackSnapshot(
        view: View,
        position: Long,
        playbackSpeed: Float,
        activeTimeMs: Long,
        active: Boolean
    ) {
        // Position is anchored before activation/deactivation so every projection starts or
        // freezes from the same canonical media-time sample.
        synchronizePosition(view, position, playbackSpeed, activeTimeMs)
        setPlaybackActive(view, active)
    }

    fun setPlaybackActiveRecursively(view: View, active: Boolean) {
        visitProjectionViews(view) { projection ->
            setPlaybackActive(projection, active)
        }
    }

    fun applyPlaybackSnapshotRecursively(
        view: View,
        position: Long,
        playbackSpeed: Float,
        activeTimeMs: Long,
        active: Boolean
    ) {
        visitProjectionViews(view) { projection ->
            applyPlaybackSnapshot(
                projection,
                position,
                playbackSpeed,
                activeTimeMs,
                active
            )
        }
    }

    /** Stops a projection before its native Xiaomi content is restored. */
    fun stopRecursively(view: View) {
        visitProjectionViews(view) { projection ->
            YoYoAnimation.cancelAnimation(projection)
            setPlaybackActive(projection, false)
        }
    }

    private fun visitProjectionViews(view: View, action: (View) -> Unit) {
        when (view) {
            is RichLyricLineView,
            is SpaceGateRichLyricLineView -> action(view)

            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    visitProjectionViews(view.getChildAt(index), action)
                }
            }
        }
    }
}
