package com.lidesheng.hyperlyric.root

import android.os.SystemClock
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.extensions.TimingNavigator
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.InterludeTracker
import com.lidesheng.hyperlyric.lyric.view.SongPreprocessor
import com.lidesheng.hyperlyric.lyric.view.TimedLine
import com.lidesheng.hyperlyric.lyric.view.TitleSlot
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.plugin.api.PluginSongField

object LyriconDataBridge {

    private const val TAG = "LyriconDataBridge"

    val versionCounter = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    var currentSong: Song? = null

    @Volatile
    var currentSongName: String? = null

    /** Latest media fields supplied by the active lyric source, before MediaSession fallback. */
    @Volatile
    var currentLyricMediaMetadata: LyricMediaMetadata? = null

    @Volatile
    var currentLyric: String? = null

    @Volatile
    var currentLyricLine: IRichLyricLine? = null

    @Volatile
    var currentNextLyricLine: IRichLyricLine? = null

    @Volatile
    var currentPosition: Long = 0L

    /**
     * Immutable monotonic playback clock shared by every presentation of the current lyric.
     * [currentPosition] remains the raw source position used for timeline selection; rendering
     * must use [currentPlaybackClock] so a lifecycle refresh cannot seek back to an old sample.
     */
    data class PlaybackClockReading(
        val positionMs: Long,
        val playbackSpeed: Float,
        /** Wall-clock time accumulated only while playback is active. */
        val activeTimeMs: Long
    )

    private data class PlaybackClockSnapshot(
        val positionMs: Long,
        val playbackSpeed: Float,
        val activeTimeMs: Long,
        val sampledAtUptimeMs: Long,
        val isPlaying: Boolean
    ) {
        fun readAt(uptimeMs: Long): PlaybackClockReading {
            if (!isPlaying) {
                return PlaybackClockReading(positionMs, playbackSpeed, activeTimeMs)
            }
            val elapsedMs = (uptimeMs - sampledAtUptimeMs).coerceAtLeast(0L)
            val projectedPosition = positionMs.toDouble() +
                    elapsedMs.toDouble() * playbackSpeed.toDouble()
            return PlaybackClockReading(
                positionMs = projectedPosition
                    .coerceIn(0.0, Long.MAX_VALUE.toDouble())
                    .toLong(),
                playbackSpeed = playbackSpeed,
                activeTimeMs = (activeTimeMs.toDouble() + elapsedMs.toDouble())
                    .coerceIn(0.0, Long.MAX_VALUE.toDouble())
                    .toLong()
            )
        }
    }

    @Volatile
    private var playbackClock = PlaybackClockSnapshot(
        positionMs = 0L,
        playbackSpeed = 1f,
        activeTimeMs = 0L,
        sampledAtUptimeMs = SystemClock.uptimeMillis(),
        isPlaying = false
    )
    private val playbackClockLock = Any()

    @Volatile
    var currentLyricPackageName: String? = null

    /** 是否处于纯文本模式（椒盐音乐等通过 onSendText 推送） */
    @Volatile
    var isTextMode: Boolean = false

    /**
     * Full-song lyric sources publish this state from [updateSong]. Streaming sources leave it
     * unknown and are considered ready only after a non-empty line or plain-text event arrives.
     */
    @Volatile
    private var fullSongLyricsAvailable: Boolean? = null

    @Volatile
    private var placeholderFormat = RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT

    fun updateLyricPackage(packageName: String?) {
        currentLyricPackageName = packageName
    }

    fun updatePlaybackClock(
        positionMs: Long,
        playbackSpeed: Float,
        isPlaying: Boolean,
        sampledAtUptimeMs: Long = SystemClock.uptimeMillis()
    ) {
        synchronized(playbackClockLock) {
            val previous = playbackClock
            val previousReading = previous.readAt(sampledAtUptimeMs)
            playbackClock = PlaybackClockSnapshot(
                positionMs = positionMs.coerceAtLeast(0L),
                playbackSpeed = normalizePlaybackSpeed(playbackSpeed, previous.playbackSpeed),
                activeTimeMs = previousReading.activeTimeMs,
                sampledAtUptimeMs = sampledAtUptimeMs,
                isPlaying = isPlaying
            )
        }
    }

