package com.lidesheng.hyperlyric.plugin.demo

import com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin
import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import com.lidesheng.hyperlyric.plugin.api.PluginContext
import com.lidesheng.hyperlyric.plugin.api.PluginLyricField
import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginMetadata
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult
import com.lidesheng.hyperlyric.plugin.api.PluginWord

class DemoPlugin : HyperLyricPlugin {
    private companion object {
        const val EXTENSION_ID = "demo.logger"
        const val HARMONY_EXTENSION_ID = "demo.harmony"
        const val DUET_EXTENSION_ID = "demo.duet"
        const val TRANSLATION_EXTENSION_ID = "demo.translation"
        const val ROMA_METADATA_EXTENSION_ID = "demo.roma-metadata"
        // Keep the old key so an existing Demo installation does not silently lose its switch.
        const val DUET_CONFIG_KEY = "replace_lyrics"
        const val HARMONY_CONFIG_KEY = "add_harmony"
        const val TRANSLATION_CONFIG_KEY = "add_translation"
        const val ROMA_CONFIG_KEY = "add_roma_metadata"
        const val HARMONY_PREFIX = "[和声]"
        const val DUET_PREFIX = "[对唱]"
        const val ROMA_PREFIX = "[罗马音]"
        const val TRANSLATION_PREFIX = "[翻译]"
        const val DEMO_METADATA_KEY = "hyperlyric.demo"
        const val MAX_DUPLICATED_LYRIC_LINES = 10_000
        const val MAX_LOG_LYRIC_LINES = 5
        const val MAX_LOG_WORDS_PER_LINE = 8
        const val MAX_LOG_TEXT_LENGTH = 120
    }

