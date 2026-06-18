package com.lidesheng.hyperlyric.root.source

import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine

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

    fun initialize(app: android.app.Application) {
        this.app = app
    }

    override fun isAvailable(): Boolean = try {
        val available = SuperLyricHelper.isAvailable()
        HookLogger.i(TAG, "isAvailable = $available")
        available
    } catch (e: Exception) {
        HookLogger.w(TAG, "isAvailable 检查失败: ${e.message}")
        false
    }

    override fun start(sink: LyricSink) {
        this.sink = sink

        // 先检查 SuperLyric 系统服务是否可用
        val available = try {
            SuperLyricHelper.isAvailable()
        } catch (e: Exception) {
            HookLogger.e(TAG, "SuperLyric 服务不可用（未安装 SuperLyric 模块？）", e)
            false
        }
        if (!available) {
            HookLogger.e(TAG, "SuperLyric 服务未就绪，接收端注册跳过")
            return
        }

        val stub = object : ISuperLyricReceiver.Stub() {
            override fun onLyric(publisher: String, data: SuperLyricData) {
                HookLogger.d(TAG, "onLyric: publisher=$publisher hasLyric=${data.hasLyric()}")
                try {
                    handleLyric(publisher, data)
                } catch (e: Exception) {
                    HookLogger.w(TAG, "处理歌词数据失败", e)
                }
            }

            override fun onStop(publisher: String, data: SuperLyricData) {
                HookLogger.d(TAG, "收到停止事件, publisher=$publisher")
                @Suppress("UNNECESSARY_SAFE_CALL")
                sink?.onPlaybackStateChanged(false)
                @Suppress("UNNECESSARY_SAFE_CALL")
                sink?.onStop()
                stopPositionPolling()
            }
        }
        receiver = stub

        try {
            SuperLyricHelper.registerReceiver(stub)
            val registered = SuperLyricHelper.isReceiverRegistered(stub)
            HookLogger.i(TAG, "接收端注册${if (registered) "成功" else "失败"}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "接收端注册异常", e)
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
        sink = null
        HookLogger.i(TAG, "数据源已停止")
    }

    private fun handleLyric(publisher: String, data: SuperLyricData) {
        val currentSink = sink ?: return

        // 无实际数据（如拖动进度条时的 BUFFERING 状态），忽略
        val hasContent = data.hasLyric() || data.hasTitle() || data.hasArtist() || data.hasAlbum()
        if (!hasContent) return

        currentSink.onPlaybackStateChanged(true)
        currentSink.onMetadata(
            if (data.hasTitle()) data.title else null,
            if (data.hasArtist()) data.artist else null,
            if (data.hasAlbum()) data.album else null,
            publisher
        )
        startPositionPolling(publisher)

        if (data.hasLyric()) {
            val lyric = data.lyric
            if (lyric != null) {
                val st = lyric.startTime
                val et = lyric.endTime
                @Suppress("DEPRECATION")
                val dl = lyric.delay
                val words = lyric.words
                HookLogger.d(TAG, "歌词: text=${lyric.text}, start=$st, end=$et, delay=$dl, " +
                    "words=${words?.size ?: 0}, pos=$lastKnownPosition, pub=$publisher")

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
                        HookLogger.d(TAG, "→ onLyricLine (推算时间): begin=${richLine.begin}, end=${richLine.end}")
                        currentSink.onLyricLine(richLine)
                        startPositionPolling(publisher)
                    } else {
                        val text = buildString {
                            append(lyric.text)
                            if (data.hasTranslation()) {
                                data.translation?.text?.let { append("\n").append(it) }
                            }
                        }
                        HookLogger.d(TAG, "→ onPlainText: $text")
                        currentSink.onPlainText(text)
                    }
                } else {
                    val richLine = convertToRichLyricLine(lyric, data)
                    HookLogger.d(TAG, "→ onLyricLine (原始时间): begin=$st, end=$et")
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

    private fun startPositionPolling(publisher: String) {
        positionJob?.cancel()
        val context = app ?: return
        positionJob = positionScope.launch {
            while (isActive) {
                val pos = MediaMetadataHelper.getPlaybackPosition(context, publisher)
                if (pos >= 0) {
                    lastKnownPosition = pos
                    sink?.onPositionChanged(pos)
                }
                delay(50)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
        lastKnownPosition = -1L
    }

    companion object {
        private const val TAG = "SuperLyricSource"
    }
}

