package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

/**
 * Shared contract for the user-defined music information layout.
 *
 * The serialized field lists intentionally use a comma-separated string rather than a
 * StringSet, because field order is part of the user-visible layout.
 */
object MusicInfoLayoutPolicy {
    const val FIELD_TITLE = "title"
    const val FIELD_ARTIST = "artist"
    const val FIELD_ALBUM = "album"
    const val FIELD_DURATION = "duration"
    const val FIELD_ELAPSED = "elapsed"
    const val FIELD_REMAINING = "remaining"
    const val FIELD_PROGRESS_PERCENT = "progress_percent"

    const val SEPARATOR_PLUS = "plus"
    const val SEPARATOR_SPACE = "space"
    const val SEPARATOR_COMMA = "comma"
    const val SEPARATOR_IDEOGRAPHIC_COMMA = "ideographic_comma"
    const val SEPARATOR_SLASH = "slash"
    const val SEPARATOR_HYPHEN = "hyphen"
    const val SEPARATOR_NONE = "none"

    val supportedFields: Set<String> = setOf(
        FIELD_TITLE,
        FIELD_ARTIST,
        FIELD_ALBUM,
        FIELD_DURATION,
        FIELD_ELAPSED,
        FIELD_REMAINING,
        FIELD_PROGRESS_PERCENT
    )

    val defaultFirstLine: List<String> = listOf(FIELD_TITLE)
    val defaultSecondLine: List<String> = listOf(FIELD_ARTIST)

    fun readFields(
        prefs: SharedPreferences,
        key: String,
        defaultValue: List<String>
    ): List<String> {
        val storedValue = prefs.getString(key, null) ?: return defaultValue
        return storedValue
            .split(',')
            .map(String::trim)
            .filter { it in supportedFields }
            .distinct()
    }

    fun readSeparator(prefs: SharedPreferences): String {
        return prefs.getString(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SEPARATOR,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_INFO_SEPARATOR
        )?.takeIf(::isSupportedSeparator)
            ?: RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_INFO_SEPARATOR
    }

    private val titleAliasPattern =
        Regex("""（[^（）]*）|\([^()]*\)|【[^【】]*】|\[[^\[\]]*\]""")
    private val collapsedSpaces = Regex("\\s{2,}")

    fun readHideTitleAlias(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS
        )
    }

    /**
     * 移除歌名中成对括号（全角 （）【】 与半角 ()[]）内的别名，用于显示。
     * 移除后折叠连续空格并去除首尾空白；若结果为空则回退原标题。
     */
    fun stripTitleAlias(title: String): String {
        val stripped = title
            .replace(titleAliasPattern, "")
            .replace(collapsedSpaces, " ")
            .trim()
        return stripped.ifBlank { title }
    }

    fun separatorValue(separator: String): String = when (separator) {
        SEPARATOR_PLUS -> " + "
        SEPARATOR_SPACE -> " "
        SEPARATOR_COMMA -> ", "
        SEPARATOR_IDEOGRAPHIC_COMMA -> "、"
        SEPARATOR_SLASH -> " / "
        SEPARATOR_NONE -> ""
        else -> " - "
    }

    fun isSupportedSeparator(separator: String): Boolean = separator in setOf(
        SEPARATOR_PLUS,
        SEPARATOR_SPACE,
        SEPARATOR_COMMA,
        SEPARATOR_IDEOGRAPHIC_COMMA,
        SEPARATOR_SLASH,
        SEPARATOR_HYPHEN,
        SEPARATOR_NONE
    )
}
