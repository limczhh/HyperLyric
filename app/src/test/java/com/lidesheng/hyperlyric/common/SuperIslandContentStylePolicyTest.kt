package com.lidesheng.hyperlyric.common

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class SuperIslandContentStylePolicyTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("music-wave-style-policy", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun previousCombinedPreferenceIsIgnoredByIndependentSettings() {
        prefs.edit()
            .putInt(
                "key_hook_island_music_wave_style",
                RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT
            )
            .commit()

        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_MODE_DEFAULT,
            SuperIslandContentStylePolicy.readMusicWaveMode(prefs)
        )
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_DEFAULT,
            SuperIslandContentStylePolicy.readMusicWaveColorStyle(prefs)
        )
    }

    @Test
    fun newSettingsAreReadIndependently() {
        prefs.edit()
            .putInt(
                RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_COLOR_STYLE,
                RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT
            )
            .putInt(
                RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_MODE,
                RootConstants.ISLAND_MUSIC_WAVE_MODE_REALTIME_SPECTRUM
            )
            .commit()

        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_MODE_REALTIME_SPECTRUM,
            SuperIslandContentStylePolicy.readMusicWaveMode(prefs)
        )
        assertEquals(
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT,
            SuperIslandContentStylePolicy.readMusicWaveColorStyle(prefs)
        )
    }
}
