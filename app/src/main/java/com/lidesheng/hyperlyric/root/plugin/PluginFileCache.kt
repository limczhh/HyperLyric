package com.lidesheng.hyperlyric.root.plugin

import android.content.SharedPreferences
import android.util.AtomicFile
import android.util.Base64
import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import java.io.File
import java.io.FileOutputStream

/**
 * Private SystemUI file backend for potentially large plugin cache bodies.
 *
 * The legacy preferences remain read-only migration input so upgrading does not discard an
 * existing cache. Individual keys migrate lazily when a plugin next reads or writes them.
 */
internal class FilePluginCache(
    private val directory: File,
    private val legacyPreferences: SharedPreferences,
    private val logger: PluginLogger,
) : PluginCache {
    private companion object {
        const val MAX_KEY_LENGTH = 256
        const val MAX_VALUE_BYTES = 2 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 64 * 1024 * 1024
    }

    private val lock = Any()

    init {
        logger.info("插件缓存目录: ${directory.absolutePath}")
    }

    override fun getString(key: String): String? {
        if (!isValidKey(key)) return null
        return synchronized(lock) {
            readFileValue(key) ?: migrateLegacyValue(key)
        }
    }

    override fun putString(key: String, value: String) {
        if (!isValidKey(key) || !isWithinLimit(value.toByteArray(Charsets.UTF_8))) {
            val message = "拒绝超限或非法缓存写入: key=$key"
            logger.warn(message)
            throw IllegalArgumentException(message)
        }
        synchronized(lock) {
            check(writeFileValue(key, value)) { "无法写入插件缓存: key=$key" }
            check(legacyPreferences.edit().remove(key).commit()) {
                "无法移除旧版插件缓存: key=$key"
            }
        }
    }

    override fun getBytes(key: String): ByteArray? {
        val encoded = getString(key) ?: return null
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .map { decoded ->
                if (!isWithinLimit(decoded)) {
                    remove(key)
                    null
                } else {
                    decoded
                }
            }
            .onFailure {
                logger.warn("解析缓存字节失败，删除记录: key=$key", it)
                remove(key)
            }
            .getOrNull()
    }

    override fun putBytes(key: String, value: ByteArray) {
        if (!isValidKey(key) || !isWithinLimit(value)) {
            logger.warn("忽略超限或非法缓存字节写入: key=$key")
            return
        }
        putString(key, Base64.encodeToString(value, Base64.NO_WRAP))
    }

    override fun contains(key: String): Boolean {
        if (!isValidKey(key)) return false
        return synchronized(lock) {
            val file = fileForKey(key)
            hasReadableFile(file) || legacyPreferences.contains(key)
        }
    }

    override fun remove(key: String) {
        if (!isValidKey(key)) return
        synchronized(lock) {
            val file = fileForKey(key)
            deleteFileIfPresent(file)
            deleteFileIfPresent(File(file.path + ".bak"))
            check(legacyPreferences.edit().remove(key).commit()) {
                "无法删除旧版插件缓存: key=$key"
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            directory.listFiles()?.forEach { file ->
                if (file.isFile) deleteFileIfPresent(file)
            }
            check(legacyPreferences.edit().clear().commit()) {
                "无法清空旧版插件缓存"
            }
        }
    }

    private fun readFileValue(key: String): String? {
        val file = fileForKey(key)
        val backup = File(file.path + ".bak")
        if (!file.isFile && !backup.isFile) return null
        return runCatching {
            AtomicFile(file).openRead().use { input ->
                if (!isWithinLimit(file.length())) {
                    error("插件缓存文件超限: key=$key")
                }
                input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            }
        }
            .onFailure {
                logger.warn("读取缓存文件失败: key=$key", it)
                removeFile(file)
            }
            .getOrNull()
    }

    /** Also restores a pending AtomicFile backup before cache metadata asks whether it exists. */
    private fun hasReadableFile(file: File): Boolean {
        val backup = File(file.path + ".bak")
        if (!file.isFile && !backup.isFile) return false
        return runCatching {
            AtomicFile(file).openRead().use { isWithinLimit(file.length()) }
        }.onFailure { error ->
            logger.warn("检查缓存文件失败: file=${file.name}", error)
            removeFile(file)
        }.getOrDefault(false)
    }

    private fun migrateLegacyValue(key: String): String? {
        val legacy = runCatching { legacyPreferences.getString(key, null) }
            .onFailure { logger.warn("读取旧缓存失败: key=$key", it) }
            .getOrNull()
            ?: return null
        if (!isWithinLimit(legacy.toByteArray(Charsets.UTF_8))) {
            legacyPreferences.edit().remove(key).apply()
            return null
        }
        if (writeFileValue(key, legacy)) {
            legacyPreferences.edit().remove(key).apply()
        }
        return legacy
    }

    private fun writeFileValue(key: String, value: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val file = fileForKey(key)
        if (!ensureCapacity(file, bytes.size.toLong())) {
            logger.warn("插件缓存总容量已达上限: key=$key")
            return false
        }
        return runCatching {
            if (!directory.exists() && !directory.mkdirs()) {
                error("无法创建插件缓存目录")
            }
            val atomicFile = AtomicFile(file)
            var output: FileOutputStream? = null
            try {
                output = atomicFile.startWrite()
                output.write(bytes)
                output.fd.sync()
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                output?.let(atomicFile::failWrite)
                throw error
            }
            true
        }.onFailure { logger.warn("写入缓存文件失败: key=$key", it) }
            .getOrDefault(false)
    }

    private fun ensureCapacity(replacing: File, newSizeBytes: Long): Boolean {
        val existingSize = replacing.takeIf(File::isFile)?.length() ?: 0L
        val totalSize = directory.listFiles()
            ?.asSequence()
            ?.filter {
                it.isFile && it.name.endsWith(
                    com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout.CACHE_FILE_EXTENSION
                )
            }
            ?.sumOf(File::length)
            ?: 0L
        return totalSize - existingSize + newSizeBytes <= MAX_TOTAL_BYTES
    }

    private fun fileForKey(key: String): File = File(
        directory,
        com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout.fileNameForKey(key)
    )

    private fun removeFile(file: File) {
        runCatching {
            file.delete()
            File(file.path + ".bak").delete()
        }
    }

    private fun deleteFileIfPresent(file: File) {
        if (file.exists()) {
            check(file.delete()) { "无法删除插件缓存文件: ${file.name}" }
        }
    }

    private fun isValidKey(key: String): Boolean =
        key.isNotBlank() && key.length <= MAX_KEY_LENGTH

    private fun isWithinLimit(value: ByteArray): Boolean = value.size <= MAX_VALUE_BYTES

    private fun isWithinLimit(value: Long): Boolean = value in 0..MAX_VALUE_BYTES.toLong()
}
