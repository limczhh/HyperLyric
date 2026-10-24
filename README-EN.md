<h1 align="center">HyperLyric</h1>

<p align="center">
  <strong>System-level lyrics for Xiaomi devices</strong>
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

<p align="center">
  <a href="https://github.com/limczhh/HyperLyric/releases">Download</a> ·
  <a href="docs/en/getting-started.md">Setup guide</a> ·
  <a href="docs/en/faq.md">FAQ</a>
</p>

## Lyrics

Choose to display lyrics in either the **status bar or HyperIsland**. HyperLyric supports line-synced, word-synced, dual-line, and split lyrics, with translations, romanization, backing vocals, and duet parts. AMLL lyric enhancement and AI translation can add word-synced lyrics and translations.

Customize the font, size, weight, and italics. Text can use cover colors, gradients, or colors that follow the status bar, alongside lyric transitions, word highlighting, and word lift animations.

Lyric preview, interlude countdowns, and configurable behavior for missing lyrics and outros help handle the gaps between lines. You can also combine the title, artist, album, duration, and playback progress to display music information.

## Media cards

Customize the media cards in Notification Center and expanded HyperIsland. Choose a layout, add cover colors, gradients, or ambient flow to the background, then adjust the cover, playback buttons, and progress bar. Configure each location separately and swipe between cards from multiple music apps in Notification Center.

## Feature reference

| Feature | Description | Version support |
| :--- | :--- | :--- |
| HyperIsland lyrics | Display lyrics in HyperIsland. Customize cover art, the music visualizer, edge glow, perimeter progress, and long-press, swipe, and pause behavior | HyperOS 3 / 4 |
| Status bar lyrics | Display lyrics in the status bar. Customize placement, portrait/landscape widths, lock screen hiding, automatic spacing around HyperIsland, and cover icons; control clock, notification icon, and media island visibility, and configure gestures | MIUI / HyperOS |
| Media card layouts | iOS, ColorOS, One UI, MIUI, PixelOS, and other layout styles | HyperOS 3 / 4 |
| Media card elements | Customize backgrounds, cover styles, shadows, and flip animations; hide selected elements and adjust button order and alignment | HyperOS |
| Multiple media cards | MIUI-style card switching for managing multiple media sessions | HyperOS 3 / 4 |
| Notification Spotlight allowlist bypass | Remove restrictions on which apps can display Notification Spotlight notifications | HyperOS |
| Pull-down mini-window allowlist bypass | Allow more music apps to open in mini windows by pulling down from HyperIsland | HyperOS 3.0.300+ / 4 |
| Notification lyrics | Display lyrics through Notification Spotlight or Live Update notifications | HyperOS / Android 16+ |

## Lyric sources

Choose one of the following sources in module mode. Supported music apps and the completeness of lyric data depend on the selected source or provider.

| Source | How it gets lyrics | Dependencies |
| :--- | :--- | :--- |
| **Lyricon** | Uses a LyricProvider for the music app | [Lyricon Central](https://github.com/tomakino/lyricon/releases/tag/core) + [LyricProvider](https://github.com/proify/LyricProvider/releases) |
| **SuperLyric** | The SuperLyric module provides and schedules lyrics | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | Reads lyrics from the music app's MediaSession metadata | Use [LyricInfo](https://github.com/limczhh/LyricInfo) or [ColorOS Live Lyrics Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge) to provide LyricInfo lyric data; optional if the app already provides it |

Use the in-app setup to choose where lyrics appear and which source to use, then follow the [setup guide](docs/en/getting-started.md) to enable the modules and restart as needed.

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

## Credits and license

HyperLyric is licensed under the **GNU General Public License v3.0**.

Thanks to:

- [lyricon](https://github.com/tomakino/lyricon) — HyperLyric ports and extends this project's lyric model, rendering engine, and most of its animation capabilities.
- [Miuix](https://github.com/compose-miuix-ui/miuix) — HyperOS-style Compose UI components.
- [SuperLyric](https://github.com/HChenX/SuperLyric) — Lyric data source.
- [LyricInfo](https://github.com/limczhh/LyricInfo) — A lyrics solution built on media metadata.
- [libxposed](https://github.com/libxposed/api) — Modern Xposed API.
