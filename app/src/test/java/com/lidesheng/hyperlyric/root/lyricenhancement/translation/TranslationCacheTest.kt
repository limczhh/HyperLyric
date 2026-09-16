package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCacheTest {
    @Test
    fun cacheHitAfterNewEngineDoesNotCallNetwork() {
        val cache = FakeCache()
        val song = song()
        val config = config()
        var firstCalls = 0
        AiTranslationEngine(
            cacheStore = cache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ ->
                firstCalls++
                listOf(TranslationItem(0, "translated"))
            }
        ).let { engine ->
            assertEquals("translated", engine.translate(song, config)?.lyrics?.first()?.translation)
            engine.close()
        }
        assertEquals(1, firstCalls)

        var secondCalls = 0
        AiTranslationEngine(
            cacheStore = cache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ ->
                secondCalls++
                listOf(TranslationItem(0, "network-should-not-run"))
            }
        ).let { engine ->
            val translated = engine.translate(song, config)
            assertEquals("translated", translated?.lyrics?.first()?.translation)
            engine.close()
        }
        assertEquals(0, secondCalls)
    }

    @Test
    fun corruptCacheIsRemovedAndFallsBackToNetwork() {
        val cache = FakeCache()
        val song = song()
        val config = config()
        AiTranslationEngine(
            cacheStore = cache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ -> listOf(TranslationItem(0, "cached")) }
        ).let { engine ->
            engine.translate(song, config)
            engine.close()
        }

        val entryKey = cache.values.keys.first { it.startsWith("cache.entry.v1.") }
        cache.values[entryKey] = "{broken"
        var calls = 0
        AiTranslationEngine(
            cacheStore = cache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ ->
                calls++
                listOf(TranslationItem(0, "recovered"))
            }
        ).let { engine ->
            assertEquals("recovered", engine.translate(song, config)?.lyrics?.first()?.translation)
            engine.close()
        }

        assertEquals(1, calls)
        assertTrue(cache.values[entryKey]?.contains("recovered") == true)
    }

    @Test
    fun cacheBodyIsReadableWithoutMetadataIndex() {
        val cache = FakeCache()
        val key = "a".repeat(64)
        cache.values["cache.entry.v1.$key"] =
            "[{\"index\":0,\"trans\":\"direct hit\"}]"

        val lookup = TranslationCache(cache, NO_OP_LOGGER).get(key)

        assertEquals("direct hit", lookup?.items?.single()?.trans)
        assertFalse(cache.values.containsKey("cache.index.v1"))
    }

    @Test
    fun cacheKeyExcludesApiKeyButIncludesResultInputs() {
        val song = song()
        val base = config()
        val sameWithoutSecret = base.copy(apiKey = "different-secret")
        val changedWritePolicy = base.copy(forceOverride = true)
        val changedModel = base.copy(model = "another-model")
        val changedAlbum = song.copy(album = "another-album")

        assertEquals(
            TranslationKey.calculate(song, listOf("original"), base),
            TranslationKey.calculate(song, listOf("original"), sameWithoutSecret)
        )
        assertEquals(
            TranslationKey.calculate(song, listOf("original"), base),
            TranslationKey.calculate(song, listOf("original"), changedWritePolicy)
        )
        assertFalse(
            TranslationKey.calculate(song, listOf("original"), base) ==
                    TranslationKey.calculate(song, listOf("original"), changedModel)
        )
        assertFalse(
            TranslationKey.calculate(song, listOf("original"), base) ==
                    TranslationKey.calculate(changedAlbum, listOf("original"), base)
        )
        assertFalse(
            TranslationKey.calculate(song, listOf("original"), base, "player.one") ==
                    TranslationKey.calculate(song, listOf("original"), base, "player.two")
        )
    }

    @Test
    fun networkResultIsCachedEvenWhenCurrentSongNeedsNoWriteback() {
        val cache = FakeCache()
        val song = song().copy(
            lyrics = song().lyrics!!.mapIndexed { index, line ->
                if (index == 0) line.copy(translation = "existing") else line
            }
        )
        val config = config()
        var firstCalls = 0
        AiTranslationEngine(
            cacheStore = cache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ ->
                firstCalls++
                listOf(TranslationItem(0, "new translation"))
            }
        ).let { engine ->
            assertNull(engine.translate(song, config))
            engine.close()
        }
        assertEquals(1, firstCalls)

        var secondCalls = 0
        AiTranslationEngine(
            cacheStore = cache,
            logger = NO_OP_LOGGER,
            translatorLogger = NO_OP_LOGGER,
            networkRequester = { _, _, _ ->
                secondCalls++
                listOf(TranslationItem(0, "network-should-not-run"))
            }
        ).let { engine ->
            assertNull(engine.translate(song, config))
            engine.close()
        }
        assertEquals(0, secondCalls)
    }

    @Test
    fun clearInvalidatesWritesStartedBeforeTheClear() {
        val cacheStore = FakeCache()
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)
        val key = "f".repeat(64)
        val generationBeforeClear = cache.currentGeneration()

        cache.clear()
        cache.put(
            key = key,
            items = listOf(TranslationItem(0, "stale")),
            expectedGeneration = generationBeforeClear
        )

        assertNull(cache.get(key))
        assertTrue(cacheStore.values.isEmpty())
    }

    @Test
    fun clearRemovesEntriesThatAreMissingFromTheIndex() {
        val cacheStore = FakeCache()
        cacheStore.values["cache.entry.v1." + "a".repeat(64)] =
            "[{\"index\":0,\"trans\":\"orphan\"}]"
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)

        assertTrue(cache.clear())

        assertTrue(cacheStore.values.isEmpty())
    }

    @Test
    fun cacheEntriesExposeOnlySongMetadataAndCanBeRemoved() {
        val cacheStore = FakeCache()
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)
        val key = "b".repeat(64)

        cache.put(
            key = key,
            items = listOf(TranslationItem(0, "translated")),
            title = "title",
            artist = "artist"
        )

        assertEquals(
            com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry(
                id = key,
                title = "title",
                artist = "artist"
            ),
            cache.listEntries().single()
        )
        assertTrue(cache.clearEntry(key))
        assertTrue(cache.listEntries().isEmpty())
        assertFalse(cacheStore.values.keys.any { it.endsWith(key) })
    }

    private fun song(): Song = Song(
        name = "title",
        artist = "artist",
        album = "album",
        duration = 180_000L,
        lyrics = listOf(
            RichLyricLine(
                begin = 0L,
                end = 1_000L,
                duration = 1_000L,
                text = "original lyric content"
            ),
            RichLyricLine(
                begin = 1_000L,
                end = 2_000L,
                duration = 1_000L,
                text = "second lyric content"
            ),
            RichLyricLine(
                begin = 2_000L,
                end = 3_000L,
                duration = 1_000L,
                text = "third lyric content"
            )
        )
    )

    private fun config(): AiTranslationConfig = AiTranslationConfig(
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

    private class FakeCache : LyricEnhancementCacheStore {
        val values = linkedMapOf<String, String>()

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear(): Boolean {
            values.clear()
            return true
        }
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
