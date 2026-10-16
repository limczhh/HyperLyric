package com.lidesheng.hyperlyric.root

import android.os.Bundle
import android.os.SystemClock
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.DuetLyricPolicy
import com.lidesheng.hyperlyric.common.lyric.isPureMusicLyricText
import com.lidesheng.hyperlyric.common.lyric.isPureMusicMarkerLine
import com.lidesheng.hyperlyric.common.lyric.isPureMusicSongLyrics
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
import com.lidesheng.hyperlyric.root.island.content.IslandMetadataContentAssembler
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

    /** Resolved snapshot shared by root-side surfaces, including session-validated artwork. */
    @Volatile
    var currentResolvedMediaInfo: MediaMetadataHelper.MediaInfo? = null

    @Volatile
    var currentLyric: String? = null

    @Volatile
    var currentLyricLine: IRichLyricLine? = null

    /** All source lines active at the current position, retained for two-row presentation. */
    @Volatile
    var currentLyricLines: List<IRichLyricLine> = emptyList()

    @Volatile
    var currentNextLyricLine: IRichLyricLine? = null

    /**
     * Whether the loaded song carries duet vocals. The verdict belongs to the whole song, so it is
     * resolved once with the song itself and stays valid until the next song change.
     */
    @Volatile
    var currentSongHasDuet: Boolean = false
        private set

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

    @Volatile
    private var showLongInterludeCountdown =
        RootConstants.DEFAULT_HOOK_LYRIC_LONG_INTERLUDE_COUNTDOWN

    @Volatile
    private var showLyricPreview = RootConstants.DEFAULT_HOOK_LYRIC_PREVIEW

    @Volatile
    private var noLyricsLyricBehavior = RootConstants.DEFAULT_HOOK_NO_LYRICS_LYRIC_BEHAVIOR

    @Volatile
    private var tailTimeoutBehavior = RootConstants.DEFAULT_HOOK_TAIL_TIMEOUT_BEHAVIOR

    /** 尾奏超时时长（秒），记为 n；只有开始时间的歌词额外多等 5 秒。 */
    @Volatile
    private var tailTimeoutDelaySeconds = RootConstants.DEFAULT_HOOK_TAIL_TIMEOUT_DELAY_SECONDS

    /** 当前歌词内容是否命中纯音乐标记，随歌曲/歌词/单行更新重算。 */
    @Volatile
    var currentSongIsPureMusic: Boolean = false
        private set

    /**
     * 当前播放位置是否已越过尾奏超时点。超时点由时间线最后一句推导，随选行与切歌重算；
     * 没有可用时间线时保持 false。
     */
    @Volatile
    private var currentSongInTailTimeout: Boolean = false

    /** 时间线最后一句推导出的尾奏超时时刻；无歌词行时为 Long.MAX_VALUE，即永不超时。 */
    @Volatile
    private var tailTimeoutDeadlineMs: Long = Long.MAX_VALUE

    /** 最近一次源推送的原始歌词行，用于纯音乐行为切换后重建显示内容。 */
    @Volatile
    private var rawActiveLyricLine: IRichLyricLine? = null

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
    private var interludeCountdownNavigator: TimingNavigator<TimedLine> = TimingNavigator(emptyArray())
    private var leadingPlaceholderLine: TimedLine? = null
    private var interludeTracker = InterludeTracker(8_000L)

    fun updateSong(
        song: Song?,
        placeholderFormat: Int = RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
    ) {
        HookLogger.d(TAG, "歌曲变更: ${song?.name}")
        isTextMode = false
        plainTextMarqueeOriginActiveTimeMs = 0L
        fullSongLyricsAvailable = false
        currentSongHasDuet = false
        currentLyricMediaMetadata = null
        currentResolvedMediaInfo = null
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
            currentSongIsPureMusic = false
            currentSongInTailTimeout = false
            tailTimeoutDeadlineMs = Long.MAX_VALUE
            rawActiveLyricLine = null
            timingNavigator = TimingNavigator(emptyArray())
            interludeCountdownNavigator = TimingNavigator(emptyArray())
            leadingPlaceholderLine = null
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
        leadingPlaceholderLine = null
        fullSongLyricsAvailable = null
        currentSongIsPureMusic = false
        currentSongInTailTimeout = false
        tailTimeoutDeadlineMs = Long.MAX_VALUE
        rawActiveLyricLine = null
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
        rebuildTimeline(enhancedSong, selectCurrentPosition = true)
        return true
    }

    fun updatePlaceholderFormat(format: Int): Boolean {
        val normalizedFormat = normalizePlaceholderFormat(format)
        if (placeholderFormat == normalizedFormat) return false
        placeholderFormat = normalizedFormat

        val song = currentSong ?: return true
        rebuildTimeline(song, selectCurrentPosition = true)
        return true
    }

    fun updateLongInterludeCountdownEnabled(enabled: Boolean): Boolean {
        if (showLongInterludeCountdown == enabled) return false
        showLongInterludeCountdown = enabled

        val song = currentSong?.takeIf {
            !isTextMode && fullSongLyricsAvailable != null
        } ?: return true
        rebuildTimeline(song, selectCurrentPosition = true)
        return true
    }

    fun updateLyricPreviewEnabled(enabled: Boolean): Boolean {
        if (showLyricPreview == enabled) return false
        showLyricPreview = enabled

        val song = currentSong?.takeIf {
            !isTextMode && fullSongLyricsAvailable != null
        } ?: return true
        rebuildTimeline(song, selectCurrentPosition = true)
        return true
    }

    fun updateNoLyricsLyricBehavior(behavior: Int): Boolean {
        if (noLyricsLyricBehavior == behavior) return false
        noLyricsLyricBehavior = behavior

        val song = currentSong?.takeIf {
            !isTextMode && fullSongLyricsAvailable != null
        }
        if (song != null) {
            rebuildTimeline(song, selectCurrentPosition = true)
        } else if (currentSongIsPureMusic) {
            currentLyricLine = resolveActiveLyricLine()
            currentLyricLines = currentLyricLine?.let(::listOf).orEmpty()
        }
        return true
    }

    /** 切换尾奏超时行为：重算时间线让当前选行立刻套用新行为。 */
    fun updateTailTimeoutBehavior(behavior: Int): Boolean {
        if (tailTimeoutBehavior == behavior) return false
        tailTimeoutBehavior = behavior

        val song = currentSong?.takeIf {
            !isTextMode && fullSongLyricsAvailable != null
        } ?: return true
        rebuildTimeline(song, selectCurrentPosition = true)
        return true
    }

    /** 切换尾奏超时时长：超时点由时长推导，需要重算时间线。 */
    fun updateTailTimeoutDelaySeconds(seconds: Int): Boolean {
        val normalized = RootConstants.normalizeTailTimeoutDelaySeconds(seconds)
        if (tailTimeoutDelaySeconds == normalized) return false
        tailTimeoutDelaySeconds = normalized

        val song = currentSong?.takeIf {
            !isTextMode && fullSongLyricsAvailable != null
        } ?: return true
        rebuildTimeline(song, selectCurrentPosition = true)
        return true
    }

    fun updatePosition(position: Long): Boolean {
        currentPosition = position
        if (isTextMode) return false
        val song = currentSong ?: return false
        val lyrics = song.lyrics
        if (lyrics.isNullOrEmpty()) {
            // 无歌词歌曲没有可选行的时间线，但"替换为音乐信息"里的时间字段仍需逐秒刷新。
            return noLyricsBehaviorShowsMusicInfo()
        }

        // 先展示此刻有效的源歌词行；开唱前只有当前没有源内容时才补前奏占位行。
        val activeLines = buildList {
            timingNavigator.forEachAt(position) { add(it) }
        }
        // 仍有正在唱的行时不算尾奏：对唱里前一行可能晚于最后一行结束，只看超时点会截断它。
        currentSongInTailTimeout = activeLines.isEmpty() && position >= tailTimeoutDeadlineMs
        val preludePlaceholder = leadingPlaceholderLine?.takeIf {
            position >= it.begin && position < it.end
        }
        val showPreludePlaceholder = activeLines.isEmpty() && preludePlaceholder != null
        // 倒计时圆点优先于歌词预览；不满足圆点条件时，预览模式可在有效间奏中选择下一行。
        val interludeCountdown = if (activeLines.isEmpty() && !showPreludePlaceholder) {
            interludeCountdownNavigator.first(position)
        } else {
            null
        }
        val previewLine = if (
            showLyricPreview && activeLines.isEmpty() &&
            !showPreludePlaceholder && interludeCountdown == null
        ) {
            timingNavigator.findPreviousEntry(position)?.let { previous ->
                previous.next?.takeIf { next ->
                    previous.end > previous.begin &&
                            position >= previous.end &&
                            next.begin > previous.end &&
                            position < next.begin
                }
            }
        } else {
            null
        }
        val selectedLines = when {
            showPreludePlaceholder -> listOf(preludePlaceholder)
            interludeCountdown != null -> listOf(interludeCountdown)
            activeLines.isNotEmpty() -> activeLines
            previewLine != null -> listOf(previewLine)
            // Without a preview candidate, retain the previous line through instrumental sections.
            else -> timingNavigator.findPreviousEntry(position)?.let(::listOf).orEmpty()
        }
        val effectiveLines = applyTailTimeoutBehavior(
            applyNoLyricsTimelineBehavior(selectedLines, position),
            position
        )

        val previousLines = currentLyricLines
        val previousNextLine = currentNextLyricLine
        val foundLine = effectiveLines.firstOrNull()
        currentLyricLines = effectiveLines
        currentLyricLine = foundLine
        currentNextLyricLine = if (!showPreludePlaceholder && effectiveLines === selectedLines) {
            // An active long row's adjacent entry may already have finished. A future gap
            // preview is itself the selected main row, so look beyond it rather than repeat it.
            timingNavigator.findNextEntry(maxOf(position, selectedLines.lastOrNull()?.begin ?: position))
        } else {
            null
        }
        // 间奏时保持最后一行歌词，不回退到歌名
        val newText = foundLine?.text ?: currentLyric ?: ""
        // 占位符圆点没有文本，不能只靠文本变化判断是否需要刷新。
        // 切歌或切换到同文本歌词时，新的歌词行仍然需要传给渲染器。
        val lineChanged = effectiveLines != previousLines ||
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
        leadingPlaceholderLine = null
        fullSongLyricsAvailable = null
        currentSongIsPureMusic = isPureMusicLyricText(text)
        currentLyric = text
        rawActiveLyricLine = if (!text.isNullOrBlank()) {
            val lines = text.lines()
            RichLyricLine(
                text = lines.first(),
                translation = lines.getOrNull(1)
            )
        } else {
            null
        }
        currentLyricLine = resolveActiveLyricLine()
        currentLyricLines = currentLyricLine?.let(::listOf).orEmpty()
        currentNextLyricLine = null
    }

    fun updateLyricLine(line: IRichLyricLine) {
        isTextMode = false
        leadingPlaceholderLine = null
        plainTextMarqueeOriginActiveTimeMs = 0L
        fullSongLyricsAvailable = null
        currentSongIsPureMusic = isPureMusicMarkerLine(line.text)
        rawActiveLyricLine = line
        currentLyric = line.text
        currentLyricLine = resolveActiveLyricLine()
        currentLyricLines = currentLyricLine?.let(::listOf).orEmpty()
        currentNextLyricLine = null
    }

    fun clearState() {
        currentSong = null
        currentSongName = null
        currentSongHasDuet = false
        currentLyricMediaMetadata = null
        currentResolvedMediaInfo = null
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
        currentSongIsPureMusic = false
        currentSongInTailTimeout = false
        tailTimeoutDeadlineMs = Long.MAX_VALUE
        rawActiveLyricLine = null
        timingNavigator = TimingNavigator(emptyArray())
        interludeCountdownNavigator = TimingNavigator(emptyArray())
        leadingPlaceholderLine = null

        versionCounter.incrementAndGet()
    }

    /**
     * Returns whether the current source has content that justifies owning the island text slots.
     * A whole-song source wins over the current line so an interlude cannot remove the view.
     */
    fun hasLyricsForPresentation(): Boolean {
        if (isContentSuppressedForPresentation()) return false
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
     * 构造无歌词曲目使用的占位展示行。它与 [currentLyricLine] 刻意分离，因此"回退到默认"时
     * 不会让无歌词源冒充成有歌词而通过歌词判定。
     */
    fun noLyricsPlaceholderLine(): IRichLyricLine? {
        if (isPlaceholderSuppressed()) return null
        if (noLyricsBehaviorShowsMusicInfo()) return musicInfoDisplayLine()
        val metadata = currentLyricMediaMetadata ?: return null
        return SongPreprocessor(
            resolveTitleSlot(placeholderFormat),
            showLongInterludeCountdown,
            showLyricPreview
        ).noLyricsPlaceholder(
            Song(
                name = metadata.title,
                artist = metadata.artist
            )
        )
    }

    fun hasNoLyricsPlaceholderForPresentation(): Boolean {
        if (isPlaceholderSuppressed()) return false
        // 必须与实际合成的展示行保持一致：仅判断媒体信息是否存在，会在布局字段全为空时
        // 声称有占位内容，渲染层却拿不到可显示的行。
        if (noLyricsBehaviorShowsMusicInfo()) {
            return musicInfoDisplayLine() != null
        }
        val metadata = currentLyricMediaMetadata ?: return false
        return when (placeholderFormat) {
            RootConstants.PLACEHOLDER_FORMAT_NONE -> false
            RootConstants.PLACEHOLDER_FORMAT_TITLE_ARTIST,
            RootConstants.PLACEHOLDER_FORMAT_TITLE -> !metadata.title.isNullOrBlank()
            RootConstants.PLACEHOLDER_FORMAT_COUNTDOWN -> hasMusicInfoForPresentation()
            else -> false
        }
    }

    /** 纯音乐且行为非"保持不变"时，标记文本不再作为歌词内容展示。 */
    private fun isPureMusicLyricHandled(): Boolean =
        currentSongIsPureMusic &&
                noLyricsLyricBehavior != RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_KEEP

    /**
     * 纯音乐内容被"回退到默认"主动清空。此时选行结果已被丢弃，不能再让整首歌的"有歌词"结论
     * 去占用文本槽位，否则会隐藏系统元素却不展示任何内容。"替换为音乐信息"不在此列：它把展示行
     * 替换成音乐信息，内容始终存在。
     */
    private fun isPureMusicContentSuppressed(): Boolean =
        currentSongIsPureMusic && isNoLyricsBehaviorRestoreDefault()

    /**
     * 尾奏超时只对"有完整时间线的整首歌"生效。流式与纯文本源没有可判定最后一句的时间线，
     * 沿用上一次整首歌的超时点会误判。
     */
    private fun isTailTimeoutActive(): Boolean =
        fullSongLyricsAvailable == true && currentSongInTailTimeout

    /**
     * 尾奏超时是否已经完全接管本首歌的展示。纯音乐由"无歌词/纯音乐歌词行为"负责，两者不叠加。
     */
    private fun isTailOverrideActive(): Boolean =
        !currentSongIsPureMusic && isTailTimeoutActive()

    /** 尾奏超时内容被"回退到默认"主动清空。 */
    private fun isTailContentSuppressed(): Boolean =
        isTailOverrideActive() &&
                tailTimeoutBehavior == RootConstants.TAIL_TIMEOUT_BEHAVIOR_RESTORE_DEFAULT

    /** 任一行为要求交还系统默认展示，此时既不显示歌词也不显示任何占位内容。 */
    private fun isContentSuppressedForPresentation(): Boolean =
        isPureMusicContentSuppressed() || isTailContentSuppressed()

    /**
     * 占位符路径专用的抑制判定。能走到占位符就说明此刻没有可展示的歌词，因此"回退到默认"对
     * 无歌词、纯音乐、尾奏超时三种情形一律生效，不需要再判断歌曲上下文。
     *
     * 不能与 [isContentSuppressedForPresentation] 混用：后者给 hasLyricsForPresentation 用，必须
     * 保留纯音乐门控，否则正常播放中的歌词也会被"回退到默认"一并清掉；而本方法若沿用那个门控，
     * 普通无歌词歌曲（不含纯音乐标记）选"回退到默认"时仍会注入标题/倒计时占位符。
     */
    private fun isPlaceholderSuppressed(): Boolean =
        isNoLyricsBehaviorRestoreDefault() || isTailContentSuppressed()

    /** 纯音乐被行为接管（非"保持不变"），对唱宽度锁定不适用。 */
    fun isPureMusicLyricReplaced(): Boolean = isPureMusicLyricHandled()

    /** 尾奏超时被行为接管（非"保持不变"），对唱宽度锁定不适用。 */
    fun isTailLyricReplaced(): Boolean =
        isTailOverrideActive() &&
                tailTimeoutBehavior != RootConstants.TAIL_TIMEOUT_BEHAVIOR_KEEP

    private fun isNoLyricsBehaviorRestoreDefault(): Boolean =
        noLyricsLyricBehavior == RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT

    /**
     * 无歌词/纯音乐时是否展示音乐信息占位。尾奏超时接管本首歌后必须让位：否则超级岛会用
     * "无歌词"侧的选择判定自己仍应展示，却拿不到任何内容（占位行已按尾奏行为清空），
     * 结果是被拉起并改写、内容为空的超级岛顶替掉状态栏歌词。
     */
    fun noLyricsBehaviorShowsMusicInfo(): Boolean =
        !isTailOverrideActive() &&
                noLyricsLyricBehavior == RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO

    /**
     * 模块是否真的在接管文本展示。时钟、通知图标与灵动岛的隐藏都依赖此判定，因此必须基于
     * 实际可展示的内容而非设置项本身：否则选了"替换为音乐信息"却没有内容可渲染时，
     * 系统元素会被一并隐藏，得到一条完全空白的状态栏。
     */
    fun isPresentingManagedContent(): Boolean =
        hasLyricsForPresentation() || hasNoLyricsPlaceholderForPresentation()

    /**
     * 用用户配置的音乐信息布局合成展示行，没有可显示内容时返回 null 以便调用方回退。
     * 无歌词/纯音乐与尾奏超时两种"替换为音乐信息"共用本方法。
     */
    private fun musicInfoDisplayLine(
        position: Long = currentPlaybackClock().positionMs
    ): IRichLyricLine? {
        val prefs = HookEntry.instance?.prefs ?: return null
        val mediaInfo = resolveMediaInfoForDisplay() ?: return null
        val lines = IslandMetadataContentAssembler.buildConfiguredMusicInfoLines(
            prefs = prefs,
            mediaInfo = mediaInfo,
            playbackPosition = position,
            playbackDuration = currentSong?.duration?.takeIf { it > 0L } ?: mediaInfo.duration
        )
        val firstLine = lines.firstLine.takeIf { it.isNotBlank() }
        val secondLine = lines.secondLine.takeIf { it.isNotBlank() }
        // 主行是状态栏判定可见性的唯一依据，配置的首行为空时降级用次行顶上。
        val primary = firstLine ?: secondLine ?: return null
        return RichLyricLine(
            text = primary,
            translation = if (firstLine == null) null else secondLine
        )
    }

    private fun resolveMediaInfoForDisplay(): MediaMetadataHelper.MediaInfo? {
        currentResolvedMediaInfo
            ?.takeIf { it.title.isNotBlank() || it.artist.isNotBlank() }
            ?.let { return it }
        val metadata = currentLyricMediaMetadata ?: return null
        val fallback = MediaMetadataHelper.MediaInfo(
            title = metadata.title.orEmpty(),
            artist = metadata.artist.orEmpty(),
            album = metadata.album.orEmpty(),
            duration = metadata.duration ?: -1L
        )
        return fallback.takeIf { it.title.isNotBlank() || it.artist.isNotBlank() }
    }

    private fun hasRenderableLine(line: IRichLyricLine?): Boolean {
        return line != null && (!line.text.isNullOrBlank() || !line.words.isNullOrEmpty())
    }

    /**
     * 按无歌词/纯音乐行为替换源推送的单行内容。"替换为音乐信息"在媒体信息尚未就绪时必须回退到
     * 原始行：直接置空会让 hasLyricsForPresentation 与 currentLyricLine 互相矛盾，渲染层拿到空
     * 展示、系统元素却已按"有内容"被隐藏，切歌后状态栏就整条空白。
     */
    private fun resolveActiveLyricLine(): IRichLyricLine? {
        val rawLine = rawActiveLyricLine ?: return null
        if (!currentSongIsPureMusic) return rawLine
        return when (noLyricsLyricBehavior) {
            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO ->
                musicInfoDisplayLine() ?: rawLine

            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT -> null

            else -> rawLine
        }
    }

    /** 时间线选行结果的无歌词/纯音乐覆盖，返回原列表实例表示未覆盖。 */
    private fun applyNoLyricsTimelineBehavior(
        selectedLines: List<IRichLyricLine>,
        position: Long
    ): List<IRichLyricLine> {
        if (!currentSongIsPureMusic) return selectedLines
        return when (noLyricsLyricBehavior) {
            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO ->
                musicInfoDisplayLine(position)?.let(::listOf) ?: selectedLines

            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT -> emptyList()

            else -> selectedLines
        }
    }

    /**
     * 尾奏超时后的时间线覆盖，与纯音乐替换同构：替换为音乐信息时换成音乐信息展示行，媒体信息
     * 未就绪则回退原行；回退到默认时丢弃内容交还系统；保持不变时保留最后一句。
     * 纯音乐已由上一个覆盖接管，这里不再叠加。
     */
    private fun applyTailTimeoutBehavior(
        selectedLines: List<IRichLyricLine>,
        position: Long
    ): List<IRichLyricLine> {
        if (!isTailOverrideActive()) return selectedLines
        return when (tailTimeoutBehavior) {
            RootConstants.TAIL_TIMEOUT_BEHAVIOR_MUSIC_INFO ->
                musicInfoDisplayLine(position)?.let(::listOf) ?: selectedLines

            RootConstants.TAIL_TIMEOUT_BEHAVIOR_RESTORE_DEFAULT -> emptyList()

            else -> selectedLines
        }
    }

    /**
     * 由时间线推导尾奏超时时刻：每一行按自身的时间语义算出一个超时点，取其中最晚的一个。
     * 设用户配置的时长为 n 秒，拥有合法结束时间（end > begin）的行取结束后 n 秒，只有开始时间
     * 的行取开始后 n + 5 秒。
     *
     * 只看"按开始时间排序的最后一行"在对唱歌词里会提前截断——前一行的结束时间可能晚于最后
     * 一行的结束时间，逐行取最大才能覆盖到真正最晚结束的那一句。没有可用歌词行时返回
     * [Long.MAX_VALUE]，即永不超时；接近上限的时刻做饱和处理，避免溢出成负数导致立刻判为尾奏。
     */
    private fun resolveTailTimeoutDeadline(lines: List<TimedLine>): Long {
        val endDelayMs = tailTimeoutDelaySeconds * 1_000L
        val beginOnlyDelayMs =
            (tailTimeoutDelaySeconds + RootConstants.TAIL_TIMEOUT_BEGIN_ONLY_EXTRA_SECONDS) * 1_000L
        var deadline = Long.MIN_VALUE
        for (line in lines) {
            val hasEndTime = line.end > line.begin
            val anchor = if (hasEndTime) line.end else line.begin
            if (anchor < 0L) continue
            val delay = if (hasEndTime) endDelayMs else beginOnlyDelayMs
            val candidate = if (anchor > Long.MAX_VALUE - delay) {
                Long.MAX_VALUE
            } else {
                anchor + delay
            }
            if (candidate > deadline) deadline = candidate
        }
        return if (deadline == Long.MIN_VALUE) Long.MAX_VALUE else deadline
    }

    private fun rebuildTimeline(song: Song, selectCurrentPosition: Boolean) {
        currentSongIsPureMusic = isPureMusicSongLyrics(song.lyrics.orEmpty().map { it.text })
        val processor = SongPreprocessor(
            resolveTitleSlot(placeholderFormat),
            showLongInterludeCountdown,
            showLyricPreview
        )
        val prepared = processor.prepare(song.deepCopy())
        timingNavigator = TimingNavigator(prepared.lines.toTypedArray())
        interludeCountdownNavigator =
            TimingNavigator(prepared.interludeCountdowns.toTypedArray())
        leadingPlaceholderLine = prepared.leadingPlaceholder
        if (prepared.interludeCountdowns.isNotEmpty()) {
            HookLogger.d(
                TAG,
                "间奏倒计时占位行: song=${song.name}, count=${prepared.interludeCountdowns.size}"
            )
        }
        fullSongLyricsAvailable = prepared.hasRenderableLyrics
        tailTimeoutDeadlineMs = resolveTailTimeoutDeadline(prepared.lines)
        // 是否"此刻没有正在唱的行"只能由选行判定，这里先保守置否；selectCurrentPosition 为真时
        // 随后的 updatePosition 会立刻按真实选行重算，为假时等下一次位置采样重算。
        currentSongInTailTimeout = false
        currentSongHasDuet = DuetLyricPolicy.hasDuet(song.lyrics)
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


