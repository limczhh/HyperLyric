package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCacheTest {
    @Test
    fun cacheHitAfterNewEngineDoesNotCallNetwork() {
        val cache = FakePluginCache()
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
        val cache = FakePluginCache()
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

        val entryKey = cache.values.keys.first { it.startsWith("cache.entry.v3.") }
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
        val cache = FakePluginCache()
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
    fun cacheExtensionClearsOnlyItsRequestedEntriesAndNeverPluginStorage() {
        val cacheStore = FakePluginCache()
        val pluginStorage = mutableMapOf("setting" to "must-stay")
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)
        var cancellationCount = 0
        val first = "a".repeat(64)
        val second = "b".repeat(64)
        cache.put(first, listOf(TranslationItem(0, "first")), song().copy(name = "first song"))
        cache.put(second, listOf(TranslationItem(0, "second")), song().copy(name = "second song"))
        val extension = AiTranslationCacheExtension(cache) { cancellationCount++ }

        assertTrue(extension.clearEntry(first))
        assertFalse(extension.clearEntry(first))
        assertEquals(2, cancellationCount)
        assertEquals(listOf(second), extension.listEntries().map { it.id })
        assertEquals("must-stay", pluginStorage["setting"])
        cacheStore.values["cache.entry.v3.${"c".repeat(64)}"] = "orphaned translation body"

        extension.clearAll()

        assertEquals(3, cancellationCount)
        assertTrue(extension.listEntries().isEmpty())
        assertTrue(cacheStore.values.isEmpty())
        assertEquals("must-stay", pluginStorage["setting"])
    }

    @Test
    fun damagedCacheIndexIsSafelyRebuiltForLaterWrites() {
        val cacheStore = FakePluginCache()
        cacheStore.values["cache.index.v3"] = "{broken"
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)

        assertTrue(cache.listEntries().isEmpty())
        cache.put("c".repeat(64), listOf(TranslationItem(0, "translated")), song())

        assertEquals(1, cache.listEntries().size)
    }

    @Test
    fun clearEntryReportsFailureWhenDeletingTheCacheBodyFails() {
        val cacheStore = FakePluginCache()
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)
        val key = "d".repeat(64)
        cache.put(key, listOf(TranslationItem(0, "translated")), song())
        cacheStore.failRemoveKey = "cache.entry.v3.$key"

        assertFalse(cache.clearEntry(key))
    }

    @Test
    fun clearEntryReportsFailureWhenWritingTheIndexFails() {
        val cacheStore = FakePluginCache()
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)
        val key = "e".repeat(64)
        cache.put(key, listOf(TranslationItem(0, "translated")), song())
        cacheStore.failIndexWrite = true

        assertFalse(cache.clearEntry(key))
    }

    @Test
    fun clearAllFailureIsPropagatedToTheRuntime() {
        val cacheStore = FakePluginCache().apply { failClear = true }
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)

        assertTrue(runCatching { cache.clearAll() }.isFailure)
    }

    @Test
    fun clearPreventsAnOlderTranslationTaskFromWritingBack() {
        val cacheStore = FakePluginCache()
        val cache = TranslationCache(cacheStore, NO_OP_LOGGER)
        val key = "f".repeat(64)
        val generationBeforeClear = cache.currentGeneration()

        cache.clearAll()
        cache.put(
            key = key,
            items = listOf(TranslationItem(0, "stale")),
            song = song(),
            expectedGeneration = generationBeforeClear
        )

        assertTrue(cache.listEntries().isEmpty())
    }

    private fun song(): PluginSong = PluginSong(
        name = "title",
        artist = "artist",
        album = "album",
        duration = 180_000L,
        lyrics = listOf(
            PluginLyricLine(
                begin = 0L,
                end = 1_000L,
                duration = 1_000L,
                text = "original lyric content"
            ),
            PluginLyricLine(
                begin = 1_000L,
                end = 2_000L,
                duration = 1_000L,
                text = "second lyric content"
            ),
            PluginLyricLine(
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

    private class FakePluginCache : PluginCache {
        val values = linkedMapOf<String, String>()
        var failRemoveKey: String? = null
        var failIndexWrite: Boolean = false
        var failClear: Boolean = false

        override fun getString(key: String): String? = values[key]

        override fun putString(key: String, value: String) {
            if (failIndexWrite && key == "cache.index.v3") {
                error("index write failure")
            }
            values[key] = value
        }

        override fun getBytes(key: String): ByteArray? = values[key]?.toByteArray()

        override fun putBytes(key: String, value: ByteArray) {
            values[key] = value.decodeToString()
        }

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun remove(key: String) {
            if (failRemoveKey == key) error("cache delete failure")
            values.remove(key)
        }

        override fun clear() {
            if (failClear) error("cache clear failure")
            values.clear()
        }
    }

    private companion object {
        val NO_OP_LOGGER = object : PluginLogger {
            override fun debug(message: String) = Unit
            override fun info(message: String) = Unit
            override fun warn(message: String, throwable: Throwable?) = Unit
            override fun error(message: String, throwable: Throwable?) = Unit
        }
    }
}
