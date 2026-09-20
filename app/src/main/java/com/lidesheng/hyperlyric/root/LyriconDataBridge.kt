package com.lidesheng.hyperlyric.root

import android.os.Bundle
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object LyriconDataBridge {

    private const val HOT_RELOAD_STATE_VERSION = 1
    private const val HOT_RELOAD_VERSION = "version"
    private const val HOT_RELOAD_SOURCE = "source"
    private const val HOT_RELOAD_PACKAGE = "package"
    private const val HOT_RELOAD_SONG = "song"
    private const val HOT_RELOAD_LINE = "line"
    private const val HOT_RELOAD_TEXT = "text"
    private const val HOT_RELOAD_TEXT_MODE = "textMode"
    private const val HOT_RELOAD_POSITION = "position"
    private const val HOT_RELOAD_SPEED = "speed"
    private const val HOT_RELOAD_PLAYING = "playing"
    private const val HOT_RELOAD_SONG_ID = "songId"
    private const val HOT_RELOAD_TITLE = "title"
    private const val HOT_RELOAD_ARTIST = "artist"
    private const val HOT_RELOAD_ALBUM = "album"
    private const val HOT_RELOAD_DURATION = "duration"
    private val hotReloadJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

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

    /** All source lines active at the current position, retained for two-row presentation. */
    @Volatile
    var currentLyricLines: List<IRichLyricLine> = emptyList()

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

    /** Active-playback clock origin for the current no-timeline plain-text payload. */
    @Volatile
    private var plainTextMarqueeOriginActiveTimeMs: Long = 0L

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

    fun isPlaybackActive(): Boolean = playbackClock.isPlaying

    /**
     * Exports only JSON/primitives for an API 102 generation handoff.  No Song, lyric line,
     * MediaSession token, renderer, view, callback or module-class object crosses the boundary.
     */
    fun exportHotReloadSnapshot(): Bundle? {
        val song = currentSong ?: return null
        val metadata = currentLyricMediaMetadata
        val clock = currentPlaybackClock()
        return Bundle().apply {
            putInt(HOT_RELOAD_VERSION, HOT_RELOAD_STATE_VERSION)
            putString(HOT_RELOAD_SOURCE, metadata?.sourceId)
            putString(HOT_RELOAD_PACKAGE, currentLyricPackageName)
            putString(HOT_RELOAD_SONG, hotReloadJson.encodeToString(song.deepCopy()))
            currentLyricLine?.let { line ->
                putString(
                    HOT_RELOAD_LINE,
                    hotReloadJson.encodeToString(line.toNeutralRichLine())
                )
            }
            putString(HOT_RELOAD_TEXT, currentLyric)
            putBoolean(HOT_RELOAD_TEXT_MODE, isTextMode)
            putLong(HOT_RELOAD_POSITION, clock.positionMs)
            putFloat(HOT_RELOAD_SPEED, clock.playbackSpeed)
            putBoolean(HOT_RELOAD_PLAYING, isPlaybackActive())
            putString(HOT_RELOAD_SONG_ID, metadata?.songId)
            putString(HOT_RELOAD_TITLE, metadata?.title)
            putString(HOT_RELOAD_ARTIST, metadata?.artist)
            putString(HOT_RELOAD_ALBUM, metadata?.album)
            metadata?.duration?.let { putLong(HOT_RELOAD_DURATION, it) }
        }
    }

    /**
     * Restores the neutral fallback after the new source has had a chance to re-subscribe.  This
     * is intentionally a no-op for a malformed or stale snapshot; normal source events remain the
     * authoritative state path.
     */
    fun restoreHotReloadSnapshot(snapshot: Bundle): Boolean {
        if (snapshot.getInt(HOT_RELOAD_VERSION, -1) != HOT_RELOAD_STATE_VERSION) return false
        val songJson = snapshot.getString(HOT_RELOAD_SONG) ?: return false
        val song = runCatching {
            hotReloadJson.decodeFromString<Song>(songJson)
        }.getOrNull() ?: return false

        updateSong(song)
        updateMediaMetadata(
            LyricMediaMetadata(
                sourceId = snapshot.getString(HOT_RELOAD_SOURCE).orEmpty(),
                packageName = snapshot.getString(HOT_RELOAD_PACKAGE),
                songId = snapshot.getString(HOT_RELOAD_SONG_ID),
                title = snapshot.getString(HOT_RELOAD_TITLE),
                artist = snapshot.getString(HOT_RELOAD_ARTIST),
                album = snapshot.getString(HOT_RELOAD_ALBUM),
                duration = if (snapshot.containsKey(HOT_RELOAD_DURATION)) {
                    snapshot.getLong(HOT_RELOAD_DURATION)
                } else {
                    null
                },
            )
        )
        updateLyricPackage(snapshot.getString(HOT_RELOAD_PACKAGE))

        val position = snapshot.getLong(HOT_RELOAD_POSITION, 0L).coerceAtLeast(0L)
        val speed = snapshot.getFloat(HOT_RELOAD_SPEED, 1f)
        val playing = snapshot.getBoolean(HOT_RELOAD_PLAYING, false)
        resetPlaybackClock(position, playing, speed)
        if (snapshot.getBoolean(HOT_RELOAD_TEXT_MODE, false)) {
            updateLyric(snapshot.getString(HOT_RELOAD_TEXT))
        } else {
            snapshot.getString(HOT_RELOAD_LINE)?.let { lineJson ->
                runCatching {
                    hotReloadJson.decodeFromString<RichLyricLine>(lineJson)
                }.getOrNull()?.let(::updateLyricLine)
            }
            updatePosition(position)
        }
        return true
    }

    fun currentPlainTextMarqueeOriginActiveTimeMs(): Long =
        plainTextMarqueeOriginActiveTimeMs

    private var timingNavigator: TimingNavigator<TimedLine> = TimingNavigator(emptyArray())
    private var interludeTracker = InterludeTracker(8_000L)

    fun updateSong(
        song: Song?,
        placeholderFormat: Int = RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
    ) {
        HookLogger.d(TAG, "歌曲变更: ${song?.name}")
        isTextMode = false
        plainTextMarqueeOriginActiveTimeMs = 0L
        fullSongLyricsAvailable = song?.lyrics?.any(::hasRenderableLine) == true
        currentLyricMediaMetadata = null
        currentSong = song
        currentSongName = song?.name
        currentLyric = null
        currentLyricLine = null
        currentLyricLines = emptyList()
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
     * Re-apply the source-event state without discarding an already accepted enhancement.
     * Root uses this only for a repeated source snapshot whose media identity has not yet
     * changed; a later identity change calls [updateSong] with the new raw source Song instead.
     */
    fun refreshSongEvent(): Boolean {
        val song = currentSong ?: return false
        isTextMode = false
        plainTextMarqueeOriginActiveTimeMs = 0L
        fullSongLyricsAvailable = song.lyrics?.any(::hasRenderableLine) == true
        currentSongName = song.name
        currentLyric = null
        currentLyricLine = null
        currentLyricLines = emptyList()
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
     * version so an earlier enhancement callback cannot write over the enriched snapshot.
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
        plainTextMarqueeOriginActiveTimeMs = 0L
        fullSongLyricsAvailable = null
        currentLyric = null
        currentLyricLine = null
        currentLyricLines = emptyList()
        currentNextLyricLine = null
        currentPosition = 0L
        resetPlaybackClockPositionPreservingState()
        versionCounter.incrementAndGet()
    }

    /**
     * Apply an enhancement only while the song generation that produced it is current.
     * The enhancement feature never receives this bridge; Core remains the owner of the final Song
     * and renderer refresh.
     */
    internal fun applyLyricEnhancement(
        enhancedSong: Song,
        expectedVersion: Int,
        expectedBaseSong: Song,
    ): Boolean {
        if (versionCounter.get() != expectedVersion || currentSong !== expectedBaseSong) return false
        val onlyLyricsChanged = enhancedSong.id == expectedBaseSong.id &&
                enhancedSong.name == expectedBaseSong.name &&
                enhancedSong.artist == expectedBaseSong.artist &&
                enhancedSong.album == expectedBaseSong.album &&
                enhancedSong.duration == expectedBaseSong.duration &&
                enhancedSong.metadata == expectedBaseSong.metadata
        if (!onlyLyricsChanged) {
            HookLogger.w(TAG, "歌词增强结果修改了歌曲信息，拒绝写回")
            return false
        }
        if (enhancedSong.lyrics == expectedBaseSong.lyrics) return false
        currentSong = enhancedSong
        currentSongName = enhancedSong.name
        fullSongLyricsAvailable = enhancedSong.lyrics?.any(::hasRenderableLine) == true
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

        // 保留当前时刻的全部有效行。Lyricon 的渲染模型允许重叠行，宿主这里只在展示层
        // 将其裁剪为主行和副行，不能在状态桥接层把它们提前压成一行。
        val activeLines = buildList {
            timingNavigator.forEachAt(position) { add(it) }
        }
        val selectedLines = if (activeLines.isNotEmpty()) {
            activeLines
        } else {
            timingNavigator.findPreviousEntry(position)?.let(::listOf).orEmpty()
        }

        val previousLines = currentLyricLines
        val previousNextLine = currentNextLyricLine
        val foundLine = selectedLines.firstOrNull()
        currentLyricLines = selectedLines
        currentLyricLine = foundLine
        currentNextLyricLine = selectedLines.lastOrNull()?.next
        // 间奏时保持最后一行歌词，不回退到歌名
        val newText = foundLine?.text ?: currentLyric ?: ""
        // 占位符圆点没有文本，不能只靠文本变化判断是否需要刷新。
        // 切歌或切换到同文本歌词时，新的歌词行仍然需要传给渲染器。
        val lineChanged = selectedLines != previousLines ||
                currentNextLyricLine !== previousNextLine

        if (lineChanged || newText != currentLyric) {
            currentLyric = newText
            return true
        }
        return false
    }

    fun updateLyric(text: String?) {
        if (!isTextMode || currentLyric != text) {
            plainTextMarqueeOriginActiveTimeMs = currentPlaybackClock().activeTimeMs
        }
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
        currentLyricLines = currentLyricLine?.let(::listOf).orEmpty()
        currentNextLyricLine = null
    }

    fun updateLyricLine(line: IRichLyricLine) {
        isTextMode = false
        plainTextMarqueeOriginActiveTimeMs = 0L
        fullSongLyricsAvailable = null
        currentLyricLine = line
        currentLyricLines = listOf(line)
        currentNextLyricLine = null
        currentLyric = line.text
    }

    fun clearState() {
        currentSong = null
        currentSongName = null
        currentLyricMediaMetadata = null
        currentLyric = null
        currentLyricLine = null
        currentLyricLines = emptyList()
        currentNextLyricLine = null
        currentPosition = 0L
        resetPlaybackClock()
        currentLyricPackageName = null
        isTextMode = false
        plainTextMarqueeOriginActiveTimeMs = 0L
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

    /**
     * Returns whether the active lyric source supplied displayable media fields of its own.
     * This deliberately does not inspect MediaSession fallback fields: LyricInfo must be judged
     * by the fields parsed from its lyricInfo JSON rather than by top-level MediaMetadata.
     */
    fun hasMusicInfoForPresentation(): Boolean {
        val metadata = currentLyricMediaMetadata ?: return false
        return !metadata.title.isNullOrBlank() ||
                !metadata.artist.isNullOrBlank() ||
                !metadata.album.isNullOrBlank()
    }

    /**
     * Builds the configured lyric-side placeholder for a metadata-only track. The placeholder is
     * intentionally kept separate from [currentLyricLine], so it cannot make a lyric-less source
     * pass the lyric gate when the user selected native restoration.
     */
    fun noLyricsPlaceholderLine(): IRichLyricLine? {
        val metadata = currentLyricMediaMetadata ?: return null
        return SongPreprocessor(resolveTitleSlot(placeholderFormat)).noLyricsPlaceholder(
            Song(
                name = metadata.title,
                artist = metadata.artist
            )
        )
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
            currentLyricLines = emptyList()
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

    private fun IRichLyricLine.toNeutralRichLine(): RichLyricLine = RichLyricLine(
        begin = begin,
        end = end,
        duration = duration,
        isAlignedRight = isAlignedRight,
        metadata = metadata,
        text = text,
        words = words,
        secondary = secondary,
        secondaryWords = secondaryWords,
        translation = translation,
        translationWords = translationWords,
        roma = roma,
    )

}


