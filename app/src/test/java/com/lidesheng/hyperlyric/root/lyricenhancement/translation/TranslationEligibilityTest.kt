package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationEligibilityTest {
    @Test
    fun emptyOrBlankLyricsAreSkipped() {
        assertEquals("no_lyrics", TranslationEligibility.skipReason(Song()))
        assertEquals(
            "no_lyrics",
            TranslationEligibility.skipReason(
                song(lyrics = listOf(RichLyricLine(text = "  ")))
            )
        )
    }

    @Test
    fun oneOrTwoEffectiveLinesAreSkipped() {
        assertEquals(
            "too_few_lines",
            TranslationEligibility.skipReason(
                song(lyrics = listOf(RichLyricLine(text = "A long lyric line")))
            )
        )
        assertEquals(
            "too_few_lines",
            TranslationEligibility.skipReason(
                song(
                    lyrics = listOf(
                        RichLyricLine(text = "First lyric line"),
                        RichLyricLine(text = "Second lyric line")
                    )
                )
            )
        )
    }

    @Test
    fun threeEffectiveLinesRemainEligible() {
        assertNull(
            TranslationEligibility.skipReason(
                song(
                    lyrics = listOf(
                        RichLyricLine(text = "First lyric line"),
                        RichLyricLine(text = "Second lyric line"),
                        RichLyricLine(text = "Third lyric line")
                    )
                )
            )
        )
    }

    @Test
    fun blankLinesDoNotCountTowardMinimum() {
        assertNull(
            TranslationEligibility.skipReason(
                song(
                    lyrics = listOf(
                        RichLyricLine(text = "First lyric line"),
                        RichLyricLine(text = "  "),
                        RichLyricLine(text = "Second lyric line"),
                        RichLyricLine(text = "Third lyric line")
                    )
                )
            )
        )
    }

    @Test
    fun titleOnlySongNeverReachesNetwork() {
        var networkCalls = 0
        val engine = AiTranslationEngine(
            cacheStore = EmptyCache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ ->
                networkCalls++
                listOf(TranslationItem(0, "should-not-be-requested"))
            }
        )

        try {
            assertNull(
                engine.translate(
                    song(
                        name = "おかえりなさい",
                        lyrics = listOf(RichLyricLine(text = "おかえりなさい"))
                    ),
                    config()
                )
            )
        } finally {
            engine.close()
        }
        assertEquals(0, networkCalls)
    }

    private fun song(
        name: String? = "title",
        lyrics: List<RichLyricLine>? = listOf(
            RichLyricLine(text = "first lyric content"),
            RichLyricLine(text = "second lyric content"),
            RichLyricLine(text = "third lyric content")
        )
    ): Song = Song(name = name, lyrics = lyrics)

    private fun config() = AiTranslationConfig(
        provider = "OPENAI",
        apiKey = "secret",
        baseUrl = "https://example.test/v1/",
        model = "model",
        targetLanguage = "中文",
        prompt = "prompt",
        skipLanguages = emptySet(),
        skipExisting = false,
        forceOverride = false,
        temperature = 1f,
        topP = 1f,
        maxTokens = 100,
        enabled = true
    )

    private object EmptyCache : LyricEnhancementCacheStore {
        override fun getString(key: String): String? = null
        override fun putString(key: String, value: String) = Unit
        override fun remove(key: String) = Unit
        override fun clear(): Boolean = true
    }

    private companion object {
        val NO_OP_LOGGER = LyricEnhancementLogger(
            "Test",
            object : HyperLogger {
                override fun d(tag: String, msg: String) = Unit
                override fun i(tag: String, msg: String) = Unit
                override fun w(tag: String, msg: String, e: Throwable?) = Unit
                override fun e(tag: String, msg: String, e: Throwable?) = Unit
            }
        )
    }
}
