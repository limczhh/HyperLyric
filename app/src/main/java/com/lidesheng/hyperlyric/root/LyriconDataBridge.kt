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

    /** 当前歌词内容是否命中纯音乐标记，随歌曲/歌词/单行更新重算。 */
    @Volatile
    var currentSongIsPureMusic: Boolean = false
        private set

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

    fun updatePosition(position: Long): Boolean {
        currentPosition = position
        if (isTextMode) return false
        val song = currentSong ?: return false
        val lyrics = song.lyrics
        if (lyrics.isNullOrEmpty()) return false

        // 先展示此刻有效的源歌词行；开唱前只有当前没有源内容时才补前奏占位行。
        val activeLines = buildList {
            timingNavigator.forEachAt(position) { add(it) }
        }
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
        val effectiveLines = applyNoLyricsTimelineBehavior(selectedLines)

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
        // 纯音乐替换为音乐信息时，占位内容不走时间线选行，
        // 每次位置更新都触发一次渲染检查，保证时间字段逐秒刷新（渲染层按签名去重）。
        if (isPureMusicLyricHandled() && noLyricsBehaviorShowsMusicInfo()) {
            currentLyric = ""
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
        if (isPureMusicLyricHandled()) return false
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
        if (isNoLyricsContentSuppressed()) return null
        if (noLyricsBehaviorShowsMusicInfo()) return pureMusicMusicInfoLine()
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
        if (isNoLyricsContentSuppressed()) return false
        if (noLyricsBehaviorShowsMusicInfo()) {
            return resolvePureMusicMediaInfo() != null
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

    /** 纯音乐被行为接管（非"保持不变"），对唱宽度锁定不适用。 */
    fun isPureMusicLyricReplaced(): Boolean = isPureMusicLyricHandled()

    /** "替换为空"是否生效，状态栏仅保留图标。 */
    fun isNoLyricsBehaviorEmpty(): Boolean =
        noLyricsLyricBehavior == RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_EMPTY

    /** "替换为空/回退到默认"时不展示任何占位内容。 */
    private fun isNoLyricsContentSuppressed(): Boolean =
        noLyricsLyricBehavior == RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_EMPTY ||
                noLyricsLyricBehavior == RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT

    /** 无歌词/纯音乐时是否展示音乐信息占位。 */
    fun noLyricsBehaviorShowsMusicInfo(): Boolean =
        noLyricsLyricBehavior == RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO

    /** 模块是否正在接管文本展示（歌词或替换内容），状态栏时钟/通知图标隐藏依赖此判定。 */
    fun isPresentingManagedContent(): Boolean =
        hasLyricsForPresentation() || noLyricsBehaviorShowsMusicInfo()

    /** 用用户配置的音乐信息布局合成展示行，媒体信息不可用时返回 null。 */
    private fun pureMusicMusicInfoLine(): IRichLyricLine? {
        val prefs = HookEntry.instance?.prefs ?: return null
        val mediaInfo = resolvePureMusicMediaInfo() ?: return null
        val playbackClock = currentPlaybackClock()
        val lines = IslandMetadataContentAssembler.buildConfiguredMusicInfoLines(
            prefs = prefs,
            mediaInfo = mediaInfo,
            playbackPosition = playbackClock.positionMs,
            playbackDuration = currentSong?.duration?.takeIf { it > 0L } ?: mediaInfo.duration
        )
        return RichLyricLine(
            text = lines.firstLine.takeIf { it.isNotBlank() },
            translation = lines.secondLine.takeIf { it.isNotBlank() }
        )
    }

    private fun resolvePureMusicMediaInfo(): MediaMetadataHelper.MediaInfo? {
        currentResolvedMediaInfo?.let { resolved ->
            return resolved.takeIf { it.title.isNotBlank() || it.artist.isNotBlank() }
        }
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

    /** 按无歌词/纯音乐行为处理源推送的单行内容，接管时置空以走无歌词占位链路。 */
    private fun resolveActiveLyricLine(): IRichLyricLine? {
        val rawLine = rawActiveLyricLine ?: return null
        return if (isPureMusicLyricHandled()) null else rawLine
    }

    /** 时间线选行结果的行为覆盖，纯音乐被接管时清空选行，返回原列表实例表示未覆盖。 */
    private fun applyNoLyricsTimelineBehavior(
        selectedLines: List<IRichLyricLine>
    ): List<IRichLyricLine> {
        return if (isPureMusicLyricHandled()) emptyList() else selectedLines
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


