package com.lidesheng.hyperlyric.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.TypedValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.toColorInt
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.online.LrcCacheManager
import com.lidesheng.hyperlyric.online.LrcLine
import com.lidesheng.hyperlyric.online.OnlineLyricTargeter
import com.lidesheng.hyperlyric.online.model.DynamicLyricData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class LiveLyricService : NotificationListenerService() {
    private lateinit var mediaSessionManager: MediaSessionManager
    private val currentControllers = mutableListOf<MediaController>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val islandBitmapHeight = 128

    private var currentSongIdentifier = ""

    private var bitmapRetryJob: Job? = null
    private var bitmapRetryCount = 0
    private val maxBitmapRetries = 5
    private val bitmapRetryDelayMs = 500L

    private var cachedNotificationEnabled = false
    private var lastPermissionCheckTime = 0L
    private val permissionCheckInterval = 30_000L

    private val textPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
            textSize = 100f
            val rawHeight = fontMetrics.descent - fontMetrics.ascent
            textSize = 100f * (islandBitmapHeight.toFloat() / rawHeight)
        }
    }

        private val lyricUpdateFlow =
        MutableSharedFlow<SyncData>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private data class SyncData(
        val rawTitle: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val position: Long,
        val isPlaying: Boolean,
        val currentPackageName: String,
        val isNewSong: Boolean,
        val albumBitmap: Bitmap?,
        val notificationAlbumBitmap: Bitmap?,
        val identifier: String
    )

    private var tickerJob: Job? = null 
    private var progressJob: Job? = null
    private var isCurrentlyPlaying: Boolean = false
    private var currentLyricLines: List<LrcLine>? = null
    private var lastDispatchedLrc: String = ""
    private var currentSyncData: SyncData? = null

    // ─── 解耦模块：通知展示调度器 ─────────────────────────
    private lateinit var notificationPresenter: NotificationPresenter

        override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        cachedNotificationEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()

        // 初始化通知展示模块
        notificationPresenter = NotificationPresenter(this, serviceScope)
        notificationPresenter.register()
        DynamicLyricData.initWhitelist(this)

        serviceScope.launch(Dispatchers.Default) {
            lyricUpdateFlow.collectLatest { data -> processSyncData(data) }
        }

        // 订阅状态变化，驱动通知展示
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                DynamicLyricData.musicState,
                DynamicLyricData.progressFlow,
                DynamicLyricData.whitelistState
            ) { state, _, _ -> state }.collect { state ->
                notificationPresenter.updateState(state, force = false)
            }
        }
    }

       override fun onListenerConnected() {
        super.onListenerConnected()
        val componentName = ComponentName(this, LiveLyricService::class.java)
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
                updateCurrentController(controllers)
            }, componentName)

            updateCurrentController(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCurrentController(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            unregisterAllControllers()
            clearLyricState()
            return
        }

        val playingController = controllers.find {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }

        if (playingController != null) {
            val alreadyTracking = currentControllers.singleOrNull()?.sessionToken == playingController.sessionToken
            if (!alreadyTracking) {
                unregisterAllControllers()
                currentControllers.add(playingController)
                playingController.registerCallback(mediaCallback)
                syncToGlobalData(playingController)
            }
        } else {
            val currentTokens = currentControllers.map { it.sessionToken }.toSet()
            val newTokens = controllers.map { it.sessionToken }.toSet()
            if (currentTokens != newTokens) {
                unregisterAllControllers()
                for (controller in controllers) {
                    currentControllers.add(controller)
                    controller.registerCallback(mediaCallback)
                }
                syncToGlobalData(controllers.first())
            }
        }
    }

    private fun unregisterAllControllers() {
        for (controller in currentControllers) {
            try {
                controller.unregisterCallback(mediaCallback)
            } catch (_: Exception) {}
        }
        currentControllers.clear()
    }

    private fun clearLyricState() {
        currentSongIdentifier = ""
        isCurrentlyPlaying = false
        DynamicLyricData.updateLoadingAlbumArt(false)
        DynamicLyricData.updateFetchingLyrics(false)
        DynamicLyricData.updateAnchor(0L, false)
        DynamicLyricData.updateRightTitles(" ", " ", " ", " ", 0L, false, "")
        notificationPresenter.clearNotifications()
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val playingController = currentControllers.find {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: currentControllers.firstOrNull()
            syncToGlobalData(playingController)
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val playingController = currentControllers.find {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: currentControllers.firstOrNull()
            syncToGlobalData(playingController)
        }
        override fun onSessionDestroyed() {
            try {
                val componentName = ComponentName(this@LiveLyricService, LiveLyricService::class.java)
                updateCurrentController(mediaSessionManager.getActiveSessions(componentName))
            } catch (_: Exception) {}
        }
    }


    private fun syncToGlobalData(controller: MediaController?) {
        controller ?: return

        val metadata = controller.metadata ?: return
        val playbackState = controller.playbackState ?: return
        val currentPackageName = controller.packageName ?: ""

        val rawTitle = (metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.lines()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: "Playing~")
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val position = playbackState.position
        val isPlaying = playbackState.state == PlaybackState.STATE_PLAYING

        val newIdentifier = "$currentPackageName-$artist-$album-$duration"
        val isNewSong = (newIdentifier != currentSongIdentifier) || DynamicLyricData.currentState.albumBitmap == null
        
        if (isNewSong) {
            currentSongIdentifier = newIdentifier
            DynamicLyricData.updateBitmaps(null, null)
            
            cancelBitmapRetry()
            tickerJob?.cancel()
            progressJob?.cancel()
            tickerJob = null
            progressJob = null
        }
        
        // 使用 AlbumImageProcessor 处理图片
        val albumBitmap = if (isNewSong) {
            val raw = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            AlbumImageProcessor.safeCopyBitmap(raw)
        } else {
            DynamicLyricData.currentState.albumBitmap
        }

        val notificationAlbumBitmap = if (isNewSong) {
            albumBitmap?.let { AlbumImageProcessor.processAlbumBitmap(it) }
        } else {
            DynamicLyricData.currentState.notificationAlbumBitmap
        }

        if (isNewSong && albumBitmap == null) {
            scheduleBitmapRetry(controller)
        } else if (isNewSong) {
            cancelBitmapRetry()
        }

        lyricUpdateFlow.tryEmit(
            SyncData(
                rawTitle, artist, album, duration, position, isPlaying,
                currentPackageName, isNewSong, albumBitmap, notificationAlbumBitmap, newIdentifier
            )
        )
    }

    private fun scheduleBitmapRetry(controller: MediaController) {
        cancelBitmapRetry()
        bitmapRetryCount = 0
        DynamicLyricData.updateLoadingAlbumArt(true)
        bitmapRetryJob = serviceScope.launch {
            while (bitmapRetryCount < maxBitmapRetries) {
                delay(bitmapRetryDelayMs)
                bitmapRetryCount++
                val metadata = controller.metadata ?: continue
                val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                if (bitmap != null) {
                    if (controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != currentSyncData?.rawTitle) {
                        break
                    }
                    DynamicLyricData.updateLoadingAlbumArt(false)
                    syncToGlobalData(controller)
                    break
                }
            }
            DynamicLyricData.updateLoadingAlbumArt(false)
        }
    }

    private fun cancelBitmapRetry() {
        bitmapRetryJob?.cancel()
        bitmapRetryJob = null
    }

    private suspend fun processSyncData(data: SyncData) {
        val sp = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
        val enableDynamicIsland = sp.getBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, Constants.DEFAULT_ENABLE_DYNAMIC_ISLAND)
        val pauseListening = !enableDynamicIsland
        val isWhitelisted = DynamicLyricData.whitelistState.value.contains(data.currentPackageName)

        if (pauseListening || !isWhitelisted) {
            isCurrentlyPlaying = false
            DynamicLyricData.updateAnchor(data.position, false)
            DynamicLyricData.updateRightTitles(
                islandText = " ",
                notificationText = " ",
                newSongLyric = " ",
                newSongInfo = " ",
                newDuration = 0L,
                newIsPlaying = false,
                newPackageName = data.currentPackageName
            )
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastPermissionCheckTime > permissionCheckInterval) {
            cachedNotificationEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
            lastPermissionCheckTime = now
        }
        if (!cachedNotificationEnabled) return

        DynamicLyricData.updateAnchor(data.position, data.isPlaying)

        // 使用 AlbumImageProcessor 做取色，仅在开关打开时
        if (data.isNewSong) {
            val progressColorEnabled = sp.getBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, Constants.DEFAULT_PROGRESS_COLOR_ENABLED)
            val colors = if (progressColorEnabled) AlbumImageProcessor.extractColors(data.albumBitmap) else AlbumImageProcessor.ExtractedColors(
                "#E0E0E0".toColorInt(),
                "#E0E0E0".toColorInt()
            )
            DynamicLyricData.updateColor(colors.dominant, colors.vibrant)
        }

        isCurrentlyPlaying = data.isPlaying
        currentSyncData = data

        if (data.isNewSong) {
            lastDispatchedLrc = ""
            currentLyricLines = null
            
            if (sp.getBoolean(Constants.KEY_ONLINE_LYRIC_ENABLED, Constants.DEFAULT_ONLINE_LYRIC_ENABLED)) {
                DynamicLyricData.updateFetchingLyrics(true)
                val lines = withContext(Dispatchers.IO) {
                    var l = LrcCacheManager.getLyricFromCache(this@LiveLyricService, data.rawTitle, data.artist)?.let { parseLrc(it) }
                    if (l.isNullOrEmpty()) {
                        l = OnlineLyricTargeter.fetchBestLyric(this@LiveLyricService, data.currentPackageName, data.rawTitle, data.artist, data.duration)
                        if (!l.isNullOrEmpty()) {
                            LrcCacheManager.saveLyricToCache(this@LiveLyricService, data.rawTitle, data.artist, buildLrcString(l))
                        }
                    }
                    l
                }
                DynamicLyricData.updateFetchingLyrics(false)

                if (!lines.isNullOrEmpty()) {
                    if (currentSongIdentifier == data.identifier) {
                        currentLyricLines = lines
                        launchLyricScheduler()
                        launchProgressScheduler()
                    }
                } else {
                    if (currentSongIdentifier == data.identifier) {
                        dispatchLyricContent(data.rawTitle, data)
                        launchProgressScheduler()
                    }
                }
            } else {
                dispatchLyricContent(data.rawTitle, data)
            }
        } else {
            if (currentLyricLines != null) launchLyricScheduler() else dispatchLyricContent(data.rawTitle, data)
            launchProgressScheduler()
        }
    }

    private fun launchLyricScheduler() {
        tickerJob?.cancel()
        val lines = currentLyricLines ?: return
        val data = currentSyncData ?: return
        
        tickerJob = serviceScope.launch {
            while (true) {
                val currentPos = with(DynamicLyricData) { musicState.value.getCurrentPosition() }
                val currentLineIndex = lines.indexOfLast { it.startTimeMs <= currentPos }
                val targetLine = if (currentLineIndex != -1) lines[currentLineIndex].content else data.rawTitle
                
                if (targetLine != lastDispatchedLrc) {
                    lastDispatchedLrc = targetLine
                    dispatchLyricContent(targetLine, data)
                }
                
                if (!isCurrentlyPlaying) break

                val nextLineIndex = currentLineIndex + 1
                if (nextLineIndex < lines.size) {
                    val nextLineStartTime = lines[nextLineIndex].startTimeMs
                    val delayMs = (nextLineStartTime - currentPos).coerceAtLeast(10L)
                    delay(delayMs)
                } else break
            }
        }
    }

    private fun launchProgressScheduler() {
        progressJob?.cancel()
        if (!isCurrentlyPlaying) return
        val duration = currentSyncData?.duration ?: return
        if (duration <= 0) return

        progressJob = serviceScope.launch {
            var lastPercent = -1
            while (true) {
                val currentPos = with(DynamicLyricData) { musicState.value.getCurrentPosition() }
                val currentPercent = ((currentPos.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
                
                if (currentPercent != lastPercent) {
                    DynamicLyricData.emitProgress(currentPercent.toFloat())
                    lastPercent = currentPercent
                }

                if (currentPercent >= 100) break
                
                val nextPercentPos = ((currentPercent + 1).toDouble() / 100.0 * duration).toLong()
                val delayMs = (nextPercentPos - currentPos).coerceIn(500L, 2000L)
                delay(delayMs)
            }
        }
    }

    private var lastDispatchedIslandLeft = ""
    private var lastDispatchedIsPlaying = false
    private var lastDispatchedShowAlbum = false

    private fun dispatchLyricContent(targetText: String, data: SyncData) {
        val songLyric = if (currentLyricLines != null) targetText else data.rawTitle
        val pref = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)

        val showIslandLeftAlbum = pref.getBoolean(Constants.KEY_ISLAND_LEFT_ALBUM, Constants.DEFAULT_ISLAND_LEFT_ALBUM)
        val disableLyricSplit = pref.getBoolean(Constants.KEY_DISABLE_LYRIC_SPLIT, Constants.DEFAULT_DISABLE_LYRIC_SPLIT)

        val (islandLeft, islandRight, notificationLeft, notificationRight) = splitTitleByPixelWidth(songLyric, showIslandLeftAlbum)

        val finalIslandLeft: String
        val finalIslandRight: String
        if (disableLyricSplit) {
            finalIslandLeft = ""
            finalIslandRight = songLyric
        } else {
            finalIslandLeft = islandLeft
            finalIslandRight = islandRight
        }

        // 通知栏：始终按自己的像素宽度分割，不受 disableLyricSplit 影响
        val finalNotificationLeft = notificationLeft

        val songInfo = if (currentLyricLines != null) "${data.artist} - ${data.rawTitle}" else data.artist

        val shouldUpdateBitmap = data.isNewSong ||
                                finalIslandLeft != lastDispatchedIslandLeft || 
                                data.isPlaying != lastDispatchedIsPlaying || 
                                showIslandLeftAlbum != lastDispatchedShowAlbum

        if (shouldUpdateBitmap) {
            lastDispatchedIslandLeft = finalIslandLeft
            lastDispatchedIsPlaying = data.isPlaying
            lastDispatchedShowAlbum = showIslandLeftAlbum
        }

        DynamicLyricData.updateBitmaps(data.albumBitmap, data.notificationAlbumBitmap)
        DynamicLyricData.updateLeftTitles(finalIslandLeft, finalNotificationLeft)
        DynamicLyricData.updateRightTitles(finalIslandRight,
            notificationRight, songLyric, songInfo, data.duration, data.isPlaying, data.currentPackageName, showIslandLeftAlbum)
    }

    private fun parseLrc(lrcText: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        lrcText.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msRaw = match.groupValues[3]
                val ms = if (msRaw.length == 2) msRaw.toLong() * 10 else msRaw.toLong()
                val content = match.groupValues[4].trim()
                if (content.isNotEmpty()) {
                    lines.add(LrcLine(min * 60000 + sec * 1000 + ms, content))
                }
            }
        }
        return lines
    }

    @SuppressLint("DefaultLocale")
    private fun buildLrcString(lines: List<LrcLine>): String {
        return lines.joinToString("\n") { line ->
            val totalMs = line.startTimeMs
            val min = totalMs / 60000
            val sec = (totalMs % 60000) / 1000
            val ms = (totalMs % 1000) / 10
            String.format("[%02d:%02d.%02d]%s", min, sec, ms, line.content)
        }
    }

    data class LyricSplitResult(
        val islandLeft: String,
        val islandRight: String,
        val notificationLeft: String,
        val notificationRight: String
    )


    private val defaultSizePx by lazy { TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics) }
    
    private fun scalePxToInternalLimit(targetLimitPx: Float): Float {
        return targetLimitPx * (textPaint.textSize / defaultSizePx.coerceAtLeast(1f))
    }

    private fun splitTitleByPixelWidth(title: String, showIslandLeftAlbum: Boolean = false): LyricSplitResult {
        if (title.isBlank()) return LyricSplitResult("", "HyperLyric", "", "HyperLyric")

        val totalWidth = textPaint.measureText(title)
        
        val cutLimitRaw = if (showIslandLeftAlbum) 650f else 720f
        val leftTextLimitRaw = if (showIslandLeftAlbum) 280f else 360f
        
        val cutLimitPX = scalePxToInternalLimit(cutLimitRaw)
        val leftTextLimitPX = scalePxToInternalLimit(leftTextLimitRaw)
        
        var islandLeft: String
        var islandRight: String

        if (totalWidth <= cutLimitPX) {
            val targetLeftWidth = if (showIslandLeftAlbum) (totalWidth / 2f) - scalePxToInternalLimit(60f) else totalWidth / 2f
            val cutIndex = computeSplitIndexByPixel(title, targetLeftWidth.coerceAtLeast(0f), leftTextLimitPX)
            islandLeft = title.substring(0, cutIndex).trim()
            islandRight = title.substring(cutIndex).trim()
        } else {
            val cutIndex = computeSplitIndexByPixel(title, leftTextLimitPX, leftTextLimitPX)
            islandLeft = title.substring(0, cutIndex).trim()
            islandRight = title.substring(cutIndex).trim()
        }

        if (islandRight.isEmpty() && islandLeft.isNotEmpty()) {
            islandRight = islandLeft
            islandLeft = ""
        }

        val focusNotificationLimitPX = scalePxToInternalLimit(600f)
        var notificationLeft: String
        var notificationRight = " "

        if (totalWidth <= focusNotificationLimitPX) {
            notificationLeft = title
        } else {
            val cutIndex = computeSplitIndexByPixel(title, focusNotificationLimitPX, focusNotificationLimitPX)
            notificationLeft = title.substring(0, cutIndex).trim()
            notificationRight = title.substring(cutIndex).trim()
        }

        return LyricSplitResult(islandLeft, islandRight, notificationLeft, notificationRight)
    }

    private fun computeSplitIndexByPixel(title: String, targetWidthPx: Float, maxLeftPx: Float): Int {
        var splitIndex = textPaint.breakText(title, true, targetWidthPx, null)

        if (splitIndex < title.length && textPaint.measureText(title, 0, splitIndex) < targetWidthPx) {
            if (textPaint.measureText(title, 0, splitIndex + 1) <= maxLeftPx) {
                splitIndex++
            }
        }
        
        splitIndex = splitIndex.coerceIn(0, title.length)

        return adjustForWordBoundary(title, splitIndex, maxLeftPx)
    }

    private fun adjustForWordBoundary(text: String, originalIndex: Int, maxLimitPx: Float): Int {
        var idx = originalIndex
        if (idx <= 0 || idx >= text.length) return idx.coerceIn(0, text.length)

        val isAsciiAlnum = { c: Char -> c.isLetterOrDigit() && c.code < 128 }
        
        if (!isAsciiAlnum(text[idx - 1]) || !isAsciiAlnum(text[idx])) return idx

        var backSplit = idx
        while (backSplit > 0 && isAsciiAlnum(text[backSplit - 1])) backSplit--

        var forwardSplit = idx
        while (forwardSplit < text.length && isAsciiAlnum(text[forwardSplit])) forwardSplit++

        val forwardPx = textPaint.measureText(text, 0, forwardSplit)

        if (forwardPx > maxLimitPx) {
             return backSplit
        }

        val forwardLeftLen = forwardSplit
        val forwardRightLen = text.length - forwardSplit
        val forwardDiff = kotlin.math.abs(forwardLeftLen - forwardRightLen)

        val backLeftLen = backSplit
        val backRightLen = text.length - backSplit
        val backDiff = kotlin.math.abs(backLeftLen - backRightLen)

        idx = if (backDiff < forwardDiff) backSplit else forwardSplit

        return idx.coerceIn(0, text.length)
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationPresenter.unregister()
        notificationPresenter.clearNotifications()
        serviceScope.cancel()
        tickerJob?.cancel()
        progressJob?.cancel()
        unregisterAllControllers()
    }

    companion object {
        /**
         * 静默重连 NotificationListenerService。
         * 通过先禁用再启用组件，触发系统重新绑定监听服务。
         * 用于解决杀后台后监听器假死的问题。
         */
        fun ensureListenerBound(context: Context) {
            try {
                val pm = context.packageManager
                val cn = ComponentName(context, LiveLyricService::class.java)
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                requestRebind(cn)
            } catch (_: Exception) { }
        }
    }
}
