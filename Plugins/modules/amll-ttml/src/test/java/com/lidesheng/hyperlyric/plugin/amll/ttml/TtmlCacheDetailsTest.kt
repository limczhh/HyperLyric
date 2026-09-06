package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginCacheDetail
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlCacheDetailsTest {

    private val logger = object : PluginLogger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }

    private class FakePluginCache : PluginCache {
        val values = linkedMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun getBytes(key: String): ByteArray? = values[key]?.toByteArray()
        override fun putBytes(key: String, value: ByteArray) {
            values[key] = value.decodeToString()
        }

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear() = values.clear()
    }

    private fun newCache(storage: FakePluginCache = FakePluginCache()) =
        TtmlCache(storage, logger) to storage

    private val details = listOf(
        PluginCacheDetail("歌曲名", "No Dazzle, No Break"),
        PluginCacheDetail("歌词作者", "ITManCHINA (50987405)")
    )

    @Test
    fun putWithDetailsRoundTripsThroughListEntries() {
        val (cache, _) = newCache()
        cache.put(
            TtmlCache.exactKey(AmllPlatformIdField.NCM, "2639639291"),
            "<tt/>",
            title = "不乱不破",
            artist = "HOYO-MiX",
            details = details
        )

        val entry = cache.listEntries().single()
        assertEquals("不乱不破", entry.title)
        assertEquals(details, entry.details)
    }

    @Test
    fun putWithoutDetailsKeepsEmptyDetails() {
        val (cache, _) = newCache()
        cache.put(TtmlCache.searchKey("title", "artist"), "<tt/>", title = "t", artist = "a")

        assertTrue(cache.listEntries().single().details.isEmpty())
    }

    @Test
    fun legacyIndexWithoutDetailsReadsAsEmptyDetails() {
        val (cache, storage) = newCache()
        cache.put(
            TtmlCache.exactKey(AmllPlatformIdField.NCM, "2639639291"),
            "<tt/>",
            title = "不乱不破",
            artist = "HOYO-MiX",
            details = details
        )
        // 模拟升级前旧索引：v2 版本号但条目无 details 字段
        val index = JSONObject(storage.values.getValue("cache.index.v2"))
        val entries = index.getJSONArray("entries")
        for (i in 0 until entries.length()) {
            entries.getJSONObject(i).remove("details")
        }
        storage.values["cache.index.v2"] = index.toString()

        val entry = cache.listEntries().single()
        assertEquals("不乱不破", entry.title)
        assertTrue(entry.details.isEmpty())
    }

    @Test
    fun corruptDetailsArrayInIndexIsTolerated() {
        val (cache, storage) = newCache()
        cache.put(TtmlCache.exactKey(AmllPlatformIdField.NCM, "1"), "<tt/>", title = "t", artist = null)
        val index = JSONObject(storage.values.getValue("cache.index.v2"))
        index.getJSONArray("entries").getJSONObject(0).put("details", "not-an-array")
        storage.values["cache.index.v2"] = index.toString()

        val entry = cache.listEntries().single()
        assertTrue(entry.details.isEmpty())
    }

    @Test
    fun blankLabelDetailsAreDroppedOnDecode() {
        val (cache, storage) = newCache()
        cache.put(TtmlCache.exactKey(AmllPlatformIdField.NCM, "1"), "<tt/>", title = "t", artist = null)
        val index = JSONObject(storage.values.getValue("cache.index.v2"))
        index.getJSONArray("entries").getJSONObject(0).put(
            "details",
            JSONArray()
                .put(JSONObject().put("label", "  ").put("value", "blank-label"))
                .put(JSONObject().put("label", "有效").put("value", "kept"))
        )
        storage.values["cache.index.v2"] = index.toString()

        val entry = cache.listEntries().single()
        assertEquals(listOf(PluginCacheDetail("有效", "kept")), entry.details)
    }

    @Test
    fun clearEntryRemovesEntryFromList() {
        val (cache, _) = newCache()
        cache.put(TtmlCache.exactKey(AmllPlatformIdField.NCM, "1"), "<tt/>", title = "t1", artist = null)
        cache.put(TtmlCache.exactKey(AmllPlatformIdField.NCM, "2"), "<tt/>", title = "t2", artist = null)

        val target = cache.listEntries().first { it.title == "t1" }
        assertTrue(cache.clearEntry(target.id))
        assertEquals(listOf("t2"), cache.listEntries().map { it.title })
    }
}
