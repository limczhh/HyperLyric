package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal class TranslationResponseParser(
    private val logger: LyricEnhancementLogger,
) {
    private companion object {
        const val MAX_LOG_BODY_LENGTH = 1_000
    }

    fun parse(content: String, requestIndices: Set<Int>): List<TranslationItem> {
        val json = extractJson(content) ?: return emptyList()
        val root = runCatching { JSONTokener(json).nextValue() }.getOrNull() ?: return emptyList()
        val array = when (root) {
            is JSONObject -> findTranslationArray(root)
            is JSONArray -> root
            else -> null
        } ?: return emptyList()

        val accepted = LinkedHashMap<Int, TranslationItem>()
        val orderedRequestIndices = requestIndices.sorted()
        var rejectedIndex = 0
        var rejectedTranslation = 0
        var duplicate = 0
        var unsupported = 0
        var stringItems = 0
        for (position in 0 until array.length()) {
            when (val rawItem = array.opt(position)) {
                is JSONObject -> {
                    val itemIndex = readIndex(rawItem)
                    if (itemIndex == null || itemIndex !in requestIndices) {
                        rejectedIndex++
                        continue
                    }
                    val translation = readTranslation(rawItem)
                    if (translation.isBlank()) {
                        rejectedTranslation++
                        continue
                    }
                    if (itemIndex in accepted) {
                        duplicate++
                        continue
                    }
                    accepted[itemIndex] = TranslationItem(itemIndex, translation)
                }

                // Some OpenAI-compatible models return {"translations":["..."]}
                // despite the requested indexed-object schema. Only map this form by the
                // original request order, never by an arbitrary response index.
                is String -> {
                    stringItems++
                    val itemIndex = orderedRequestIndices.getOrNull(position)
                    if (itemIndex == null) {
                        rejectedIndex++
                        continue
                    }
                    val translation = rawItem.trim()
                    if (translation.isBlank()) {
                        rejectedTranslation++
                        continue
                    }
                    if (itemIndex in accepted) {
                        duplicate++
                        continue
                    }
                    accepted[itemIndex] = TranslationItem(itemIndex, translation)
                }

                else -> unsupported++
            }
        }
        val result = accepted.values.toList()
        logger.debug(
            "解析翻译响应完成: parsed=${array.length()}, accepted=${result.size}, " +
                    "rejectedIndex=$rejectedIndex, rejectedTranslation=$rejectedTranslation, " +
                    "duplicate=$duplicate, unsupported=$unsupported, stringItems=$stringItems"
        )
        return result
    }

    private fun findTranslationArray(root: JSONObject): JSONArray? =
        listOf("translations", "translation", "results", "items")
            .asSequence()
            .mapNotNull(root::optJSONArray)
            .firstOrNull()

    private fun readIndex(item: JSONObject): Int? {
        for (key in listOf("index", "line_index", "lineIndex", "id")) {
            if (!item.has(key)) continue
            when (val value = item.opt(key)) {
                is Number -> return value.toInt()
                is String -> value.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun readTranslation(item: JSONObject): String {
        for (key in listOf(
            "trans",
            "translation",
            "translated",
            "translated_text",
            "translatedText",
            "target"
        )) {
            if (!item.has(key)) continue
            item.optString(key, "").trim().takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    private fun extractJson(raw: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)
            ?.groupValues
            ?.getOrNull(1)
        val trimmed = (fenced ?: raw).trim()
        if (trimmed.isEmpty()) return null

        val objectStart = trimmed.indexOf('{')
        val arrayStart = trimmed.indexOf('[')
        val start = listOf(objectStart, arrayStart)
            .filter { it >= 0 }
            .minOrNull()
            ?: return null.also {
                logger.warn("翻译响应缺少 JSON: body=${trimForLog(trimmed)}")
            }
        val open = trimmed[start]
        val close = if (open == '{') '}' else ']'
        val end = findMatching(trimmed, start, open, close)
        return end.takeIf { it > start }?.let { trimmed.substring(start, it + 1) }
            ?: run {
                logger.warn("翻译响应缺少 JSON: body=${trimForLog(trimmed)}")
                null
            }
    }

    private fun findMatching(text: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    quoted = false
                }
                continue
            }
            when (char) {
                '"' -> quoted = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    private fun trimForLog(value: String): String =
        if (value.length <= MAX_LOG_BODY_LENGTH) value else value.take(MAX_LOG_BODY_LENGTH) + "..."
}
