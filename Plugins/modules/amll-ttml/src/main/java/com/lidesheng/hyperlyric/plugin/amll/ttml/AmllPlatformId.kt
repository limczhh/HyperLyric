package com.lidesheng.hyperlyric.plugin.amll.ttml

/**
 * AMLL 平台 ID 字段枚举
 *
 * 对应 AMLL TTML DataBase `/v1/lyrics/get` 接口的平台 ID 查询参数。
 */
internal enum class AmllPlatformIdField(val queryParam: String) {
    /** 网易云音乐（ncmMusicId） */
    NCM("ncmMusicId"),

    /** QQ 音乐（qqMusicId） */
    QQ("qqMusicId"),

    /** Apple Music（appleMusicId） */
    APPLE("appleMusicId"),

    /** Spotify（spotifyId） */
    SPOTIFY("spotifyId")
}

/**
 * 平台 ID 解析器
 *
 * main 分支通过歌词源包名直接映射平台（AmllPlatformIdMapper）；
 * 插件当前拿不到 packageName（spec §7.1），以「ID 格式预判 + 多平台探测 +
 * 命中条目交叉校验」替代。宿主开放 packageName 后可经 [mapPackageName] 单次直查。
 */
internal object AmllPlatformId {

    /** 包名 → AMLL 平台 ID 字段映射（main 分支原样映射表，预留） */
    private val PACKAGE_TO_FIELD = mapOf(
        "com.netease.cloudmusic" to AmllPlatformIdField.NCM,
        "com.tencent.qqmusic" to AmllPlatformIdField.QQ,
        "com.apple.android.music" to AmllPlatformIdField.APPLE,
        "com.spotify.music" to AmllPlatformIdField.SPOTIFY
    )

    /**
     * 将歌词源包名映射到 AMLL 平台 ID 字段。
     *
     * @param packageName 歌词源包名
     * @return 对应的平台字段；未知包名返回 null（调用方回退到探测/搜索模糊匹配）
     */
    fun mapPackageName(packageName: String?): AmllPlatformIdField? =
        packageName?.let { PACKAGE_TO_FIELD[it] }

    /**
     * 按歌曲 ID 格式预判平台探测顺序：
     * - 纯数字 ID → NCM → QQ → APPLE（三家歌曲 ID 均为纯数字，国内用户 NCM 命中率最高）
     * - 含非数字字符（base62 等）→ 仅 SPOTIFY
     *
     * 每个平台的命中结果仍须经条目交叉校验（防跨平台 ID 撞号误匹配）。
     */
    fun probeOrderFor(songId: String): List<AmllPlatformIdField> {
        return if (songId.isNotEmpty() && songId.all { it.isDigit() }) {
            listOf(
                AmllPlatformIdField.NCM,
                AmllPlatformIdField.QQ,
                AmllPlatformIdField.APPLE
            )
        } else {
            listOf(AmllPlatformIdField.SPOTIFY)
        }
    }
}
