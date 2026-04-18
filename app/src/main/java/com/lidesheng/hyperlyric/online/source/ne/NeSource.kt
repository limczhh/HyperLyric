package com.lidesheng.hyperlyric.online.source.ne

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.lidesheng.hyperlyric.online.model.LyricsResult
import com.lidesheng.hyperlyric.online.model.SearchSource
import com.lidesheng.hyperlyric.online.model.SongSearchResult
import com.lidesheng.hyperlyric.online.model.Source
import com.lidesheng.hyperlyric.online.utils.NeCryptoUtils
import com.lidesheng.hyperlyric.online.utils.YrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

class NeSource(
    private val api: NeApi,
    private val json: Json,
    private val context: Context
): SearchSource {
    override val sourceType = Source.NE


    private val cookieMap = mutableMapOf<String, String>()
    private val DEVICEID_XOR_KEY = "3go8&$8*3*3h0k(2)2"


    private val initMutex = Mutex()
    private var isInitialized = false
    private var userId: Long = 0

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val APP_VER = "3.1.3.203419"
    private val OS_VER = "Microsoft-Windows-10--build-19045-64bit"
    private val DEVICE_ID = UUID.randomUUID().toString().replace("-", "")
    private var clientSign: String = ""

    private val PREF_NAME = "ne_source_prefs"
    private val KEY_COOKIES = "cookies"
    private val KEY_USER_ID = "user_id"
    private val KEY_INIT_TIME = "init_time"
    private val EXPIRE_TIME = 10 * 24 * 60 * 60 * 1000L

    init {
        clientSign = generateClientSign()
    }

    private fun loadSession(): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedInitTime = prefs.getLong(KEY_INIT_TIME, 0L)

        if (System.currentTimeMillis() - savedInitTime > EXPIRE_TIME) return false

        val savedCookiesJson = prefs.getString(KEY_COOKIES, null)
        val savedUserId = prefs.getLong(KEY_USER_ID, 0L)

        return if (!savedCookiesJson.isNullOrEmpty() && savedUserId != 0L) {
            try {
                val map: Map<String, String> = json.decodeFromString(savedCookiesJson)
                cookieMap.clear()
                cookieMap.putAll(map)
                userId = savedUserId
                true
            } catch (_: Exception) { false }
        } else false
    }


    private fun saveSession(uid: Long, cookies: Map<String, String>) {
        val prefs = context.getSharedPreferences("ne_source_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putLong("user_id", uid)
                .putString("cookies", json.encodeToString(cookies))
                .putLong("init_time", System.currentTimeMillis())
        }
    }

    private fun generateClientSign(): String {
        val mac = (1..6).joinToString(":") {
            "%02X".format(Random.nextInt(256))
        }
        val randomStr = (1..8).map {
            ('A'..'Z').random()
        }.joinToString("")
        val hashPart = (1..64).map {
            "0123456789abcdef".random()
        }.joinToString("")

        return "$mac@@@$randomStr@@@@@@$hashPart"
    }

    private suspend fun ensureInit() {
        if (isInitialized) return

        initMutex.withLock {
            if (isInitialized) return

            if (loadSession()) {
                isInitialized = true
                Log.d("NeSource", "从缓存恢复会话成功, uid: $userId")
                return@withLock
            }

            Log.d("NeSource", "开始执行匿名登录流程...")

            val modes = listOf(
                "MS-iCraft B760M WIFI", "ASUS ROG STRIX Z790", "MSI MAG B550 TOMAHAWK",
                "ASRock X670E Taichi", "GIGABYTE Z790 AORUS ELITE"
            )
            val preCookies = mutableMapOf(
                "os" to "pc",
                "deviceId" to DEVICE_ID,
                "osver" to "Microsoft-Windows-10--build-${Random.nextInt(20000, 30000)}-64bit",
                "clientSign" to clientSign,
                "channel" to "netease",
                "mode" to modes.random(),
                "appver" to APP_VER
            )

            val username = getAnonimousUsername(DEVICE_ID)

            val params = buildJsonObject {
                put("username", username)
                put("e_r", true)
            }

            try {
                val cookieStr = preCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VER",
                    "Referer" to "https://music.163.com/",
                    "Cookie" to cookieStr,
                    "Accept" to "*/*",
                    "Host" to "interface.music.163.com"
                )
                val requestBody = buildBody(params, preCookies)

                val response = api.request("https://interface.music.163.com/eapi/register/anonimous", headers, requestBody)

                if (response.isSuccessful) {
                    val setCookieHeaders = response.headers().values("Set-Cookie")
                    val responseCookies = mutableMapOf<String, String>()
                    setCookieHeaders.forEach { cookieLine ->
                        val cookiePair = cookieLine.split(";")[0].split("=")
                        if (cookiePair.size >= 2) {
                            responseCookies[cookiePair[0]] = cookiePair[1]
                        }
                    }

                    cookieMap.clear()
                    cookieMap.putAll(preCookies)
                    responseCookies["MUSIC_A"]?.let { cookieMap["MUSIC_A"] = it }
                    responseCookies["NMTID"]?.let { cookieMap["NMTID"] = it }
                    responseCookies["__csrf"]?.let { cookieMap["__csrf"] = it }

                    val wnmcid = "${(1..6).map { ('a'..'z').random() }.joinToString("")}.${System.currentTimeMillis()}.01.0"
                    cookieMap["WNMCID"] = wnmcid

                    val responseBodyBytes = response.body()?.bytes() ?: byteArrayOf()
                    if (responseBodyBytes.isNotEmpty()) {
                        val decrypted = NeCryptoUtils.aesDecrypt(responseBodyBytes)
                        val jsonRes = json.decodeFromString<JsonObject>(decrypted)

                        if (jsonRes["code"]?.jsonPrimitive?.content == "200") {
                            userId = jsonRes["userId"]?.jsonPrimitive?.longOrNull ?: 0

                            saveSession(userId, cookieMap)

                            isInitialized = true
                            Log.d("NeSource", "匿名登录成功: userId=$userId, 缓存已更新")
                        } else {
                            Log.e("NeSource", "登录失败, 服务器返回: ${jsonRes["code"]}")
                        }
                    }
                } else {
                    Log.e("NeSource", "HTTP 请求失败: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("NeSource", "初始化过程中发生异常", e)
            }
        }
    }


    private fun buildBody(params: JsonObject, preCookies: Map<String, String>): RequestBody {
        val headerParam = buildJsonObject {
            put("clientSign", preCookies["clientSign"] ?: "")
            put("osver", preCookies["osver"] ?: "")
            put("deviceId", preCookies["deviceId"] ?: "")
            put("os", preCookies["os"] ?: "")
            put("appver", preCookies["appver"] ?: "")
            put("requestId", System.currentTimeMillis().toString())
        }

        val finalParamsMap = params.toMutableMap()
        finalParamsMap["header"] = JsonPrimitive(json.encodeToString(headerParam))

        if (!finalParamsMap.containsKey("e_r")) {
            finalParamsMap["e_r"] = JsonPrimitive(true)
        }

        val paramsStr = json.encodeToString(JsonObject(finalParamsMap))
        val encryptPath = "/api/register/anonimous"

        val encryptedBytes = NeCryptoUtils.encryptParams(encryptPath, paramsStr)
        val encryptedHexString = encryptedBytes.joinToString("") { "%02x".format(it) }.uppercase()

        val formBody = "params=$encryptedHexString"
        return formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    private suspend fun doRequest(
        path: String,
        params: JsonObject,
        encryptPath: String? = null
    ): String = withContext(Dispatchers.Default) {

        val headerParam = buildJsonObject {
            put("clientSign", clientSign)
            put("osver", OS_VER)
            put("deviceId", DEVICE_ID)
            put("os", "pc")
            put("appver", APP_VER)
            put("requestId", System.currentTimeMillis().toString())
        }

        val headerParamString = json.encodeToString(headerParam)
        val finalParams = params.toMutableMap()
        finalParams["header"] = JsonPrimitive(headerParamString)
        if (!finalParams.containsKey("e_r")) {
            finalParams["e_r"] = JsonPrimitive(true)
        }

        val mergedParams = JsonObject(finalParams)
        val paramsStr = json.encodeToString(mergedParams)
        val actualEncryptPath = encryptPath ?: path.replace("/eapi/", "/api/")

        val encryptedBytes = NeCryptoUtils.encryptParams(actualEncryptPath, paramsStr)
        val encryptedHexString = encryptedBytes.joinToString("") { "%02x".format(it) }.uppercase()
        val formBody = "params=$encryptedHexString"
        val requestBody = formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())

        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VER",
            "Referer" to "https://music.163.com/",
            "Cookie" to cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        )

        val fullUrl = "https://interface.music.163.com$path"

        val responseBody = api.request(fullUrl, headers, requestBody)
        val responseBytes = responseBody.body()?.bytes() ?: return@withContext ""
        if (responseBytes.isEmpty()) return@withContext ""

        try {
            val decrypted = NeCryptoUtils.aesDecrypt(responseBytes)

            if (decrypted.contains("\"code\":301") || decrypted.contains("\"code\":401")) {
                Log.w("NeSource", "Session invalid (code 301/401), clearing cache...")
                isInitialized = false
            }

            return@withContext decrypted
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun search(keyword: String, page: Int, separator: String, pageSize: Int): List<SongSearchResult> = withContext(
        Dispatchers.IO) {
        ensureInit()

        val path = "/eapi/search/song/list/page"
        val offset = (page - 1) * 20

        val params = buildJsonObject {
            put("limit", pageSize.toString())
            put("offset", offset.toString())
            put("keyword", keyword)
            put("scene", "NORMAL")
            put("needCorrect", "true")
        }

        try {
            val rawJson = doRequest(path, params)
            Log.d("NeSource", "Search raw: $rawJson")

            val resp = json.decodeFromString<NeSearchResponse>(rawJson)

            if (resp.code != 200) return@withContext emptyList()
            Log.d("NeSource", "Search result: $resp")
            return@withContext resp.data?.resources?.map { res ->
                val song = res.baseInfo.simpleSongData
                SongSearchResult(
                    id = song.id.toString(),
                    title = song.name,
                    artist = song.artists.joinToString(separator) { it.name },
                    album = song.album.name,
                    duration = song.duration,
                    source = Source.NE,
                    date = song.publishTime?.let { formatMillisToDate(it) } ?: "",
                    trackerNumber = song.trackerNumber,
                    picUrl = song.album.picUrl
                )
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e("NeSource", "Search exception", e)
            return@withContext emptyList()
        }
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        ensureInit()
        val path = "/eapi/song/lyric/v1"
        val params = buildJsonObject {
            put("id", song.id.toLongOrNull() ?: 0)
            put("lv", "-1")
            put("tv", "-1")
            put("rv", "-1")
            put("yv", "-1")
        }
        val rawJson = doRequest(path, params)
        val resp = try {
            json.decodeFromString<NeLyricResponse>(rawJson)
        } catch (_: Exception) { return@withContext null }
        Log.d("NeSource", "Lyric lrc result: ${resp.lrc}")
        Log.d("NeSource", "Lyric yrc result: ${resp.yrc}")
        Log.d("NeSource", "Lyric translation result: ${resp.tlyric}")
        Log.d("NeSource", "Lyric romanization result: ${resp.romalrc}")
        return@withContext YrcParser.parse(
            yrc = resp.yrc?.lyric,
            lrc = resp.lrc?.lyric,
            tlyric = resp.tlyric?.lyric,
            romalrc = resp.romalrc?.lyric
        )
    }

    fun getAnonimousUsername(deviceId: String): String {
        val keyLength = DEVICEID_XOR_KEY.length
        val sb = StringBuilder()

        deviceId.forEachIndexed { index, char ->
            val keyChar = DEVICEID_XOR_KEY[index % keyLength]
            val xoredChar = (char.code xor keyChar.code).toChar()
            sb.append(xoredChar)
        }
        val xoredString = sb.toString()
        val md = MessageDigest.getInstance("MD5")
        val md5Digest = md.digest(xoredString.toByteArray(Charsets.UTF_8))

        val base64Md5 = Base64.encodeToString(md5Digest, Base64.NO_WRAP)

        val combinedStr = "$deviceId $base64Md5"

        return Base64.encodeToString(combinedStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    private fun formatMillisToDate(millis: Long): String {
        if (millis <= 0L) return ""

        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }
}
