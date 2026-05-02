package com.lidesheng.hyperlyric.root.aitrans

import io.github.proify.android.extensions.md5
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.AiTranslationConfigs

internal object AITranslationKey {
    fun calculate(configs: AiTranslationConfigs, song: Song, lines: List<String>): String {
        return buildString {
            append("target=").appendLine(configs.targetLanguage.orEmpty())
            append("title=").appendLine(song.name.orEmpty())
            append("artist=").appendLine(song.artist.orEmpty())
            lines.forEachIndexed { index, line ->
                append(index).append(':').appendLine(line)
            }
        }.md5()
    }
}
