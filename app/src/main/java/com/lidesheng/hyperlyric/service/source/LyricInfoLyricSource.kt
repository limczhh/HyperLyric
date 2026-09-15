package com.lidesheng.hyperlyric.service.source

import com.lidesheng.hyperlyric.common.lyric.LyricInfoParser
import com.lidesheng.hyperlyric.lyric.LrcLine

class LyricInfoLyricSource : ServiceLyricSource {
    override val id = "lyricinfo"
    override val displayName = "LyricInfo"

    override suspend fun getLyrics(data: SyncData): List<LrcLine>? {
        val lyricInfo = data.lyricInfoRaw
        if (lyricInfo.isNullOrBlank()) return null
        return try {
            val song = LyricInfoParser.parse(lyricInfo)
            song?.lyrics?.map { LrcLine(it.begin, it.text ?: "") }
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
