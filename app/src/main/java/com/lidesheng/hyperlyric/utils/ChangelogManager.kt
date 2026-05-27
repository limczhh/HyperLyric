package com.lidesheng.hyperlyric.utils

data class ChangelogItem(
    val version: String,
    val title: String,
    val summary: String
)

object ChangelogManager {
    fun getChangelog(): List<ChangelogItem> {
        return listOf(
            ChangelogItem(
                version = "6.0-1929",
                title = "测试内容",
                summary = "- 新增小米超级岛“分离歌词”歌词模式，选择“分离歌词”后重启系统界面，不允许自定义容器内容\n" +
                        "- 修复AI翻译自动跳过中文歌曲不生效的错误\n" +
                        "- 更改在线歌词缓存至外部存储\n" +
                        "- 新增应用日志，优化调整更多日志内容"
            ),
            ChangelogItem(
                version = "5.10-1929",
                title = "食盐了，上个版本的shizuku操作不当会引起软件崩溃，得连夜赶工o_o ....",
                summary = "- 修复shizuku异常关闭引起的错误\n" +
                        "- 超级岛内容长度下限改至20\n" +
                        "- 移除“羽化边缘长度”的滑块"
            ),
            ChangelogItem(
                version = "5.9-1929",
                title = "以后更新节奏放缓",
                summary = "- 新增shizuku绕过焦点通知限制\n" +
                        "- 优化日志页面和日志内容\n" +
                        "- 优化bottomsheet页面样式\n" +
                        "- 修复焦点通知aod显示内容错误\n" +
                        "- 修复ai翻译引发的错误"
            ),
            ChangelogItem(
                version = "5.9-1928",
                title = "仅针对xposed功能的修复",
                summary = "- 修复取色延迟的错误"
            ),
            ChangelogItem(
                version = "5.8-1928",
                title = "小更新",
                summary = "- 超级岛内容新增metadata标题和艺术家双行信息显示\n" +
                        "- 新增焦点通知歌词“歌曲信息强调色”\n" +
                        "- 新增焦点通知超级岛“歌词强调色”\n" +
                        "- 优化“通知型灵动岛歌词”的专辑封面取色逻辑，大幅提升\n" +
                        "- 优化xposed相关功能的稳定性和兼容性\n" +
                        "- 优化xposed相关功能的日志输出\n" +
                        "- 优化多个页面性能"
            ),
            ChangelogItem(
                version = "5.7-1928",
                title = "本次更新日志如下",
                summary = "- 新增音乐信息“居中显示”功能\n" +
                        "- 新增“更新日志”页面\n" +
                        "- 新增“歌曲信息”滚动\n" +
                        "- 新增超级岛“边缘光效封面色”\n" +
                        "- 主页“特殊功能”新增模块激活状态检测\n" +
                        "- 歌曲信息“滚动延迟”和“滚动循环间隔”最大值提升到10秒\n" +
                        "- 悬浮底栏新增高光样式\n" +
                        "- 合并“歌词提供服务”和“歌词白名单”页面，优化白名单逻辑，无需手动添加\n" +
                        "- 优化翻译和ai翻译功能\n" +
                        "- 优化主页底栏、权限弹窗、日志等级的样式\n" +
                        "- 优化日志扫描内容\n" +
                        "- 优化应用架构\n" +
                        "- 应用内全部toast替换成snackbar\n" +
                        "- 优化模糊效果\n" +
                        "- 优化组件间距\n" +
                        "- 优化性能"
            ),
            ChangelogItem(
                version = "",
                title = "",
                summary = "v5.7之前的更新内容请前往酷安或github查看"
            )
        )
    }
}
