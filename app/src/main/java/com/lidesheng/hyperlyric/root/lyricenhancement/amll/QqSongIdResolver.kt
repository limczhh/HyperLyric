package com.lidesheng.hyperlyric.root.lyricenhancement.amll

import com.lidesheng.hyperlyric.BuildConfig
import com.lidesheng.hyperlyric.root.utils.HookLogger
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * QQ 音乐数字 songId 与 songmid 的双向转换。
 *
 * AMLL 条目的 qqMusicIds 只登记两种标识符之一，而本地歌词源只提供数字 songId，
 * 因此第一个候选未命中时需要换取另一个标识符作第二候选。
 *
 * - 单次尝试不重试：失败即降级为单候选，留给下次播放再试
 * - 网络层用裸 [HttpURLConnection]，root 模块无 Retrofit/OkHttp
 * - 确定性结论（换到了、或确认无对应曲目）都写缓存，避免每次播放重复请求；
 *   只有网络失败与意外异常不写，留给下次播放重试
 */
internal object QqSongIdResolver {

    private const val LOG_TAG = "LyricEnhancement/AmllTtml"
    private const val ENDPOINT = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000

    /** 确定性换不到时的负缓存哨兵 */
    private const val NEGATIVE_SENTINEL = "-"

    private val USER_AGENT =
        "HyperLyric/" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"

    /**
     * 已知一个 QQ 标识符，换回另一个。
     *
     * @param known 数字 songId 或 songmid
     * @return 另一方向的标识符；确定性换不到、网络失败或预算不足返回 null
     */
    fun counterpart(known: String, cache: TtmlCache, budget: ProcessingBudget): String? {
        cache.getQqCounterpart(known)?.let { cached ->
            val hit = cached.takeIf { it != NEGATIVE_SENTINEL }
            HookLogger.d(LOG_TAG, "QQ 转换缓存命中: known=" + known + ", value=" + cached)
            return hit
        }
        if (Thread.currentThread().isInterrupted) {
            HookLogger.d(LOG_TAG, "QQ ID 转换被中断: known=" + known)
            return null
        }
        if (!budget.hasEnoughForAttempt(CONNECT_TIMEOUT_MS.toLong())) {
            HookLogger.d(
                LOG_TAG,
                "预算不足，放弃 QQ ID 转换: remaining=" + budget.remainingMs() + "ms, known=" + known
            )
            return null
        }
        val knownIsMid = AmllPlatformId.isQqMid(known)
        val param = buildParam(known, knownIsMid)
        if (param == null) {
            HookLogger.d(LOG_TAG, "QQ ID 转换标识符非法: known=" + known)
            return null
        }
        val expectedGeneration = cache.currentGeneration()
        val result = try {
            requestCounterpart(param, known, knownIsMid)
        } catch (error: IOException) {
            // 网络失败不写缓存：下次播放允许重试
            HookLogger.d(
                LOG_TAG,
                "QQ ID 转换网络错误: type=" + error.javaClass.simpleName + ", known=" + known
            )
            return null
        } catch (error: Exception) {
            // 本地异常（协议转换、反序列化等）同样不写缓存，只降级为单候选。
            // 不能让它抛穿到 runFeature，否则整首歌的 AMLL 增强连搜索回退都跑不到
            // （分支口径对齐 AmllTtmlClient.executeWithRetry）。
            HookLogger.d(
                LOG_TAG,
                "QQ ID 转换异常: type=" + error.javaClass.simpleName + ", known=" + known
            )
            return null
        }
        if (result.cacheable) {
            cache.putQqCounterpart(
                known = known,
                value = result.value ?: NEGATIVE_SENTINEL,
                expectedGeneration = expectedGeneration
            )
        }
        val value = result.value
        if (value == null) {
            HookLogger.d(LOG_TAG, "QQ ID 转换未命中: known=" + known)
        } else {
            HookLogger.d(LOG_TAG, "QQ ID 转换: " + known + " -> " + value)
        }
        return value
    }

    /** 转换结果：value 为另一方向标识符；cacheable 表示该结论可写入缓存 */
    private data class CounterpartResult(
        val value: String?,
        val cacheable: Boolean,
    )

    /** 输入决定 param 字段：mid 用 song_mid，数字 id 用 song_id（必须为 JSON 数字） */
    private fun buildParam(known: String, knownIsMid: Boolean): JSONObject? = when {
        knownIsMid -> JSONObject().put("song_mid", known)
        else -> known.toLongOrNull()?.let { id -> JSONObject().put("song_id", id) }
    }

    private fun requestCounterpart(
        param: JSONObject,
        known: String,
        knownIsMid: Boolean,
    ): CounterpartResult {
        val body = JSONObject()
            .put("comm", JSONObject().put("ct", "24").put("cv", "0"))
            .put(
                "req_0",
                JSONObject()
                    .put("module", "music.pf_song_detail_svr")
                    .put("method", "get_song_detail_yqq")
                    .put("param", param)
            )
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(body.toString())
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                HookLogger.d(LOG_TAG, "QQ ID 转换请求失败: code=" + code + ", known=" + known)
                return CounterpartResult(null, cacheable = false)
            }
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseCounterpart(raw, knownIsMid)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 解析转换响应。
     *
     * 服务端 HTTP 状态恒为 200、顶层 code 恒为 0，成功与否只能由 req_0.code
     * 与 track_info.mid 判定；req_0.code=404 表示该标识符无对应曲目。
     */
    private fun parseCounterpart(raw: String, knownIsMid: Boolean): CounterpartResult {
        val request = runCatching { JSONObject(raw).optJSONObject("req_0") }.getOrNull()
            ?: return CounterpartResult(null, cacheable = false)
        val code = request.optInt("code", -1)
        val trackInfo = request.optJSONObject("data")?.optJSONObject("track_info")
        val mid = trackInfo?.optString("mid", "").orEmpty()
        if (code == 404 || (code == 0 && mid.isEmpty())) {
            // 无对应曲目属确定性结论，负缓存避免每次播放重复请求
            return CounterpartResult(null, cacheable = true)
        }
        if (code != 0 || trackInfo == null) {
            return CounterpartResult(null, cacheable = false)
        }
        val value = if (knownIsMid) {
            trackInfo.optLong("id", 0L).takeIf { it > 0L }?.toString()
        } else {
            mid
        }
        // 走到这里说明 code=0 且 track_info 存在，两个方向都属确定性结论：换到了就缓存
        // 标识符，换不到（如 code=0 但 track_info.id 缺失）就缓存哨兵。只缓存失败会让
        // mid-only 的歌曲每次播放都重发一次转换请求。
        return CounterpartResult(value, cacheable = true)
    }
}
