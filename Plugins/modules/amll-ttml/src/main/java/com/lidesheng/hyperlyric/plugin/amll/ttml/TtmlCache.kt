package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginCacheEntry
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

/**
 * TTML 缓存（参照 AI 翻译插件 TranslationCache 的成熟模式）
 *
 * - 语义 key（可读，用于日志）经 SHA-256 映射为物理 key，规避宿主 PluginCache
 *   256 字符 key 上限（title/artist 可能超长）；
 * - 内存 LRU + 持久索引（JSON，最近使用在前），容量 200 条，LRU 淘汰含存储删除；
 *   缓存管理（PluginCacheExtension）最多列出 100 条；
 * - 索引格式 v2：记录 title/artist/size/updatedAt 展示元数据（缓存正文与
 *   API Key 绝不跨 PluginCacheEntry 边界）；旧 v1 纯物理 key 数组索引在首次
 *   读取时懒迁移为 v2（已缓存条目保留，无元数据条目以兜底标题展示）；
 * - schema 版本进语义 key（v1），解析逻辑升级时递增即可整体失效；
 * - 永不过期（AMLL 官方承诺 id 检索结果永久不变，对齐 main 分支语义）；
 *   未命中不缓存（负缓存会导致 AMLL 库新增条目后永远搜不到）；
 * - 损坏自愈：索引/条目解析失败 → 删除对应条目并回退网络路径；
 * - 值为 TTML 原文字符串；超过宿主单值上限（2MB）的异常大 TTML 放弃缓存仅本次使用；
 * - generation 机制：clearAll/clearEntry 递增代次，处理过程中捕获的过期代次
 *   写入被丢弃（宿主清理前虽已取消进行中的处理，此处兜底防残留写入）；
 * - resolve 条目（songId → 命中平台名）不进索引、仅存储：列表不展示，
 *   clearAll 随 storage.clear() 一并移除；清理后残留仅影响探测快路径，无正确性影响。
 */
