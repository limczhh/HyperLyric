package com.lidesheng.hyperlyric.online

import android.content.Context
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.service.Constants as ServiceConstants
import com.lidesheng.hyperlyric.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LrcCacheManager {

    private fun getCacheDir(context: Context): File {
        val dir = context.externalCacheDir ?: File(context.cacheDir, "online_lyrics")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun generateCacheFileName(context: Context, title: String, artist: String): String {
        val cleanArtist = sanitizeFileName(artist)
        val cleanTitle = sanitizeFileName(title)
        val baseName = "$cleanArtist - $cleanTitle"
        val dir = getCacheDir(context)

        var fileName = "$baseName.lrc"
        if (!File(dir, fileName).exists()) {
            return fileName
        }

        var counter = 2
        while (File(dir, "$baseName ($counter).lrc").exists()) {
            counter++
        }
        return "$baseName ($counter).lrc"
    }

    private fun findCacheFile(context: Context, title: String, artist: String): File? {
        val cleanArtist = sanitizeFileName(artist)
        val cleanTitle = sanitizeFileName(title)
        val baseName = "$cleanArtist - $cleanTitle"
        val dir = getCacheDir(context)

        val exactMatch = File(dir, "$baseName.lrc")
        if (exactMatch.exists()) return exactMatch

        var counter = 2
        while (counter <= 100) {
            val file = File(dir, "$baseName ($counter).lrc")
            if (file.exists()) return file
            counter++
        }
        return null
    }

    suspend fun getLyricFromCache(context: Context, title: String, artist: String): String? = withContext(Dispatchers.IO) {
        val fileName = "${sanitizeFileName(artist)} - ${sanitizeFileName(title)}.lrc"
        LogManager.d("LrcCache", "正在查找缓存: $fileName")
        val file = findCacheFile(context, title, artist)
        if (file != null && file.exists() && file.isFile) {
            file.setLastModified(System.currentTimeMillis())
            val content = try {
                file.readText()
            } catch (e: Exception) {
                LogManager.w("LrcCache", "缓存读取失败: ${file.name}", e)
                null
            }
            if (content != null) {
                LogManager.d("LrcCache", "缓存命中: ${file.name}, 大小=${content.toByteArray().size}B")
            }
            return@withContext content
        }
        LogManager.d("LrcCache", "缓存未命中: $fileName")
        null
    }

    suspend fun saveLyricToCache(context: Context, title: String, artist: String, lrcContent: String) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        val limit = prefs.getInt(ServiceConstants.KEY_ONLINE_LYRIC_CACHE_LIMIT, ServiceConstants.DEFAULT_ONLINE_LYRIC_CACHE_LIMIT)

        if (limit == 0) return@withContext

        val fileName = generateCacheFileName(context, title, artist)
        val file = File(getCacheDir(context), fileName)
        try {
            file.writeText(lrcContent)
            LogManager.d("LrcCache", "正在保存缓存: $fileName, 大小=${lrcContent.toByteArray().size}B")
        } catch (e: Exception) {
            LogManager.w("LrcCache", "缓存保存失败: $fileName", e)
        }

        cleanupCacheIfNecessary(context, limit)
    }

    private fun cleanupCacheIfNecessary(context: Context, limit: Int) {
        val dir = getCacheDir(context)
        val files = dir.listFiles() ?: return
 
        if (limit < 0) return
        
        if (files.size > limit) {
            val sortedFiles = files.sortedBy { it.lastModified() }
            val numToDelete = files.size - limit
            for (i in 0 until numToDelete) {
                sortedFiles[i].delete()
            }
        }
    }
}
