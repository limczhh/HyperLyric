package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationEligibilityTest {
    @Test
    fun emptyOrBlankLyricsAreSkipped() {
        assertEquals("no_lyrics", TranslationEligibility.skipReason(PluginSong()))
        assertEquals(
            "no_lyrics",
            TranslationEligibility.skipReason(
                song(lyrics = listOf(PluginLyricLine(text = "  ")))
            )
        )
    }

    @Test
    fun oneOrTwoEffectiveLinesAreSkipped() {
        assertEquals(
            "too_few_lines",
            TranslationEligibility.skipReason(
                song(
                    lyrics = listOf(PluginLyricLine(text = "A long lyric line"))
                )
            )
        )
        assertEquals(
            "too_few_lines",
            TranslationEligibility.skipReason(
                song(
                    lyrics = listOf(
                        PluginLyricLine(text = "First lyric line"),
                        PluginLyricLine(text = "Second lyric line")
                    )
                )
            )
        )
    }

    @Test
    fun threeEffectiveLinesRemainEligible() {
        val song = song(
            lyrics = listOf(
                PluginLyricLine(text = "First lyric line"),
                PluginLyricLine(text = "Second lyric line"),
                PluginLyricLine(text = "Third lyric line")
            )
        )

        assertNull(TranslationEligibility.skipReason(song))
    }

    @Test
    fun blankLinesDoNotCountTowardMinimum() {
        assertNull(
            TranslationEligibility.skipReason(
                song(
                    lyrics = listOf(
                        PluginLyricLine(text = "First lyric line"),
                        PluginLyricLine(text = "  "),
                        PluginLyricLine(text = "Second lyric line"),
                        PluginLyricLine(text = "Third lyric line")
                    )
                )
            )
        )
    }

    @Test
    fun titleOnlySongNeverReachesNetwork() {
        var networkCalls = 0
        val engine = AiTranslationEngine(
            cacheStore = EmptyPluginCache,
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
                        lyrics = listOf(PluginLyricLine(text = "おかえりなさい"))
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
        lyrics: List<PluginLyricLine>? = listOf(
            PluginLyricLine(text = "first lyric content"),
            PluginLyricLine(text = "second lyric content"),
            PluginLyricLine(text = "third lyric content")
        )
    ): PluginSong = PluginSong(name = name, lyrics = lyrics)

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

    private object EmptyPluginCache : PluginCache {
        override fun getString(key: String): String? = null
        override fun putString(key: String, value: String) = Unit
        override fun getBytes(key: String): ByteArray? = null
        override fun putBytes(key: String, value: ByteArray) = Unit
        override fun contains(key: String): Boolean = false
        override fun remove(key: String) = Unit
        override fun clear() = Unit
    }

    private val NO_OP_LOGGER = object : PluginLogger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }
}
