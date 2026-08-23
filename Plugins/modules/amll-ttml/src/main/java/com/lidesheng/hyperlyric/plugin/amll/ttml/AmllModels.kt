package com.lidesheng.hyperlyric.plugin.amll.ttml

import org.json.JSONObject

/**
 * AMLL TTML DataBase API 响应模型（org.json 手解析）
 *
 * 按 AMLL 官方 schema 映射（https://api.amll.dev），对齐 main 分支 AmllModels.kt 的字段。
 * 未知字段忽略；解析异常由调用方按未命中处理。
 */

/** AMLL 歌词条目：search 接口返回的条目不含 [lyrics]；get 接口返回完整 TTML 字符串 */
internal data class SongItem(
    val id: Long? = null,
    val musicNames: List<String>? = null,
    val artistNames: List<String>? = null,
    val albumNames: List<String>? = null,
    val lyrics: String? = null
)

internal object AmllModels {

    /**
     * 解析 `/v1/lyrics/get` 响应体：`{status, data: SongItem}`，返回 data 条目。
     * data 缺失/非法 JSON 返回 null。
     */
    fun parseGetResponse(body: String): SongItem? {
        return try {
            parseSongItem(JSONObject(body).optJSONObject("data"))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析 `/v1/lyrics/search` 响应体：`{status, data: {items: [SongItem], total, page, pageSize}}`，
     * 返回 items 条目列表（不含 lyrics）。data/items 缺失或非法 JSON 返回 null。
     */
    fun parseSearchResponse(body: String): List<SongItem>? {
        return try {
            val items = JSONObject(body)
                .optJSONObject("data")
                ?.optJSONArray("items")
                ?: return null
            buildList(items.length()) {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    parseSongItem(item)?.let(::add)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun parseSongItem(json: JSONObject?): SongItem? {
        if (json == null) return null
        return SongItem(
            id = json.optLongOrNull("id"),
            musicNames = json.optStringList("musicNames"),
            artistNames = json.optStringList("artistNames"),
            albumNames = json.optStringList("albumNames"),
            lyrics = json.optStringOrNull("lyrics")
        )
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) {
            optString(key, "").takeIf { it.isNotEmpty() }
        } else {
            null
        }

    private fun JSONObject.optStringList(key: String): List<String>? {
        val array = optJSONArray(key) ?: return null
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index, "").takeIf { it.isNotBlank() }?.let(::add)
            }
        }.takeIf { it.isNotEmpty() }
    }
}
