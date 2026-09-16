package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.Song
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object TranslationKey {
    private const val CACHE_SCHEMA_VERSION = 2

    fun calculate(
        song: Song,
        lines: List<String>,
        config: AiTranslationConfig,
        sourcePackageName: String? = null
    ): String {
        val source = buildString {
            appendPart("schema", CACHE_SCHEMA_VERSION.toString())
            appendPart("provider", config.provider)
            appendPart("target", config.targetLanguage)
            appendPart("title", song.name.orEmpty())
            appendPart("artist", song.artist.orEmpty())
            appendPart("album", song.album.orEmpty())
            appendPart("duration", song.duration.toString())
            // The source package is only an auxiliary matching context. Title/artist/album,
            // duration and lyric text remain the song identity, so package alone never identifies
            // a translation cache entry.
            appendPart("source_package", sourcePackageName.orEmpty())
            appendPart("model", config.model)
            appendPart("base_url", config.baseUrl.trim().removeSuffix("/"))
            appendPart("prompt", config.prompt)
            appendPart("temperature", config.temperature.toString())
            appendPart("top_p", config.topP.toString())
            appendPart("max_tokens", config.maxTokens.toString())
            lines.forEachIndexed { index, line ->
                appendPart("line_$index", line)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun StringBuilder.appendPart(name: String, value: String) {
        append(name).append('=').append(value.length).append(':').append(value).append('\u0000')
    }
}
