package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/**
 * AI 翻译缓存。
 *
 * 翻译正文仍只由 SystemUI 持有；索引只保存缓存管理页面需要的歌名和歌手。索引格式升级
 * 时保留旧的 v1 key 数组读取能力，旧条目在没有元数据的情况下以“未知歌曲”展示。
 */
internal class TranslationCache(
    private val storage: LyricEnhancementCacheStore,
    private val logger: LyricEnhancementLogger,
) {
    private companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_LIST_ENTRIES = 100
        const val INDEX_KEY = "cache.index.v2"
        const val LEGACY_INDEX_KEY = "cache.index.v1"
        const val ENTRY_PREFIX = "cache.entry.v1."
        const val INDEX_VERSION = 2
        val KEY_PATTERN = Regex("[0-9a-f]{64}")
        const val UNKNOWN_TITLE = "未知歌曲"
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

    fun get(
        key: String,
        title: String? = null,
        artist: String? = null,
    ): CacheLookup? = synchronized(lock) {
        memory[key]?.let {
            rememberHitMetadataLocked(key, title, artist)
            return@synchronized CacheLookup(it, fromMemory = true)
        }
        val raw = runCatching { storage.getString(entryKey(key)) }.getOrElse {
            logger.warn("读取翻译缓存失败", it)
            return@synchronized null
        } ?: return@synchronized null
        val items = decode(raw)
        if (items.isNullOrEmpty()) {
            logger.warn("翻译缓存内容损坏，已删除")
            storage.remove(entryKey(key))
            return@synchronized null
        }
        memory[key] = items
        rememberHitMetadataLocked(key, title, artist)
        CacheLookup(items, fromMemory = false)
    }

    fun put(
        key: String,
        items: List<TranslationItem>,
        expectedGeneration: Long = currentGeneration(),
        title: String? = null,
        artist: String? = null,
    ) {
        if (items.isEmpty()) return
        synchronized(lock) {
            if (generation != expectedGeneration) {
                logger.debug("缓存已清理，丢弃过期翻译结果")
                return
            }
            val encoded = encode(items)
            runCatching { storage.putString(entryKey(key), encoded) }.onFailure {
                logger.warn("写入翻译缓存失败", it)
                return
            }
            memory[key] = items
            val record = CacheRecord(
                key = key,
                title = title.orEmpty().ifBlank { UNKNOWN_TITLE },
                artist = artist.orEmpty().ifBlank { null }
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

    /** Internal invalid-cache cleanup; unlike page deletion it accepts a semantic cache key. */
    fun remove(key: String) = synchronized(lock) {
        generation++
        memory.remove(key)
        storage.remove(entryKey(key))
        writeIndexLocked(readIndexLocked().filterNot { it.key == key })
    }

    fun listEntries(): List<LyricEnhancementCacheEntry> = synchronized(lock) {
        readIndexLocked().asSequence()
            .take(MAX_LIST_ENTRIES)
            .filter { record ->
                runCatching { storage.getString(entryKey(record.key)) }
                    .onFailure { logger.warn("读取翻译缓存条目失败", it) }
                    .getOrNull() != null
            }
            .map { record ->
                LyricEnhancementCacheEntry(
                    id = record.key,
                    title = record.title,
                    artist = record.artist
                )
            }
            .toList()
    }

    fun clearEntry(entryId: String): Boolean = synchronized(lock) {
        if (!KEY_PATTERN.matches(entryId)) return@synchronized false
        val index = readIndexLocked()
        if (index.none { it.key == entryId }) return@synchronized false

        generation++
        memory.remove(entryId)
        val removed = runCatching {
            storage.remove(entryKey(entryId))
            true
        }.onFailure { error ->
            logger.warn("删除翻译缓存失败", error)
        }.getOrDefault(false)
        val indexWritten = writeIndexLocked(index.filterNot { it.key == entryId })
        removed && indexWritten
    }

    fun clear(): Boolean = synchronized(lock) {
        generation++
        memory.clear()
        return storage.clear()
    }

    private fun readIndexLocked(): List<CacheRecord> {
        val current = runCatching { storage.getString(INDEX_KEY) }.getOrElse {
            logger.warn("读取翻译缓存索引失败", it)
            null
        }
        if (current != null) return parseCurrentIndex(current)

        val legacy = runCatching { storage.getString(LEGACY_INDEX_KEY) }.getOrElse {
            logger.warn("读取旧翻译缓存索引失败", it)
            null
        } ?: return emptyList()
        return parseLegacyIndex(legacy)
    }

    private fun parseCurrentIndex(raw: String): List<CacheRecord> = runCatching {
        val json = JSONObject(raw)
        require(json.optInt("version") == INDEX_VERSION) {
            "Unsupported translation cache index"
        }
        val entries = json.optJSONArray("entries") ?: JSONArray()
        buildList(entries.length()) {
            for (index in 0 until entries.length()) {
                decodeRecord(entries.optJSONObject(index))?.let(::add)
            }
        }.distinctBy { it.key }.take(MAX_ENTRIES)
    }.getOrElse { error ->
        logger.warn("翻译缓存索引损坏，按空列表处理", error)
        storage.remove(INDEX_KEY)
        emptyList()
    }

    private fun parseLegacyIndex(raw: String): List<CacheRecord> = runCatching {
        val array = JSONArray(raw)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val key = array.optString(index, "")
                if (KEY_PATTERN.matches(key)) {
                    add(CacheRecord(key, UNKNOWN_TITLE, null))
                }
            }
        }.distinctBy { it.key }.take(MAX_ENTRIES)
    }.getOrElse { error ->
        logger.warn("旧翻译缓存索引损坏，按空列表处理", error)
        storage.remove(LEGACY_INDEX_KEY)
        emptyList()
    }

    private fun decodeRecord(json: JSONObject?): CacheRecord? {
        val key = json?.optString("key", "")?.takeIf(KEY_PATTERN::matches) ?: return null
        val title = json.optString("title", "").trim().ifBlank { UNKNOWN_TITLE }
        return CacheRecord(
            key = key,
            title = title,
            artist = json.optString("artist", "").trim().takeIf { it.isNotBlank() }
        )
    }

    private fun writeIndexLocked(index: List<CacheRecord>): Boolean = runCatching {
        storage.putString(INDEX_KEY, encodeIndex(index.take(MAX_ENTRIES)))
        storage.remove(LEGACY_INDEX_KEY)
        true
    }.onFailure { error ->
        logger.warn("写入翻译缓存索引失败", error)
    }.getOrDefault(false)

    private fun rememberHitMetadataLocked(
        key: String,
        title: String?,
        artist: String?,
    ) {
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return
        val index = readIndexLocked()
        val existing = index.firstOrNull { it.key == key }
        val record = CacheRecord(
            key = key,
            title = title.orEmpty().ifBlank { existing?.title ?: UNKNOWN_TITLE },
            artist = artist.orEmpty().ifBlank { existing?.artist }
        )
        if (existing?.title == record.title && existing.artist == record.artist) return
        writeIndexLocked(index.filterNot { it.key == key }.let { listOf(record) + it })
    }

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
                        .also { item ->
                            record.artist?.let { item.put("artist", it) }
                        }
                )
            }
        })
        .toString()

    private fun entryKey(key: String): String = ENTRY_PREFIX + key

    private data class CacheRecord(
        val key: String,
        val title: String,
        val artist: String?,
    )

    data class CacheLookup(
        val items: List<TranslationItem>,
        val fromMemory: Boolean,
    )
}
