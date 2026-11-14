package com.lidesheng.hyperlyric.root.source

import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import com.hchen.superlyricapi.SuperLyricLine
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger

class SuperLyricSource : LyricSource {

    override val id = "superlyric"
    override val displayName = "SuperLyric"

    private var app: android.app.Application? = null
    @Volatile
    private var sink: LyricSink? = null
    private var receiver: ISuperLyricReceiver? = null
    private var arbiter: LyricSessionArbiter? = null

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
        val application = app ?: return
        val streams = LinkedHashMap<String, PublisherStream>()
        val owner = LyricSessionArbiter(application, sink, 50L) { streams.remove(it)?.reset() }
        arbiter = owner
        owner.start()

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
                    owner.update(publisher, publisher) {
                        val stream = streams.getOrPut(publisher) { PublisherStream(owner) }
                        stream.sink = this
                        stream.handleLyric(publisher, data)
                    }
                } catch (e: Exception) {
                    HookLogger.w(TAG, "处理歌词数据失败", e)
                }
            }

            override fun onStop(publisher: String, data: SuperLyricData) {
                owner.update(publisher, publisher) {
                    streams[publisher]?.playbackStarted = false
                    onPlaybackStateChanged(false)
                }
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
        val owner = arbiter
        owner?.close()
        receiver?.let {
            try {
                SuperLyricHelper.unregisterReceiver(it)
            } catch (e: Exception) {
                HookLogger.w(TAG, "注销接收端失败", e)
            }
        }
        receiver = null
        if (owner == null) sink?.onStop()
        arbiter = null
        sink = null
        HookLogger.d(TAG, "数据源已停止")
    }

    private inner class PublisherStream(private val owner: LyricSessionArbiter) {
        var sink: LyricSink? = null
        @Volatile
        private var activePublisher: String? = null
        @Volatile
        var playbackStarted = false
        private var lastMetadataKey: String? = null
        private var lastMetadataTitle: String? = null
        private var lastMetadataArtist: String? = null
        private var lastMetadataAlbum: String? = null
        private var activeLyricId: String? = null
        private var fullSong: Song? = null

        fun reset() {
            activePublisher = null
            playbackStarted = false
            clearMetadata()
            activeLyricId = null
            fullSong = null
        }

        fun handleLyric(publisher: String, rawData: SuperLyricData) {
            val currentSink = sink ?: return

            // Timelines are kept per publisher. Deltas only update that publisher's ID/position;
            // using the SDK's global lyricId cache would mix publishers that reuse the same ID.
            val data = rawData

            // 无实际数据（如拖动进度条时的 BUFFERING 状态），忽略
            val hasContent = data.hasLyric() || data.hasAllLyrics() ||
                data.hasTitle() || data.hasArtist() || data.hasAlbum() || data.hasLyricId()
            if (!hasContent) return

            val newLyricId = data.lyricId?.takeIf { it.isNotBlank() }
            val publisherChanged = activePublisher != publisher
            val lyricIdChanged = !publisherChanged && activeLyricId != null &&
                newLyricId != null && activeLyricId != newLyricId
            if (publisherChanged || lyricIdChanged) {
                val previousPublisher = activePublisher
                activePublisher = publisher
                clearMetadata()
                playbackStarted = false
                fullSong = null
                activeLyricId = null
                if (previousPublisher != null) {
                    currentSink.onStop()
                }
            }
            currentSink.onSourceStateChanged(true, MediaIdentity(packageName = publisher))
            activePublisher = publisher
            if (newLyricId != null) activeLyricId = newLyricId

            if (data.hasTitle()) lastMetadataTitle = data.title
            if (data.hasArtist()) lastMetadataArtist = data.artist
            if (data.hasAlbum()) lastMetadataAlbum = data.album
            val metadataKey = listOf(
                publisher,
                lastMetadataTitle,
                lastMetadataArtist,
                lastMetadataAlbum
            ).joinToString("\u001F")
            val metadataChanged = lastMetadataKey != metadataKey
            if (metadataChanged) {
                lastMetadataKey = metadataKey
            }
            if (fullSong != null && !data.hasAllLyrics() && newLyricId == null &&
                (data.hasTitle() && lastMetadataTitle != fullSong?.name ||
                    data.hasArtist() && lastMetadataArtist != fullSong?.artist ||
                    data.hasAlbum() && lastMetadataAlbum != fullSong?.album)
            ) {
                fullSong = null
            }
            val incomingFullSong = if (data.hasAllLyrics() &&
                (fullSong == null || rawData.hasAllLyrics())
            ) {
                Song(
                    // lyricId identifies the lyric payload, not the music platform's song ID.
                    // AMLL's platform probe must not receive it as Song.id.
                    name = lastMetadataTitle,
                    artist = lastMetadataArtist,
                    album = lastMetadataAlbum,
                    duration = data.duration.takeIf { data.hasDuration() } ?: 0L,
                    lyrics = data.allLyrics.orEmpty().mapNotNull { line ->
                        line?.let { convertToRichLyricLine(it) }
                    }
                )
            } else null
            // Do not re-publish a recovered timeline on every progress delta: that would discard
            // the enhancement result and rebuild the timeline at the publisher's update rate.
            val publishFull = incomingFullSong != null &&
                (fullSong == null || (rawData.hasAllLyrics() && incomingFullSong != fullSong))
            if (publishFull) {
                fullSong = incomingFullSong
                currentSink.onSongChanged(incomingFullSong)
            } else if (metadataChanged && fullSong == null) {
                currentSink.onSongChanged(
                    Song(name = lastMetadataTitle, artist = lastMetadataArtist,
                        album = lastMetadataAlbum, lyrics = emptyList())
                )
            }
            if (metadataChanged || publishFull) {
                currentSink.onMetadata(
                    LyricMediaMetadata(sourceId = id, packageName = publisher,
                        title = lastMetadataTitle, artist = lastMetadataArtist,
                        album = lastMetadataAlbum)
                )
            }
            // Establish the new stream owner before asking the renderer to resume. A SuperLyric
            // stream may deliver its first lyric line immediately after metadata; publishing the
            // package first and playback before the line lets the self-heal path see a ready session.
            if (!playbackStarted) {
                playbackStarted = true
                currentSink.onPlaybackStateChanged(true)
            }

            if (data.hasPosition()) {
                currentSink.onPositionChanged(data.position, 1f)
            }

            if (fullSong == null && data.hasLyric()) {
                val lyric = data.lyric
                if (lyric != null) {
                    val st = lyric.startTime
                    val et = lyric.endTime

                    @Suppress("DEPRECATION")
                    val dl = lyric.delay
                    if (st == 0L && et == 0L) {
                        val pos = data.position.takeIf { data.hasPosition() && it >= 0L }
                            ?: owner.currentPosition(publisher).takeIf { it >= 0L }
                        if (dl > 0 && pos != null) {
                            val richLine = convertToRichLyricLine(lyric, data).copy(
                                begin = pos,
                                end = pos + dl,
                                duration = dl
                            )
                            currentSink.onLyricLine(richLine)
                        } else if (data.hasTranslation() || data.hasSecondary()) {
                            // Keep all structured lanes when SuperLyric has no usable line timing.
                            // The plain-text sink can represent only the original and one translated
                            // line, so sending this as text would silently discard secondary content.
                            currentSink.onLyricLine(convertToRichLyricLine(lyric, data))
                        } else {
                            currentSink.onPlainText(lyric.text)
                        }
                    } else {
                        val richLine = convertToRichLyricLine(lyric, data)
                        currentSink.onLyricLine(richLine)
                    }
                }
            }
        }

        private fun convertToRichLyricLine(
            line: SuperLyricLine,
            data: SuperLyricData? = null
        ): RichLyricLine {
            val words = line.words?.map { word ->
                LyricWord(
                    begin = word.startTime,
                    end = word.endTime,
                    text = word.word
                )
            }

            val translationText = data?.translation?.text ?: line.translation
            val translationWords = if (data?.hasTranslation() == true) {
                data.translation?.words?.map { word ->
                    LyricWord(
                        begin = word.startTime,
                        end = word.endTime,
                        text = word.word
                    )
                }
            } else null

            // SuperLyric calls this lane "secondary", but the unified model exposes it as
            // romanization. RichLyricLine has no romaWords field, so retain word-only payloads by
            // joining their text rather than leaving the Roma lane empty.
            val romaText = if (data?.hasSecondary() == true) {
                data.secondary?.text?.takeIf { it.isNotBlank() }
                    ?: data.secondary?.words?.joinToString("") { it.word }
                        ?.takeIf { it.isNotBlank() }
            } else line.secondary

            return RichLyricLine(
                begin = line.startTime,
                end = line.endTime,
                text = line.text,
                words = words,
                translation = translationText,
                translationWords = translationWords,
                roma = romaText
            )
        }

        private fun clearMetadata() {
            lastMetadataKey = null
            lastMetadataTitle = null
            lastMetadataArtist = null
            lastMetadataAlbum = null
        }
    }

    companion object {
        private const val TAG = "SuperLyricSource"
    }
}
