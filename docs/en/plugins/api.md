# Plugin API and Configuration Reference

## Version and dependency

The API lives in `Plugins/api`; the current `HYPERLYRIC_PLUGIN_API_VERSION` is `1`:

```kotlin
compileOnly(project(":plugins:api"))
```

Do not depend on `:app` or host-internal classes. The host accepts plugins whose API version is no higher than its own. Keep the plugin ID stable after release because it identifies the plugin and its configuration and storage namespaces.

## Entry point and processor

The entry class implements `HyperLyricPlugin`, has a public no-argument constructor, and registers processors from `onLoad`:

```kotlin
class MyPlugin : HyperLyricPlugin {
    private lateinit var context: PluginContext

    override fun onLoad(context: PluginContext) {
        this.context = context
        context.registerExtension(MyProcessor(context))
    }

    override fun onEnable() = Unit
    override fun onConfigChanged(config: PluginConfig) = Unit

    override fun onUnload() {
        // Cancel requests, close pools, and release resources.
    }
}
```

A processor implements `LyricProcessorExtension`, receives `processResult(song, processingContext)`, and returns `PluginSongResult?`. Returning `null` means that this run produced no result, so the host keeps the current lyrics.

```kotlin
private class MyProcessor(
    private val context: PluginContext,
) : LyricProcessorExtension {
    override val id = "translation"
    override val stage = PluginProcessorStage.TRANSLATION_ENHANCEMENT

    override fun processResult(
        song: PluginSong,
        processingContext: PluginProcessingContext,
    ): PluginSongResult? {
        val lyrics = song.lyrics ?: return null
        val updated = song.copy(
            lyrics = lyrics.map { line ->
                line.copy(translation = translate(line.text))
            },
        )
        return PluginSongResult(
            song = updated,
            changedFields = setOf(PluginSongField.LYRICS),
            lyricsUpdateMode = PluginLyricsUpdateMode.PATCH,
            changedLyricFields = setOf(PluginLyricField.TRANSLATION),
        )
    }
}
```

A plugin can register more than one processor. Processor code must handle interruption and coroutine cancellation so cancelled network work does not keep consuming resources.

## Input and top-level fields

`PluginSong` is a read-only boundary DTO with `id`, `name`, `artist`, `album`, `duration`, `metadata`, and `lyrics`. `album` is a separate song field, not part of `PluginMetadata`.

| `PluginSongField` | Writeback |
| --- | --- |
| `ID` | `id` |
| `NAME` | `name` |
| `ARTIST` | `artist` |
| `ALBUM` | `album` |
| `DURATION` | `duration`; internal `Song` uses `0` for unknown |
| `METADATA` | top-level `metadata` |
| `LYRICS` | lyric update selected by `lyricsUpdateMode` |

`PluginSongResult.changedFields` is the only top-level change declaration. An undeclared field keeps its current value; a declared `null` explicitly clears it. The host does not infer changes by comparing the input and output objects.

## Lyric fields and update modes

`PluginLyricField` currently includes `BEGIN`, `END`, `DURATION`, `IS_ALIGNED_RIGHT`, `METADATA`, `TEXT`, `WORDS`, `SECONDARY`, `SECONDARY_WORDS`, `TRANSLATION`, `TRANSLATION_WORDS`, and `ROMA`.

### `PATCH`

Use `PATCH` for translation, romanization, secondary text, and word-level enhancements:

- the candidate list must have the same number of rows as the current lyrics, with the same row indexes;
- only fields in `changedLyricFields` are applied;
- a declared nullable field may be cleared with `null`;
- Core validates row and word timing after the merge.

### `REPLACE`

Use `REPLACE` for a complete new lyric result. The candidate may change row count, timing, original text, words, secondary text, translation, and romanization. A declared `null` or empty list explicitly clears the lyrics; exceptions, timeouts, and invalid results do not take that clear path.

For example, a translation plugin can declare `TRANSLATION`, an original word-level processor can declare `TEXT/WORDS`, and a romanization processor can declare `ROMA`. Their changes can be merged on the same rows.

## Timing rules

- `begin >= 0`, `end > begin`, and `duration == end - begin`; lyric rows are non-decreasing by `begin`.
- Words in `words`, `secondaryWords`, and `translationWords` must fit inside their row and be ordered by time.
- Do not write any `*Words` field when timing is unreliable.
- When changing original word-level text, normally declare and return matching `WORDS` as well.
- `PATCH` keeps row count and stable indexes; `REPLACE` is subject to host result-size limits.

