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
    fun missingPreferencesFallBackToDefaults() {
        val state = WordHighlightPreferencePolicy.read(prefs)

        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            state.unsungOpacity,
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            state.sungOpacity,
            0f
        )
    }

    @Test
    fun sungAndUnsungPreferencesAreReadIndependently() {
        commitUnsungOpacity(0.8f)
        commitSungOpacity(0.2f)

        val state = WordHighlightPreferencePolicy.read(prefs)

        assertEquals(0.8f, state.unsungOpacity, 0f)
        assertEquals(0.2f, state.sungOpacity, 0f)
    }

    @Test
    fun unsungValidStoredValueIsReadDirectly() {
        commitUnsungOpacity(0.35f)

        assertEquals(0.35f, WordHighlightPreferencePolicy.read(prefs).unsungOpacity, 0f)
    }

    @Test
    fun unsungBoundaryValuesAreAccepted() {
        commitUnsungOpacity(RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY)
        assertEquals(
            RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
            0f
        )

        commitUnsungOpacity(RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY)
        assertEquals(
            RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
            0f
        )
    }

    @Test
    fun unsungOutOfRangeOrNonFiniteStoredValuesFallBackToDefaultOnRead() {
        listOf(1.7f, -0.2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            .forEach { illegal ->
                commitUnsungOpacity(illegal)
                assertEquals(
                    RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                    WordHighlightPreferencePolicy.read(prefs).unsungOpacity,
                    0f
                )
            }
    }

    @Test
    fun unsungIllegalStoredValueIsSanitizedOnStartup() {
        commitUnsungOpacity(1.7f)

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
    fun floatPreferencePolicyNormalizesIllegalUnsungValuesToDefault() {
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
    fun floatPreferencePolicyAcceptsInRangeUnsungValues() {
        val key = RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY

        assertEquals(0.35f, requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, 0.35f)), 0f)
        assertEquals(
            0.35f,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, "0.35")),
            0f
        )
        assertNull(FloatPreferencePolicy.normalizeStoredFloat("key_unknown_float", 0.35f))
    }

    @Test
    fun sungValidStoredValueIsReadDirectly() {
        commitSungOpacity(0.35f)

        assertEquals(0.35f, WordHighlightPreferencePolicy.read(prefs).sungOpacity, 0f)
    }

    @Test
    fun sungBoundaryValuesAreAccepted() {
        commitSungOpacity(RootConstants.MIN_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY)
        assertEquals(
            RootConstants.MIN_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).sungOpacity,
            0f
        )

        commitSungOpacity(RootConstants.MAX_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY)
        assertEquals(
            RootConstants.MAX_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            WordHighlightPreferencePolicy.read(prefs).sungOpacity,
            0f
        )
    }

    @Test
    fun sungOutOfRangeOrNonFiniteStoredValuesFallBackToDefaultOnRead() {
        listOf(1.7f, -0.2f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            .forEach { illegal ->
                commitSungOpacity(illegal)
                assertEquals(
                    RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
                    WordHighlightPreferencePolicy.read(prefs).sungOpacity,
                    0f
                )
            }
    }

    @Test
    fun sungIllegalStoredValueIsSanitizedOnStartup() {
        commitSungOpacity(-0.2f)

        FloatPreferencePolicy.normalizeInPlace(prefs)

        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY
            ),
            0f
        )
    }

    @Test
    fun normalizeSungOpacityClampsFiniteValues() {
        assertEquals(
            RootConstants.MIN_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeSungOpacity(-0.2f),
            0f
        )
        assertEquals(
            RootConstants.MAX_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeSungOpacity(1.7f),
            0f
        )
        assertEquals(0.35f, WordHighlightPreferencePolicy.normalizeSungOpacity(0.35f), 0f)
    }

    @Test
    fun normalizeSungOpacityFallsBackForNonFiniteValues() {
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeSungOpacity(Float.NaN),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            WordHighlightPreferencePolicy.normalizeSungOpacity(Float.POSITIVE_INFINITY),
            0f
        )
        assertEquals(0.2f, WordHighlightPreferencePolicy.normalizeSungOpacity(Float.NaN, 0.2f), 0f)
    }

    @Test
    fun floatPreferencePolicyNormalizesIllegalSungValuesToDefault() {
        val key = RootConstants.KEY_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY

        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, 1.7f)),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, -0.2f)),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, Float.NaN)),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, "非法值")),
            0f
        )
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, null)),
            0f
        )
    }

    @Test
    fun floatPreferencePolicyAcceptsInRangeSungValues() {
        val key = RootConstants.KEY_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY

        assertEquals(0.35f, requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, 0.35f)), 0f)
        assertEquals(
            0.35f,
            requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, "0.35")),
            0f
        )
        assertEquals(1f, requireNotNull(FloatPreferencePolicy.normalizeStoredFloat(key, 1f)), 0f)
    }

    @Test
    fun stateKeepsUnsungAsFirstPositionalParameter() {
        val state = WordHighlightPreferenceState(0.42f)

        assertEquals(0.42f, state.unsungOpacity, 0f)
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            state.sungOpacity,
            0f
        )
    }

    @Test
    fun stateWithoutSungArgumentFallsBackToDefaultSung() {
        val state = WordHighlightPreferenceState(
            unsungOpacity = 0.42f
        )

        assertEquals(0.42f, state.unsungOpacity, 0f)
        assertEquals(
            RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            state.sungOpacity,
            0f
        )
    }

    private fun commitUnsungOpacity(value: Float) {
        commitFloat(RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY, value)
    }

    private fun commitSungOpacity(value: Float) {
        commitFloat(RootConstants.KEY_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY, value)
    }

    private fun commitFloat(key: String, value: Float) {
        prefs.edit()
            .putFloat(key, value)
            .commit()
    }
}
