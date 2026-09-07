# HyperLyric Plugin Development

If you want to connect a new lyric service, translation feature, or lyric processor to HyperLyric, start here. This page explains the integration flow; detailed fields and packaging rules are on the reference pages.

## Check the boundary first

The current Plugin API is for lyric processors. A plugin runs in the injected SystemUI process, receives a complete read-only `PluginSong`, and returns translations, romanization, word-level data, or other lyric changes.

It is not the MediaSession or online-lyric-source interface for the standalone App. The App continues to manage its own media sessions and lyric sources. If an App lyric-source extension is needed later, it should use a separate App Runtime and `LyricSourceExtension` contract rather than reusing this Processor API.

A plugin may use only types exposed by `Plugins/api`. Do not depend on `:app`, or access the host's `Song`, Renderer, Canvas, SystemUI Views, MediaSession, `Context`, or Xposed objects directly.

## How a plugin runs

```text
Plugin ZIP
  → App installs, configures, and synchronizes the plugin
  → SystemUI loads it at startup
  → The plugin registers processors
  → A processor receives a read-only PluginSong
  → It returns PluginSongResult
  → The host validates and merges the result
```

Keep these four rules in mind:

- The original lyrics are shown first while plugin processing runs in the background.
- A `null` result, exception, timeout, or invalid result leaves the current lyrics intact and does not stop other plugins.
- Processors run by stages such as `LYRIC_REPLACEMENT` and `TRANSLATION_ENHANCEMENT`; each later processor receives the merged result from the previous one.
- A song change, source stop, or media change cancels old work. Plugin code must respond to thread interruption or coroutine cancellation, and late results must not update the new song.

The host waits up to 40 seconds for one processor. Network calls, model calls, and cache reads should use shorter timeouts. Configuration changes are synchronized to the plugin but take effect only on the next normal request; the current song is not rerun automatically.

## Integration steps

### 1. Create a plugin module

Create an independent Gradle module under `Plugins/modules/`. Use `compileOnly` for the API and host-provided libraries, and `implementation` for runtime libraries owned by the plugin. See [Packaging, installation, and validation](plugins/packaging.md) for the module layout and dependency example.

### 2. Add the entry point and processor

Implement `HyperLyricPlugin` and register a `LyricProcessorExtension` from `onLoad`. Read from `PluginSong`, create new DTOs, and return `PluginSongResult`. Do not mutate the input or retain references to host-internal objects.

See [Plugin API and configuration reference](plugins/api.md) for the entry point, processor, and DTO examples.

### 3. Choose the right writeback mode

| Goal | Result |
| --- | --- |
| Add translation, romanization, secondary text, or word-level data | `LYRICS + PATCH`, with the relevant `PluginLyricField` declarations |
| Return a complete new lyric set | `LYRICS + REPLACE` |
| Change title, artist, album, or another top-level field | Declare the relevant `PluginSongField` in `changedFields` |
| No reliable result | Return `null`; do not return a partial result |

`PATCH` keeps the same row count and row indexes. `REPLACE` may change row count and timing. Write `WORDS`, `TRANSLATION_WORDS`, or other word-level fields only when their timing is reliable. See the [API reference](plugins/api.md) for merge rules.

### 4. Let the host render settings

Declare plugin settings in the Manifest Settings Schema. The host creates the settings UI; the plugin should not add its own Compose, Miuix, or Android page. Mark API keys and other sensitive values with `backup: false`.

The API reference covers settings, storage, cache, and logging. To expose user-manageable cache, declare semantic `cacheScopes` (an `id`, a plugin-defined `title`, and optional `summary`) in the Manifest and register a same-ID `PluginCacheExtension`. The host uses the title for its entry and page title; summary is optional metadata and is not guaranteed to be displayed. The plugin owns opaque entry IDs, indexing, serialization, and display metadata; the host sends request-ID-matched list/clear requests with one-time response tokens and receives bounded results through its guarded provider, never by parsing plugin cache JSON. Never put cache bodies, full translations, or API keys in `PluginCacheEntry`. To link out to an external site, declare `uri: "https://..."` on an `ACTION` setting and the host renders it as a clickable link button.

### 5. Package and validate

Build a Debug ZIP first, then verify the Release/R8 ZIP. Restart SystemUI after installation, removal, or a code upgrade; ordinary configuration changes do not require a restart.

Before submitting, test at least:

1. Load, enable, disable, and remove the plugin.
2. Keep the original lyrics usable after network failures, timeouts, empty results, parse errors, and corrupt cache entries.
3. Cancel old work during a fast song change and ensure late results do not reach the new song.
4. Apply changed settings on the next request without rerunning the current song.
5. Load the Release/R8 ZIP without duplicate host API classes.

See [Packaging, installation, and validation](plugins/packaging.md) for commands and keep rules.

## Reference implementations

- `Plugins/modules/ai-translation`: network requests, settings, caching, scheduling, and translation PATCH results.
- `Plugins/modules/demo-logger`: entry points, lifecycle, field merging, and word-level timing.

Use them to understand the API, but keep new plugins dependent only on `Plugins/api` rather than copying host internals.