    private lateinit var context: PluginContext

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.registerExtension(HarmonyProcessor(context))
        context.registerExtension(DuetProcessor(context))
        context.registerExtension(TranslationProcessor(context))
        context.registerExtension(RomaMetadataProcessor(context))
        context.registerExtension(LoggerProcessor(context))
        context.logger.info("lifecycle=onLoad, extensions=5")
    }

    override fun onEnable() {
        context.logger.info("lifecycle=onEnable")
    }

    override fun onConfigChanged(config: PluginConfig) {
        context.logger.debug(
            "lifecycle=onConfigChanged, log_song=${config.getBoolean("log_song", true)}, " +
                    "add_harmony=${config.getBoolean(HARMONY_CONFIG_KEY, false)}, " +
                    "add_duet=${config.getBoolean(DUET_CONFIG_KEY, false)}, " +
                    "add_translation=${config.getBoolean(TRANSLATION_CONFIG_KEY, false)}, " +
                    "add_roma=${config.getBoolean(ROMA_CONFIG_KEY, false)}"
        )
    }

    override fun onUnload() {
        context.logger.info("lifecycle=onUnload")
    }

    private class LoggerProcessor(private val context: PluginContext) : LyricProcessorExtension {
        override val id: String = EXTENSION_ID

        override fun processResult(
            song: PluginSong,
            processingContext: PluginProcessingContext
        ): PluginSongResult? {
            if (context.config.getBoolean("log_song", true)) {
                val media = processingContext.mediaInfo
                val lyrics = song.lyrics.orEmpty()
                context.logger.info(
                    "event=processSong, song={" +
                            "id=${song.id.logValue()}, " +
                            "name=${song.name.logValue()}, " +
                            "artist=${song.artist.logValue()}, " +
                            "album=${song.album.logValue()}, " +
                            "duration=${song.duration}, " +
                            "metadata=${song.metadata?.values.logValue()}, " +
                            "lyricsCount=${lyrics.size}}, " +
                            "mediaInfo={" +
                            "title=${media?.title.logValue()}, " +
                            "artist=${media?.artist.logValue()}, " +
                            "album=${media?.album.logValue()}, " +
                            "duration=${media?.duration ?: "<null>"}}"
                )
                lyrics.take(MAX_LOG_LYRIC_LINES).forEachIndexed { index, line ->
                    context.logger.info(
                        "event=processSongLyric, index=$index, " +
                                "timeline=${line.begin}-${line.end}/${line.duration}, " +
                                "text=${line.text.logValue()}, " +
                                "words=${formatWords(line.words)}, " +
                                "secondary=${line.secondary.logValue()}, " +
                                "translation=${line.translation.logValue()}, " +
                                "roma=${line.roma.logValue()}, " +
                                "secondaryWordCount=${line.secondaryWords?.size ?: 0}, " +
                                "translationWordCount=${line.translationWords?.size ?: 0}"
                    )
                }
                if (lyrics.size > MAX_LOG_LYRIC_LINES) {
                    context.logger.info(
                        "event=processSongLyric, omitted=${lyrics.size - MAX_LOG_LYRIC_LINES}"
                    )
                }
            }
            return null
        }

        private fun formatWords(words: List<PluginWord>?): String {
            if (words == null) return "<null>"
            val preview = words.take(MAX_LOG_WORDS_PER_LINE).joinToString("|") { word ->
                "${word.begin}-${word.end}:${word.text.logValue()}"
            }
            val omitted = words.size - MAX_LOG_WORDS_PER_LINE
            return if (omitted > 0) {
                "[$preview|...+$omitted]"
            } else {
                "[$preview]"
            }
        }

        private fun Any?.logValue(): String = when (this) {
            null -> "<null>"
            is String -> replace("\\r", "\\\\r")
                .replace("\\n", "\\\\n")
                .take(MAX_LOG_TEXT_LENGTH)
            else -> toString().take(MAX_LOG_TEXT_LENGTH)
        }
    }

    private class HarmonyProcessor(
        private val context: PluginContext
    ) : LyricProcessorExtension {
        override val id: String = HARMONY_EXTENSION_ID
        override val stage: PluginProcessorStage = PluginProcessorStage.TRANSLATION_ENHANCEMENT

        override fun processResult(song: PluginSong): PluginSongResult? {
            if (!context.config.getBoolean(HARMONY_CONFIG_KEY, false)) return null
            val lyrics = song.lyrics ?: return null
            val enriched = lyrics.map { line ->
                val sourceText = line.secondary
                    ?.takeIf { it.isNotBlank() }
                    ?: line.contentText()
                    ?: return@map line
                if (sourceText.startsWith(HARMONY_PREFIX)) return@map line

                val hasExistingSecondary = !line.secondary.isNullOrBlank() ||
                        !line.secondaryWords.isNullOrEmpty()
                val sourceWords = if (hasExistingSecondary) {
                    line.secondaryWords?.takeIf { it.isNotEmpty() }
                } else {
                    line.words?.takeIf { it.isNotEmpty() }
                }
                line.withFields(
                    secondary = HARMONY_PREFIX + sourceText,
                    secondaryWords = prefixWords(
                        words = sourceWords,
                        prefix = HARMONY_PREFIX,
                        lineBegin = line.begin,
                        lineEnd = line.end
                    )
                )
            }
            if (enriched == lyrics) return null
            return PluginSongResult(
                song = song.withLyrics(enriched),
                changedFields = setOf(PluginSongField.LYRICS),
                lyricsUpdateMode = PluginLyricsUpdateMode.PATCH,
                changedLyricFields = setOf(
                    PluginLyricField.SECONDARY,
                    PluginLyricField.SECONDARY_WORDS
                )
            )
        }
    }

    private class DuetProcessor(
        private val context: PluginContext
    ) : LyricProcessorExtension {
        override val id: String = DUET_EXTENSION_ID
        override val stage: PluginProcessorStage = PluginProcessorStage.LYRIC_REPLACEMENT

        override fun processResult(song: PluginSong): PluginSongResult? {
            if (!context.config.getBoolean(DUET_CONFIG_KEY, false)) return null
            val lyrics = song.lyrics ?: return null
            if (lyrics.isEmpty() || lyrics.size > MAX_DUPLICATED_LYRIC_LINES) return null
            if (lyrics.any { !it.hasValidTimeline() }) {
                context.logger.warn(
                    "Demo 对唱降级为原文前缀：歌词存在无法复制的无效时间轴"
                )
                return prefixDuetText(song, lyrics)
            }

            val duetLyrics = buildList(lyrics.size * 2) {
                lyrics.forEach { line ->
                    val role = line.metadata?.values?.get(DUET_METADATA_KEY)
                    when (role) {
                        DUET_SECONDARY_ROLE -> add(line)
                        DUET_MAIN_ROLE -> add(line)
                        else -> {
                            val sourceText = line.contentText()
                            if (sourceText == null) {
                                add(line)
                                return@forEach
                            }

                            add(line.withDuetMainMetadata())
                            add(
                                line.withFields(
                                    metadata = line.metadata.withDuetMetadata(
                                        role = DUET_SECONDARY_ROLE,
                                        agent = DEMO_DUET_AGENT,
                                        replaceAgent = true
                                    ),
                                    text = DUET_PREFIX + sourceText,
                                    words = prefixWords(
                                        words = line.words,
                                        prefix = DUET_PREFIX,
                                        lineBegin = line.begin,
                                        lineEnd = line.end
                                    )
                                )
                            )
                        }
                    }
                }
            }
            if (duetLyrics == lyrics) return null
            return PluginSongResult(
                song = song.withLyrics(duetLyrics),
                changedFields = setOf(PluginSongField.LYRICS),
                lyricsUpdateMode = PluginLyricsUpdateMode.REPLACE,
                changedLyricFields = emptySet()
            )
        }
    }

    private class TranslationProcessor(
        private val context: PluginContext
    ) : LyricProcessorExtension {
        override val id: String = TRANSLATION_EXTENSION_ID
        override val stage: PluginProcessorStage = PluginProcessorStage.TRANSLATION_ENHANCEMENT

        override fun processResult(song: PluginSong): PluginSongResult? {
            if (!context.config.getBoolean(TRANSLATION_CONFIG_KEY, false)) return null
            val lyrics = song.lyrics ?: return null
            val translated = lyrics.map { line ->
                val sourceText = line.translation
                    ?.takeIf { it.isNotBlank() }
                    ?: line.contentText()
                    ?: return@map line
                val translation = prefixed(TRANSLATION_PREFIX, sourceText)
                line.withFields(translation = translation, translationWords = null)
            }
            if (translated == lyrics) return null
            return PluginSongResult(
                song = song.withLyrics(translated),
                changedFields = setOf(PluginSongField.LYRICS),
                lyricsUpdateMode = PluginLyricsUpdateMode.PATCH,
                changedLyricFields = setOf(
                    PluginLyricField.TRANSLATION,
                    PluginLyricField.TRANSLATION_WORDS
                )
            )
        }
    }

    private class RomaMetadataProcessor(
        private val context: PluginContext
    ) : LyricProcessorExtension {
        override val id: String = ROMA_METADATA_EXTENSION_ID
        override val stage: PluginProcessorStage = PluginProcessorStage.TRANSLATION_ENHANCEMENT

        override fun processResult(song: PluginSong): PluginSongResult? {
            if (!context.config.getBoolean(ROMA_CONFIG_KEY, false)) return null
            val lyrics = song.lyrics ?: return null
            var changed = false
            val enriched = lyrics.map { line ->
                val sourceText = line.roma
                    ?.takeIf { it.isNotBlank() }
                    ?: line.contentText()
                if (sourceText == null) return@map line
                val roma = prefixed(ROMA_PREFIX, sourceText)
                val metadata = line.metadata.withValues(DEMO_METADATA_KEY to "true")
                if (line.roma != roma || line.metadata != metadata) changed = true
                line.withFields(roma = roma, metadata = metadata)
            }
            if (!changed) return null
            return PluginSongResult(
                song = song.withLyrics(enriched),
                changedFields = setOf(PluginSongField.LYRICS),
                lyricsUpdateMode = PluginLyricsUpdateMode.PATCH,
                changedLyricFields = setOf(PluginLyricField.ROMA, PluginLyricField.METADATA)
            )
        }
    }
}

