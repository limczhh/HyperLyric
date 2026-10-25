package com.lidesheng.hyperlyric.root.island.content

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.presentation.LyricPresentationScheduler
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], manifest = Config.NONE)
class LyricMusicInfoReplacementTest {
    private lateinit var prefs: SharedPreferences
    private val media = MediaMetadataHelper.MediaInfo(
        title = "Song title", artist = "Artist", album = "Album", duration = 5_000L
    )

    @Before
    fun setup() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("replacement", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE, "title")
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE, "artist")
            .putBoolean(RootConstants.KEY_HOOK_LYRIC_SHOW_TRANSLATION, false)
            .putBoolean(RootConstants.KEY_HOOK_LYRIC_SHOW_ROMA, false)
            .putBoolean(RootConstants.KEY_HOOK_ONLY_SECONDARY, true)
            .putBoolean(RootConstants.KEY_HOOK_SWAP_SECONDARY, true)
            .putInt(RootConstants.KEY_HOOK_MUSIC_INFO_ALIGNMENT, RootConstants.CONTENT_ALIGNMENT_RIGHT)
            .putInt(RootConstants.KEY_HOOK_LYRIC_ALIGNMENT, RootConstants.CONTENT_ALIGNMENT_LEFT)
            .putInt(RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR, RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO)
            .putInt(RootConstants.KEY_HOOK_TAIL_TIMEOUT_BEHAVIOR, RootConstants.TAIL_TIMEOUT_BEHAVIOR_MUSIC_INFO)
            .putBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, false)
            .commit()
        LyriconDataBridge.clearState()
        LyriconDataBridge.updateTailTimeoutDelaySeconds(1)
        LyriconDataBridge.updateLyricPreviewEnabled(false)
        LyriconDataBridge.updateLongInterludeCountdownEnabled(false)
    }

    @After
    fun cleanup() {
        LyriconDataBridge.clearState()
        SlotContentCoordinator.invalidate()
    }

    @Test
    fun noLyricsUsesTheSameTwoRowsAsExplicitMusicInfo() {
        LyriconDataBridge.updateLyric(null)
        publishMetadata()
        val replacement = newView()
        val explicit = newView()
        render(replacement)
        render(explicit, RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO)
        assertEquals("Song title", replacement.rawLine?.text)
        assertEquals("Artist", replacement.rawSecondaryLine?.text)
        assertTrue(replacement.alwaysShowSecondary)
        assertEquals(View.VISIBLE, replacement.secondary.visibility)
        assertTrue(replacement.main.rightIfPossible)
        assertEquals(explicit.rawLine, replacement.rawLine)
        assertEquals(explicit.rawSecondaryLine, replacement.rawSecondaryLine)
        assertNull(LyriconDataBridge.currentLyricLine)
        assertTrue(LyriconDataBridge.currentLyricLines.isEmpty())
    }

    @Test
    fun pureMusicKeepsTheSourceLineUnmodified() {
        LyriconDataBridge.updateLyric("纯音乐，请欣赏")
        val original = LyriconDataBridge.currentLyricLine
        publishMetadata()
        val view = newView()
        render(view)
        assertEquals("Song title", view.rawLine?.text)
        assertEquals("Artist", view.rawSecondaryLine?.text)
        assertSame(original, LyriconDataBridge.currentLyricLine)
        assertEquals("纯音乐，请欣赏", LyriconDataBridge.currentLyricLine?.text)
    }

    @Test
    fun outroSwitchesToMetadataAndSeekingBackRestoresLyrics() {
        prefs.edit().putBoolean(RootConstants.KEY_HOOK_ONLY_SECONDARY, false)
            .putBoolean(RootConstants.KEY_HOOK_SWAP_SECONDARY, false).commit()
        loadSong(listOf(RichLyricLine(text = "Original lyric", begin = 1_000, end = 2_000)))
        assertTrue(LyriconDataBridge.updatePosition(1_000))
        val view = newView()
        render(view)
        assertEquals("Original lyric", view.rawLine?.text)
        // The selected source line is unchanged at the deadline. Its mode transition
        // must still notify the renderer, rather than wait for another lyric line.
        assertTrue(LyriconDataBridge.updatePosition(3_000))
        assertEquals("Original lyric", LyriconDataBridge.currentLyricLine?.text)
        render(view)
        assertEquals("Song title", view.rawLine?.text)
        assertEquals("Artist", view.rawSecondaryLine?.text)
        assertTrue(LyriconDataBridge.updatePosition(1_500))
        render(view)
        assertEquals("Original lyric", view.rawLine?.text)
        assertNull(view.rawSecondaryLine)
        assertFalse(SlotContentCoordinator.updatePlaybackProgress(view, 4_000))
    }

    @Test
    fun instrumentalGapWithinASongDoesNotTriggerNoLyricsReplacement() {
        loadSong(listOf(
            RichLyricLine(text = "First", begin = 1_000, end = 2_000),
            RichLyricLine(text = "Second", begin = 10_000, end = 11_000)
        ))
        LyriconDataBridge.updatePosition(5_000)
        assertFalse(LyricPresentationScheduler.snapshot(prefs).hasMusicInfo)
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            IslandSlotRuntimeConfig.from(prefs).resolveContentMode(RootConstants.ISLAND_CONTENT_MODE_LYRIC))
    }

    @Test
    fun emptyMetadataOrAnEmptyLayoutDoesNotClaimReplacementContent() {
        LyriconDataBridge.updateLyric(null)
        assertFalse(LyricPresentationScheduler.snapshot(prefs).hasMusicInfo)
        publishMetadata()
        prefs.edit().putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE, "")
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE, "").commit()
        assertFalse(LyricPresentationScheduler.snapshot(prefs).hasMusicInfo)
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            IslandSlotRuntimeConfig.from(prefs).resolveContentMode(RootConstants.ISLAND_CONTENT_MODE_LYRIC))
    }

    @Test
    fun secondaryOnlyMusicInfoDoesNotDependOnTheLyricTranslationSetting() {
        LyriconDataBridge.updateLyric(null)
        publishMetadata()
        prefs.edit().putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE, "").commit()
        val view = newView()
        render(view)
        assertEquals("Artist", view.rawLine?.text)
        assertNull(view.rawSecondaryLine)
    }

    @Test
    fun explicitContentChoicesAreNotOverridden() {
        LyriconDataBridge.updateLyric("纯音乐，请欣赏")
        publishMetadata()
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_NONE,
            IslandSlotRuntimeConfig.from(prefs).resolveContentMode(RootConstants.ISLAND_CONTENT_MODE_NONE))
        prefs.edit().putInt(RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR,
            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT).commit()
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO,
            IslandSlotRuntimeConfig.from(prefs).resolveContentMode(RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO))
    }

    @Test
    fun playbackFieldsUpdateThroughTheMetadataRenderer() {
        LyriconDataBridge.updateLyric(null)
        publishMetadata()
        prefs.edit().putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE, "elapsed")
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE, "progress_percent").commit()
        val view = newView()
        render(view)
        assertEquals("00:00", view.rawLine?.text)
        assertEquals("0%", view.rawSecondaryLine?.text)
        assertTrue(SlotContentCoordinator.updatePlaybackProgress(view, 2_500))
        assertEquals("00:02", view.rawLine?.text)
        assertEquals("50%", view.rawSecondaryLine?.text)
        assertNull(LyriconDataBridge.currentLyricLine)
    }

    @Test
    fun splitModeShowsCompleteMetadataOnBothSides() {
        LyriconDataBridge.updateLyric(null)
        publishMetadata()
        prefs.edit().putInt(RootConstants.KEY_HOOK_LYRIC_MODE, 1)
            .putInt(RootConstants.KEY_HOOK_MUSIC_INFO_ALIGNMENT, RootConstants.CONTENT_ALIGNMENT_CENTER)
            .commit()
        val left = SpaceGateRichLyricLineView(RuntimeEnvironment.getApplication())
        val right = SpaceGateRichLyricLineView(RuntimeEnvironment.getApplication())
        left.tag = IslandProbeUtils.LEFT_TEST_VIEW_TAG
        right.tag = IslandProbeUtils.RIGHT_TEST_VIEW_TAG
        render(left)
        render(right)
        for (view in listOf(left, right)) {
            assertEquals("Song title", view.rawLine?.text)
            assertEquals("Artist", view.rawSecondaryLine?.text)
            assertTrue(view.main.centerIfPossible)
            assertFalse(view.main.rightIfPossible)
        }
    }

    @Test
    fun restoreDefaultClearsMetadataAndReleasesManagedContent() {
        LyriconDataBridge.updateLyric("纯音乐，请欣赏")
        publishMetadata()
        val view = newView()
        render(view)
        assertTrue(view.alwaysShowSecondary)
        prefs.edit().putInt(RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR,
            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT).commit()
        render(view)
        assertNull(view.rawLine)
        assertNull(view.rawSecondaryLine)
        assertFalse(view.alwaysShowSecondary)
        assertFalse(LyricPresentationScheduler.snapshot(prefs).hasManagedContent)
        assertFalse(SlotContentCoordinator.updatePlaybackProgress(view, 2_500))
    }

    @Test
    fun contentModeSnapshotIsSharedByStylingAndSlotWidths() {
        LyriconDataBridge.updateLyric(null)
        publishMetadata()
        prefs.edit().putInt(RootConstants.KEY_HOOK_LYRIC_MODE, 1).commit()
        val metadataConfig = IslandSlotRuntimeConfig.from(prefs)
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO,
            metadataConfig.modeForTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG))
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO,
            metadataConfig.modeForTag(IslandProbeUtils.RIGHT_TEST_VIEW_TAG))
        LyriconDataBridge.updateLyric("New lyric")
        val lyricConfig = IslandSlotRuntimeConfig.from(prefs)
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            lyricConfig.modeForTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG))
        assertEquals(RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO,
            metadataConfig.modeForTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG))
    }

    private fun newView() = RichLyricLineView(RuntimeEnvironment.getApplication())

    private fun loadSong(lines: List<RichLyricLine>) {
        LyriconDataBridge.updateSong(Song(name = media.title, duration = 20_000, lyrics = lines))
        publishMetadata()
    }

    private fun publishMetadata() {
        LyriconDataBridge.updateMediaMetadata(LyricMediaMetadata(
            sourceId = "test", title = media.title, artist = media.artist,
            album = media.album, duration = media.duration
        ))
        LyriconDataBridge.currentResolvedMediaInfo = media
    }

    private fun render(view: View, mode: Int = RootConstants.ISLAND_CONTENT_MODE_LYRIC) {
        SlotContentCoordinator.applySlotContent(
            view = view, prefs = prefs, config = IslandSlotRuntimeConfig.from(prefs),
            mode = mode, force = true, playbackActive = false, suppressAnimation = true,
            mediaInfo = media, playbackClock = LyriconDataBridge.PlaybackClockReading(0L, 1f, 0L)
        )
    }
}
