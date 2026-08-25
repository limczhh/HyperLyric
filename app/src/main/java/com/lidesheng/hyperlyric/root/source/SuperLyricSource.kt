package com.lidesheng.hyperlyric.root.source

import android.os.SystemClock
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class SuperLyricSource : LyricSource {

    override val id = "superlyric"
    override val displayName = "SuperLyric"

    private var app: android.app.Application? = null
    private var sink: LyricSink? = null
    private var receiver: ISuperLyricReceiver? = null
    private var positionJob: Job? = null
    private val positionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var lastKnownPosition: Long = -1L
    @Volatile
    private var activePublisher: String? = null
    private var positionPublisher: String? = null
    private val streamGeneration = AtomicLong(0L)
    @Volatile
    private var playbackStarted = false
    private var lastMetadataKey: String? = null
    private var lastMetadataTitle: String? = null
    private var lastMetadataArtist: String? = null
    private var lastMetadataAlbum: String? = null
    private var lastDebugLyricSignature: String? = null
    private var lastDebugLyricAt: Long = 0L

    fun initialize(app: android.app.Application) {
        this.app = app
    }

    override fun isAvailable(): Boolean = try {
        val available = SuperLyricHelper.isAvailable()
        HookLogger.d(TAG, "检查数据源可用性: available=$available")
        available
    } catch (e: Exception) {
        HookLogger.w(TAG, "检查数据源可用性失败", e)
        false
    }

    override fun start(sink: LyricSink) {
        this.sink = sink
        stopPositionPolling()
        activePublisher = null
        playbackStarted = false

        // 先检查 SuperLyric 系统服务是否可用
        val available = runCatching { SuperLyricHelper.isAvailable() }
            .getOrElse {
                HookLogger.w(TAG, "跳过接收端注册: reason=availability_check_failed", it)
                return
            }
        if (!available) {
            HookLogger.w(TAG, "跳过接收端注册: reason=service_unavailable")
            return
        }

        val stub = object : ISuperLyricReceiver.Stub() {
            override fun onLyric(publisher: String, data: SuperLyricData) {
                try {
                    handleLyric(publisher, data)
                } catch (e: Exception) {
                    HookLogger.w(TAG, "处理歌词数据失败", e)
                }
            }

            override fun onStop(publisher: String, data: SuperLyricData) {
                if (activePublisher != publisher) {
                    HookLogger.d(
                        TAG,
                        "忽略过期停止事件: publisher=$publisher, active=${activePublisher ?: "<none>"}"
                    )
                    return
                }
                // SuperLyric uses this callback for both pause/stop and publisher process
                // termination. It is a playback-state event, not a source-session boundary;
                // keep the publisher metadata so a pause/resume without metadata stays on the
                // same media session.
                HookLogger.d(TAG, "收到停止事件，保留媒体会话: publisher=$publisher")
                stopPositionPolling(clearMetadata = false)
                playbackStarted = false
                @Suppress("UNNECESSARY_SAFE_CALL")
                sink?.onPlaybackStateChanged(false)
            }
        }
        receiver = stub

        try {
            SuperLyricHelper.registerReceiver(stub)
            if (HookLogger.isDebugEnabled) {
                val registered = SuperLyricHelper.isReceiverRegistered(stub)
                HookLogger.d(TAG, "更新接收端注册状态: registered=$registered")
            }
        } catch (e: Exception) {
            HookLogger.e(TAG, "注册接收端失败", e)
        }
    }

    override fun stop() {
        stopPositionPolling()
        receiver?.let {
            try {
                SuperLyricHelper.unregisterReceiver(it)
            } catch (e: Exception) {
                HookLogger.w(TAG, "注销接收端失败", e)
            }
        }
        receiver = null
        activePublisher = null
        playbackStarted = false
        sink?.onStop()
        sink = null
        HookLogger.d(TAG, "数据源已停止")
    }

    private fun handleLyric(publisher: String, data: SuperLyricData) {
        val currentSink = sink ?: return

        // 无实际数据（如拖动进度条时的 BUFFERING 状态），忽略
        val hasContent = data.hasLyric() || data.hasTitle() || data.hasArtist() || data.hasAlbum()
        if (!hasContent) return

        if (activePublisher != publisher) {
            val previousPublisher = activePublisher
            activePublisher = publisher
            stopPositionPolling()
            playbackStarted = false
            if (previousPublisher != null) {
                // SuperLyric does not expose a stable track id. A publisher change is the
                // source-level boundary that can safely discard the previous stream.
                currentSink.onStop()
            }
        }

        if (data.hasTitle()) lastMetadataTitle = data.title
        if (data.hasArtist()) lastMetadataArtist = data.artist
        if (data.hasAlbum()) lastMetadataAlbum = data.album
        val metadataKey = listOf(
            publisher,
            lastMetadataTitle,
            lastMetadataArtist,
            lastMetadataAlbum
        ).joinToString("\u001F")
        if (lastMetadataKey != metadataKey) {
            lastMetadataKey = metadataKey
            currentSink.onMetadata(
                LyricMediaMetadata(
                    sourceId = id,
                    packageName = publisher,
                    title = lastMetadataTitle,
                    artist = lastMetadataArtist,
                    album = lastMetadataAlbum
                )
            )
        }
        // Establish the new stream owner before asking the renderer to resume. A SuperLyric
        // stream may deliver its first lyric line immediately after metadata; publishing the
        // package first and playback before the line lets the self-heal path see a ready session.
        if (!playbackStarted) {
            playbackStarted = true
            currentSink.onPlaybackStateChanged(true)
        }
        startPositionPolling(publisher)

        if (data.hasLyric()) {
            val lyric = data.lyric
            if (lyric != null) {
                val st = lyric.startTime
                val et = lyric.endTime

                @Suppress("DEPRECATION")
                val dl = lyric.delay
                logLyricEvent(publisher, lyric.text, st, et, dl)

                if (st == 0L && et == 0L) {
                    val pos = lastKnownPosition.takeIf { it >= 0 }
                        ?: app?.let { MediaMetadataHelper.getPlaybackPosition(it, publisher) }
                            ?.takeIf { it >= 0 }
                            ?.also { lastKnownPosition = it }
                    if (dl > 0 && pos != null) {
                        val richLine = convertToRichLyricLine(lyric, data).copy(
                            begin = pos,
                            end = pos + dl,
                            duration = dl
                        )
                        currentSink.onLyricLine(richLine)
                        startPositionPolling(publisher)
                    } else {
                        val text = buildString {
                            append(lyric.text)
                            if (data.hasTranslation()) {
                                data.translation?.text?.let { append("\n").append(it) }
                            }
                        }
                        currentSink.onPlainText(text)
                    }
                } else {
                    val richLine = convertToRichLyricLine(lyric, data)
                    currentSink.onLyricLine(richLine)
                    startPositionPolling(publisher)
                }
            }
        }
    }

    private fun convertToRichLyricLine(line: SuperLyricLine, data: SuperLyricData): RichLyricLine {
        val words = line.words?.map { word ->
            LyricWord(
                begin = word.startTime,
                end = word.endTime,
                text = word.word
            )
        }

        val translationText = if (data.hasTranslation()) data.translation?.text else null
        val translationWords = if (data.hasTranslation()) {
            data.translation?.words?.map { word ->
                LyricWord(
                    begin = word.startTime,
                    end = word.endTime,
                    text = word.word
                )
            }
        } else null

        val secondaryText = if (data.hasSecondary()) data.secondary?.text else null
        val secondaryWords = if (data.hasSecondary()) {
            data.secondary?.words?.map { word ->
                LyricWord(
                    begin = word.startTime,
                    end = word.endTime,
                    text = word.word
                )
            }
        } else null

        return RichLyricLine(
            begin = line.startTime,
            end = line.endTime,
            text = line.text,
            words = words,
            translation = translationText,
            translationWords = translationWords,
            secondary = secondaryText,
            secondaryWords = secondaryWords
        )
    }

    private fun logLyricEvent(
        publisher: String,
        text: String,
        startTime: Long,
        endTime: Long,
        delay: Long
    ) {
        if (!HookLogger.isDebugEnabled) return
        val now = SystemClock.uptimeMillis()
        val signature = "$publisher\u001F$text\u001F$startTime\u001F$endTime\u001F$delay"
        synchronized(this) {
            if (signature == lastDebugLyricSignature ||
                now - lastDebugLyricAt < DEBUG_LYRIC_LOG_MIN_INTERVAL_MS
            ) {
                return
            }
            lastDebugLyricSignature = signature
            lastDebugLyricAt = now
        }
        HookLogger.d(
            TAG,
            "歌词事件: text=$text, start=$startTime, end=$endTime, " +
                    "delay=$delay, pos=$lastKnownPosition, pub=$publisher"
        )
    }

    private fun startPositionPolling(publisher: String) {
        if (positionPublisher == publisher && positionJob?.isActive == true) return
        positionJob?.cancel()
        positionPublisher = publisher
        val generation = streamGeneration.incrementAndGet()
        val context = app ?: return
        positionJob = positionScope.launch {
            while (isActive && activePublisher == publisher &&
                streamGeneration.get() == generation
            ) {
                val progress = MediaMetadataHelper.getPlaybackProgress(context, publisher)
                if (activePublisher != publisher || streamGeneration.get() != generation) break
                if (progress.position >= 0) {
                    lastKnownPosition = progress.position
                    sink?.onPositionChanged(progress.position, progress.playbackSpeed)
                }
                delay(50)
            }
        }
    }

    private fun stopPositionPolling(clearMetadata: Boolean = true) {
        streamGeneration.incrementAndGet()
        positionJob?.cancel()
        positionJob = null
        positionPublisher = null
        lastKnownPosition = -1L
        lastDebugLyricSignature = null
        lastDebugLyricAt = 0L
        if (clearMetadata) {
            lastMetadataKey = null
            lastMetadataTitle = null
            lastMetadataArtist = null
            lastMetadataAlbum = null
        }
    }

    companion object {
        private const val TAG = "SuperLyricSource"
        private const val DEBUG_LYRIC_LOG_MIN_INTERVAL_MS = 200L
    }
}

