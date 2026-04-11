package com.lidesheng.hyperlyric.service

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.PowerManager
import android.view.KeyEvent
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.online.model.DynamicLyricData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 通知展示调度中心。
 *
 * 息屏降频优化和通知发射逻辑。LiveLyricService 仅在开关打开时，
 *
 * 管理播控广播接收器 (ACTION_TOGGLE_PLAYBACK)
 */
class NotificationPresenter(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val notificationManager by lazy { context.getSystemService(NotificationManager::class.java) }
    private var lastUiState: NotificationManagerHelper.UiState? = null
    private var pauseDebounceJob: Job? = null
    private val pauseDebounceMs = 150L

    private val isDisableLyricSplit: Boolean
        get() = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(Constants.KEY_DISABLE_LYRIC_SPLIT, Constants.DEFAULT_DISABLE_LYRIC_SPLIT)

    private val notificationType: Int
        get() = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
            .getInt(Constants.KEY_NOTIFICATION_TYPE, Constants.DEFAULT_NOTIFICATION_TYPE)

    // ─── 播控广播接收器 ───────────────────────────────────
    private val playbackToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == "com.lidesheng.hyperlyric.ACTION_TOGGLE_PLAYBACK") {
                val audioManager = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val eventTime = android.os.SystemClock.uptimeMillis()
                val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
                audioManager?.dispatchMediaKeyEvent(downEvent)
                val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
                audioManager?.dispatchMediaKeyEvent(upEvent)
            }
        }
    }

    // ─── 生命周期 ─────────────────────────────────────────

    fun register() {
        val filter = IntentFilter("com.lidesheng.hyperlyric.ACTION_TOGGLE_PLAYBACK")
        context.registerReceiver(playbackToggleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        NotificationManagerHelper.createNotificationChannel(notificationManager)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(playbackToggleReceiver)
        } catch (_: Exception) { }
        pauseDebounceJob?.cancel()
    }

    // ─── 核心入口：接收数据并决定是否发射通知 ──────────────

    /**
     * 由 LiveLyricService 在每次状态变更时调用。
     * 内部完成：开关检查、白名单过滤、状态去重、息屏降频、防抖，最终发射通知。
     */
    fun updateState(globalState: com.lidesheng.hyperlyric.online.model.LyricState, force: Boolean) {
        val isWhitelisted = DynamicLyricData.whitelistState.value.contains(globalState.targetPackageName)
        if (!isWhitelisted) {
            clearNotifications()
            return
        }

        val sp = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
        if (!sp.getBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, Constants.DEFAULT_ENABLE_DYNAMIC_ISLAND)) {
            clearNotifications()
            return
        }

        val duration = globalState.duration
        val safeDuration = if (duration > 0) duration else 100L
        val currentPos = with(DynamicLyricData) { globalState.getCurrentPosition() }.coerceIn(0, safeDuration)
        val progressPercent = if (safeDuration > 1000) ((currentPos.toDouble() / safeDuration.toDouble()) * 100).roundToInt().coerceIn(0, 100) else 0

        val currentUiState = NotificationManagerHelper.UiState(
            title = globalState.islandTitleRight,
            songLyric = globalState.songLyric,
            songInfo = globalState.songInfo,
            islandTitleLeft = globalState.islandTitleLeft,
            notificationTitleLeft = globalState.notificationTitleLeft,
            notificationTitleRight = globalState.notificationTitleRight,
            albumBitmap = globalState.albumBitmap?.takeIf { !it.isRecycled },
            color = if (sp.getBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, Constants.DEFAULT_PROGRESS_COLOR_ENABLED)) globalState.albumColor else 0xFF2C2C2C.toInt(),
            colorEnd = if (sp.getBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, Constants.DEFAULT_PROGRESS_COLOR_ENABLED)) globalState.albumColorEnd else 0xFF2C2C2C.toInt(),
            progress = progressPercent,
            isPlaying = globalState.isPlaying,
            targetPackageName = globalState.targetPackageName,
            showIslandLeftAlbum = globalState.showIslandLeftAlbum,
            disableLyricSplit = isDisableLyricSplit,
            notificationAlbumBitmap = globalState.notificationAlbumBitmap?.takeIf { !it.isRecycled },
            focusNotificationType = sp.getInt(Constants.KEY_FOCUS_NOTIFICATION_TYPE, Constants.DEFAULT_FOCUS_NOTIFICATION_TYPE)
        )

        if (!force && currentUiState == lastUiState) return

        val isScreenOn = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
        if (!force && !isScreenOn && lastUiState != null) {
            if (currentUiState.isProgressOnlyChange(lastUiState!!)) return
        }

        lastUiState = currentUiState

        if (currentUiState.isPlaying) {
            pauseDebounceJob?.cancel()
            pauseDebounceJob = null

            dispatchNotifications(currentUiState, safeDuration, isScreenOn)
        } else if (pauseDebounceJob == null || pauseDebounceJob?.isActive != true) {
            pauseDebounceJob = scope.launch {
                delay(pauseDebounceMs)
                if (DynamicLyricData.currentState.isPlaying) return@launch
                clearNotifications()
            }
        }
    }

    // ─── 内部方法 ─────────────────────────────────────────

    private fun NotificationManagerHelper.UiState.isProgressOnlyChange(other: NotificationManagerHelper.UiState): Boolean {
        return title == other.title &&
                islandTitleLeft == other.islandTitleLeft &&
                notificationTitleLeft == other.notificationTitleLeft &&
                notificationTitleRight == other.notificationTitleRight &&
                songLyric == other.songLyric &&
                songInfo == other.songInfo &&
                isPlaying == other.isPlaying &&
                showIslandLeftAlbum == other.showIslandLeftAlbum
    }

    private fun dispatchNotifications(uiState: NotificationManagerHelper.UiState, duration: Long, isScreenOn: Boolean) {
        when (notificationType) {
            0 -> {
                // 实时通知
                val notification = NotificationManagerHelper.buildNormalNotification(context, uiState, duration)
                notifyWrapper(NotificationManagerHelper.NORMAL_NOTIFICATION_ID, notification)
                NotificationManagerHelper.cancelFocusNotification(notificationManager)
            }
            1 -> {
                // 焦点通知
                val focusNotification = NotificationManagerHelper.buildFocusNotification(context, uiState, isScreenOn)
                notifyWrapper(NotificationManagerHelper.FOCUS_NOTIFICATION_ID, focusNotification)
                NotificationManagerHelper.cancelNormalNotification(notificationManager)
            }
        }
    }

    private fun notifyWrapper(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearNotifications() {
        NotificationManagerHelper.cancelFocusNotification(notificationManager)
        NotificationManagerHelper.cancelNormalNotification(notificationManager)
        lastUiState = null
    }
}
