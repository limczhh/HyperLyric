<h1 align="center">HyperLyric</h1>

<p align="center">
  <strong>An Xposed module that brings lyrics to Xiaomi HyperIsland on HyperOS 3</strong>
</p>

<p align="center">
  <a href="https://github.com/limczhh/HyperLyric/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License GPL-3.0"/></a>
  <a href="https://android.com"><img src="https://img.shields.io/badge/Android-13.0%20--%2016-3DDC84.svg" alt="Android Support"/></a>
  <a href="https://github.com/compose-miuix-ui/miuix"><img src="https://img.shields.io/badge/UI--Framework-Miuix--Compose-0084FF.svg" alt="Miuix UI"/></a>
  <a href="https://github.com/libxposed/api"><img src="https://img.shields.io/badge/Hook--Framework-libxposed%20102-purple.svg" alt="libxposed"/></a>
  <a href="https://github.com/limczhh/HyperLyric/releases"><img src="https://img.shields.io/github/downloads/limczhh/HyperLyric/total?style=flat&color=orange" alt="Downloads"/></a>
</p>

<p align="center">
  <a href="https://qm.qq.com/q/5ZiRlGtvkQ"><img src="https://img.shields.io/badge/QQ%20Group-0084FF?style=flat&logo=qq&logoColor=white" alt="QQ Group"/></a>
  <a href="https://t.me/MiniLeaf"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=flat&logo=telegram&logoColor=white" alt="Telegram"/></a>
</p>

<p align="center">
  <a href="README.md"><strong>简体中文</strong></a> | English
</p>

---

HyperLyric displays line-synced, word-synced, and separated lyrics in Xiaomi HyperIsland. It also lets you customize lyric styles, island content, and system media cards. Its primary mode uses **Xposed to integrate with SystemUI**, while a lightweight rootless notification mode remains available.

## Features

### Lyric presentation

- **Line- and word-synced lyrics**: Lyrics are shown one line at a time. With word-level timing, each word is highlighted as the song plays; with line-level timing, the whole line changes together; lyrics without timing can still scroll.
- **Separated lyrics**: Splits one lyric line across the left and right sides of HyperIsland while keeping word progress and scrolling. The width can stay fixed or change with the lyric.
- **Second line**: Shows a translation, romanization, or the next lyric line. You can also swap the original and translation, show only the translation, or switch automatically.
- **OpenAI Translation**: After installing the AI translation plugin, HyperLyric can generate translations through an OpenAI-compatible API. You can set the target language, model, endpoint, and prompt, skip selected source languages, or replace translations supplied by the lyric source.
- **Lyric time offset**: Each Lyricon provider can move lyrics earlier or later independently.

### HyperIsland layout and content

- Each side of HyperIsland can show lyrics, music information, or nothing.
- Music information can combine the title, artist, album, total duration, elapsed time, remaining time, and playback progress. Both lines and the field separator are configurable.
- HyperIsland width can stay fixed or change with its content. Left and right padding are adjustable, and lyrics can be centered or right-aligned.
- The cover art can use the default style, the app icon, or be hidden. Rhythm colors can use the default color, cover color, or cover gradient.
- Edge glow, perimeter progress, and gradient progress can use cover colors, with adjustable start points and directions.
- You can choose whether HyperIsland stays visible or collapses after pausing playback or changing tracks.

### Lyric styling and animation

- Set the font, font file, size, weight, narrow Latin/numeric font, and text color.
- Text can use the default color, cover color, cover gradient, or the current status bar color.
- Set lyric scrolling, lyric transitions, word highlighting, and progress styles.
- Adjust lift, wave, and per-character motion separately for CJK and Latin text.

### System media cards

- Customize the **Notification Center media card** and **expanded HyperIsland media card** separately. You can also prevent the card from collapsing on the **Always On Display**.
- Card layout styles include the system default, iOS, ColorOS, One UI, MIUI, and PixelOS, with additional layout controls.
- Card background styles include default, cover collage, blurred cover, radial gradient, linear gradient, soft cover, and ambient flow. Brightness, blur, auto-invert, and transitions are also adjustable.
- Adjust the cover shape, rotation, shadow, and flip animation. You can hide time, device switching, or custom action buttons, and change button order and alignment.
- Choose a default or waveform progress bar, then adjust the trailing glow and thumb style.
- Switching between multiple media cards supports single-card and multi-card views with a configurable display limit.

