package com.lidesheng.hyperlyric.service

import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.online.model.DynamicLyricData
import com.lidesheng.hyperlyric.online.model.LyricState
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class ForegroundLyricService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    private val notificationType: Int
        get() = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
            .getInt(Constants.KEY_NOTIFICATION_TYPE, Constants.DEFAULT_NOTIFICATION_TYPE)

    private val isDisableLyricSplit: Boolean
        get() = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
            .getBoolean(Constants.KEY_DISABLE_LYRIC_SPLIT, Constants.DEFAULT_DISABLE_LYRIC_SPLIT)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastUiState: NotificationManagerHelper.UiState? = null
    private var isForeground = false
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var pauseDebounceJob: Job? = null
    private val pauseDebounceMs = 150L

    private val isPersistentEnabled: Boolean
        get() = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
            .getBoolean(Constants.KEY_PERSISTENT_FOREGROUND, false)

    private val playbackToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.lidesheng.hyperlyric.ACTION_TOGGLE_PLAYBACK") {
                val audioManager = context?.getSystemService(AUDIO_SERVICE) as? AudioManager
                val eventTime = android.os.SystemClock.uptimeMillis()
                val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
                audioManager?.dispatchMediaKeyEvent(downEvent)
                val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
                audioManager?.dispatchMediaKeyEvent(upEvent)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        
        val filter = IntentFilter("com.lidesheng.hyperlyric.ACTION_TOGGLE_PLAYBACK")
        registerReceiver(playbackToggleReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        NotificationManagerHelper.createNotificationChannel(notificationManager)
        DynamicLyricData.initWhitelist(this)

        if (isPersistentEnabled) {
            switchToPersistentNotification()
            ensureListenerBound()
        }

        updateNotificationUI(DynamicLyricData.currentState, force = true)

        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                DynamicLyricData.musicState,
                DynamicLyricData.progressFlow,
                DynamicLyricData.whitelistState
            ) { state, _, _ -> state }.collect { state ->
                updateNotificationUI(state, force = false)
            }
        }
    }

    private fun updateNotificationUI(globalState: LyricState, force: Boolean) {
        val isWhitelisted = DynamicLyricData.whitelistState.value.contains(globalState.targetPackageName)
        if (!isWhitelisted) {
            if (isPersistentEnabled)  switchToPersistentNotification().also { lastUiState = null } else stopWithCleanup()
            return
        }

        val sp = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
        if (!sp.getBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, Constants.DEFAULT_ENABLE_DYNAMIC_ISLAND)) {
            NotificationManagerHelper.cancelFocusNotification(notificationManager)
            NotificationManagerHelper.cancelNormalNotification(notificationManager)
            if (isPersistentEnabled) switchToPersistentNotification().also { lastUiState = null } else stopWithCleanup()
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

        if (!force && currentUiState == lastUiState && isForeground) return

        val isScreenOn = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive == true
        if (!force && !isScreenOn && isForeground && lastUiState != null) {
             if (currentUiState.isProgressOnlyChange(lastUiState!!)) return
        }

        lastUiState = currentUiState

        if (currentUiState.isPlaying) {
            pauseDebounceJob?.cancel()
            pauseDebounceJob = null

            dispatchNotifications(currentUiState, safeDuration, isScreenOn)
        } else if (pauseDebounceJob == null || pauseDebounceJob?.isActive != true) {
            pauseDebounceJob = serviceScope.launch {
                delay(pauseDebounceMs)
                if (DynamicLyricData.currentState.isPlaying) return@launch
                clearNotificationsAndCheckPersistent()
            }
        }
    }

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

    private fun stopWithCleanup() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            lastUiState = null
        }
        stopSelf()
    }

    private fun clearNotificationsAndCheckPersistent() {
        NotificationManagerHelper.cancelFocusNotification(notificationManager)
        NotificationManagerHelper.cancelNormalNotification(notificationManager)
        if (isPersistentEnabled) switchToPersistentNotification().also { lastUiState = null } else stopWithCleanup()
    }

    private fun dispatchNotifications(uiState: NotificationManagerHelper.UiState, duration: Long, isScreenOn: Boolean) {
        when (notificationType) {
            0 -> {
                // 实时通知
                val notification = NotificationManagerHelper.buildNormalNotification(this, uiState, duration)
                notifyWrapper(NotificationManagerHelper.NORMAL_NOTIFICATION_ID, notification)
                NotificationManagerHelper.cancelFocusNotification(notificationManager)
            }
            1 -> {
                // 焦点通知
                val focusNotification = NotificationManagerHelper.buildFocusNotification(this, uiState, isScreenOn)
                notifyWrapper(NotificationManagerHelper.FOCUS_NOTIFICATION_ID, focusNotification)
                NotificationManagerHelper.cancelNormalNotification(notificationManager)
            }
        }
    }

    private fun notifyWrapper(id: Int, notification: Notification) {
        try {
            if (!isForeground) {
                startForeground(id, notification)
                isForeground = true
            } else {
                notificationManager.notify(id, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                notificationManager.notify(id, notification)
            } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(playbackToggleReceiver)
        serviceScope.cancel()
        isForeground = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PERSISTENT -> {
                switchToPersistentNotification()
                ensureListenerBound()
            }
            ACTION_STOP_PERSISTENT -> {
                if (!DynamicLyricData.currentState.isPlaying) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForeground = false
                    stopSelf()
                }
            }
            LyricTileService.ACTION_PAUSE_TOGGLED -> {
                updateNotificationUI(DynamicLyricData.currentState, force = true)
            }
            LyricTileService.ACTION_RESUME_TOGGLED -> {
                updateNotificationUI(DynamicLyricData.currentState, force = true)
            }
        }
        return START_STICKY
    }

    private fun switchToPersistentNotification() {
        val notification = NotificationManagerHelper.buildPersistentNotification(this)
        startForeground(NotificationManagerHelper.PERSISTENT_NOTIFICATION_ID, notification)
        isForeground = true
    }

    private fun ensureListenerBound() {
        try {
            val pm = packageManager
            val cn = ComponentName(this, LiveLyricService::class.java)
            pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            android.service.notification.NotificationListenerService.requestRebind(cn)
        } catch (_: Exception) { }
    }

    companion object {
        private const val ACTION_START_PERSISTENT = "com.lidesheng.hyperlyric.START_PERSISTENT"
        private const val ACTION_STOP_PERSISTENT = "com.lidesheng.hyperlyric.STOP_PERSISTENT"

        fun startPersistent(context: Context) {
            val intent = Intent(context, ForegroundLyricService::class.java)
            intent.action = ACTION_START_PERSISTENT
            context.startForegroundService(intent)
        }

        fun stopPersistent(context: Context) {
            val intent = Intent(context, ForegroundLyricService::class.java)
            intent.action = ACTION_STOP_PERSISTENT
            context.startService(intent)
        }
    }
}