    fun updatePlaybackState(
        isPlaying: Boolean,
        playbackSpeed: Float,
        eventUptimeMs: Long = SystemClock.uptimeMillis()
    ) {
        synchronized(playbackClockLock) {
            val previous = playbackClock
            val reading = previous.readAt(eventUptimeMs)
            playbackClock = PlaybackClockSnapshot(
                positionMs = reading.positionMs,
                playbackSpeed = normalizePlaybackSpeed(playbackSpeed, reading.playbackSpeed),
                activeTimeMs = reading.activeTimeMs,
                sampledAtUptimeMs = eventUptimeMs,
                isPlaying = isPlaying
            )
        }
    }

    fun resetPlaybackClock(
        positionMs: Long = 0L,
        isPlaying: Boolean = false,
        playbackSpeed: Float = 1f,
        sampledAtUptimeMs: Long = SystemClock.uptimeMillis()
    ) {
        synchronized(playbackClockLock) {
            playbackClock = PlaybackClockSnapshot(
                positionMs = positionMs.coerceAtLeast(0L),
                playbackSpeed = normalizePlaybackSpeed(playbackSpeed, 1f),
                activeTimeMs = 0L,
                sampledAtUptimeMs = sampledAtUptimeMs,
                isPlaying = isPlaying
            )
        }
    }

    fun currentPlaybackClock(
        uptimeMs: Long = SystemClock.uptimeMillis()
    ): PlaybackClockReading = playbackClock.readAt(uptimeMs)

    private var timingNavigator: TimingNavigator<TimedLine> = TimingNavigator(emptyArray())
    private var interludeTracker = InterludeTracker(8_000L)

    fun updateSong(
        song: Song?,
        placeholderFormat: Int = RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
    ) {
        HookLogger.d(TAG, "歌曲变更: ${song?.name}")
        isTextMode = false
        fullSongLyricsAvailable = song?.lyrics?.any(::hasRenderableLine) == true
        currentLyricMediaMetadata = null
        currentSong = song
        currentSongName = song?.name
        currentLyric = null
        currentLyricLine = null
        currentNextLyricLine = null
        this.placeholderFormat = normalizePlaceholderFormat(placeholderFormat)

        versionCounter.incrementAndGet()

        if (song != null) {
            rebuildTimeline(song, selectCurrentPosition = false)
        } else {
            timingNavigator = TimingNavigator(emptyArray())
        }
    }

    /**
     * Re-apply the source-event state without discarding an already accepted plugin enhancement.
     * Root uses this only for a repeated source snapshot whose media identity has not yet
     * changed; a later identity change calls [updateSong] with the new raw source Song instead.
     */
    fun refreshSongEvent(): Boolean {
        val song = currentSong ?: return false
        isTextMode = false
        fullSongLyricsAvailable = song.lyrics?.any(::hasRenderableLine) == true
        currentSongName = song.name
        currentLyric = null
        currentLyricLine = null
        currentNextLyricLine = null
        rebuildTimeline(song, selectCurrentPosition = false)
        return true
    }

    fun updateMediaMetadata(metadata: LyricMediaMetadata?) {
        currentLyricMediaMetadata = metadata?.normalized()
    }

    /**
     * Merge resolved Core media fields into the current full Song without delaying its first
     * render. Source-owned Song fields win because only missing values are filled here.
     *
     * The identity context remains outside Song. A changed result starts a new processing
     * version so an earlier plugin callback cannot write over the enriched snapshot.
     */
    fun applyResolvedMediaInfo(mediaInfo: MediaMetadataHelper.MediaInfo): Boolean {
        val song = currentSong ?: return false
        val merged = song.copy(
            name = song.name.orMissingText(mediaInfo.title),
            artist = song.artist.orMissingText(mediaInfo.artist),
            album = song.album.orMissingText(mediaInfo.album),
            duration = if (song.duration > 0L) {
                song.duration
            } else {
                mediaInfo.duration.takeIf { it > 0L } ?: song.duration
            }
        )
        if (merged == song) return false

        currentSong = merged
        currentSongName = merged.name.orMissingText(mediaInfo.title)
        versionCounter.incrementAndGet()
        if (merged.name != song.name || merged.artist != song.artist) {
            rebuildTimeline(merged, selectCurrentPosition = true)
        }
        return true
    }

