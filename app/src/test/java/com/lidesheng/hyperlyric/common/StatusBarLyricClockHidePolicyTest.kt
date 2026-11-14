package com.lidesheng.hyperlyric.common

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class StatusBarLyricClockHidePolicyTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("clock-hide-policy", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun missingPreferenceFallsBackToWhilePlaying() {
        assertEquals(
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING,
            StatusBarLyricClockHidePolicy.read(prefs),
        )
    }

    @Test
    fun storedValueIsClampedToKnownRange() {
        commitBehavior(RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT)
        assertEquals(
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
            StatusBarLyricClockHidePolicy.read(prefs),
        )

        commitBehavior(99)
        assertEquals(
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
            StatusBarLyricClockHidePolicy.read(prefs),
        )

        commitBehavior(-5)
        assertEquals(RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_NONE, StatusBarLyricClockHidePolicy.read(prefs))
    }

    @Test
    fun mediaSessionBehaviorHidesWhilePaused() {
        val state = baseState().copy(
            playbackActive = false,
            ownsStatusBarContent = false,
            mediaSessionPresent = true,
        )

        assertTrue(
            StatusBarLyricClockHidePolicy.shouldHide(
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
                state,
            )
        )
    }

    @Test
    fun mediaSessionBehaviorNeedsAnExistingSession() {
        val state = baseState().copy(mediaSessionPresent = false)

        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
                state,
            )
        )
    }

    @Test
    fun mediaSessionBehaviorYieldsToLockScreen() {
        val state = baseState().copy(mediaSessionPresent = true, lockScreenSuppressed = true)

        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
                state,
            )
        )
    }

    @Test
    fun whilePlayingBehaviorKeepsItsExistingConditions() {
        val behavior = RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING
        val playing = baseState().copy(playbackActive = true, ownsStatusBarContent = true)

        assertTrue(StatusBarLyricClockHidePolicy.shouldHide(behavior, playing))
        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                behavior,
                playing.copy(playbackActive = false),
            )
        )
        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                behavior,
                playing.copy(ownsStatusBarContent = false),
            )
        )
        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                behavior,
                playing.copy(lyricTemporarilyHidden = true),
            )
        )
    }

    @Test
    fun islandBehaviorStillRequiresPlayback() {
        val behavior = RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT
        val playing = baseState().copy(playbackActive = true, islandPresent = true)

        assertTrue(StatusBarLyricClockHidePolicy.shouldHide(behavior, playing))
        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                behavior,
                playing.copy(playbackActive = false),
            )
        )
        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                behavior,
                playing.copy(islandPresent = false),
            )
        )
    }

    @Test
    fun temporaryRevealAndDisabledTargetAlwaysWin() {
        val hidden = baseState().copy(
            playbackActive = true,
            ownsStatusBarContent = true,
            islandPresent = true,
            mediaSessionPresent = true,
        )
        val behaviors = listOf(
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING,
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT,
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
        )

        behaviors.forEach { behavior ->
            assertFalse(
                StatusBarLyricClockHidePolicy.shouldHide(
                    behavior,
                    hidden.copy(temporarilyRevealed = true),
                )
            )
            assertFalse(
                StatusBarLyricClockHidePolicy.shouldHide(
                    behavior,
                    hidden.copy(lyricTypeEnabled = false),
                )
            )
        }
        assertFalse(
            StatusBarLyricClockHidePolicy.shouldHide(
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_NONE,
                hidden,
            )
        )
    }

    private fun baseState() = StatusBarLyricClockHidePolicy.State(
        lyricTypeEnabled = true,
        temporarilyRevealed = false,
        playbackActive = false,
        ownsStatusBarContent = false,
        lyricTemporarilyHidden = false,
        islandPresent = false,
        mediaSessionPresent = false,
        lockScreenSuppressed = false,
    )

    private fun commitBehavior(behavior: Int) {
        prefs.edit()
            .putInt(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR, behavior)
            .commit()
    }
}
