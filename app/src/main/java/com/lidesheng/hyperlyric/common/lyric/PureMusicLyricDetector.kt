package com.lidesheng.hyperlyric.common.lyric

private val PURE_MUSIC_MARKER = "纯音乐请欣赏"

/** 判断单行歌词文本去除标点与空白后是否为纯音乐标记。 */
fun isPureMusicMarkerLine(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val normalized = text.filter { it.isLetterOrDigit() }
    return normalized == PURE_MUSIC_MARKER
}

/** 判断整段歌词文本（按行拆分）是否包含纯音乐标记。 */
fun isPureMusicLyricText(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    return text.lines().any(::isPureMusicMarkerLine)
}

/** 判断歌词行文本集合中是否包含纯音乐标记。 */
fun isPureMusicSongLyrics(lines: List<String?>): Boolean =
    lines.any(::isPureMusicMarkerLine)
