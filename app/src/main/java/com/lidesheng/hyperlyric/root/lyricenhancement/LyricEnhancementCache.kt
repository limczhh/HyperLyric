package com.lidesheng.hyperlyric.root.lyricenhancement

import android.content.Context
import android.util.AtomicFile
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

internal interface LyricEnhancementCacheStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(key: String)

    fun clear(): Boolean
}

internal class FileLyricEnhancementCache(
    private val directory: File,
    private val logTag: String,
) : LyricEnhancementCacheStore {
    companion object {
        private const val CACHE_ROOT_DIRECTORY = "hyperlyric_lyric_enhancement_cache"
        private val enhancementIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
        private const val MAX_KEY_LENGTH = 256
        private const val MAX_VALUE_BYTES = 2 * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 64 * 1024 * 1024
        private const val CACHE_FILE_EXTENSION = ".cache"

        fun directory(context: Context, enhancementId: String): File {
            require(enhancementIdPattern.matches(enhancementId)) {
                "Invalid lyric enhancement id"
            }
            return File(File(context.filesDir, CACHE_ROOT_DIRECTORY), enhancementId)
        }
    }

    private val lock = Any()

    init {
        HookLogger.d(logTag, "歌词增强缓存目录: ${directory.absolutePath}")
    }

    override fun getString(key: String): String? {
        if (!isValidKey(key)) return null
        return synchronized(lock) { readFileValue(fileForKey(key)) }
    }

    override fun putString(key: String, value: String) {
        if (!isValidKey(key)) return
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_VALUE_BYTES) {
            HookLogger.w(logTag, "缓存内容超限，跳过写入: key=$key")
            return
        }
        if (value.isEmpty()) {
            remove(key)
            return
        }
        synchronized(lock) {
            if (!writeFileValue(fileForKey(key), bytes)) {
                HookLogger.w(logTag, "缓存写入失败: key=$key")
            }
        }
    }

    override fun remove(key: String) {
        if (!isValidKey(key)) return
        synchronized(lock) {
            deleteFile(fileForKey(key))
        }
    }

    override fun clear(): Boolean {
        synchronized(lock) {
            val files = directory.listFiles()
                ?.filter {
                    it.isFile &&
                            (it.name.endsWith(CACHE_FILE_EXTENSION) || it.name.endsWith(".bak"))
                }
                .orEmpty()
            var success = true
            files.forEach { file ->
                if (!deleteFile(file)) success = false
            }
            if (
                directory.listFiles()?.any {
                    it.isFile &&
                            (it.name.endsWith(CACHE_FILE_EXTENSION) || it.name.endsWith(".bak"))
                } == true
            ) {
                success = false
            }
            return success
        }
    }

    private fun readFileValue(file: File): String? {
        val backup = File(file.path + ".bak")
        if (!file.isFile && !backup.isFile) return null
        return runCatching {
            AtomicFile(file).openRead().use { input ->
                if (file.length() > MAX_VALUE_BYTES) error("缓存文件超限: ${file.name}")
                input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }.map { value ->
            value.takeIf(String::isNotEmpty) ?: run {
                deleteFile(file)
                null
            }
        }.onFailure {
            HookLogger.w(logTag, "缓存读取失败: key=${file.name}", it)
            deleteFile(file)
        }.getOrNull()
    }

    private fun writeFileValue(file: File, bytes: ByteArray): Boolean {
        if (!ensureCapacity(file, bytes.size.toLong())) return false
        return runCatching {
            if (!directory.exists() && !directory.mkdirs()) {
                error("无法创建歌词增强缓存目录")
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
        }.getOrElse {
            HookLogger.w(logTag, "缓存写入异常: key=${file.name}", it)
            false
        }
    }

    private fun ensureCapacity(replacing: File, newSizeBytes: Long): Boolean {
        val existingSize = replacing.takeIf(File::isFile)?.length() ?: 0L
        val totalSize = directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(CACHE_FILE_EXTENSION) }
            ?.sumOf(File::length)
            ?: 0L
        return totalSize - existingSize + newSizeBytes <= MAX_TOTAL_BYTES
    }

    private fun fileForKey(key: String): File =
        File(directory, sha256(key) + CACHE_FILE_EXTENSION)

    private fun deleteFile(file: File): Boolean {
        val primaryDeleted = !file.exists() || runCatching { file.delete() }.getOrDefault(false)
        val backup = File(file.path + ".bak")
        val backupDeleted = !backup.exists() || runCatching { backup.delete() }.getOrDefault(false)
        return primaryDeleted && backupDeleted
    }

    private fun isValidKey(key: String): Boolean =
        key.isNotBlank() && key.length <= MAX_KEY_LENGTH

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
