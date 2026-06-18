package com.lidesheng.hyperlyric.utils

data class MigrationItem(
    val text: String,
    val summary: String? = null,
    val url: String? = null
)

data class MigrationNote(
    val versionCode: Int,
    val items: List<MigrationItem>
)

object MigrationData {
    val notes = listOf(
        MigrationNote(
            versionCode = 1931,
            items = listOf(
                MigrationItem(
                    text = "HyperLyric v6.0 往后，需要Lyricon central才可继续使用Lyricon 歌词源",
                    summary = "点击跳转下载 Lyricon central 模块",
                    url = "https://github.com/tomakino/lyricon/releases/tag/core"
                )
            )
        )
    )
}
