package com.lidesheng.hyperlyric.common.lyric

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine

const val METADATA_RESOLVED_SECONDARY_CONTENT = "resolvedSecondaryContent"

/** The explicit content types that may occupy the single secondary lyric row. */
enum class LyricSecondaryContent(val preferenceValue: String) {
    /** A source-provided secondary vocal lane, such as Apple Music background vocals. */
    BACKGROUND_VOCAL("background_vocal"),

    /** A second independently timed lyric line active at the same playback position. */
    OVERLAPPING_LINE("overlapping_line"),

    TRANSLATION("translation"),
    ROMA("roma"),
    NEXT_LINE("next_line");

    companion object {
        val DEFAULT_ORDER: List<LyricSecondaryContent> = listOf(
            BACKGROUND_VOCAL,
            OVERLAPPING_LINE,
            TRANSLATION,
            NEXT_LINE,
            ROMA
        )

        fun fromPreferenceValue(value: String): LyricSecondaryContent? =
            entries.firstOrNull { it.preferenceValue == value.trim().lowercase() }
    }
}

/** One immutable snapshot of the user-selectable secondary lyric content policy. */
data class LyricContentDisplaySettings(
    val showTranslation: Boolean,
    val showRoma: Boolean,
    val showNextLyric: Boolean,
    val order: List<LyricSecondaryContent>,
    val showBackgroundVocal: Boolean = true,
    val showOverlappingLine: Boolean = true
) {
    fun isEnabled(content: LyricSecondaryContent): Boolean = when (content) {
        LyricSecondaryContent.BACKGROUND_VOCAL -> showBackgroundVocal
        LyricSecondaryContent.OVERLAPPING_LINE -> showOverlappingLine
        LyricSecondaryContent.TRANSLATION -> showTranslation
        LyricSecondaryContent.ROMA -> showRoma
        LyricSecondaryContent.NEXT_LINE -> showNextLyric
    }

    fun hasEnabledContent(): Boolean = LyricSecondaryContent.entries.any { isEnabled(it) }
}

/**
 * Resolves one content type for the current song.
 *
 * When [songLines] is available, a configured content type wins based on whether it exists
 * anywhere in the song, not whether it happens to exist on the current line. This keeps a
 * higher-priority translation or romanization lane stable when individual lines are missing it.
 * Streaming sources may omit [songLines], in which case the current line remains the only
 * availability information available to the consumer.
 */
fun LyricContentDisplaySettings.preferredContentFor(
    line: IRichLyricLine,
    nextLine: IRichLyricLine?,
    songLines: List<IRichLyricLine>? = null,
    overlappingLine: IRichLyricLine? = null
): LyricSecondaryContent? = LyricContentDisplayPolicy
    .normalizeOrder(order)
    .firstOrNull { content ->
        isEnabled(content) && when (content) {
            LyricSecondaryContent.BACKGROUND_VOCAL -> line.hasSourceSecondaryContent()
            LyricSecondaryContent.OVERLAPPING_LINE -> overlappingLine.hasLyricContent()
            LyricSecondaryContent.TRANSLATION,
            LyricSecondaryContent.ROMA -> songLines?.any { songLine ->
                content.hasContent(songLine)
            }
                ?: content.hasContent(line)

            LyricSecondaryContent.NEXT_LINE -> nextLine.hasLyricContent()
        }
    }

/** Returns the first enabled, non-next-line content for the current song. */
fun LyricContentDisplaySettings.preferredSecondaryContentFor(
    line: IRichLyricLine,
    songLines: List<IRichLyricLine>? = null
): LyricSecondaryContent? = LyricContentDisplayPolicy
    .normalizeOrder(order)
    .firstOrNull { content ->
        content != LyricSecondaryContent.NEXT_LINE &&
                content != LyricSecondaryContent.OVERLAPPING_LINE &&
                isEnabled(content) &&
                (songLines?.any { songLine -> content.hasContent(songLine) }
                    ?: content.hasContent(line))
}

