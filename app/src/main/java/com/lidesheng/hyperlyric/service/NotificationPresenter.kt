package com.lidesheng.hyperlyric.service

import android.app.Notification
import android.app.NotificationManager
import com.lidesheng.hyperlyric.utils.LogManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.PowerManager
import android.view.KeyEvent
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.service.Constants as ServiceConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.lyric.DynamicLyricData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.lidesheng.hyperlyric.service.utils.shizuku.ShizukuManager

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

    private var networkCutJob: Job? = null
    private val networkCutMutex = kotlinx.coroutines.sync.Mutex()
    private val networkCutDurationMs = 100L
    private var networkCutSeq = 0L

    private val isBypassFocusLimitEnabled: Boolean
        get() = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT, ServiceConstants.DEFAULT_BYPASS_FOCUS_NOTIFICATION_LIMIT)

    private val isDisableLyricSplit: Boolean
        get() = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT, ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT)

    private val notificationType: Int
        get() = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
            .getInt(ServiceConstants.KEY_NOTIFICATION_TYPE, ServiceConstants.DEFAULT_NOTIFICATION_TYPE)

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
        NotificationManagerHelper.createNotificationChannel(context, notificationManager)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(playbackToggleReceiver)
        } catch (e: Exception) {
            LogManager.w("NotificationPresenter", "注销播放控制接收器失败", e)
        }
        pauseDebounceJob?.cancel()
    }

    // ─── 核心入口：接收数据并决定是否发射通知 ──────────────

    /**
     * 由 LiveLyricService 在每次状态变更时调用。
     * 内部完成：开关检查、白名单过滤、状态去重、息屏降频、防抖，最终发射通知。
     */
    fun updateState(globalState: com.lidesheng.hyperlyric.lyric.LyricState, force: Boolean) {
        val isWhitelisted = DynamicLyricData.whitelistState.value.contains(globalState.targetPackageName)
        if (!isWhitelisted) {
            clearNotifications()
            return
        }

        val sp = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        if (!sp.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND)) {
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
            color = globalState.albumColor,
            colorEnd = globalState.albumColorEnd,
            progress = progressPercent,
            isPlaying = globalState.isPlaying,
            targetPackageName = globalState.targetPackageName,
            showIslandLeftAlbum = globalState.showIslandLeftAlbum,
            disableLyricSplit = isDisableLyricSplit,
            notificationAlbumBitmap = globalState.notificationAlbumBitmap?.takeIf { !it.isRecycled },
            notificationAlbumBitmapCircular = globalState.notificationAlbumBitmapCircular?.takeIf { !it.isRecycled },
            islandLeftIconStyle = sp.getInt(ServiceConstants.KEY_ISLAND_LEFT_ICON, ServiceConstants.DEFAULT_ISLAND_LEFT_ICON),
            focusNotificationType = sp.getInt(ServiceConstants.KEY_NOTIFICATION_FOCUS_STYLE, ServiceConstants.DEFAULT_NOTIFICATION_FOCUS_STYLE),
            showAlbumArt = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_ALBUM, ServiceConstants.DEFAULT_NOTIFICATION_ALBUM),
            highlightColorEnabled = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_HIGHLIGHT_COLOR, ServiceConstants.DEFAULT_NOTIFICATION_HIGHLIGHT_COLOR),
            songInfoHighlightColorEnabled = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR, ServiceConstants.DEFAULT_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR),
            progressColorEnabled = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_PROGRESS_COLOR, ServiceConstants.DEFAULT_NOTIFICATION_PROGRESS_COLOR)
        )

        val isScreenOn = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true
        val showProgressSetting = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS, ServiceConstants.DEFAULT_NOTIFICATION_SHOW_PROGRESS)

        if (!force && lastUiState != null) {
            if (currentUiState == lastUiState) {
                LogManager.d("NotificationPresenter", "updateState 跳过: 状态未变化")
                return
            }
            val progressOnly = currentUiState.isProgressOnlyChange(lastUiState!!)
            if (progressOnly) {
                // 如果当前关闭了进度条显示，或者屏幕处于关闭状态，则不因进度变化触发通知
                if (!showProgressSetting || !isScreenOn) {
                    LogManager.d("NotificationPresenter", "updateState 跳过: 仅进度变化, 进度条开关=$showProgressSetting, 屏幕=$isScreenOn")
                    return
                }
            }
            LogManager.d("NotificationPresenter", "updateState: 强制=$force, 仅进度变化=$progressOnly, 播放中=${currentUiState.isPlaying}")
        }

        if (currentUiState.isPlaying) {
            pauseDebounceJob?.cancel()
            pauseDebounceJob = null

            dispatchNotifications(currentUiState, safeDuration, isScreenOn)
            lastUiState = currentUiState
        } else {
            lastUiState = currentUiState
            if (pauseDebounceJob == null || pauseDebounceJob?.isActive != true) {
                pauseDebounceJob = scope.launch {
                    delay(pauseDebounceMs)
                    if (DynamicLyricData.currentState.isPlaying) return@launch
                    clearNotifications()
                }
            }
        }
    }

    // ─── 内部方法 ─────────────────────────────────────────

    private fun NotificationManagerHelper.UiState.isProgressOnlyChange(other: NotificationManagerHelper.UiState): Boolean {
        return progress != other.progress &&
                title == other.title &&
                islandTitleLeft == other.islandTitleLeft &&
                notificationTitleLeft == other.notificationTitleLeft &&
                notificationTitleRight == other.notificationTitleRight &&
                songLyric == other.songLyric &&
                songInfo == other.songInfo &&
                isPlaying == other.isPlaying &&
                showIslandLeftAlbum == other.showIslandLeftAlbum &&
                islandLeftIconStyle == other.islandLeftIconStyle
    }

    private fun dispatchNotifications(uiState: NotificationManagerHelper.UiState, duration: Long, isScreenOn: Boolean) {
        val sp = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        val showProgressSetting = sp.getBoolean(ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS, ServiceConstants.DEFAULT_NOTIFICATION_SHOW_PROGRESS)
        val actualShowProgress = isScreenOn && showProgressSetting
        LogManager.d("NotificationPresenter", "正在发送通知: 类型=${if (notificationType == 1) "焦点" else "普通"}, bypass=$isBypassFocusLimitEnabled, 进度=$actualShowProgress")

        when (notificationType) {
            0 -> {
                // 实时通知
                val notification = NotificationManagerHelper.buildNormalNotification(context, uiState, duration, actualShowProgress)
                notifyWrapper(NotificationManagerHelper.NORMAL_NOTIFICATION_ID, notification)
                NotificationManagerHelper.cancelFocusNotification(notificationManager)
            }
            1 -> {
                // 焦点通知
                val focusNotification = NotificationManagerHelper.buildFocusNotification(context, uiState, actualShowProgress)
                if (isBypassFocusLimitEnabled) {
                    networkCutJob?.cancel()
                    val seq = ++networkCutSeq
                    networkCutJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        networkCutMutex.lock()
                        try {
                            // 1. 闪断 XMSF 联网
                            ShizukuManager.setXmsfNetworkingEnabled(context, false)
                            
                            // 2. 极速在主线程发射通知
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                notifyWrapper(NotificationManagerHelper.FOCUS_NOTIFICATION_ID, focusNotification)
                            }
                            
                            // 3. 保持盲区防抖窗口 (100ms)
                            try {
                                kotlinx.coroutines.delay(networkCutDurationMs)
                            } catch (_: kotlinx.coroutines.CancellationException) {
                                // 被新发生的发送任务 cancel，自动延续断网状态
                            }
                            
                            // 4. 到期安全自动恢复网络
                            if (seq == networkCutSeq) {
                                ShizukuManager.setXmsfNetworkingEnabled(context, true)
                            }
                        } finally {
                            networkCutMutex.unlock()
                        }
                    }
                } else {
                    notifyWrapper(NotificationManagerHelper.FOCUS_NOTIFICATION_ID, focusNotification)
                }
                NotificationManagerHelper.cancelNormalNotification(notificationManager)
            }
        }
    }

    private fun notifyWrapper(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: Exception) {
            LogManager.e("NotificationPresenter", "通知发送失败 id=$id", e)
        }
    }

    fun clearNotifications() {
        NotificationManagerHelper.cancelFocusNotification(notificationManager)
        NotificationManagerHelper.cancelNormalNotification(notificationManager)
        lastUiState = null

        if (isBypassFocusLimitEnabled) {
            networkCutJob?.cancel()
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                ShizukuManager.setXmsfNetworkingEnabled(context, true)
            }
        }
    }
}
