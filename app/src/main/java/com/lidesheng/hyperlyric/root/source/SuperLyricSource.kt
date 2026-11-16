package com.lidesheng.hyperlyric.root.source

import com.hchen.superlyricapi.LyricListener
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.SuperLyric
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.PlaybackState
import com.hchen.superlyricapi.model.Stop
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger

class SuperLyricSource : LyricSource {

    override val id = "superlyric"
    override val displayName = "SuperLyric"

    private var app: android.app.Application? = null
    @Volatile
    private var sink: LyricSink? = null
    private var listener: LyricListener? = null
    private var arbiter: LyricSessionArbiter? = null

    fun initialize(app: android.app.Application) {
        this.app = app
    }

    override fun isAvailable(): Boolean = try {
        val available = SuperLyric.isAvailable()
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
        val available = runCatching { SuperLyric.isAvailable() }
            .getOrElse {
                HookLogger.w(TAG, "跳过接收端注册: reason=availability_check_failed", it)
                return
            }
        if (!available) {
            HookLogger.w(TAG, "跳过接收端注册: reason=service_unavailable")
            return
        }

        val stub = object : LyricListener {
            override fun onLyric(view: LyricView) {
                try {
                    val publisher = view.publisher?.takeIf { it.isNotBlank() } ?: "unknown"
                    owner.update(publisher, publisher) {
                        val stream = streams.getOrPut(publisher) { PublisherStream(owner) }
                        stream.sink = this
                        stream.handleLyric(publisher, view)
                    }
                } catch (e: Exception) {
                    HookLogger.w(TAG, "处理歌词数据失败", e)
                }
            }

            override fun onStop(stop: Stop) {
                try {
                    val publisher = stop.publisher?.takeIf { it.isNotBlank() } ?: ""
                    owner.update(publisher, publisher) {
                        streams[publisher]?.playbackStarted = false
                        onPlaybackStateChanged(false)
                    }
                } catch (e: Exception) {
                    HookLogger.w(TAG, "处理停止事件失败", e)
                }
            }
        }
        listener = stub

        try {
            SuperLyric.receiver().register(stub)
            HookLogger.d(TAG, "已注册 SuperLyric 4.0 接收端")
            val latest = runCatching { SuperLyric.receiver().latest() }.getOrNull()
            if (latest != null) {
                stub.onLyric(latest)
            }
        } catch (e: Exception) {
            HookLogger.e(TAG, "注册接收端失败", e)
        }
    }

    override fun stop() {
        val owner = arbiter
        owner?.close()
        listener?.let {
            try {
                SuperLyric.receiver().unregister(it)
            } catch (e: Exception) {
                HookLogger.w(TAG, "注销接收端失败", e)
            }
        }
        listener = null
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

        fun handleLyric(publisher: String, view: LyricView) {
            val currentSink = sink ?: return
            val lyric = view.lyric

            val newLyricId = lyric.id.takeIf { it.isNotBlank() }
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

            val title = lyric.title.takeIf { it.isNotBlank() }
            val artist = lyric.artist.takeIf { it.isNotBlank() }
            val album = lyric.album.takeIf { it.isNotBlank() }

            if (title != null) lastMetadataTitle = title
            if (artist != null) lastMetadataArtist = artist
            if (album != null) lastMetadataAlbum = album

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

            if (fullSong != null && lyric.lines.isEmpty() &&
                (title != null && lastMetadataTitle != fullSong?.name ||
                    artist != null && lastMetadataArtist != fullSong?.artist ||
                    album != null && lastMetadataAlbum != fullSong?.album)
            ) {
                fullSong = null
            }

            val incomingFullSong = if (lyric.lines.isNotEmpty()) {
                Song(
                    name = lastMetadataTitle,
                    artist = lastMetadataArtist,
                    album = lastMetadataAlbum,
                    duration = lyric.duration.coerceAtLeast(0L),
                    lyrics = lyric.lines.map { line -> convertToRichLyricLine(line) }
                )
            } else null

            val publishFull = incomingFullSong != null &&
                (fullSong == null || incomingFullSong != fullSong)

            if (publishFull) {
                fullSong = incomingFullSong
                currentSink.onSongChanged(incomingFullSong)
            } else if (metadataChanged && fullSong == null) {
                currentSink.onSongChanged(
                    Song(
                        name = lastMetadataTitle,
                        artist = lastMetadataArtist,
                        album = lastMetadataAlbum,
                        lyrics = emptyList()
                    )
                )
            }

            if (metadataChanged || publishFull) {
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

            val isPlaying = if (view.state != null) view.state == PlaybackState.PLAYING else true
            if (playbackStarted != isPlaying) {
                playbackStarted = isPlaying
                currentSink.onPlaybackStateChanged(isPlaying)
            }

            if (view.position >= 0L) {
                val speed = if (view.speed > 0f) view.speed else 1f
                currentSink.onPositionChanged(view.position, speed)
            }

            if (fullSong == null && view.index in lyric.lines.indices) {
                val line = lyric.lines[view.index]
                val richLine = convertToRichLyricLine(line)
                currentSink.onLyricLine(richLine)
            }
        }

        private fun convertToRichLyricLine(line: Line): RichLyricLine {
            val words = line.words?.map { word ->
                LyricWord(
                    begin = word.start,
                    end = word.end,
                    text = word.text
                )
            }

            val translationText = line.translation?.takeIf { it.isNotBlank() }
            val romaText = line.phonetic?.takeIf { it.isNotBlank() }

            return RichLyricLine(
                begin = line.start,
                end = line.end,
                text = line.text,
                words = words,
                translation = translationText,
                translationWords = null,
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
