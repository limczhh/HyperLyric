package com.lidesheng.hyperlyric.common

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class WordHighlightPreferencePolicyTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("word-highlight-policy", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun missingPreferenceFallsBackToDefault() {
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
            0f
        )
    }

    @Test
    fun validStoredValueIsReadDirectly() {
        commitOpacity(0.35f)

        assertEquals(0.35f, WordHighlightPreferencePolicy.read(prefs).unsungOpacity, 0f)
    }

    @Test
    fun boundaryValuesAreAccepted() {
        commitOpacity(RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY)
        assertEquals(
            RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
            0f
        )

        commitOpacity(RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY)
        assertEquals(
            RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
            0f
        )
    }

    @Test
    fun outOfRangeOrNonFiniteStoredValuesFallBackToDefaultOnRead() {
        listOf(1.7f, -0.2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            .forEach { illegal ->
                commitOpacity(illegal)
                assertEquals(
                    RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                    WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
                    0f
                )
            }
    }

    @Test
    fun illegalStoredValueIsSanitizedOnStartup() {
        commitOpacity(1.7f)

        FloatPreferencePolicy.normalizeInPlace(prefs)

        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY
            ),
            0f
        )
    }

    @Test
    fun normalizeUnsungOpacityClampsFiniteValues() {
        assertEquals(
            RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeUnsungOpacity(-0.2f),
            0f
        )
        assertEquals(
            RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeUnsungOpacity(1.7f),
            0f
        )
        assertEquals(0.35f, WordHighlightPreferencePolicy.normalizeUnsungOpacity(0.35f), 0f)
    }

    @Test
    fun normalizeUnsungOpacityFallsBackForNonFiniteValues() {
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeUnsungOpacity(Float.NaN),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeUnsungOpacity(Float.NEGATIVE_INFINITY),
            0f
        )
        assertEquals(0.2f, WordHighlightPreferencePolicy.normalizeUnsungOpacity(Float.NaN, 0.2f), 0f)
    }

    @Test
    fun floatPreferencePolicyNormalizesIllegalValuesToDefault() {
        val key = RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY

        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, 1.7f)),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, -0.2f)),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, Float.NaN)),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, "非法值")),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, null)),
            0f
        )
    }

    @Test
    fun floatPreferencePolicyAcceptsInRangeValues() {
        val key = RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY

        assertEquals(0.35f, requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, 0.35f)), 0f)
        assertEquals(
            0.35f,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, "0.35")),
            0f
        )
        assertNull(FloatPreferencePolicy.normalizeStoredFloat("key_unknown_float", 0.35f))
    }

    private fun commitOpacity(value: Float) {
        prefs.edit()
            .putFloat(RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY, value)
            .commit()
    }
}