    /** Clear only streaming lyric content after the resolved media identity changes. */
    fun resetLyricContentForMediaChange() {
        isTextMode = false
        fullSongLyricsAvailable = null
        currentLyric = null
        currentLyricLine = null
        currentNextLyricLine = null
        currentPosition = 0L
        resetPlaybackClockPositionPreservingState()
        versionCounter.incrementAndGet()
    }

    /**
     * Apply a plugin enhancement only while the song generation that produced it is current.
     * The plugin never receives this bridge; Core remains the owner of the final Song and
     * renderer refresh.
     */
    fun applyPluginEnhancement(
        enhancedSong: Song,
        expectedVersion: Int,
        expectedBaseSong: Song,
        changedFields: Set<PluginSongField> = emptySet()
    ): Boolean {
        if (versionCounter.get() != expectedVersion || currentSong !== expectedBaseSong) return false
        currentSong = enhancedSong
        currentSongName = enhancedSong.name
        fullSongLyricsAvailable = enhancedSong.lyrics?.any(::hasRenderableLine) == true
        if (changedFields.any { it in MEDIA_SONG_FIELDS }) {
            currentLyricMediaMetadata?.let { metadata ->
                currentLyricMediaMetadata = metadata.copy(
                    songId = if (PluginSongField.ID in changedFields) {
                        enhancedSong.id
                    } else {
                        metadata.songId
                    },
                    title = if (PluginSongField.NAME in changedFields) {
                        enhancedSong.name
                    } else {
                        metadata.title
                    },
                    artist = if (PluginSongField.ARTIST in changedFields) {
                        enhancedSong.artist
                    } else {
                        metadata.artist
                    },
                    album = if (PluginSongField.ALBUM in changedFields) {
                        enhancedSong.album
                    } else {
                        metadata.album
                    },
                    duration = if (PluginSongField.DURATION in changedFields) {
                        enhancedSong.duration.takeIf { it > 0L }
                    } else {
                        metadata.duration
                    }
                )
            }
        }
        rebuildTimeline(enhancedSong, selectCurrentPosition = true)
        return true
    }

    fun updatePlaceholderFormat(format: Int): Boolean {
        val normalizedFormat = normalizePlaceholderFormat(format)
        if (placeholderFormat == normalizedFormat) return false
        placeholderFormat = normalizedFormat

        val song = currentSong ?: return false
        rebuildTimeline(song, selectCurrentPosition = true)
        return true
    }

    fun updatePosition(position: Long): Boolean {
        currentPosition = position
        if (isTextMode) return false
        val song = currentSong ?: return false
        val lyrics = song.lyrics
        if (lyrics.isNullOrEmpty()) return false

        // 使用 TimingNavigator 高效定位当前歌词行
        var foundLine: TimedLine? = null
        timingNavigator.forEachAtOrPrevious(position) { timedLine ->
            foundLine = timedLine
        }

        val previousLine = currentLyricLine
        currentLyricLine = foundLine
        currentNextLyricLine = foundLine?.next
        // 间奏时保持最后一行歌词，不回退到歌名
        val newText = foundLine?.text ?: currentLyric ?: ""
        // 占位符圆点没有文本，不能只靠文本变化判断是否需要刷新。
        // 切歌或切换到同文本歌词时，新的歌词行仍然需要传给渲染器。
        val lineChanged = foundLine != null && foundLine !== previousLine

        if (lineChanged || newText != currentLyric) {
            currentLyric = newText
            return true
        }
        return false
    }