private const val DUET_METADATA_KEY = "hyperlyric.demo.duet"
private const val DUET_MAIN_ROLE = "main"
private const val DUET_SECONDARY_ROLE = "secondary"
private const val AGENT_METADATA_KEY = "amll:agent"
private const val DEMO_MAIN_AGENT = "hyperlyric.demo.main"
private const val DEMO_DUET_AGENT = "hyperlyric.demo.duet"
private val AGENT_METADATA_KEYS = listOf("agent", "amll:agent", "vocal", "amll:vocal")

private fun prefixDuetText(
    song: PluginSong,
    lyrics: List<PluginLyricLine>
): PluginSongResult? {
    val prefixed = lyrics.map { line ->
        val sourceText = line.contentText() ?: return@map line
        if (sourceText.startsWith("[对唱]")) {
            line
        } else {
            line.withFields(text = "[对唱]" + sourceText)
        }
    }
    if (prefixed == lyrics) return null
    return PluginSongResult(
        song = song.withLyrics(prefixed),
        changedFields = setOf(PluginSongField.LYRICS),
        lyricsUpdateMode = PluginLyricsUpdateMode.PATCH,
        changedLyricFields = setOf(PluginLyricField.TEXT)
    )
}

private fun PluginLyricLine.contentText(): String? =
    text?.takeIf { it.isNotBlank() }
        ?: words?.takeIf { it.isNotEmpty() }
            ?.joinToString("") { it.text.orEmpty() }
            ?.takeIf { it.isNotBlank() }

