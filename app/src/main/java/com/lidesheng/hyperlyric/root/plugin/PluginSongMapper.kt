package com.lidesheng.hyperlyric.root.plugin

import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.plugin.api.PluginLyricField
import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginMetadata
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult
import com.lidesheng.hyperlyric.plugin.api.PluginWord

/** Converts the private lyric model to and from the stable plugin snapshots. */
object PluginSongMapper {
    private const val MAX_LYRICS = 20_000
    private const val MAX_WORDS_PER_LINE = 2_000
    private const val MAX_TOTAL_WORDS = 100_000
    private val CONTENT_ONLY_PATCH_FIELDS = setOf(
        PluginLyricField.METADATA,
        PluginLyricField.TEXT,
        PluginLyricField.SECONDARY,
        PluginLyricField.TRANSLATION,
        PluginLyricField.ROMA
    )

    fun toPluginSong(song: Song): PluginSong = PluginSong(
        id = song.id,
        name = song.name,
        artist = song.artist,
        album = song.album,
        duration = song.duration,
        metadata = song.metadata?.let(::toPluginMetadata),
        lyrics = song.lyrics?.map(::toPluginLine)
    )

    /**
     * Applies the fields explicitly declared by a plugin result. The candidate DTO is never
     * allowed to replace fields that are absent from [PluginSongResult.changedFields].
     */
    fun mergePluginSong(
        base: PluginSong,
        result: PluginSongResult
    ): PluginSong? {
        if (result.changedFields.isEmpty()) {
            if (result.changedLyricFields.isNotEmpty()) return null
            return base
        }
        if (result.changedLyricFields.isNotEmpty() &&
            PluginSongField.LYRICS !in result.changedFields
        ) {
            return null
        }

        var merged = base
        result.changedFields.forEach { field ->
            merged = when (field) {
                PluginSongField.ID -> merged.copy(id = result.song.id)
                PluginSongField.NAME -> merged.copy(name = result.song.name)
                PluginSongField.ARTIST -> merged.copy(artist = result.song.artist)
                PluginSongField.ALBUM -> merged.copy(album = result.song.album)
                PluginSongField.DURATION -> {
                    if (result.song.duration < 0L) return null
                    merged.copy(duration = result.song.duration)
                }
                PluginSongField.METADATA -> merged.copy(metadata = result.song.metadata)
                PluginSongField.LYRICS -> {
                    val lyrics = mergeLyrics(
                        base = merged.lyrics,
                        candidate = result.song.lyrics,
                        mode = result.lyricsUpdateMode,
                        changedFields = result.changedLyricFields
                    ) ?: return null
                    merged.copy(lyrics = lyrics.lyrics)
                }
            }
        }
        return merged
    }

    /**
     * Converts a validated plugin result back to the private model. Every field is sourced from
     * the merged DTO, so explicitly cleared nullable fields remain cleared.
     */
    fun toInternalSong(
        base: Song,
        result: PluginSongResult
    ): Song? {
        val candidate = mergePluginSong(
            base = toPluginSong(base),
            result = result
        ) ?: return null

        return base.copy(
            id = candidate.id,
            name = candidate.name,
            artist = candidate.artist,
            album = candidate.album,
            duration = candidate.duration,
            metadata = candidate.metadata?.let(::toInternalMetadata),
            lyrics = candidate.lyrics?.map(::toInternalLine)
        )
    }

    private data class LyricsMergeResult(
        val lyrics: List<PluginLyricLine>?
    )

    private fun mergeLyrics(
        base: List<PluginLyricLine>?,
        candidate: List<PluginLyricLine>?,
        mode: PluginLyricsUpdateMode,
        changedFields: Set<PluginLyricField>
    ): LyricsMergeResult? {
        return when (mode) {
            PluginLyricsUpdateMode.REPLACE -> {
                // REPLACE is already field-complete; mixing it with a patch declaration is
                // ambiguous and usually indicates that the plugin forgot to choose a mode.
                if (changedFields.isNotEmpty()) return null
                if (candidate.isNullOrEmpty()) {
                    // An explicit LYRICS result is allowed to clear the nullable lyrics field.
                    LyricsMergeResult(candidate)
                } else if (hasValidLyrics(candidate)) {
                    LyricsMergeResult(candidate)
                } else {
                    null
                }
            }

            PluginLyricsUpdateMode.PATCH -> {
                if (changedFields.isEmpty() || base == null || candidate == null) return null
                if (base.size != candidate.size || candidate.size > MAX_LYRICS) return null

                val patched = base.mapIndexed { index, line ->
                    applyLyricFields(line, candidate[index], changedFields)
                }
                // Content-only plugins do not alter the line timeline or primary word timing.
                // Some lyric sources intentionally expose complete text with begin/end/duration
                // left at zero, so re-validating untouched timing would discard a safe prefix,
                // harmony, translation or Roma update. Secondary/translation word lanes may also
                // be explicitly cleared without introducing a new invalid timeline.
                val isSafeContentOnlyPatch = changedFields.all { field ->
                    field in CONTENT_ONLY_PATCH_FIELDS ||
                        field == PluginLyricField.SECONDARY_WORDS ||
                        field == PluginLyricField.TRANSLATION_WORDS
                } && (
                    PluginLyricField.SECONDARY_WORDS !in changedFields ||
                        candidate.all { it.secondaryWords == null }
                    ) && (
                    PluginLyricField.TRANSLATION_WORDS !in changedFields ||
                        candidate.all { it.translationWords == null }
                    )
                if (isSafeContentOnlyPatch) return LyricsMergeResult(patched)
                if (patched.isEmpty() || hasValidLyrics(patched)) {
                    LyricsMergeResult(patched)
                } else {
                    null
                }
            }
        }
    }

