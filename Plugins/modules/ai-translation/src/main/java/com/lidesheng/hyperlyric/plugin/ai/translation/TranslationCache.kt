package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginCacheEntry
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * Translation-owned cache with a bounded metadata index. The index never contains translation
 * bodies or configuration secrets, so it can back a PluginCacheExtension safely.
 */
internal class TranslationCache(
    private val storage: PluginCache,
    private val logger: PluginLogger,
) {
    private companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_LIST_ENTRIES = 100
        const val INDEX_KEY = "cache.index.v3"
        const val ENTRY_PREFIX = "cache.entry.v3."
        const val INDEX_VERSION = 3
    }

    private val lock = Any()
    private val memory: MutableMap<String, List<TranslationItem>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, List<TranslationItem>>(MAX_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<TranslationItem>>?
                ): Boolean = size > MAX_ENTRIES
            }
        )
    private var generation: Long = 0L

    fun currentGeneration(): Long = synchronized(lock) { generation }

    /** The body is authoritative for lookup; the index is only an optional listing aid. */
    fun get(key: String): CacheLookup? = synchronized(lock) {
        memory[key]?.let { return@synchronized CacheLookup(it, fromMemory = true) }

        val raw = runCatching { storage.getString(entryKey(key)) }.getOrElse {
            logger.warn("读取翻译缓存失败", it)
            return@synchronized null
        } ?: run {
            return@synchronized null
        }
        val items = decode(raw)
        if (items.isNullOrEmpty()) {
            logger.warn("翻译缓存内容损坏，已删除: key=$key")
            runCatching { storage.remove(entryKey(key)) }
            return@synchronized null
        }

        memory[key] = items
        CacheLookup(items, fromMemory = false)
    }

    fun put(
        key: String,
        items: List<TranslationItem>,
        song: PluginSong,
        expectedGeneration: Long = currentGeneration(),
    ) {
        if (items.isEmpty()) return
        synchronized(lock) {
            if (generation != expectedGeneration) {
                logger.debug("缓存已清理，丢弃过期翻译结果: key=$key")
                return
            }
            val encoded = encode(items)
            runCatching { storage.putString(entryKey(key), encoded) }.onFailure {
                logger.warn("写入翻译缓存失败", it)
                return
            }
            memory[key] = items
            val now = System.currentTimeMillis()
            val record = CacheRecord(
                key = key,
                title = song.name.orEmpty().ifBlank { "未知歌曲" },
                artist = song.artist.orEmpty().ifBlank { null },
                album = song.album.orEmpty().ifBlank { null },
                updatedAtEpochMs = now,
                sizeBytes = encoded.toByteArray(Charsets.UTF_8).size.toLong()
            )
            val updated = readIndexLocked().toMutableList().apply {
                removeAll { it.key == key }
                add(0, record)
                while (size > MAX_ENTRIES) {
                    val removed = removeAt(lastIndex)
                    memory.remove(removed.key)
                    runCatching { storage.remove(entryKey(removed.key)) }.onFailure {
                        logger.warn("删除翻译缓存失败", it)
                    }
                }
            }
            writeIndexLocked(updated)
        }
    }

    fun remove(key: String) = synchronized(lock) {
        memory.remove(key)
        removeEntryLocked(readIndexLocked(), key)
    }

    fun listEntries(): List<PluginCacheEntry> = synchronized(lock) {
        readIndexLocked().asSequence()
            .filter { storage.contains(entryKey(it.key)) }
            .take(MAX_LIST_ENTRIES)
            .map { record ->
                PluginCacheEntry(
                    id = record.key,
                    title = record.title,
                    summary = listOfNotNull(record.artist, record.album)
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() },
                    sizeBytes = record.sizeBytes,
                    updatedAtEpochMs = record.updatedAtEpochMs
                )
            }
            .toList()
    }

    fun clearAll() {
        synchronized(lock) {
            generation++
            memory.clear()
            // This plugin has a single cache scope. Clearing the PluginCache removes orphaned
            // entry bodies too when a damaged index can no longer enumerate their opaque keys.
            runCatching { storage.clear() }.getOrElse { error ->
                logger.error("清空翻译缓存失败", error)
                throw IllegalStateException("无法清空翻译缓存", error)
            }
        }
    }

    fun clearEntry(entryId: String): Boolean = synchronized(lock) {
        generation++
        val index = readIndexLocked()
        if (index.none { it.key == entryId }) return@synchronized false
        memory.remove(entryId)
        removeEntryLocked(index, entryId)
    }

    private fun readIndexLocked(): List<CacheRecord> {
        val raw = runCatching { storage.getString(INDEX_KEY) }.getOrElse {
            logger.warn("读取翻译缓存索引失败", it)
            return emptyList()
        } ?: return emptyList()
        return runCatching {
            val json = JSONObject(raw)
            require(json.optInt("version") == INDEX_VERSION) { "Unsupported translation cache index" }
            val entries = json.optJSONArray("entries") ?: JSONArray()
            buildList(entries.length()) {
                for (index in 0 until entries.length()) {
                    decodeRecord(entries.optJSONObject(index))?.let(::add)
                }
            }.distinctBy { it.key }.take(MAX_ENTRIES)
        }.getOrElse { error ->
            logger.warn("翻译缓存索引损坏，已删除并按空列表处理", error)
            runCatching { storage.remove(INDEX_KEY) }
            emptyList()
        }
    }

    private fun decodeRecord(json: JSONObject?): CacheRecord? {
        val key = json?.optString("key", "")?.takeIf(KEY_PATTERN::matches) ?: return null
        val title = json.optString("title", "").trim().takeIf { it.isNotBlank() } ?: return null
        return CacheRecord(
            key = key,
            title = title,
            artist = json.optString("artist", "").trim().takeIf { it.isNotBlank() },
            album = json.optString("album", "").trim().takeIf { it.isNotBlank() },
            updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L).takeIf { it > 0L }
                ?: return null,
            sizeBytes = json.optLong("sizeBytes", -1L).takeIf { it >= 0L }
        )
    }

    private fun removeEntryLocked(index: List<CacheRecord>, key: String): Boolean {
        val updated = index.filterNot { it.key == key }
        val entryRemoved = runCatching {
            storage.remove(entryKey(key))
            true
        }.onFailure { error ->
            logger.warn("删除翻译缓存失败", error)
        }.getOrDefault(false)
        val indexWritten = if (updated.size != index.size) writeIndexLocked(updated) else true
        return entryRemoved && indexWritten
    }

    private fun writeIndexLocked(index: List<CacheRecord>): Boolean = runCatching {
        storage.putString(INDEX_KEY, encodeIndex(index))
        true
    }.onFailure { error ->
        logger.warn("写入翻译缓存索引失败", error)
    }.getOrDefault(false)

    private fun encode(items: List<TranslationItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().put("index", item.index).put("trans", item.trans))
        }
    }.toString()

    private fun decode(raw: String): List<TranslationItem>? = runCatching {
        val array = JSONArray(raw)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (!item.has("index") || !item.has("trans")) continue
                val text = item.optString("trans", "").trim()
                if (text.isNotBlank()) add(TranslationItem(item.optInt("index"), text))
            }
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun encodeIndex(records: List<CacheRecord>): String = JSONObject()
        .put("version", INDEX_VERSION)
        .put("entries", JSONArray().apply {
            records.forEach { record ->
                put(
                    JSONObject()
                        .put("key", record.key)
                        .put("title", record.title)
                        .put("updatedAtEpochMs", record.updatedAtEpochMs)
                        .also { item ->
                            record.artist?.let { item.put("artist", it) }
                            record.album?.let { item.put("album", it) }
                            record.sizeBytes?.let { item.put("sizeBytes", it) }
                        }
                )
            }
        }).toString()

    private fun entryKey(key: String): String = ENTRY_PREFIX + key

    private data class CacheRecord(
        val key: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val updatedAtEpochMs: Long,
        val sizeBytes: Long?,
    )

    private val KEY_PATTERN = Regex("[0-9a-f]{64}")

    data class CacheLookup(
        val items: List<TranslationItem>,
        val fromMemory: Boolean,
    )
}