fun LyricSecondaryContent.textOf(line: IRichLyricLine): String? = when (this) {
    LyricSecondaryContent.BACKGROUND_VOCAL -> line.secondary
    LyricSecondaryContent.OVERLAPPING_LINE -> null
    LyricSecondaryContent.TRANSLATION -> line.translation
    LyricSecondaryContent.ROMA -> line.roma
    LyricSecondaryContent.NEXT_LINE -> null
}

fun LyricSecondaryContent.wordsOf(line: IRichLyricLine): List<LyricWord>? = when (this) {
    LyricSecondaryContent.BACKGROUND_VOCAL -> line.secondaryWords
    LyricSecondaryContent.OVERLAPPING_LINE -> null
    LyricSecondaryContent.TRANSLATION -> line.translationWords
    LyricSecondaryContent.ROMA,
    LyricSecondaryContent.NEXT_LINE -> null
}

fun LyricSecondaryContent.hasContent(line: IRichLyricLine): Boolean =
    !textOf(line).isNullOrBlank() || !wordsOf(line).isNullOrEmpty()

private fun IRichLyricLine?.hasLyricContent(): Boolean =
    this != null && (!text.isNullOrBlank() || !words.isNullOrEmpty())

/** Reads and serializes the source-independent secondary lyric display policy. */
object LyricContentDisplayPolicy {
    private val LEGACY_ORDER: List<LyricSecondaryContent> = listOf(
        LyricSecondaryContent.TRANSLATION,
        LyricSecondaryContent.NEXT_LINE,
        LyricSecondaryContent.ROMA
    )

    fun read(prefs: SharedPreferences): LyricContentDisplaySettings {
        return LyricContentDisplaySettings(
            showTranslation = prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_SHOW_TRANSLATION,
                RootConstants.DEFAULT_HOOK_LYRIC_SHOW_TRANSLATION
            ),
            showRoma = prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_SHOW_ROMA,
                RootConstants.DEFAULT_HOOK_LYRIC_SHOW_ROMA
            ),
            showNextLyric = prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_SHOW_NEXT_LINE,
                RootConstants.DEFAULT_HOOK_LYRIC_SHOW_NEXT_LINE
            ),
            order = readOrder(prefs),
            showBackgroundVocal = prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_SHOW_BACKGROUND_VOCAL,
                RootConstants.DEFAULT_HOOK_LYRIC_SHOW_BACKGROUND_VOCAL
            ),
            showOverlappingLine = prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_SHOW_OVERLAPPING_LINE,
                RootConstants.DEFAULT_HOOK_LYRIC_SHOW_OVERLAPPING_LINE
            )
        )
    }

    /** Encodes an order for SharedPreferences while repairing omissions and duplicates. */
    fun encodeOrder(order: List<LyricSecondaryContent>): String =
        normalizeOrder(order).joinToString(",") { it.preferenceValue }

    fun normalizeOrder(order: List<LyricSecondaryContent>): List<LyricSecondaryContent> =
        buildList {
            order.forEach { content ->
                if (content !in this) add(content)
            }
            LyricSecondaryContent.DEFAULT_ORDER.forEach { content ->
                if (content !in this) add(content)
            }
        }

    private fun readOrder(prefs: SharedPreferences): List<LyricSecondaryContent> {
        val raw = prefs.all[RootConstants.KEY_HOOK_LYRIC_SECONDARY_ORDER]
            ?: return LyricSecondaryContent.DEFAULT_ORDER
        val values = when (raw) {
            is String -> raw.split(',')
            is Set<*> -> raw.filterIsInstance<String>()
            else -> RootConstants.DEFAULT_HOOK_LYRIC_SECONDARY_ORDER.split(',')
        }
        val parsed = values.mapNotNull(LyricSecondaryContent::fromPreferenceValue)
        // Upgrade the old untouched default so new installations and existing default users both
        // see the newly supported source lanes before translation/next-line fallback.
        return if (parsed == LEGACY_ORDER) {
            LyricSecondaryContent.DEFAULT_ORDER
        } else {
            normalizeOrder(parsed)
        }
    }
}

private fun IRichLyricLine.hasSourceSecondaryContent(): Boolean =
    !secondary.isNullOrBlank() || !secondaryWords.isNullOrEmpty()
