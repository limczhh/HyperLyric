package com.lidesheng.hyperlyric.root.statusbar

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.lidesheng.hyperlyric.common.LyricTypePreference
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
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
                    gestureSnapshot?.let { snapshot ->
                        performGestureHapticFeedback(snapshot.hapticFeedbackEnabled)
                        performTapAction(snapshot.doubleTapAction)
                    }
                }
                return true
            }

            override fun onDoubleTapEvent(event: MotionEvent): Boolean = true

            override fun onLongPress(event: MotionEvent) {
                longPressRecognized = true
                if (!ignoreCurrentSequence) {
                    gestureSnapshot?.let { snapshot ->
                        performGestureHapticFeedback(snapshot.hapticFeedbackEnabled)
                        performTapAction(snapshot.longPressAction)
                    }
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
            performGestureHapticFeedback(snapshot.hapticFeedbackEnabled)
            performSwipeAction(snapshot.swipeLeftAction)
        } else {
            performGestureHapticFeedback(snapshot.hapticFeedbackEnabled)
            performSwipeAction(snapshot.swipeRightAction)
        }
        return true
    }

    private fun readGestureSnapshot(): GestureSnapshot? {
        val prefs = HookEntry.instance?.prefs ?: return null
        if (!LyricTypePreference.isEnabled(prefs, RootConstants.LYRIC_TYPE_STATUS_BAR)) return null
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
            hapticFeedbackEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK,
            ),
        )
        return snapshot.takeIf(GestureSnapshot::hasAnyBehavior)
    }

    private fun performTapAction(action: Int) {
        when (action) {
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK ->
                performPlaybackToggle()

            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_CLOCK ->
                StatusBarLyricRenderer.toggleTemporaryClockReveal()

            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_HIDE_LYRIC ->
                StatusBarLyricRenderer.toggleTemporaryLyricHide()

            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP ->
                performOpenMediaApp()

            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP_FREEFORM ->
                performOpenMediaAppInFreeform()
        }
    }

    private fun performGestureHapticFeedback(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            touchView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }.onFailure { error ->
            HookLogger.w(TAG, "执行状态栏歌词触感反馈失败", error)
        }
    }

    private fun performOpenMediaApp() {
        val target = resolveCurrentMediaAppLaunchTarget() ?: return
        val controller = target.controller
        val packageName = target.packageName
        val context = touchView.context

        if (controller?.packageName == packageName) {
            val sessionActivity = runCatching { controller.sessionActivity }
                .onFailure { error ->
                    HookLogger.w(TAG, "读取当前媒体会话启动入口失败", error)
                }
                .getOrNull()
            if (sessionActivity != null) {
                runCatching {
                    sessionActivity.send()
                }.onFailure { error ->
                    HookLogger.w(TAG, "打开当前媒体会话对应的应用失败", error)
                }
            }
        }

        val launchIntent = runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }.onFailure { error ->
            HookLogger.w(TAG, "查找媒体应用启动入口失败: package=$packageName", error)
        }.getOrNull()
        if (launchIntent == null) {
            HookLogger.w(TAG, "媒体应用没有可用的启动入口: package=$packageName")
            return
        }
        runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            HookLogger.d(TAG, "已提交媒体应用启动请求: package=$packageName")
        }.onFailure { error ->
            HookLogger.w(TAG, "启动媒体应用失败: package=$packageName", error)
        }
    }

    private fun performOpenMediaAppInFreeform() {
        val target = resolveCurrentMediaAppLaunchTarget() ?: return
        val context = touchView.context
        val packageName = target.packageName
        val launchIntent = runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName)
        }.onFailure { error ->
            HookLogger.d(
                TAG,
                "查找媒体应用入口失败: ${error.javaClass.simpleName}, package=$packageName",
            )
        }.getOrNull()
        if (launchIntent == null) {
            HookLogger.w(TAG, "媒体应用没有可用启动入口: package=$packageName")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val freeformOptions = runCatching {
            val apiClass = context.classLoader.loadClass("android.util.MiuiMultiWindowUtils")
            val method = apiClass.getMethod(
                "getActivityOptions",
                Context::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
            )
            (method.invoke(null, context, packageName, true, false) as? ActivityOptions)
                ?.toBundle()
        }.onFailure { error ->
            HookLogger.d(TAG, "获取小米 Freeform 参数失败: ${error.javaClass.simpleName}")
        }.getOrNull()

        val openedInFreeform = freeformOptions?.let { options ->
            runCatching {
                context.startActivity(launchIntent, options)
                true
            }.onFailure { error ->
                HookLogger.d(TAG, "小窗启动失败: ${error.javaClass.simpleName}")
            }.getOrDefault(false)
        } ?: false
        if (openedInFreeform) {
            HookLogger.d(TAG, "已提交媒体应用 Freeform 启动请求: package=$packageName")
            return
        }

        HookLogger.d(TAG, "Freeform 不可用，回退全屏启动: package=$packageName")
        runCatching {
            context.startActivity(launchIntent)
            HookLogger.d(TAG, "已提交媒体应用全屏启动请求: package=$packageName")
        }.onFailure { error ->
            HookLogger.w(TAG, "媒体应用全屏启动失败: package=$packageName", error)
        }
    }

    private fun resolveCurrentMediaAppLaunchTarget(): MediaAppLaunchTarget? {
        // 替换内容（音乐信息/占位行）也是模块自己展示的，点按同样应该能回到媒体应用。
        if (!LyriconDataBridge.isPlaybackActive() ||
            !LyriconDataBridge.isPresentingManagedContent() ||
            StatusBarLyricHostRegistry.liveHosts().none(StatusBarLyricHost::isShowingLyric)
        ) {
            return null
        }

        val sourceMetadata = LyriconDataBridge.currentLyricMediaMetadata
        val sourcePackage = LyriconDataBridge.currentLyricPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val metadataPackage = sourceMetadata?.packageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (sourcePackage != null && metadataPackage != null &&
            sourcePackage != metadataPackage
        ) {
            HookLogger.w(TAG, "当前歌词来源与媒体应用不匹配，忽略打开应用")
            return null
        }

        val controller = runCatching {
            IslandPlaybackControllerResolver.resolveForCurrentLyric(touchView.context)
        }.onFailure { error ->
            HookLogger.w(TAG, "解析当前歌词对应的媒体会话失败，继续尝试按包名启动", error)
        }.getOrNull()
        val packageName = sourcePackage ?: metadataPackage ?: controller?.packageName
        if (packageName.isNullOrBlank()) {
            HookLogger.w(TAG, "缺少当前歌词对应的媒体应用包名，忽略打开应用")
            return null
        }

        return MediaAppLaunchTarget(packageName, controller)
    }

    private data class MediaAppLaunchTarget(
        val packageName: String,
        val controller: MediaController?,
    )

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
        val hapticFeedbackEnabled: Boolean,
    ) {
        fun hasAnyBehavior(): Boolean =
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
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP,
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP_FREEFORM,
            RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_HIDE_LYRIC,
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
