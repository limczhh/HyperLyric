package com.lidesheng.hyperlyric.utils

data class ChangelogItem(
    val version: String,
    val title: String,
    val summary: String
)

object ChangelogData {
    fun getChangelog(): List<ChangelogItem> {
        return listOf(
            ChangelogItem(
                version = "6.4-1934",
                title = "燃尽了",
                summary = "- 新增媒体卡片“音频封面样式”、“隐藏来源标识”、“隐藏设备切换按钮”功能\n" +
                        "- 新增媒体卡片动态流光“封面流光”样式\n" +
                        "- 新增媒体卡片背景“柔光封面”样式\n" +
                        "- 新增 AI 翻译强制覆盖已有译文功能\n" +
                        "- 新增“边缘光效进度条”上下双向样式\n" +
                        "- 优化超级岛展开态媒体卡片浅色背景下的显示效果\n" +
                        "- 优化媒体卡片动态流光表现\n" +
                        "- 移除歌词翻译显示控制值，由hyperlyric统一管理\n" +
                        "- 修复通知中心媒体卡片收起动画异常\n" +
                        "- 修复了一些错误\n" +
                        "- 优化了一些性能\n" +
                        "- 优化了页面布局以及关于页面\n" +
                        "- 优化热重载内容\n" +
                        "- 规范日志内容输出"
            ),
            ChangelogItem(
                version = "6.3-1934",
                title = "应该算是大更新吧",
                summary = "- 新增“边缘光效进度条”功能\n" +
                        "- 新增“音频律动封面色”功能\n" +
                        "- 新增“音频封面样式”自定义功能\n" +
                        "- 新增“媒体卡片”背景颜色和动态流光自定义功能\n" +
                        "- 修复了一些错误\n" +
                        "- 优化页面布局"
            ),
            ChangelogItem(
                version = "6.2-1933",
                title = "晚上好",
                summary = "- miuix 更新至0.9.3\n" +
                        "- libxposed api 更新至102，适配热重载\n" +
                        "- 优化小米超级岛歌词自定义配置的界面布局"
            ),
            ChangelogItem(
                version = "6.1-1933",
                title = "修 bug 为主~",
                summary = "- 新增应用语言切换功能\n" +
                        "- 新增下一句歌词显示功能，仅支持 lyricon 和 lyricinfo 歌词源\n" +
                        "- 修复超级岛歌词和光效注入延迟的错误\n" +
                        "- 移除严苛的翻译显示判断条件\n" +
                        "- 优化应用收进超级岛的过渡动画\n" +
                        "- 移除超级岛设置页的长度控制滑条\n" +
                        "- 修复上个版本不小心删除的更新日志\n" +
                        "- 修复了一些 bug，优化了一些功能"
            ),
            ChangelogItem(
                version = "6.0-1932",
                title = "本次更新只针对无root模式",
                summary = "- 重构 service 服务\n" +
                        "- 新增 metadata 歌词源选择\n" +
                        "- 移除在线歌词缓存上限\n" +
                        "- 更改歌曲信息的数据来源\n" +
                        "- 修复一些遗漏的错误"
            ),
            ChangelogItem(
                version = "6.0-1931",
                title = "comming soon",
                summary = "- 新增应用图标\n" +
                        "- 新增 SuperLyric 和 LyricInfo 歌词源，支持实时切换。SuperLyric 不支持 ai 翻译功能\n" +
                        "- 新增“分离歌词”歌词模式，支持实时切换\n" +
                        "- 新增应用日志，优化、调整、新增更多日志内容和日志显示\n" +
                        "- 新增ai翻译“自动跳过已有翻译歌曲”\n" +
                        "- 新增版本更新提示弹窗\n" +
                        "- 大幅重构项目\n" +
                        "- 优化小米超级岛歌曲信息组合内容和数据获取\n" +
                        "- 优化引导页面和使用帮助页面\n" +
                        "- 移除 Lyricon central\n" +
                        "- 移除hook模式白名单绑定和判断，暂停注入控制移至主页“小米超级岛歌词”开关，优化使用体验\n" +
                        "- 修复AI翻译自动跳过中文歌曲不生效的错误\n" +
                        "- 修复边缘光效颜色不独立生效的问题\n" +
                        "- 修复个别在线歌词会误删空格的错误\n" +
                        "- 修复 LyricParsers 会误删空格的错误\n" +
                        "- 修复不少想不起来的错误\n" +
                        "- 更改在线歌词缓存至外部存储"
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
