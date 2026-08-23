<h1 align="center">HyperLyric</h1>

<p align="center">
  <strong>专为小米 HyperOS 3 带来超级岛歌词的 Xposed 模块</strong>
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

HyperLyric 在小米超级岛中显示普通逐行、逐字和分离歌词，也可以自定义歌词样式、岛内内容和系统媒体卡片。主要使用方式为 **Xposed / SystemUI 模式**，同时保留轻量的免 Root 通知歌词模式。

## 功能特性

### 歌词显示

- **逐行与逐字歌词**：歌词按行显示；有词级时间轴时会跟随播放进度逐字高亮，只有行级时间轴时整行切换，没有时间轴的歌词也可以滚动显示。
- **分离歌词**：把一句歌词分到超级岛左右两边显示，同时保留逐字和滚动效果；长度可以固定，也可以随歌词变化。
- **第二行内容**：显示翻译、罗马音或下一句歌词；也可以对调原词与翻译、只显示翻译或自动切换。
- **OpenAI 翻译**：安装 AI 翻译插件后，可通过 OpenAI 兼容接口生成翻译，并设置目标语言、模型、接口地址和提示词；还可以跳过指定语言或替换歌词源自带的翻译。
- **歌词时间偏移**：每个 Lyricon 提供者都可以单独提前或延后歌词。

### 超级岛布局与内容

- 超级岛左右两边可以分别显示歌词、音乐信息，也可以留空。
- 音乐信息可以自由组合标题、艺术家、专辑、总时长、已播放时间、剩余时间和播放进度，并设置两行内容和字段连接符。
- 超级岛宽度可以固定，也可以跟随内容变化；左右边距可分别调整，歌词可居中或右对齐。
- 封面可使用默认样式、应用图标或直接隐藏；律动颜色可使用默认颜色、封面色或封面渐变色。
- 可显示封面取色的边缘光效、环绕进度和渐变进度，并调整进度起点与方向。
- 可设置暂停播放或切换歌曲后，超级岛继续显示还是自动收起。

### 歌词样式与动画

- 可设置字体、字体文件、字号、字重、英数窄字体和文字颜色。
- 文字颜色可使用默认颜色、封面色、封面渐变色或跟随状态栏。
- 可设置歌词滚动、歌词切换动画、逐字高亮和进度样式。
- 中日韩与拉丁文字的上浮、波动和逐字母动画可以分别调整。

### 系统媒体卡片

- 可分别自定义**通知中心媒体卡片**和**超级岛展开态媒体卡片**，也可以防止息屏显示中的媒体卡片自动折叠。
- 卡片布局可选择系统默认、iOS、ColorOS、One UI、MIUI 或 PixelOS 风格，并继续调整布局参数。
- 卡片背景可选择默认、拼贴封面、模糊封面、径向渐变、线性渐变、柔光封面或动态流光；明暗、模糊、亮色封面自动反色和切换动画也可以调整。
- 可调整封面形状、旋转、阴影和翻转，也可以隐藏时间、设备切换按钮或自定义动作按钮，并调整按钮顺序和对齐方式。
- 可选择默认或波浪进度条，并设置拖尾光效和滑块样式。
- 支持通知中心多媒体卡片切换，可选择单卡片或多卡片视图，并限制显示数量。

### 系统限制解除

- 移除小米焦点通知发送白名单限制。
- 移除超级岛媒体卡片下拉小窗白名单限制。

> [!NOTE]
> SystemUI 插件会随系统版本更新。媒体卡片、白名单和超级岛扩展能力需要目标版本提供对应结构，实际支持范围以当前版本说明和设备表现为准。

## 歌词来源

HyperLyric 可在设置中切换三种 Xposed 歌词来源。逐字、翻译和下一句歌词等能力取决于来源实际提供的数据。

| 歌词源 | 主要能力 | 依赖 |
| :--- | :--- | :--- |
| **Lyricon** | 通过 LyricProvider 提供逐行、逐字、翻译等歌词数据；具体能力取决于播放器对应的 LyricProvider | [Lyricon Central](https://github.com/tomakino/lyricon/releases/tag/core) + [LyricProvider](https://github.com/proify/LyricProvider/releases) |
| **SuperLyric** | 通过 SuperLyric 模块持续提供普通或逐字歌词；不支持下一句歌词和 AI 翻译 | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | 从媒体元数据读取标准化歌词；逐行/逐字、翻译和下一句歌词等内容取决于元数据本身 | [LyricInfo](https://github.com/limczhh/LyricInfo)（推荐安装；播放器自身能把歌词写入 MediaSession 元数据时可不安装） |

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

## 兼容性

> [!WARNING]
> HyperOS 与 SystemUI 插件更新频繁，下表描述当前主要适用范围，不代表所有设备和系统版本都已完成实机验证。

| 功能 | Android 版本 | 系统版本 | 说明 |
| :--- | :--- | :--- | :--- |
| **超级岛歌词与媒体岛增强** | Android 15+ | HyperOS 3 | 需要 LSPosed v2.0 框架支持 |
| **焦点通知白名单解除** | Android 13+ | HyperOS 2、HyperOS 3 | 通过 Xposed 绕过发送限制 |
| **下拉小窗白名单解除** | Android 16 | HyperOS 3.0.300+ | 用于超级岛媒体卡片下拉扩展 |
| **实时通知歌词** | Android 16 | HyperOS 3.0.300+、ColorOS 16 | 使用标准 Android 实时通知接口 |
| **焦点通知歌词** | Android 13+ | HyperOS 2、HyperOS 3 | 独立模式可配合 Shizuku 使用 |

## 下载

前往 [GitHub Releases](https://github.com/limczhh/HyperLyric/releases) 下载最新的 HyperLyric APK。

## 独立通知模式

不使用 LSPosed 时，HyperLyric 也可监听媒体元数据，并通过小米焦点通知或 Android 实时通知显示歌词。该模式支持音乐应用白名单、通知样式和快捷设置磁贴。

## 插件

插件是 HyperLyric 按需安装的额外歌词功能，例如翻译、罗马音和逐字歌词。

- [插件介绍](docs/plugins.md)
- [插件开发文档](docs/plugin-development.md)

## 配置与问题

- [基础配置教程](docs/getting-started.md)
- [常见问题](docs/faq.md)

## 致谢与协议

HyperLyric 采用 **GNU General Public License v3.0** 开源协议。

感谢以下项目：

- [lyricon](https://github.com/tomakino/lyricon) — HyperLyric 的歌词模型、渲染与大部分动画能力基于该项目移植和扩展。
- [Miuix](https://github.com/compose-miuix-ui/miuix) — HyperOS 风格的 Compose UI 组件库。
- [SuperLyric](https://github.com/HChenX/SuperLyric) — 歌词数据来源。
- [LyricInfo](https://github.com/limczhh/LyricInfo) — 媒体元数据歌词方案。
- [libxposed](https://github.com/libxposed/api) — Modern Xposed API。
