package com.lidesheng.hyperlyric.online

import android.content.Context
import com.lidesheng.hyperlyric.online.model.SongSearchResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

data class LrcLine(val startTimeMs: Long, val content: String)

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
            else -> listOf(ne, qm)
        }

        val keyword = "$title $artist"
        
        val cleanLocalTitle = cleanString(title)
        val localArtists = artist.split("&", ",", "，", "、").map { cleanString(it) }
        val featureKeywords = listOf("live", "remastered", "翻唱", "cover")
        val localFeatures = featureKeywords.filter { title.lowercase().contains(it) }
        
        for (source in sources) {
            val results = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    source.search(keyword, 1, "/", 20)
                } catch (_: Exception) {
                    null
                }
            }
            if (results.isNullOrEmpty()) continue

            var bestScore = -1
            var bestSong: SongSearchResult? = null

            for (song in results) {
                val score = calculateScore(song, cleanLocalTitle, localArtists, localFeatures, durationMs)
                if (score > bestScore) {
                    bestScore = score
                    bestSong = song
                }
            }

            if (bestScore >= PASS_SCORE && bestSong != null) {
                val lyricsResult = withTimeoutOrNull(TIMEOUT_MS) {
                    try {
                        source.getLyrics(bestSong)
                    } catch (_: Exception) {
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
                    if (list.isNotEmpty()) return list
                }
            }
        }
        return null
    }

    private fun calculateScore(
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

        val cleanSongTitle = cleanString(song.title)

        if (cleanLocalTitle == cleanSongTitle || cleanSongTitle.contains(cleanLocalTitle) || cleanLocalTitle.contains(cleanSongTitle)) {
            score += 50
        }

        val songArtists = song.artist.split("&", ",", "，", "、").map { cleanString(it) }
        
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

    private fun cleanString(input: String): String {
        return input.replace(Regex("\\(.*?\\)|\\[.*?]|\\{.*?\\}"), "").trim().lowercase()
    }
}
