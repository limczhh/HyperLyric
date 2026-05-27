package com.lidesheng.hyperlyric.lyric

import android.content.Context
import com.lidesheng.hyperlyric.online.LrcCacheManager
import com.lidesheng.hyperlyric.online.OnlineLyricTargeter
import com.lidesheng.hyperlyric.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricProviderImpl(private val context: Context) : ILyricProvider {

    override suspend fun fetchLyrics(params: LyricSearchParams): List<LrcLine>? {
        return withContext(Dispatchers.IO) {
            LogManager.d("LyricProvider", "正在获取歌词: 标题=${params.title}, 艺术家=${params.artist}, 专辑=${params.album}, pkg=${params.packageName}, 时长=${params.duration}ms")

            // 1. 尝试从缓存获取
            var lines = LrcCacheManager.getLyricFromCache(context, params.title, params.artist)?.let {
                parseLrc(it)
            }

            if (!lines.isNullOrEmpty()) {
                LogManager.d("LyricProvider", "缓存命中: 行数=${lines.size}")
            } else {
                // 2. 缓存没有，则在线搜索
                LogManager.d("LyricProvider", "缓存未命中，正在在线搜索")
                lines = OnlineLyricTargeter.fetchBestLyric(
                    context,
                    params.packageName,
                    params.title,
                    params.artist,
                    params.duration
                )

                // 3. 搜索成功，存入缓存
                if (!lines.isNullOrEmpty()) {
                    LrcCacheManager.saveLyricToCache(
                        context,
                        params.title,
                        params.artist,
                        buildLrcString(lines)
                    )
                }
            }
            LogManager.d("LyricProvider", "歌词获取完成: 行数=${lines?.size ?: 0}")
            lines
        }
    }

    private fun parseLrc(lrcText: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        lrcText.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msRaw = match.groupValues[3]
                val ms = if (msRaw.length == 2) msRaw.toLong() * 10 else msRaw.toLong()
                val content = match.groupValues[4].trim()
                if (content.isNotEmpty()) {
                    lines.add(LrcLine(min * 60000 + sec * 1000 + ms, content))
                }
            }
        }
        return lines
    }

    private fun buildLrcString(lines: List<LrcLine>): String {
        return lines.joinToString("\n") { line ->
            val totalMs = line.startTimeMs
            val min = totalMs / 60000
            val sec = (totalMs % 60000) / 1000
            val ms = (totalMs % 1000) / 10
            String.format("[%02d:%02d.%02d]%s", min, sec, ms, line.content)
        }
    }
}
