package com.lidesheng.hyperlyric

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.core.graphics.withScale

class LiveLyricService : NotificationListenerService() {
    private lateinit var mediaSessionManager: MediaSessionManager
    private var currentController: MediaController? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastUpdateTime = 0L
    private val updateInterval = 100L

    private val IMAGE_SHRINK_THRESHOLD = 10
    private val DEFAULT_COLOR = Color.BLACK
    
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
            textSize = 90f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
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
            albumBitmap?.let { scaleBitmapEfficiently(it) }
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
            val extractedColor = if (progressColorEnabled) extractColorFromBitmap(data.albumBitmap) else DEFAULT_COLOR
            DynamicLyricData.updateColor(extractedColor)
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

        val finalNotificationLeft: String
        val finalNotificationRight: String
        if (disableLyricSplit) {
            finalNotificationLeft = songLyric
            finalNotificationRight = " "
        } else {
            finalNotificationLeft = notificationLeft
            finalNotificationRight = notificationRight
        }

        val songInfo = if (currentLyricLines != null) "${data.artist} - ${data.rawTitle}" else data.artist

        // Optimization: Reuse bitmap if text and state haven't changed
        val shouldUpdateBitmap = data.isNewSong || 
                                islandLeft != lastDispatchedIslandLeft || 
                                data.isPlaying != lastDispatchedIsPlaying || 
                                showIslandLeftAlbum != lastDispatchedShowAlbum

        val newLabelBitmap = if (shouldUpdateBitmap && data.isPlaying && isSendNormalNotification && !disableLyricSplit) {
            if (islandLeft.isNotBlank()) {
                if (showIslandLeftAlbum && data.albumBitmap != null && !data.albumBitmap.isRecycled) {
                    val textBitmap = generateTextBitmap(islandLeft)
                    val combined = combineBitmapsSideBySide(data.albumBitmap, textBitmap)
                    textBitmap.recycle()
                    combined
                } else {
                    generateTextBitmap(islandLeft)
                }
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
            lastDispatchedIslandLeft = islandLeft
            lastDispatchedIsPlaying = data.isPlaying
            lastDispatchedShowAlbum = showIslandLeftAlbum
        }

        DynamicLyricData.updateBitmaps(previousLabelBitmap, data.albumBitmap, data.notificationAlbumBitmap)
        DynamicLyricData.updateLeftTitles(islandLeft, finalNotificationLeft)
        DynamicLyricData.updateRightTitles(islandRight, finalNotificationRight, songLyric, songInfo, data.duration, data.isPlaying, data.currentPackageName, showIslandLeftAlbum)

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
        val targetHeight = textBitmap.height
        val gap = (targetHeight * 0.1f).toInt().coerceAtLeast(1)
        val totalWidth = targetHeight + gap + textBitmap.width

        val result = createBitmap(totalWidth, targetHeight)
        val canvas = Canvas(result)

        canvas.drawBitmap(textBitmap, (targetHeight + gap).toFloat(), 0f, null)

        val dstRect = android.graphics.Rect(0, 0, targetHeight, targetHeight)
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(album, null, dstRect, bmpPaint)

        return result
    }

    private fun generateTextBitmap(text: String): Bitmap {
        val visualWeight = calculateTotalVisualWidth(text)
        val fontMetrics = textPaint.fontMetrics
        val textPhysicalHeight = ceil(fontMetrics.descent - fontMetrics.ascent).toInt()
        val textPhysicalWidth = ceil(textPaint.measureText(text)).toInt()

        val safeWidth = textPhysicalWidth.coerceAtLeast(1)
        val safeHeight = textPhysicalHeight.coerceAtLeast(1)

        val finalCanvasHeight = if (visualWeight <= IMAGE_SHRINK_THRESHOLD) {
            safeHeight
        } else {
            (safeHeight * (visualWeight.toFloat() / IMAGE_SHRINK_THRESHOLD)).toInt()
        }

        val bitmap = createBitmap(safeWidth, finalCanvasHeight)
        val canvas = Canvas(bitmap)

        val centerY = finalCanvasHeight / 2f
        val textMiddleOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        val baselineY = centerY - textMiddleOffset

        canvas.withScale(0.95f, 0.95f, safeWidth / 2f, finalCanvasHeight / 2f) {
            drawText(text, 0f, baselineY, textPaint)
        }

        return bitmap
    }

    private fun extractColorFromBitmap(bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.isRecycled) return DEFAULT_COLOR
        return try {
            val targetBitmap = if (bitmap.width > 100 || bitmap.height > 100) {
                bitmap.scale(100, 100, false)
            } else bitmap
            
            val palette = Palette.from(targetBitmap).generate()
            if (targetBitmap != bitmap && !targetBitmap.isRecycled) targetBitmap.recycle()
            
            palette.getVibrantColor(palette.getDominantColor(DEFAULT_COLOR))
        } catch (_: Exception) {
            DEFAULT_COLOR
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
        
        val cutLimitRaw = if (showIslandLeftAlbum) 660f else 720f
        val leftTextLimitRaw = if (showIslandLeftAlbum) 280f else 360f
        
        val cutLimitPX = scalePxToInternalLimit(cutLimitRaw)
        val leftTextLimitPX = scalePxToInternalLimit(leftTextLimitRaw)
        
        var islandLeft: String
        var islandRight: String

        if (totalWidth <= cutLimitPX) {
            val targetLeftWidth = if (showIslandLeftAlbum) (totalWidth / 2f) - scalePxToInternalLimit(10f) else totalWidth / 2f
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

        val focusNotificationLimitPX = scalePxToInternalLimit(700f)
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

    private fun calculateTotalVisualWidth(text: String): Int = text.sumOf { getCharVisualWidth(it) }
    private fun getCharVisualWidth(c: Char): Int = if (c.code in 0..127) 1 else 2

    private fun scaleBitmapEfficiently(bitmap: Bitmap, maxSize: Int = 128): Bitmap {
        if (bitmap.isRecycled) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val scale = maxSize.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return try {
            bitmap.scale(newW, newH)
        } catch (_: Exception) {
            bitmap
        }
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