    private fun applyLyricFields(
        base: PluginLyricLine,
        candidate: PluginLyricLine,
        changedFields: Set<PluginLyricField>
    ): PluginLyricLine = base.copy(
        begin = candidate.begin.takeIf { PluginLyricField.BEGIN in changedFields } ?: base.begin,
        end = candidate.end.takeIf { PluginLyricField.END in changedFields } ?: base.end,
        duration = candidate.duration.takeIf { PluginLyricField.DURATION in changedFields }
            ?: base.duration,
        isAlignedRight = if (PluginLyricField.IS_ALIGNED_RIGHT in changedFields) {
            candidate.isAlignedRight
        } else {
            base.isAlignedRight
        },
        metadata = if (PluginLyricField.METADATA in changedFields) {
            candidate.metadata
        } else {
            base.metadata
        },
        text = if (PluginLyricField.TEXT in changedFields) candidate.text else base.text,
        words = if (PluginLyricField.WORDS in changedFields) candidate.words else base.words,
        secondary = if (PluginLyricField.SECONDARY in changedFields) {
            candidate.secondary
        } else {
            base.secondary
        },
        secondaryWords = if (PluginLyricField.SECONDARY_WORDS in changedFields) {
            candidate.secondaryWords
        } else {
            base.secondaryWords
        },
        translation = if (PluginLyricField.TRANSLATION in changedFields) {
            candidate.translation
        } else {
            base.translation
        },
        translationWords = if (PluginLyricField.TRANSLATION_WORDS in changedFields) {
            candidate.translationWords
        } else {
            base.translationWords
        },
        roma = if (PluginLyricField.ROMA in changedFields) candidate.roma else base.roma
    )

    private fun hasValidLyrics(lyrics: List<PluginLyricLine>): Boolean {
        if (lyrics.size > MAX_LYRICS) return false
        var previousBegin = Long.MIN_VALUE
        var totalWords = 0
        return lyrics.all { line ->
            val lineValid = line.begin >= 0L &&
                    line.end > line.begin &&
                    line.duration == line.end - line.begin &&
                    line.begin >= previousBegin &&
                    (!line.text.isNullOrBlank() || !line.words.isNullOrEmpty())
            if (!lineValid) return@all false
            previousBegin = line.begin

            val wordLists = listOf(line.words, line.secondaryWords, line.translationWords)
            if (wordLists.any { it != null && it.size > MAX_WORDS_PER_LINE }) return@all false
            totalWords += wordLists.sumOf { it?.size ?: 0 }
            if (totalWords > MAX_TOTAL_WORDS) return@all false
            wordLists.all { words -> hasValidWords(line, words) }
        }
    }

    private fun hasValidWords(
        line: PluginLyricLine,
        words: List<PluginWord>?
    ): Boolean {
        if (words == null) return true
        var previousEnd = line.begin
        return words.all { word ->
            val valid = word.begin >= line.begin &&
                    word.end > word.begin &&
                    word.end <= line.end &&
                    word.duration == word.end - word.begin &&
                    word.begin >= previousEnd
            if (valid) previousEnd = word.end
            valid
        }
    }

    private fun toPluginMetadata(metadata: LyricMetadata): PluginMetadata =
        PluginMetadata(metadata.entries.associate { it.key to it.value })

    private fun toInternalMetadata(metadata: PluginMetadata): LyricMetadata =
        LyricMetadata(metadata.values)

    private fun toPluginLine(line: RichLyricLine): PluginLyricLine =
        PluginLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = line.metadata?.let(::toPluginMetadata),
            text = line.text,
            words = line.words?.map(::toPluginWord),
            secondary = line.secondary,
            secondaryWords = line.secondaryWords?.map(::toPluginWord),
            translation = line.translation,
            translationWords = line.translationWords?.map(::toPluginWord),
            roma = line.roma
        )

    private fun toInternalLine(line: PluginLyricLine): RichLyricLine =
        RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = line.metadata?.let(::toInternalMetadata),
            text = line.text,
            words = line.words?.map(::toInternalWord),
            secondary = line.secondary,
            secondaryWords = line.secondaryWords?.map(::toInternalWord),
            translation = line.translation,
            translationWords = line.translationWords?.map(::toInternalWord),
            roma = line.roma
        )

    private fun toPluginWord(word: LyricWord): PluginWord = PluginWord(
        begin = word.begin,
        end = word.end,
        duration = word.duration,
        text = word.text,
        metadata = word.metadata?.let(::toPluginMetadata)
    )

    private fun toInternalWord(word: PluginWord): LyricWord = LyricWord(
        begin = word.begin,
        end = word.end,
        duration = word.duration,
        text = word.text,
        metadata = word.metadata?.let(::toInternalMetadata)
    )
}
