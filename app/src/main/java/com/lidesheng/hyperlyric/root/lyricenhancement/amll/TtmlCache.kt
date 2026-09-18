package com.lidesheng.hyperlyric.root.lyricenhancement.amll

import com.lidesheng.hyperlyric.common.LyricEnhancementCacheDetail
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

/**
 * AMLL TTML 缓存。
 *
 * TTML 正文仍只保存在 SystemUI。索引保存缓存管理页面需要的歌名、歌手、大小、时间和
 * TTML 元数据；resolve 条目继续保持内部实现细节，不出现在列表中。
 */
internal class TtmlCache(
    private val storage: LyricEnhancementCacheStore,
    private val logger: LyricEnhancementLogger,
) {
    companion object {
        private const val SCHEMA_PREFIX = "amll.ttml.v2"
        private const val ENTRY_PREFIX = "ttml.entry.v1."
        private const val INDEX_KEY = "ttml.index.v1"
        private const val MAX_MEMORY_ENTRIES = 256
        private const val MAX_INDEX_ENTRIES = 256
        private const val MAX_LIST_ENTRIES = 100
        private const val INDEX_VERSION = 1
        private val PHYSICAL_KEY_PATTERN = Regex("[0-9a-f]{64}")
        private const val UNKNOWN_TITLE = "未知歌曲"

        fun exactKey(platform: AmllPlatformIdField, songId: String): String =
            SCHEMA_PREFIX + "|exact|" + platform.name + "|" + songId

        fun searchKey(title: String, artist: String): String {
            val normalizedTitle = title.trim().replace(Regex("\\s+"), " ")
            val normalizedArtist = artist.trim().replace(Regex("\\s+"), " ")
            return SCHEMA_PREFIX + "|search|" + normalizedTitle + "|" + normalizedArtist
        }

        fun resolveKey(songId: String): String =
            SCHEMA_PREFIX + "|resolve|" + songId

        fun shortKey(semanticKey: String): String =
            semanticKey.removePrefix(SCHEMA_PREFIX + "|")
    }

    private val lock = Any()
    private val memory = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, String>?
            ): Boolean = size > MAX_MEMORY_ENTRIES
        }
    )
    private var generation = 0L

    data class CacheLookup(
        val ttml: String,
        val fromMemory: Boolean,
    )

    fun currentGeneration(): Long = synchronized(lock) { generation }

    fun get(
        semanticKey: String,
        title: String? = null,
        artist: String? = null,
    ): CacheLookup? = synchronized(lock) {
        val physicalKey = physicalKeyOf(semanticKey)
        memory[physicalKey]?.let {
            rememberHitMetadataLocked(physicalKey, title, artist)
            return@synchronized CacheLookup(it, true)
        }
        val raw = runCatching {
            storage.getString(entryKey(physicalKey))
        }.getOrElse {
            logger.warn("读取缓存失败: key=" + shortKey(semanticKey), it)
            return@synchronized null
        }?.takeIf { it.isNotEmpty() } ?: return@synchronized null
        memory[physicalKey] = raw
        rememberHitMetadataLocked(physicalKey, title, artist)
        CacheLookup(raw, false)
    }

    fun put(
        semanticKey: String,
        ttml: String,
        expectedGeneration: Long = currentGeneration(),
        title: String? = null,
        artist: String? = null,
        details: List<LyricEnhancementCacheDetail> = emptyList(),
    ) {
        if (ttml.isEmpty()) return
        synchronized(lock) {
            if (generation != expectedGeneration) {
                logger.debug("缓存已清理，丢弃过期写入: key=" + shortKey(semanticKey))
                return
            }
            val physicalKey = physicalKeyOf(semanticKey)
            runCatching { storage.putString(entryKey(physicalKey), ttml) }.onFailure {
                logger.warn("写入缓存失败: key=" + shortKey(semanticKey), it)
                return
            }
            memory[physicalKey] = ttml

            val record = CacheRecord(
                key = physicalKey,
                title = title.orEmpty().ifBlank { UNKNOWN_TITLE },
                artist = artist.orEmpty().ifBlank { null },
                sizeBytes = ttml.toByteArray(Charsets.UTF_8).size.toLong(),
                updatedAtEpochMs = System.currentTimeMillis(),
                details = sanitizeDetails(details)
            )
            val updated = readIndexLocked().toMutableList().apply {
                removeAll { it.key == physicalKey }
                add(0, record)
                while (size > MAX_INDEX_ENTRIES) {
                    val removed = removeAt(lastIndex)
                    memory.remove(removed.key)
                    runCatching { storage.remove(entryKey(removed.key)) }.onFailure {
                        logger.warn("删除超量 TTML 缓存失败", it)
                    }
                }
            }
            writeIndexLocked(updated)
        }
    }

    fun getResolve(songId: String): String? = synchronized(lock) {
        runCatching {
            storage.getString(entryKey(physicalKeyOf(resolveKey(songId))))
        }.getOrElse {
            logger.warn("读取平台探测缓存失败: songId=" + songId, it)
            null
        }?.takeIf { it.isNotBlank() }
    }

    fun putResolve(
        songId: String,
        platformName: String,
        expectedGeneration: Long = currentGeneration(),
    ) = synchronized(lock) {
        if (generation != expectedGeneration) {
            logger.debug("缓存已清理，丢弃过期平台探测写入: songId=$songId")
            return@synchronized
        }
        runCatching {
            storage.putString(entryKey(physicalKeyOf(resolveKey(songId))), platformName)
        }.onFailure {
            logger.warn("写入平台探测缓存失败: songId=" + songId, it)
        }
    }

    /** Internal semantic-key removal used only when a cache body is found to be invalid. */
    fun remove(semanticKey: String) = synchronized(lock) {
        generation++
        val physicalKey = physicalKeyOf(semanticKey)
        memory.remove(physicalKey)
        storage.remove(entryKey(physicalKey))
        writeIndexLocked(readIndexLocked().filterNot { it.key == physicalKey })
    }

    fun listEntries(): List<LyricEnhancementCacheEntry> = synchronized(lock) {
        readIndexLocked().asSequence()
            .take(MAX_LIST_ENTRIES)
            .mapNotNull { record ->
                val body = runCatching { storage.getString(entryKey(record.key)) }
                    .onFailure { logger.warn("读取 TTML 缓存条目失败", it) }
                    .getOrNull() ?: return@mapNotNull null
                record to body
            }
            .map { (record, body) ->
                LyricEnhancementCacheEntry(
                    id = record.key,
                    title = record.title,
                    artist = record.artist,
                    sizeBytes = record.sizeBytes
                        ?: body.toByteArray(Charsets.UTF_8).size.toLong(),
                    updatedAtEpochMs = record.updatedAtEpochMs,
                    details = record.details
                )
            }
            .toList()
    }

    fun clearEntry(entryId: String): Boolean = synchronized(lock) {
        if (!PHYSICAL_KEY_PATTERN.matches(entryId)) return@synchronized false
        val index = readIndexLocked()
        if (index.none { it.key == entryId }) return@synchronized false

        generation++
        memory.remove(entryId)
        val removed = runCatching {
            storage.remove(entryKey(entryId))
            true
        }.onFailure { error ->
            logger.warn("删除 TTML 缓存失败", error)
        }.getOrDefault(false)
        val indexWritten = writeIndexLocked(index.filterNot { it.key == entryId })
        removed && indexWritten
    }

    fun clear(): Boolean = synchronized(lock) {
        generation++
        memory.clear()
        return storage.clear()
    }

    fun physicalKeyOf(semanticKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(semanticKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun readIndexLocked(): List<CacheRecord> {
        val raw = runCatching { storage.getString(INDEX_KEY) }.getOrElse {
            logger.warn("读取 TTML 缓存索引失败", it)
            null
        } ?: return emptyList()
        return runCatching {
            val json = JSONObject(raw)
            require(json.optInt("version") == INDEX_VERSION) {
                "Unsupported TTML cache index"
            }
            val entries = json.optJSONArray("entries") ?: JSONArray()
            buildList(entries.length()) {
                for (index in 0 until entries.length()) {
                    decodeRecord(entries.optJSONObject(index))?.let(::add)
                }
            }.distinctBy { it.key }.take(MAX_INDEX_ENTRIES)
        }.getOrElse { error ->
            logger.warn("TTML 缓存索引损坏，按空列表处理", error)
            storage.remove(INDEX_KEY)
            emptyList()
        }
    }

    private fun decodeRecord(json: JSONObject?): CacheRecord? {
        val key = json?.optString("key", "")?.takeIf(PHYSICAL_KEY_PATTERN::matches)
            ?: return null
        return CacheRecord(
            key = key,
            title = json.optString("title", "").trim().ifBlank { UNKNOWN_TITLE },
            artist = json.optString("artist", "").trim().takeIf { it.isNotBlank() },
            sizeBytes = json.optLong("sizeBytes", -1L).takeIf { it >= 0L },
            updatedAtEpochMs = json.optLong("updatedAtEpochMs", -1L)
                .takeIf { it > 0L },
            details = decodeDetails(json.optJSONArray("details"))
        )
    }

    private fun writeIndexLocked(index: List<CacheRecord>): Boolean = runCatching {
        storage.putString(INDEX_KEY, encodeIndex(index.take(MAX_INDEX_ENTRIES)))
        true
    }.onFailure { error ->
        logger.warn("写入 TTML 缓存索引失败", error)
    }.getOrDefault(false)

    private fun rememberHitMetadataLocked(
        physicalKey: String,
        title: String?,
        artist: String?,
    ) {
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return
        val index = readIndexLocked()
        val existing = index.firstOrNull { it.key == physicalKey }
        val record = CacheRecord(
            key = physicalKey,
            title = title.orEmpty().ifBlank { existing?.title ?: UNKNOWN_TITLE },
            artist = artist.orEmpty().ifBlank { existing?.artist },
            sizeBytes = existing?.sizeBytes,
            updatedAtEpochMs = existing?.updatedAtEpochMs,
            details = existing?.details.orEmpty()
        )
        if (existing?.title == record.title && existing.artist == record.artist) return
        writeIndexLocked(index.filterNot { it.key == physicalKey }.let { listOf(record) + it })
    }

    private fun encodeIndex(records: List<CacheRecord>): String = JSONObject()
        .put("version", INDEX_VERSION)
        .put("entries", JSONArray().apply {
            records.forEach { record ->
                put(
                    JSONObject()
                        .put("key", record.key)
                        .put("title", record.title)
                        .also { item -> record.artist?.let { item.put("artist", it) } }
                        .also { item -> record.sizeBytes?.let { item.put("sizeBytes", it) } }
                        .also { item ->
                            record.updatedAtEpochMs?.let {
                                item.put("updatedAtEpochMs", it)
                            }
                        }
                        .also { item ->
                            if (record.details.isNotEmpty()) {
                                item.put("details", JSONArray().apply {
                                    record.details.forEach { detail ->
                                        put(
                                            JSONObject()
                                                .put("label", detail.label)
                                                .put("value", detail.value)
                                        )
                                    }
                                })
                            }
                        }
                )
            }
        })
        .toString()

    private fun decodeDetails(array: JSONArray?): List<LyricEnhancementCacheDetail> {
        if (array == null) return emptyList()
        return sanitizeDetails(buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString("label", "").trim()
                val value = item.optString("value", "").trim()
                if (label.isNotBlank() && value.isNotBlank()) {
                    add(LyricEnhancementCacheDetail(label, value))
                }
            }
        })
    }

    private fun sanitizeDetails(
        details: List<LyricEnhancementCacheDetail>,
    ): List<LyricEnhancementCacheDetail> = details.asSequence()
        .mapNotNull { detail ->
            val label = detail.label.trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val value = detail.value.trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            LyricEnhancementCacheDetail(
                label = label.take(32),
                value = value.take(120)
            )
        }
        .take(10)
        .toList()

    private fun entryKey(physicalKey: String): String = ENTRY_PREFIX + physicalKey

    private data class CacheRecord(
        val key: String,
        val title: String,
        val artist: String?,
        val sizeBytes: Long? = null,
        val updatedAtEpochMs: Long? = null,
        val details: List<LyricEnhancementCacheDetail> = emptyList(),
    )

}