    fun updateLyric(text: String?) {
        isTextMode = true
        fullSongLyricsAvailable = null
        currentLyric = text
        currentLyricLine = if (!text.isNullOrBlank()) {
            val lines = text.lines()
            RichLyricLine(
                text = lines.first(),
                translation = lines.getOrNull(1)
            )
        } else {
            null
        }
        currentNextLyricLine = null
    }

    fun updateLyricLine(line: IRichLyricLine) {
        isTextMode = false
        fullSongLyricsAvailable = null
        currentLyricLine = line
        currentNextLyricLine = null
        currentLyric = line.text
    }

    fun clearState() {
        currentSong = null
        currentSongName = null
        currentLyricMediaMetadata = null
        currentLyric = null
        currentLyricLine = null
        currentNextLyricLine = null
        currentPosition = 0L
        resetPlaybackClock()
        currentLyricPackageName = null
        isTextMode = false
        fullSongLyricsAvailable = null
        timingNavigator = TimingNavigator(emptyArray())

        versionCounter.incrementAndGet()
    }

    /**
     * Returns whether the current source has content that justifies owning the island text slots.
     * A whole-song source wins over the current line so an interlude cannot remove the view.
     */
    fun hasLyricsForPresentation(): Boolean {
        fullSongLyricsAvailable?.let { return it }
        return !currentLyric.isNullOrBlank() || hasRenderableLine(currentLyricLine)
    }

    private fun hasRenderableLine(line: IRichLyricLine?): Boolean {
        return line != null && (!line.text.isNullOrBlank() || !line.words.isNullOrEmpty())
    }

    private fun rebuildTimeline(song: Song, selectCurrentPosition: Boolean) {
        val processor = SongPreprocessor(resolveTitleSlot(placeholderFormat))
        val lines = processor.prepare(song.deepCopy())
        timingNavigator = TimingNavigator(lines.toTypedArray())
        interludeTracker = InterludeTracker(8_000L)

        if (selectCurrentPosition) {
            currentLyric = null
            currentLyricLine = null
            currentNextLyricLine = null
            updatePosition(currentPosition)
        }
    }

    private fun normalizePlaceholderFormat(format: Int): Int {
        return when (format) {
            RootConstants.PLACEHOLDER_FORMAT_NONE,
            RootConstants.PLACEHOLDER_FORMAT_TITLE_ARTIST,
            RootConstants.PLACEHOLDER_FORMAT_TITLE,
            RootConstants.PLACEHOLDER_FORMAT_COUNTDOWN -> format

            else -> RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
        }
    }

    private fun resolveTitleSlot(format: Int): TitleSlot {
        return when (format) {
            RootConstants.PLACEHOLDER_FORMAT_NONE -> TitleSlot.NONE
            RootConstants.PLACEHOLDER_FORMAT_TITLE -> TitleSlot.NAME
            RootConstants.PLACEHOLDER_FORMAT_COUNTDOWN -> TitleSlot.COUNTDOWN
            else -> TitleSlot.NAME_ARTIST
        }
    }

    private fun normalizePlaybackSpeed(speed: Float, fallback: Float): Float {
        return speed.takeIf { it.isFinite() && it in 0.1f..4f }
            ?: fallback.takeIf { it.isFinite() && it in 0.1f..4f }
            ?: 1f
    }

    private fun resetPlaybackClockPositionPreservingState(
        sampledAtUptimeMs: Long = SystemClock.uptimeMillis()
    ) {
        synchronized(playbackClockLock) {
            val previous = playbackClock
            playbackClock = PlaybackClockSnapshot(
                positionMs = 0L,
                playbackSpeed = previous.playbackSpeed,
                activeTimeMs = 0L,
                sampledAtUptimeMs = sampledAtUptimeMs,
                isPlaying = previous.isPlaying
            )
        }
    }

    private fun String?.orMissingText(fallback: String): String? =
        this?.takeIf { it.isNotBlank() } ?: fallback.takeIf { it.isNotBlank() }

    private val MEDIA_SONG_FIELDS = setOf(
        PluginSongField.ID,
        PluginSongField.NAME,
        PluginSongField.ARTIST,
        PluginSongField.ALBUM,
        PluginSongField.DURATION,
        PluginSongField.METADATA
    )

}