internal class TtmlCache(
    private val storage: PluginCache,
    private val logger: PluginLogger,
) {
    companion object {
        /** 语义 key 恒定前缀（含 schema 版本）；解析逻辑升级时递增即可整体失效 */
        private const val SCHEMA_PREFIX = "amll.ttml.v1"

        private const val MAX_ENTRIES = 200

        /** 缓存管理列表的最大返回条数（对齐 AI 翻译插件） */
        private const val MAX_LIST_ENTRIES = 100

        /** 宿主 PluginCache 单值上限（SharedPreferencesPluginCache.MAX_VALUE_BYTES） */
        private const val MAX_VALUE_BYTES = 2 * 1024 * 1024

        /** 索引 key：v2 含展示元数据；v1 为纯物理 key 数组（懒迁移源） */
        private const val INDEX_KEY = "cache.index.v2"
        private const val LEGACY_INDEX_KEY = "cache.index.v1"
        private const val INDEX_VERSION = 2
        private const val ENTRY_PREFIX = "cache.entry.v1."
        private val KEY_PATTERN = Regex("[0-9a-f]{64}")

        /** 精确命中（平台探测）的语义 key */
        fun exactKey(platform: AmllPlatformIdField, songId: String): String =
            "$SCHEMA_PREFIX|exact|${platform.name}|$songId"

        /** 搜索命中的语义 key（title/artist 归一化保证同曲稳定） */
        fun searchKey(title: String, artist: String): String {
            val normalizedTitle = title.trim().replace(Regex("\\s+"), " ")
            val normalizedArtist = artist.trim().replace(Regex("\\s+"), " ")
            return "$SCHEMA_PREFIX|search|$normalizedTitle|$normalizedArtist"
        }

        /** 平台探测结果（songId → 命中平台名）的语义 key：二次播放跳过逐平台探测 */
        fun resolveKey(songId: String): String = "$SCHEMA_PREFIX|resolve|$songId"

        /** 日志展示用短 key：去掉恒定前缀（如 exact|NCM|551339078） */
        fun shortKey(semanticKey: String): String =
            semanticKey.removePrefix("$SCHEMA_PREFIX|")
    }

    private val lock = Any()

    /** 内存 LRU：物理 key → TTML 原文（物理 key 与持久索引共用同一标识） */
    private val memory: MutableMap<String, String> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(16, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, String>?
                ): Boolean = size > MAX_ENTRIES
            }
        )

    /** 清理代次：clearAll/clearEntry 递增；put 校验防止过期代次写回 */
    private var generation: Long = 0L

    data class CacheLookup(
        val ttml: String,
        val fromMemory: Boolean,
    )

    fun currentGeneration(): Long = synchronized(lock) { generation }

    /** 语义 key → 物理 key（SHA-256 十六进制，64 字符） */
    fun physicalKeyOf(semanticKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(semanticKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    fun get(semanticKey: String): CacheLookup? = synchronized(lock) {
        val physicalKey = physicalKeyOf(semanticKey)
        memory[physicalKey]?.let { return@synchronized CacheLookup(it, fromMemory = true) }

        val index = readIndexLocked()
        if (index.none { it.key == physicalKey }) return@synchronized null

        val raw = runCatching { storage.getString(entryKey(physicalKey)) }.getOrElse {
            logger.warn("读取缓存失败: key=${shortKey(semanticKey)}", it)
            return@synchronized null
        } ?: run {
            // 索引命中但条目缺失：损坏条目，自愈删除
            removeEntryLocked(index, physicalKey)
            return@synchronized null
        }

        memory[physicalKey] = raw
        touchIndexLocked(index, physicalKey)
        CacheLookup(raw, fromMemory = false)
    }

    fun put(
        semanticKey: String,
        ttml: String,
        title: String?,
        artist: String?,
        expectedGeneration: Long = currentGeneration(),
    ) {
        if (ttml.isEmpty()) return
        val bytes = ttml.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_VALUE_BYTES) {
            logger.warn("超出缓存单值上限，放弃缓存: size=${bytes.size}B", null)
            return
        }
        val physicalKey = physicalKeyOf(semanticKey)
        synchronized(lock) {
            if (generation != expectedGeneration) {
                logger.debug("缓存已清理，丢弃过期写入: key=${shortKey(semanticKey)}")
                return
            }
            runCatching { storage.putString(entryKey(physicalKey), ttml) }.onFailure {
                logger.warn("写入缓存失败: key=${shortKey(semanticKey)}", it)
                return
            }

            memory[physicalKey] = ttml
            val record = CacheRecord(
                key = physicalKey,
                title = title?.takeIf { it.isNotBlank() } ?: "未知歌曲",
                artist = artist?.takeIf { it.isNotBlank() },
                updatedAtEpochMs = System.currentTimeMillis(),
                sizeBytes = bytes.size.toLong()
            )
            val updated = readIndexLocked().toMutableList().apply {
                removeAll { it.key == physicalKey }
                add(0, record)
                while (size > MAX_ENTRIES) {
                    val removed = removeAt(lastIndex)
                    memory.remove(removed.key)
                    runCatching { storage.remove(entryKey(removed.key)) }.onFailure {
                        logger.warn("删除缓存条目失败: key=${removed.key}", it)
                    }
                }
            }
            writeIndexLocked(updated)
        }
    }

    /** 读取 resolve 条目（songId → 命中平台名）；不进索引故不走通用 [get] 路径 */
    fun getResolve(songId: String): String? {
        return runCatching {
            storage.getString(entryKey(physicalKeyOf(resolveKey(songId))))
        }.getOrElse {
            logger.warn("读取平台探测缓存失败: songId=$songId", it)
            null
        }?.takeIf { it.isNotBlank() }
    }

    /** 写入 resolve 条目：仅存储不进索引（清理后残留仅影响探测快路径，无正确性影响） */
    fun putResolve(songId: String, platformName: String) {
        runCatching { storage.putString(entryKey(physicalKeyOf(resolveKey(songId))), platformName) }
            .onFailure { logger.warn("写入平台探测缓存失败: songId=$songId", it) }
    }

    /** 缓存管理：列出当前索引条目（仅展示元数据，缓存正文不出边界） */
    fun listEntries(): List<PluginCacheEntry> = synchronized(lock) {
        readIndexLocked().asSequence()
            .filter { storage.contains(entryKey(it.key)) }
            .take(MAX_LIST_ENTRIES)
            .map { record ->
                PluginCacheEntry(
                    id = record.key,
                    title = record.title,
                    summary = record.artist,
                    sizeBytes = record.sizeBytes,
                    updatedAtEpochMs = record.updatedAtEpochMs
                )
            }
            .toList()
    }

    /** 缓存管理：清空本作用域（含索引、条目、resolve 遗留），不动 PluginConfig/PluginStorage */
    fun clearAll() {
        synchronized(lock) {
            generation++
            memory.clear()
            // 单一缓存作用域：直接清空 PluginCache，损坏索引无法枚举的孤儿条目一并移除
            runCatching { storage.clear() }.getOrElse { error ->
                logger.error("清空缓存失败", error)
                throw IllegalStateException("无法清空 TTML 缓存", error)
            }
        }
    }

    /** 缓存管理：按物理 key 删除单条；返回条目是否存在 */
    fun clearEntry(entryId: String): Boolean = synchronized(lock) {
        generation++
        if (!KEY_PATTERN.matches(entryId)) return@synchronized false
        val index = readIndexLocked()
        if (index.none { it.key == entryId }) return@synchronized false
        memory.remove(entryId)
        removeEntryLocked(index, entryId)
    }

    fun remove(semanticKey: String) = synchronized(lock) {
        val physicalKey = physicalKeyOf(semanticKey)
        memory.remove(physicalKey)
        removeEntryLocked(readIndexLocked(), physicalKey)
    }

    /** get 持久层命中后把记录移到索引头部（LRU 顺序），元数据保持不变 */
    private fun touchIndexLocked(index: List<CacheRecord>, key: String) {
        val record = index.firstOrNull { it.key == key } ?: return
        val updated = index.toMutableList().apply {
            removeAll { it.key == key }
            add(0, record)
        }
        writeIndexLocked(updated)
    }

    private fun readIndexLocked(): List<CacheRecord> {
        migrateLegacyIndexLocked()
        val raw = runCatching { storage.getString(INDEX_KEY) }.getOrElse {
            logger.warn("读取缓存索引失败", it)
            return emptyList()
        } ?: return emptyList()
        return runCatching {
            val json = JSONObject(raw)
            require(json.optInt("version") == INDEX_VERSION) { "Unsupported TTML cache index" }
            val entries = json.optJSONArray("entries") ?: JSONArray()
            buildList(entries.length()) {
                for (index in 0 until entries.length()) {
                    decodeRecord(entries.optJSONObject(index))?.let(::add)
                }
            }.distinctBy { it.key }.take(MAX_ENTRIES)
        }.getOrElse {
            // 索引损坏：清空重建（条目成为孤儿，LRU 淘汰/自愈路径最终清理）
            logger.warn("缓存索引损坏，重建索引", it)
            runCatching { storage.remove(INDEX_KEY) }
            emptyList()
        }
    }

    /**
     * v1 索引（纯物理 key 数组）懒迁移为 v2：已缓存条目保留（列表以兜底标题展示），
     * v1 索引删除。v2 已存在（上次迁移后删除 v1 失败/中断）时仅清理 v1 遗留，
     * 不覆盖 v2（中断点之后的新写入均在 v2，无丢失）。
     */
    private fun migrateLegacyIndexLocked() {
        val legacy = runCatching { storage.getString(LEGACY_INDEX_KEY) }.getOrNull() ?: return
        if (runCatching { storage.getString(INDEX_KEY) }.getOrNull() != null) {
            runCatching { storage.remove(LEGACY_INDEX_KEY) }
            return
        }
        val keys = runCatching {
            val array = JSONArray(legacy)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optString(index, "").takeIf(KEY_PATTERN::matches)?.let(::add)
                }
            }
        }.getOrNull()
        if (keys == null) {
            runCatching { storage.remove(LEGACY_INDEX_KEY) }
            return
        }
        val now = System.currentTimeMillis()
        val records = keys.map { key ->
            CacheRecord(
                key = key,
                title = "已缓存歌词",
                artist = null,
                updatedAtEpochMs = now,
                sizeBytes = null
            )
        }
        runCatching { storage.putString(INDEX_KEY, encodeIndex(records)) }
        runCatching { storage.remove(LEGACY_INDEX_KEY) }
    }

    private fun decodeRecord(json: JSONObject?): CacheRecord? {
        val key = json?.optString("key", "")?.takeIf(KEY_PATTERN::matches) ?: return null
        val title = json.optString("title", "").trim().takeIf { it.isNotBlank() } ?: return null
        return CacheRecord(
            key = key,
            title = title,
            artist = json.optString("artist", "").trim().takeIf { it.isNotBlank() },
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
        }.onFailure {
            logger.warn("删除缓存条目失败: key=$key", it)
        }.getOrDefault(false)
        val indexWritten = if (updated.size != index.size) writeIndexLocked(updated) else true
        return entryRemoved && indexWritten
    }

    private fun writeIndexLocked(index: List<CacheRecord>): Boolean = runCatching {
        storage.putString(INDEX_KEY, encodeIndex(index))
        true
    }.onFailure {
        logger.warn("写入缓存索引失败", it)
    }.getOrDefault(false)

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
                            record.sizeBytes?.let { item.put("sizeBytes", it) }
                        }
                )
            }
        }).toString()

    private fun entryKey(physicalKey: String): String = ENTRY_PREFIX + physicalKey

    private data class CacheRecord(
        val key: String,
        val title: String,
        val artist: String?,
        val updatedAtEpochMs: Long,
        val sizeBytes: Long?,
    )
}
