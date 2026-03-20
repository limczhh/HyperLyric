package com.lidesheng.hyperlyric

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.TypedValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.lidesheng.hyperlyric.model.DynamicLyricData
import com.lidesheng.hyperlyric.online.LrcCacheManager
import com.lidesheng.hyperlyric.online.LrcLine
import com.lidesheng.hyperlyric.online.OnlineLyricTargeter
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
import kotlin.math.abs
import kotlin.math.ceil
import androidx.core.graphics.toColorInt


class LiveLyricService : NotificationListenerService() {
    private lateinit var mediaSessionManager: MediaSessionManager
    private var currentController: MediaController? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastUpdateTime = 0L
    private val updateInterval = 100L

    private val ISLAND_BITMAP_HEIGHT = 128
    private val DEFAULT_COLOR = "#E0E0E0".toColorInt()
    
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
            textSize = 100f * (ISLAND_BITMAP_HEIGHT.toFloat() / rawHeight)
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

        override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        cachedNotificationEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()

        serviceScope.launch(Dispatchers.Default) {
            lyricUpdateFlow.collectLatest { data -> processSyncData(data) }
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
            unregisterCurrentController()
            clearLyricState()
            return
        }

        val targetController = controllers.find {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.first()

        if (currentController?.sessionToken != targetController.sessionToken) {
            unregisterCurrentController()
            currentController = targetController
            currentController?.registerCallback(mediaCallback)
            syncToGlobalData(targetController, forceUpdate = true)
        }
    }

    private fun unregisterCurrentController() {
        try {
            currentController?.unregisterCallback(mediaCallback)
        } catch (_: Exception) {}
        currentController = null
    }

    private fun clearLyricState() {
        currentSongIdentifier = ""
        isCurrentlyPlaying = false
        DynamicLyricData.updateLoadingAlbumArt(false)
        DynamicLyricData.updateFetchingLyrics(false)
        DynamicLyricData.updateAnchor(0L, false)
        DynamicLyricData.updateRightTitles(" ", " ", " ", " ", 0L, false, "")
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            syncToGlobalData(currentController, forceUpdate = true)
        }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            syncToGlobalData(currentController, forceUpdate = true)
            