private fun PluginLyricLine.hasValidTimeline(): Boolean =
    begin >= 0L && end > begin && duration == end - begin

private fun prefixed(prefix: String, text: String): String =
    if (text.startsWith(prefix)) text else prefix + text

private fun PluginMetadata?.withValues(vararg entries: Pair<String, String?>): PluginMetadata {
    val values = (this?.values ?: emptyMap()).toMutableMap()
    entries.forEach { (key, value) -> values[key] = value }
    return PluginMetadata(values)
}

private fun PluginMetadata?.withDuetMetadata(
    role: String,
    agent: String,
    replaceAgent: Boolean
): PluginMetadata {
    val values = (this?.values ?: emptyMap()).toMutableMap()
    if (replaceAgent) AGENT_METADATA_KEYS.forEach(values::remove)
    values[DUET_METADATA_KEY] = role
    values[AGENT_METADATA_KEY] = agent
    return PluginMetadata(values)
}

private fun PluginLyricLine.withDuetMainMetadata(): PluginLyricLine {
    val hasAgent = AGENT_METADATA_KEYS.any { key ->
        metadata?.values?.get(key).isNullOrBlank().not()
    }
    val nextMetadata = if (hasAgent) {
        metadata.withValues(DUET_METADATA_KEY to DUET_MAIN_ROLE)
    } else {
        metadata.withDuetMetadata(
            role = DUET_MAIN_ROLE,
            agent = DEMO_MAIN_AGENT,
            replaceAgent = false
        )
    }
    return withFields(metadata = nextMetadata)
}

/** Rebuild host-owned DTOs with their complete constructors across the ClassLoader boundary. */
private fun PluginSong.withLyrics(lyrics: List<PluginLyricLine>?): PluginSong = PluginSong(
    id = id,
    name = name,
    artist = artist,
    album = album,
    duration = duration,
    metadata = metadata,
    lyrics = lyrics
)

private fun PluginLyricLine.withFields(
    metadata: PluginMetadata? = this.metadata,
    text: String? = this.text,
    words: List<PluginWord>? = this.words,
    secondary: String? = this.secondary,
    secondaryWords: List<PluginWord>? = this.secondaryWords,
    translation: String? = this.translation,
    translationWords: List<PluginWord>? = this.translationWords,
    roma: String? = this.roma
): PluginLyricLine = PluginLyricLine(
    begin = begin,
    end = end,
    duration = duration,
    isAlignedRight = isAlignedRight,
    metadata = metadata,
    text = text,
    words = words,
    secondary = secondary,
    secondaryWords = secondaryWords,
    translation = translation,
    translationWords = translationWords,
    roma = roma
)

private fun PluginWord.withFields(
    begin: Long = this.begin,
    end: Long = this.end,
    duration: Long = this.duration,
    text: String? = this.text,
    metadata: PluginMetadata? = this.metadata
): PluginWord = PluginWord(
    begin = begin,
    end = end,
    duration = duration,
    text = text,
    metadata = metadata
)

private fun prefixWords(
    words: List<PluginWord>?,
    prefix: String,
    lineBegin: Long,
    lineEnd: Long
): List<PluginWord>? {
    if (words.isNullOrEmpty()) return words
    val lineDuration = lineEnd - lineBegin
    val slotCount = words.size + 1
    if (lineDuration <= 0L) return null
    if (lineDuration < slotCount) {
        val first = words.first()
        return words.toMutableList().apply {
            set(0, first.withFields(text = prefix + first.text.orEmpty()))
        }
    }

    val segment = lineDuration / slotCount
    val remainder = lineDuration % slotCount
    var cursor = lineBegin

    fun nextEnd(index: Int): Long {
        val end = cursor + segment + if (index < remainder) 1L else 0L
        cursor = end
        return end
    }

    return buildList {
        val prefixEnd = nextEnd(0)
        add(
            PluginWord(
                begin = lineBegin,
                end = prefixEnd,
                duration = prefixEnd - lineBegin,
                text = prefix,
                // The API is supplied by the host ClassLoader. Do not call the Kotlin
                // DefaultConstructorMarker overload across the plugin boundary.
                metadata = null
            )
        )
        words.forEachIndexed { index, word ->
            val begin = cursor
            val end = nextEnd(index + 1)
            add(word.withFields(begin = begin, end = end, duration = end - begin))
        }
    }
}