### System restriction bypasses

- Removes Xiaomi's allowlist restriction for sending Notification Spotlight notifications.
- Removes the allowlist restriction for pulling a HyperIsland media card down into a mini window.

> [!NOTE]
> SystemUI plugins change with system updates. Media cards, allowlist bypasses, and HyperIsland extensions require compatible structures in the target version, so actual availability depends on the current release and device build.

## Lyric sources

HyperLyric can switch between three Xposed lyric sources. Word timing, translations, and next-line support depend on what the selected source provides.

| Source | Main capabilities | Dependency |
| :--- | :--- | :--- |
| **Lyricon** | Provides line-synced, word-synced, and translated lyrics through LyricProvider; exact capabilities depend on the player's LyricProvider | [Lyricon Central](https://github.com/tomakino/lyricon/releases/tag/core) + [LyricProvider](https://github.com/proify/LyricProvider/releases) |
| **SuperLyric** | Continuously provides line-synced or word-synced lyrics through the SuperLyric module; next-line lyrics and AI translation are unavailable | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | Reads normalized lyrics from media metadata; line/word timing, translations, and next-line lyrics depend on the metadata itself | [LyricInfo](https://github.com/limczhh/LyricInfo) (recommended; optional when the player writes lyric metadata into MediaSession itself) |

## Screenshots

<table>
  <tr>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/001.webp?raw=true" width="300" alt="Screenshot 001"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/002.webp?raw=true" width="300" alt="Screenshot 002"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/003.webp?raw=true" width="300" alt="Screenshot 003"/></td>
  </tr>
  <tr>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/004.webp?raw=true" width="300" alt="Screenshot 004"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/005.webp?raw=true" width="300" alt="Screenshot 005"/></td>
    <td><img src="https://github.com/limczhh/HyperLyric/blob/main/assets/006.webp?raw=true" width="300" alt="Screenshot 006"/></td>
  </tr>
</table>

## Compatibility

> [!WARNING]
> HyperOS and its SystemUI plugins change frequently. The table below describes the primary target range and does not claim device testing across every listed combination.

| Feature | Android | System | Notes |
| :--- | :--- | :--- | :--- |
| **HyperIsland lyrics and media card enhancements** | Android 15+ | HyperOS 3 | Requires the LSPosed v2.0 framework |
| **Notification Spotlight allowlist bypass** | Android 13+ | HyperOS 2, HyperOS 3 | Uses Xposed to bypass sending restrictions |
| **Pull-down mini-window allowlist bypass** | Android 16 | HyperOS 3.0.300+ | Enables pull-down expansion for HyperIsland media cards |
| **Live Update lyric notifications** | Android 16 | HyperOS 3.0.300+, ColorOS 16 | Uses the standard Android Live Update API |
| **Notification Spotlight lyrics** | Android 13+ | HyperOS 2, HyperOS 3 | The standalone mode can use Shizuku |

## Download

Download the latest HyperLyric APK from [GitHub Releases](https://github.com/limczhh/HyperLyric/releases).

## Standalone notification mode

Without LSPosed, HyperLyric can listen to media metadata and display lyrics through Xiaomi Notification Spotlight or Android Live Update notifications. This mode includes a player allowlist, notification styling, and a Quick Settings tile.

## Plugins

Plugins are optional HyperLyric lyric features that can be installed when needed, such as translation, romanization, and word-level lyrics.

- [Plugin introduction](docs/en/plugins.md)
- [Plugin development guide](docs/en/plugin-development.md)

## Setup and troubleshooting

- [Basic setup guide](docs/en/getting-started.md)
- [FAQ](docs/en/faq.md)

## Credits and license

HyperLyric is licensed under the **GNU General Public License v3.0**.

Thanks to:

- [lyricon](https://github.com/tomakino/lyricon) — HyperLyric ports and extends this project's lyric model, rendering engine, and most of its animation capabilities.
- [Miuix](https://github.com/compose-miuix-ui/miuix) — HyperOS-style Compose UI components.
- [SuperLyric](https://github.com/HChenX/SuperLyric) — Lyric data source.
- [LyricInfo](https://github.com/limczhh/LyricInfo) — A lyrics solution built on media metadata.
- [libxposed](https://github.com/libxposed/api) — Modern Xposed API.