            val status = state?.state
            if (status == PlaybackState.STATE_PAUSED || status == PlaybackState.STATE_STOPPED) {
                try {
                    val componentName = ComponentName(this@LiveLyricService, LiveLyricService::class.java)
                    updateCurrentController(mediaSessionManager.getActiveSessions(componentName))
                } catch (_: Exception) {}
            }
        }
        override fun onSessionDestroyed() {
            try {
                val componentName = ComponentName(this@LiveLyricService, LiveLyricService::class.java)
                updateCurrentController(mediaSessionManager.getActiveSessions(componentName))
            } catch (_: Exception) {}
        }
    }

    private var previousLabelBitmap: Bitmap? = null

    private fun syncToGlobalData(controller: MediaController?, forceUpdate: Boolean = false) {
        controller ?: return
        val currentTime = System.currentTimeMillis()
        if (!forceUpdate && (currentTime - lastUpdateTime < updateInterval)) return
        lastUpdateTime = currentTime

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
            DynamicLyricData.updateBitmaps(null, null, null)
            
            cancelBitmapRetry()
            tickerJob?.cancel()
            progressJob?.cancel()
            tickerJob = null
            progressJob = null
        }
        

        val albumBitmap = if (isNewSong) {
            val raw = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            safeCopyBitmap(raw)
        } else {
            DynamicLyricData.currentState.albumBitmap
        }

        val notificationAlbumBitmap = if (isNewSong) {
            albumBitmap?.let { processAlbumBitmap(it) }
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
                    syncToGlobalData(controller, forceUpdate = true)
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
        val pauseListening = sp.getBoolean(Constants.KEY_PAUSE_LISTENING, Constants.DEFAULT_PAUSE_LISTENING)
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

        if (data.isNewSong) {
            val progressColorEnabled = sp.getBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, Constants.DEFAULT_PROGRESS_COLOR_ENABLED)
            val colors = if (progressColorEnabled) extractColorsFromBitmap(data.albumBitmap) else ExtractedColors(DEFAULT_COLOR, DEFAULT_COLOR)
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
        val isSendNormalNotification = pref.getBoolean(Constants.KEY_SEND_NORMAL_NOTIFICATION, Constants.DEFAULT_SEND_NORMAL_NOTIFICATION)

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

        val newLabelBitmap = if (shouldUpdateBitmap && data.isPlaying && isSendNormalNotification) {
            if (finalIslandLeft.isNotBlank()) {
                if (showIslandLeftAlbum && data.albumBitmap != null && !data.albumBitmap.isRecycled) {
                    val processedAlbum = data.notificationAlbumBitmap ?: processAlbumBitmap(data.albumBitmap)
                    val textBitmap = generateTextBitmap(finalIslandLeft)
                    val combined = combineBitmapsSideBySide(processedAlbum, textBitmap)
                    textBitmap.recycle()
                    combined
                } else {
                    generateTextBitmap(finalIslandLeft)
                }
            } else if (showIslandLeftAlbum && data.albumBitmap != null && !data.albumBitmap.isRecycled) {
                data.notificationAlbumBitmap ?: processAlbumBitmap(data.albumBitmap)
            } else null
        } else if (!shouldUpdateBitmap) {
            previousLabelBitmap
        } else null
        
        if (shouldUpdateBitmap) {
            val oldBitmapToRecycle = previousLabelBitmap
            if (oldBitmapToRecycle != null && oldBitmapToRecycle != newLabelBitmap && !oldBitmapToRecycle.isRecycled) {
                oldBitmapToRecycle.recycle()
            }
            previousLabelBitmap = newLabelBitmap
            lastDispatchedIslandLeft = finalIslandLeft
            lastDispatchedIsPlaying = data.isPlaying
            lastDispatchedShowAlbum = showIslandLeftAlbum
        }

        DynamicLyricData.updateBitmaps(previousLabelBitmap, data.albumBitmap, data.notificationAlbumBitmap)
        DynamicLyricData.updateLeftTitles(finalIslandLeft, finalNotificationLeft)
        DynamicLyricData.updateRightTitles(finalIslandRight,
            notificationRight, songLyric, songInfo, data.duration, data.isPlaying, data.currentPackageName, showIslandLeftAlbum)


        if (data.isPlaying) {
            try {
                startForegroundService(Intent(this@LiveLyricService, ForegroundLyricService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    private fun combineBitmapsSideBySide(album: Bitmap, textBitmap: Bitmap): Bitmap {
        val gap = (ISLAND_BITMAP_HEIGHT * 0.1f).toInt().coerceAtLeast(1)
        val totalWidth = album.width + gap + textBitmap.width

        val result = createBitmap(totalWidth, ISLAND_BITMAP_HEIGHT)
        val canvas = Canvas(result)

        canvas.drawBitmap(album, 0f, 0f, null)
        val textY = (ISLAND_BITMAP_HEIGHT - textBitmap.height) / 2f
        canvas.drawBitmap(textBitmap, (album.width + gap).toFloat(), textY, null)

        return result
    }

    private fun processAlbumBitmap(source: Bitmap, targetSize: Int = ISLAND_BITMAP_HEIGHT): Bitmap {
        val w = source.width
        val h = source.height
        val cropSize = minOf(w, h)
        val xOffset = (w - cropSize) / 2
        val yOffset = (h - cropSize) / 2

        val output = createBitmap(targetSize, targetSize)
        val canvas = Canvas(output)
        val cornerRadius = targetSize / 4f
        val rectF = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, maskPaint)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        val srcRect = android.graphics.Rect(xOffset, yOffset, xOffset + cropSize, yOffset + cropSize)
        val dstRect = android.graphics.Rect(0, 0, targetSize, targetSize)
        canvas.drawBitmap(source, srcRect, dstRect, bitmapPaint)

        return output
    }

    private fun generateTextBitmap(text: String): Bitmap {
        val fontMetrics = textPaint.fontMetrics
        val textWidth = ceil(textPaint.measureText(text)).toInt().coerceAtLeast(1)

        val bitmap = createBitmap(textWidth, ISLAND_BITMAP_HEIGHT)
        val canvas = Canvas(bitmap)

        val centerY = ISLAND_BITMAP_HEIGHT / 2f
        val textMiddleOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(text, 0f, centerY - textMiddleOffset, textPaint)

        return bitmap
    }

    data class ExtractedColors(val dominant: Int, val vibrant: Int)

    private fun extractColorsFromBitmap(bitmap: Bitmap?): ExtractedColors {
        if (bitmap == null || bitmap.isRecycled) return ExtractedColors(DEFAULT_COLOR, DEFAULT_COLOR)
        return try {
            val targetBitmap = if (bitmap.width > 100 || bitmap.height > 100) {
                bitmap.scale(100, 100, false)
            } else bitmap

            val palette = Palette.from(targetBitmap).generate()
            if (targetBitmap != bitmap && !targetBitmap.isRecycled) targetBitmap.recycle()

            val dominant = palette.getDominantColor(DEFAULT_COLOR)
            var vibrant = palette.getVibrantColor(dominant)
            
            if (isNearBlack(dominant) || isNearWhite(dominant)) {
                vibrant = "#808080".toColorInt()
            } else if (vibrant == dominant || isColorTooSimilar(dominant, vibrant)) {
                vibrant = lightenColor(dominant)
            }
            
            ExtractedColors(dominant, vibrant)
        } catch (_: Exception) {
            ExtractedColors(DEFAULT_COLOR, DEFAULT_COLOR)
        }
    }

    private fun isNearBlack(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2] < 0.15f
    }

    private fun isNearWhite(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2] > 0.85f && hsv[1] < 0.15f
    }

    private fun isColorTooSimilar(color1: Int, color2: Int): Boolean {
        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        Color.colorToHSV(color1, hsv1)
        Color.colorToHSV(color2, hsv2)
        
        return abs(hsv1[0] - hsv2[0]) < 10 && abs(hsv1[1] - hsv2[1]) < 0.1f
    }

    private fun lightenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 1.4f).coerceAtMost(1.0f) // 提高亮度
        hsv[1] = (hsv[1] * 0.8f) // 降低饱和度
        return Color.HSVToColor(hsv)
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
        val forwardDiff = abs(forwardLeftLen - forwardRightLen)

        val backLeftLen = backSplit
        val backRightLen = text.length - backSplit
        val backDiff = abs(backLeftLen - backRightLen)

        idx = if (backDiff < forwardDiff) backSplit else forwardSplit

        return idx.coerceIn(0, text.length)
    }

    private fun safeCopyBitmap(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) return null
        return try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        tickerJob?.cancel()
        progressJob?.cancel()
        currentController?.unregisterCallback(mediaCallback)
    }
  }
