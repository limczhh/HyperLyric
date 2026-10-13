# FAQ

## General

### What do HyperLyric, lyric providers, and lyric sources do?

HyperLyric is the lyric consumer and display layer. It renders lyrics in Xiaomi HyperIsland but does not obtain lyrics from the music app. Lyric sources such as Lyricon, SuperLyric, and LyricInfo provide the lyrics. If nothing appears, the source may not have sent lyrics, or it may not be compatible with the music app version.

## Xposed module mode

### What does Lyricon require?

HyperLyric 6.0 and later requires Lyricon Central and the LyricProvider for the target music app. HyperLyric versions before 6.0 included Central, so no separate Central module was needed; follow the documentation for the version you use.

### What does SuperLyric require?

Install SuperLyric only. It handles both lyric scheduling and lyric providing. Enable the module and restart the phone after installation or first injection.

### How does LyricInfo work?

LyricInfo is recommended because mainstream music apps usually do not write lyrics into MediaSession metadata. A few third-party players may provide this metadata themselves, in which case LyricInfo can be skipped. For other apps, the LyricInfo module writes lyrics into the metadata and HyperLyric reads it. This is also the kind of metadata used by scenarios such as the ColorOS 16 lock-screen island.

### I configured everything, but no lyrics appear. What should I check?

Check these items in order:

1. Whether the lyric source or provider supports the music app and its current version.
2. Whether HyperLyric, the selected source, and its required dependencies are enabled, and whether the music app is selected in LSPosed.
3. Whether the required restart was completed: Lyricon usually needs SystemUI and the music app restarted; SuperLyric needs a phone restart for first injection; LyricInfo usually needs only the music app restarted.
4. Whether the source actually sent lyrics. HyperLyric cannot display content that it did not receive.

## Standalone app mode

### Why must I add the music app to the whitelist?

A phone can have several MediaSession sessions at the same time. The whitelist limits processing to the selected music app and prevents data from other players from being used.

### Do I have to enable car Bluetooth lyrics and connect a Bluetooth device?

Not always. Standalone mode reads lyric metadata output by the music app. Car Bluetooth lyrics is only one way some apps provide that metadata. Some apps work without a Bluetooth connection; others write lyrics to fields such as Title only after a Bluetooth headset or another device is connected.

### I enabled lyric output, but there are still no lyrics. Why?

First check that the music app is actually outputting lyric metadata. Then check compatibility with the current HyperLyric version, notification permissions, and the lyric whitelist. If the system restricts HyperLyric in the background, grant autostart or background-running permission and play the song again.
