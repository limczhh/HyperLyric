package com.lidesheng.hyperlyric.online.source.qm


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.POST


@Serializable
data class QmRequestBody(
    val comm: Map<String, String>,
    @SerialName("req_0") val req0: QmRequestModule
)

@Serializable
data class QmRequestModule(
    val method: String,
    val module: String,
    val param: Map<String, JsonElement>
)


@Serializable
data class QmBaseWrapper<T>(
    @SerialName("req_0") val req0: QmResponseModule<T>
)

@Serializable
data class QmResponseModule<T>(
    val code: Int = 0,
    val data: T? = null
)

@Serializable
data class QmSearchData(
    val body: QmSearchBody? = null
)

@Serializable
data class QmSearchBody(
    @SerialName("item_song") val songs: List<QmSongItem> = emptyList()
)

@Serializable
data class QmSongItem(
    val id: String,
    val mid: String,
    val title: String,
    val singer: List<QmSinger> = emptyList(),
    val album: QmAlbum,
    val interval: Int,
    @SerialName("index_album") val trackerNumber: String = "",
    @SerialName("genre") val genre: String = "",
    @SerialName("time_public") val timePublic: String? = null
)

@Serializable
data class QmSinger(val name: String)

@Serializable
data class QmAlbum(val name: String, val mid: String = "")

@Serializable
data class QmLyricsData(
    val lyric: String = "",
    val trans: String = "",
    val roma: String = ""
)

interface QmApi {
    @POST("cgi-bin/musicu.fcg")
    suspend fun searchSong(@Body body: QmRequestBody): QmBaseWrapper<QmSearchData>

    @POST("cgi-bin/musicu.fcg")
    suspend fun getLyrics(@Body body: QmRequestBody): QmBaseWrapper<QmLyricsData>
}

