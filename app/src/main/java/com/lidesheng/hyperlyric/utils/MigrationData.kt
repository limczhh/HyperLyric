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
            versionCode = 1939,
            items = listOf(
                MigrationItem(
                    text = "温馨提示",
                    summary = "使用 lyricinfo 歌词源的用户需要下载 v6 版本以继续正常使用",
                    url = "https://github.com/limczhh/LyricInfo/releases/tag/v6"
                )
            )
        ),
        MigrationNote(
            versionCode = 1937,
            items = listOf(
                MigrationItem(
                    text = "Online 和 Offline 版本已合并",
                    summary = "使用 AI 翻译功能的用户请下载 AI 翻译插件并导入以继续使用",
                    url = "https://github.com/limczhh/HyperLyric/releases/tag/1937-7.3"
                )
            )
        ),
        MigrationNote(
            versionCode = 1934,
            items = listOf(
                MigrationItem(
                    text = "本次更新请重启系统界面",
                    summary = "重启手机也行~"
                )
            )
        ),
        MigrationNote(
            versionCode = 1933,
            items = listOf(
                MigrationItem(
                    text = "再一次温馨提示，HyperLyric v6.0 往后需要额外安装 lyricon central 才可继续使用 lyricon 歌词源",
                    summary = "点我跳转下载 lyricon central 模块",
                    url = "https://github.com/tomakino/lyricon/releases/tag/core"
                )
            )
        ),
        MigrationNote(
            versionCode = 1932,
            items = listOf(
                MigrationItem(
                    text = "本次更新和xposed模块功能无关，但是使用无 root 模式的请注意",
                    summary = "新版本大幅更改了“通知型灵动岛歌词”的歌词数据来源，默认选择 metadata（自动）即可。当然，你也可以选择指定的歌词源"
                )
            )
        ),
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
