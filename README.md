<h1 align="center">HyperLyric</h1>

<p align="center">
  <strong>为小米设备带来系统级歌词</strong>
</p>

<p align="center">
  <a href="https://github.com/limczhh/HyperLyric/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License GPL-3.0"/></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Android-13.0%20--%2016-3DDC84.svg" alt="Android Support"/></a>
  <a href="https://github.com/compose-miuix-ui/miuix"><img src="https://img.shields.io/badge/UI--Framework-Miuix--Compose-0084FF.svg" alt="Miuix UI"/></a>
  <a href="https://github.com/libxposed/api"><img src="https://img.shields.io/badge/Hook--Framework-libxposed%20102-purple.svg" alt="libxposed"/></a>
  <a href="https://github.com/limczhh/HyperLyric/releases"><img src="https://img.shields.io/github/downloads/limczhh/HyperLyric/total?style=flat&color=orange" alt="Downloads"/></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/5ZiRlGtvkQ"><img src="https://img.shields.io/badge/QQ 交流群-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"/></a>
  <a href="https://t.me/MiniLeaf"><img src="https://img.shields.io/badge/Telegram 频道-26A5E4?style=flat&logo=telegram&logoColor=white" alt="Telegram"/></a>
</p>

<p align="center">
  简体中文 | <a href="README-EN.md"><strong>English</strong></a>
</p>

---

<p align="center">
  <a href="https://github.com/limczhh/HyperLyric/releases">下载应用</a> ·
  <a href="docs/getting-started.md">配置教程</a> ·
  <a href="docs/faq.md">常见问题</a>
</p>

## 歌词

歌词可以选择显示在**状态栏或超级岛**，两种方式二选一。支持逐行、逐字、双行和分离歌词，也能显示翻译、罗马音、和声与对唱。通过 AMLL 歌词增强与 AI 翻译，还可以补充逐字歌词和翻译内容。

字体、字号、字重和斜体均可自定义，文字颜色可使用封面取色、渐变色或跟随状态栏。搭配歌词切换动画、逐字高亮和字词上浮动画，让歌词随音乐呈现。

支持歌词预览、间奏倒计时，以及无歌词和尾奏时的显示行为设置。除了歌词，也可以自由组合歌名、歌手、专辑、时长和播放进度，作为音乐信息展示。

## 媒体卡片

重新搭配通知中心和超级岛展开态的媒体卡片。选择喜欢的布局，用封面取色、渐变或动态流光装饰背景，再调整封面、播放按钮和进度条。两处卡片可分别配置，通知中心还支持在多个音乐应用的卡片之间滑动切换。

## 功能一览

| 功能 | 说明 | 版本支持 |
| :--- | :--- | :--- |
| 超级岛歌词 | 在超级岛显示歌词，支持自定义封面与音乐律动、边缘光效与环绕进度，以及长按、滑动和暂停行为 | HyperOS 3 / 4 |
| 状态栏歌词 | 在状态栏显示歌词，支持自定义插入位置、横竖屏宽度、锁屏隐藏、自动避让与封面图标，调整时钟、通知图标和媒体岛的显隐，并配置状态栏手势 | MIUI / HyperOS |
| 媒体卡片布局 | iOS、ColorOS、One UI、MIUI、PixelOS 等布局 | HyperOS 3 / 4 |
| 媒体卡片元素 | 自定义卡片背景、封面样式、阴影与翻转动画，隐藏部分元素，调整按钮顺序与对齐 | HyperOS |
| 多媒体卡片切换 | MIUI 同款功能，方便管理多个媒体会话 | HyperOS 3 / 4 |
| 焦点通知白名单解除 | 解除应用焦点通知显示的限制 | HyperOS |
| 下拉小窗白名单解除 | 允许更多音乐应用通过超级岛下拉打开小窗 | HyperOS 3.0.300+ / 4 |
| 通知型歌词 | 通过焦点通知或实时通知显示歌词 | HyperOS / Android 16+ |

## 歌词来源

模块模式可选择以下任一歌词源。音乐软件的支持范围与歌词数据的完整程度，取决于所选歌词源或提供器。

| 歌词源 | 获取方式 | 所需依赖 |
| :--- | :--- | :--- |
| **Lyricon** | 通过对应音乐软件的 LyricProvider 获取歌词 | [Lyricon Central](https://github.com/tomakino/lyricon/releases/tag/core) + [LyricProvider](https://github.com/proify/LyricProvider/releases) |
| **SuperLyric** | 由 SuperLyric 模块提供和调度歌词 | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | 读取音乐软件写入 MediaSession 的歌词元数据 | 可使用 [LyricInfo](https://github.com/limczhh/LyricInfo) 或 [ColorOS Live Lyrics Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge) 输出 LyricInfo 歌词数据；音乐软件自身能输出时可不安装 |

首次使用可跟随应用内引导选择显示方式和歌词源，再按[配置教程](docs/getting-started.md)启用相关模块并重启。

## 截图

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

## 致谢与协议

HyperLyric 采用 **GNU General Public License v3.0** 开源协议。

感谢以下项目：

- [lyricon](https://github.com/tomakino/lyricon) — HyperLyric 的歌词模型、渲染与大部分动画能力基于该项目移植和扩展。
- [Miuix](https://github.com/compose-miuix-ui/miuix) — HyperOS 风格的 Compose UI 组件库。
- [SuperLyric](https://github.com/HChenX/SuperLyric) — 歌词数据来源。
- [LyricInfo](https://github.com/limczhh/LyricInfo) — 媒体元数据歌词方案。
- [libxposed](https://github.com/libxposed/api) — Modern Xposed API。
