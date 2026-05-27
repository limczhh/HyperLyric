package com.lidesheng.hyperlyric.online

import android.content.Context
import com.lidesheng.hyperlyric.lyric.LrcLine
import com.lidesheng.hyperlyric.online.model.SongSearchResult
import com.lidesheng.hyperlyric.online.utils.ChineseUtils
import com.lidesheng.hyperlyric.utils.LogManager
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

object OnlineLyricTargeter {
    private const val TIMEOUT_MS = 5000L
    private const val PASS_SCORE = 85

    suspend fun fetchBestLyric(
        context: Context,
        pkgName: String, 
        title: String, 
        artist: String, 
        durationMs: Long
    ): List<LrcLine>? {
        val ne = LyricApiProvider.getNeSource(context)
        val qm = LyricApiProvider.qmSource

        val sources = when (pkgName) {
            "com.netease.cloudmusic" -> listOf(ne, qm)
            "com.tencent.qqmusic" -> listOf(qm, ne)
            else -> listOf(qm, ne)
        }

        val keyword = "$title $artist"
        LogManager.d("OnlineTargeter", "正在搜索: 关键词=\"$keyword\", 源顺序=${sources.joinToString { it.javaClass.simpleName }}")

        val cleanLocalTitle = cleanString(context, title)
        val localArtists = artist.split("&", ",", "，", "、").map { cleanString(context, it) }
        val featureKeywords = listOf("live", "remastered", "翻唱", "cover")
        val localFeatures = featureKeywords.filter { title.lowercase().contains(it) }
        
        var bestScore = -1

        for (source in sources) {
            val results = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    source.search(keyword, 1, "/", 20)
                } catch (e: Exception) {
                    LogManager.w("OnlineTargeter", "搜索异常: 源=${source.javaClass.simpleName}, ${e.message}")
                    null
                }
            }
            if (results.isNullOrEmpty()) {
                LogManager.d("OnlineTargeter", "搜索结果为空: 源=${source.javaClass.simpleName}")
                continue
            }
            LogManager.d("OnlineTargeter", "搜索结果: 源=${source.javaClass.simpleName}, 数量=${results.size}")

            var localBestScore = -1
            var bestSong: SongSearchResult? = null

            for (song in results) {
                val score = calculateScore(context, song, cleanLocalTitle, localArtists, localFeatures, durationMs)
                if (score > localBestScore) {
                    localBestScore = score
                    bestSong = song
                }
            }

            if (localBestScore > bestScore) bestScore = localBestScore
            LogManager.d("OnlineTargeter", "评分: \"${bestSong?.title}\" - \"${bestSong?.artist}\", 得分=$localBestScore, 阈值=$PASS_SCORE, 通过=${localBestScore >= PASS_SCORE}")

            if (bestScore >= PASS_SCORE && bestSong != null) {
                val lyricsResult = withTimeoutOrNull(TIMEOUT_MS) {
                    try {
                        source.getLyrics(bestSong)
                    } catch (e: Exception) {
                        LogManager.w("OnlineTargeter", "获取歌词异常: 源=${source.javaClass.simpleName}, ${e.message}")
                        null
                    }
                }
                
                if (lyricsResult != null && (lyricsResult.original.isNotEmpty() || !lyricsResult.translated.isNullOrEmpty())) {
                    val validOriginal = lyricsResult.original
                    if (validOriginal.isEmpty()) continue
                    
                    val list = mutableListOf<LrcLine>()
                    validOriginal.forEach { line ->
                        val content = line.words.joinToString("") { w -> w.text }.trim()
                        if (content.isNotEmpty()) {
                            list.add(LrcLine(line.start, content))
                        }
                    }
                    if (list.isNotEmpty()) {
                        LogManager.d("OnlineTargeter", "歌词命中: 源=${source.javaClass.simpleName}, 得分=$bestScore, 行数=${list.size}")
                        return list
                    }
                }
            }
        }
        LogManager.d("OnlineTargeter", "歌词未命中: 最佳得分=$bestScore < 阈值 $PASS_SCORE")
        return null
    }

    private fun calculateScore(
        context: Context,
        song: SongSearchResult,
        cleanLocalTitle: String,
        localArtists: List<String>,
        localFeatures: List<String>,
        localDurationMs: Long
    ): Int {
        var score = 0

        if (localDurationMs > 0 && song.duration > 0) {
            val diffMs = abs(localDurationMs - song.duration)
            if (diffMs > 5000) {
                score -= 30
            } else if (diffMs < 1500) {
                score += 15
            }
        }

        val cleanSongTitle = cleanString(context, song.title)

        if (cleanLocalTitle == cleanSongTitle || cleanSongTitle.contains(cleanLocalTitle) || cleanLocalTitle.contains(cleanSongTitle)) {
            score += 50
        }

        val songArtists = song.artist.split("&", ",", "，", "、").map { cleanString(context, it) }
        
        val hasCommonArtist = localArtists.any { lArtist -> songArtists.any { sArtist -> lArtist == sArtist || sArtist.contains(lArtist) || lArtist.contains(sArtist) } }
        if (hasCommonArtist) {
            score += 30
        }

        val songFeatures = listOf("live", "remastered", "翻唱", "cover").filter { song.title.lowercase().contains(it) }
        
        if (localFeatures.isNotEmpty() && songFeatures.isNotEmpty()) {
            val commonFeatures = localFeatures.intersect(songFeatures.toSet())
            if (commonFeatures.isNotEmpty()) {
                score += 20
            }
        }

        return score
    }

    private fun cleanString(context: Context, input: String): String {
        val cleaned = input.replace(Regex("\\(.*?\\)|\\[.*?]|\\{.*?\\}"), "").trim().lowercase()
        return ChineseUtils.toSimplified(context, cleaned)
    }
}
