package com.lidesheng.hyperlyric.root.lyricenhancement.amll

import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlCacheTest {
    @Test
    fun cacheEntriesExposeSongMetadataAndCanBeRemoved() {
        val storage = FakeCache()
        val cache = TtmlCache(storage, NO_OP_LOGGER)
        val semanticKey = TtmlCache.searchKey("title", "artist")

        cache.put(
            semanticKey = semanticKey,
            ttml = "<ttml />",
            title = "title",
            artist = "artist"
        )

        val entry = cache.listEntries().single()
        assertEquals("title", entry.title)
        assertEquals("artist", entry.artist)
        assertTrue(cache.clearEntry(entry.id))
        assertTrue(cache.listEntries().isEmpty())
    }

    @Test
    fun oldBodyGetsMetadataWhenItIsHitAgain() {
        val storage = FakeCache()
        val cache = TtmlCache(storage, NO_OP_LOGGER)
        val semanticKey = TtmlCache.searchKey("title", "artist")
        val physicalKey = cache.physicalKeyOf(semanticKey)
        storage.values["ttml.entry.v1.$physicalKey"] = "<ttml />"

        cache.get(semanticKey, title = "title", artist = "artist")

        val entry = cache.listEntries().single()
        assertEquals("title", entry.title)
        assertEquals("artist", entry.artist)
    }

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
