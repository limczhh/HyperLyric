# HyperLyric
> 歌词上岛工具，作为Xposed模块服务于HyperOS 3系统，同时拥有基于系统通知实现歌词展示的功能。

## ✨ 功能特性

- 在小米HyperOS 3系统媒体超级岛显示歌词
- 移除HyperOS 3.0.300媒体超级岛下拉白名单
- 移除小米焦点通知白名单
- 基于安卓实时通知/小米焦点通知，实现歌词上岛效果

## 🎵 歌词源介绍

HyperLyric 支持三种歌词源，用于获取音乐播放器的实时歌词数据，各歌词源支持的音乐软件请前往对应项目介绍中查看：

| 歌词源 | 说明 | 额外依赖 |
|--------|------|----------|
| **Lyricon** | 基于 Xposed 框架的 Android 状态栏歌词增强工具 | [Lyricon central](https://github.com/tomakino/lyricon/releases/tag/core) + [LyricProvider](https://github.com/proify/LyricProvider/releases)|
| **SuperLyric** | 基于 Binder 数据传递的音乐软件歌词获取与发布模块 | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | 读取 MediaMetadata lyricInfo 数据 | [LyricInfo](https://github.com/limczhh/LyricInfo) (可选) |


## 📱 适配说明

> 适配覆盖范围有限，开发者能力有限，Bug不能及时修复

| 功能 | 安卓版本 | 系统版本 |
|------|----------|--|
| xposed 超级岛歌词 | Android 15+ | HyperOS 3 |
| xposed 移除焦点通知白名单 | Android 13+  | HyperOS 2、HyperOS 3 |
| xposed 移除媒体超级岛下拉白名单 | Android 16 | HyperOS 3.0.300+ |
| Live update 安卓实时通知歌词 | Android 16 | HyperOS 3.0.300+、ColorOS 16 |
|Notification Spotlight 小米焦点通知歌词 | Android 13+ | HyperOS 2、HyperOS 3 |

## 📸 截图

<table>
  <tr>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/001.webp?raw=true" width="300" alt="截图 001"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/002.webp?raw=true" width="300" alt="截图 002"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/003.webp?raw=true" width="300" alt="截图 003"/></td>
  </tr>
  <tr>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/004.webp?raw=true" width="300" alt="截图 004"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/005.webp?raw=true" width="300" alt="截图 005"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/006.webp?raw=true" width="300" alt="截图 006"/></td>
  </tr>
</table>

## 📎 项目引用与参考
- [miuix](https://github.com/compose-miuix-ui/miuix)
- [lyricon](https://github.com/tomakino/lyricon)
- [SuperLyric](https://github.com/HChenX/SuperLyric)
- [LyricInfo](https://github.com/limczhh/LyricInfo)
- 以及很多没列出的项目，感谢