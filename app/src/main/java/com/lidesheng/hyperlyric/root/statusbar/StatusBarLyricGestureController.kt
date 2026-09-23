package com.lidesheng.hyperlyric.root.statusbar

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.hooks.IslandPlaybackControllerResolver
import com.lidesheng.hyperlyric.root.utils.HookLogger

/** Handles gestures that start inside the status-bar lyric view or Clock. */
internal class StatusBarLyricGestureController(
    private val touchView: View,
    private val allowHorizontalSwipe: Boolean = true,
) : View.OnTouchListener {
    private val gestureDetector = GestureDetector(
        touchView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = gestureSnapshot != null

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (!ignoreCurrentSequence) {
                    gestureSnapshot?.let { performTapAction(it.doubleTapAction) }
                }
                return true
            }

            override fun onDoubleTapEvent(event: MotionEvent): Boolean = true

            override fun onLongPress(event: MotionEvent) {
                longPressRecognized = true
                if (!ignoreCurrentSequence) {
                    gestureSnapshot?.let { performTapAction(it.longPressAction) }
                }
            }
        }
    )

    private var gestureSnapshot: GestureSnapshot? = null
    private var downX = 0f
    private var downY = 0f
    private var ignoreCurrentSequence = false
    private var longPressRecognized = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val snapshot = readGestureSnapshot() ?: run {
                    gestureSnapshot = null
                    return false
                }
                gestureSnapshot = snapshot
                downX = event.x
                downY = event.y
                ignoreCurrentSequence = false
                longPressRecognized = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> ignoreCurrentSequence = true

            MotionEvent.ACTION_UP -> {
                if (!ignoreCurrentSequence && !longPressRecognized) {
                    dispatchHorizontalSwipe(event)
                }
            }

            MotionEvent.ACTION_CANCEL -> ignoreCurrentSequence = true
        }

        val handledByDetector = gestureDetector.onTouchEvent(event)
        val handled = gestureSnapshot != null || handledByDetector
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            gestureSnapshot = null
            ignoreCurrentSequence = false
            longPressRecognized = false
        }
        return handled
    }

    fun release() {
        gestureSnapshot = null
        ignoreCurrentSequence = true
    }

    private fun dispatchHorizontalSwipe(event: MotionEvent): Boolean {
        val snapshot = gestureSnapshot ?: return false
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        if (!allowHorizontalSwipe) return false
        val threshold = RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_THRESHOLD_DP *
                touchView.resources.displayMetrics.density
        if (kotlin.math.abs(deltaX) < threshold ||
            kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY) * HORIZONTAL_DOMINANCE_RATIO
        ) {
            return false
        }

        if (deltaX < 0f) {
            performSwipeAction(snapshot.swipeLeftAction)
        } else {
            performSwipeAction(snapshot.swipeRightAction)
        }
        return true
    }

    private fun readGestureSnapshot(): GestureSnapshot? {
        val prefs = HookEntry.instance?.prefs ?: return null
        if (!prefs.getBoolean(
                StatusBarLyricPreferences.KEY_ENABLED,
                StatusBarLyricPreferences.DEFAULT_ENABLED,
            )
        ) return null
        val snapshot = GestureSnapshot(
            doubleTapAction = readAction(
                prefs.getInt(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION,
                ),
                TAP_ACTIONS,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION,
            ),
            longPressAction = readAction(
                prefs.getInt(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION,
                ),
                TAP_ACTIONS,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION,
            ),
            swipeLeftAction = if (allowHorizontalSwipe) {
                readAction(
                    prefs.getInt(
                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION,
                        RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION,
                    ),
                    SWIPE_ACTIONS,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION,
                )
            } else {
                RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE
            },
            swipeRightAction = if (allowHorizontalSwipe) {
                readAction(
                    prefs.getInt(
                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION,
                        RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION,
                    ),
                    SWIPE_ACTIONS,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION,
                )
            } else {
                RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE
            },
        )
        return snapshot.takeIf(GestureSnapshot::hasAnyAction)
    }

    private fun performTapAction(action: Int) {
        when (action) {
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK ->
                performPlaybackToggle()

            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_CLOCK ->
                StatusBarLyricRenderer.toggleTemporaryClockReveal()
        }
    }

    private fun performSwipeAction(action: Int) {
        when (action) {
            RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS ->
                performTrackSkip(skipToNext = false)

            RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT ->
                performTrackSkip(skipToNext = true)
        }
    }

    private fun performPlaybackToggle() {
        val controller = resolveMediaController() ?: return
        val state = runCatching { controller.playbackState }
            .onFailure { error -> HookLogger.w(TAG, "读取状态栏歌词播放状态失败", error) }
            .getOrNull() ?: return
        val actions = state.actions
        runCatching {
            when (state.state) {
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_BUFFERING -> when {
                    actions and PlaybackState.ACTION_PAUSE != 0L ->
                        controller.transportControls.pause()

                    actions and PlaybackState.ACTION_PLAY_PAUSE != 0L ->
                        dispatchMediaKey(controller, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

                    else -> return
                }

                PlaybackState.STATE_PAUSED,
                PlaybackState.STATE_STOPPED,
                PlaybackState.STATE_NONE -> when {
                    actions and PlaybackState.ACTION_PLAY != 0L ->
                        controller.transportControls.play()

                    actions and PlaybackState.ACTION_PLAY_PAUSE != 0L ->
                        dispatchMediaKey(controller, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

                    else -> return
                }

                else -> return
            }
        }.onFailure { error ->
            HookLogger.w(TAG, "执行状态栏歌词暂停/播放失败", error)
        }
    }

    private fun performTrackSkip(skipToNext: Boolean) {
        val controller = resolveMediaController() ?: return
        val state = runCatching { controller.playbackState }
            .onFailure { error -> HookLogger.w(TAG, "读取状态栏歌词播放状态失败", error) }
            .getOrNull() ?: return
        val requiredAction = if (skipToNext) {
            PlaybackState.ACTION_SKIP_TO_NEXT
        } else {
            PlaybackState.ACTION_SKIP_TO_PREVIOUS
        }
        if (state.actions and requiredAction == 0L) return

        runCatching {
            if (skipToNext) {
                controller.transportControls.skipToNext()
            } else {
                controller.transportControls.skipToPrevious()
            }
        }.onFailure { error ->
            HookLogger.w(TAG, "执行状态栏歌词切歌失败: next=$skipToNext", error)
        }
    }

    private fun dispatchMediaKey(controller: MediaController, keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        controller.dispatchMediaButtonEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        )
        controller.dispatchMediaButtonEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        )
    }

    private fun resolveMediaController(): MediaController? =
        IslandPlaybackControllerResolver.resolveForCurrentLyric(touchView.context)
            ?: run {
                HookLogger.w(TAG, "无法唯一确定当前歌词对应的媒体会话，忽略手势控制")
                null
            }

    private data class GestureSnapshot(
        val doubleTapAction: Int,
        val longPressAction: Int,
        val swipeLeftAction: Int,
        val swipeRightAction: Int,
    ) {
        fun hasAnyAction(): Boolean =
            doubleTapAction != RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_NONE ||
                    longPressAction != RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_NONE ||
                    swipeLeftAction != RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE ||
                    swipeRightAction != RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE
    }

    private companion object {
        const val TAG = "StatusBarLyricGestureController"
        const val HORIZONTAL_DOMINANCE_RATIO = 1.35f
        val TAP_ACTIONS = setOf(
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_NONE,
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK,
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_CLOCK,
        )
        val SWIPE_ACTIONS = setOf(
            RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE,
            RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS,
            RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT,
        )

        fun readAction(value: Int, allowed: Set<Int>, defaultValue: Int): Int =
            value.takeIf(allowed::contains) ?: defaultValue
    }
}