Create results with `copy(...)`. Do not mutate inputs or retain host `Song`, Renderer, Canvas, View, or Xposed references.

## `PluginContext` and media information

`PluginContext` provides `config`, `storage`, `cache`, and `logger`. `PluginProcessingContext.mediaInfo` contains the current title, artist, album, duration, and optional `sourcePackageName` for queries, cache keys, or request parameters. `sourcePackageName` comes only from the current lyric source's `LyricMediaMetadata.packageName`; it is `null` when the source did not provide it and is never inferred from MediaSession, MediaMetadataHelper, old Bridge state, or song text.

It is not `MediaMetadataHelper` and contains no session token or Xposed object. `sourcePackageName` is lyric-source/player context, not an intrinsic `PluginSong` field, and does not authorize MediaSession or host API access.

## Settings Schema

Plugins describe settings in the Manifest. The host creates the settings UI, so plugins do not need Compose or Miuix and should not create their own Android page.

| `type` | Interaction |
| --- | --- |
| `switch` | toggle |
| `text` | text input |
| `password` | password input |
| `select` | single selection |
| `multiSelect` | multiple selection |
| `number` | integer input |
| `slider` | slider with `min`, `max`, and `step` |
| `action` | action-item protocol; the current host does not run custom plugin actions |

Use `title` and `summary` for the label and explanation. Use `titleLocales` and `summaryLocales` for translations. `valuePresentation` supports `endAction`, `summary`, and `summaryPreview`. `inputType` can declare `uri` or `number`, and `conflictsWith` can describe mutually exclusive settings. The optional `group` places settings with the same value in one settings Card in the host UI; omitted settings use the `default` group.

The top-level `settingGroups` array can declare a group's `id`, `title`, and optional `titleLocales`. The host renders the group title with Miuix `SmallTitle` before the corresponding Card; older plugins that do not declare group titles keep the existing layout. For example, a setting with `group: "generation"` belongs to the group with `id: "generation"`.

When `activationSettingKey` is declared, the common switch at the top of the plugin page synchronizes the plugin enablement and that setting. Configuration changes call `onConfigChanged`, but take effect only on the next normal request; the current song is not rerun automatically.

API keys and other values that should not enter backups must use `backup: false`:

```json
{
  "type": "password",
  "key": "api_key",
  "title": "API Key",
  "default": "",
  "backup": false
}
```

## Storage, cache, and logging

- `config`: read-only configuration with `getBoolean`, `getString`, `getLong`, `getFloat`, and `getStringSet`.
- `storage`: small plugin state with read, write, remove, and clear operations; it is not a result cache.
- `cache`: disposable results with string and byte reads and writes, existence checks, removal, and clearing. The plugin owns keys, serialization, versions, and invalidation; the host provides plugin-ID isolation and error protection.
- `logger`: use `debug`, `info`, `warn`, and `error` instead of Android logging or a custom log file.

If a cache entry is corrupt, unreadable, or too large, ignore or remove it and fall back to the network or no-result path. Do not clear the original lyrics. Cache reusable results that can be applied to the current song rather than an entire stale `PluginSong`.

### Manageable cache

To expose cache management in the App, declare a UI-framework-neutral Manifest scope:

```json
{
  "cacheScopes": [
    { "id": "translation", "title": "Translation cache", "summary": "Clear results" }
  ]
}
```

Register a same-ID `PluginCacheExtension` in `onLoad`. `listEntries()` should return at most the most recent 100 metadata-only `PluginCacheEntry` values; entry IDs are opaque to Core/App. The plugin owns entry-ID/key mapping, indexing, deletion, and serialization. `clearAll()` and `clearEntry()` may clear only `PluginCache`, never `PluginConfig` or `PluginStorage`. The App sends request-ID-matched operations with a one-time response token through RemotePreferences. Since the target-process preferences are read-only, SystemUI returns the bounded result through the App's guarded provider. For cache management of a disabled plugin, Runtime may still call `onLoad`, but never `onEnable` or activate lyric processors; `onLoad` must therefore only create state and register extensions, while playback-related or proactive work belongs in `onEnable`. Plugins never create Compose/Miuix screens or rerun the current song after a clear.
