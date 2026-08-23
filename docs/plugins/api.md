# Plugin API 与配置参考

## 版本和依赖

API 位于 `Plugins/api`，当前 `HYPERLYRIC_PLUGIN_API_VERSION` 为 `1`。插件使用：

```kotlin
compileOnly(project(":plugins:api"))
```

不要依赖 `:app` 或宿主内部类。宿主只接受 API 版本不高于自身版本的插件。插件 ID 发布后应保持不变，因为它同时用于插件识别、配置命名空间和存储空间。

## 入口和处理器

入口类实现 `HyperLyricPlugin`，必须有公共无参数构造函数，并在 `onLoad` 中注册处理器：

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
        // 取消请求、关闭线程池并释放资源。
    }
}
```

处理器实现 `LyricProcessorExtension`，从 `processResult(song, processingContext)` 接收输入，返回 `PluginSongResult?`。返回 `null` 表示本次没有结果，宿主继续保留当前歌词。

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

一个插件可以注册多个处理器。处理器必须正确处理中断和协程取消，不能让已经取消的网络请求继续占用资源。

## 输入和顶层字段

`PluginSong` 是插件边界上的只读 DTO，包含 `id`、`name`、`artist`、`album`、`duration`、`metadata` 和 `lyrics`。`album` 是独立的歌曲字段，不在 `PluginMetadata` 中。

| `PluginSongField` | 写回内容 |
| --- | --- |
| `ID` | `id` |
| `NAME` | `name` |
| `ARTIST` | `artist` |
| `ALBUM` | `album` |
| `DURATION` | `duration`；内部 `Song` 使用 `0` 表示未知 |
| `METADATA` | 顶层 `metadata` |
| `LYRICS` | 按 `lyricsUpdateMode` 更新歌词 |

`PluginSongResult.changedFields` 是顶层修改的唯一声明。没有声明的字段保留当前值；声明后的 `null` 表示明确清空。宿主不会通过比较输入和输出对象来猜测插件改了什么。

## 歌词字段和更新模式

`PluginLyricField` 当前包括：`BEGIN`、`END`、`DURATION`、`IS_ALIGNED_RIGHT`、`METADATA`、`TEXT`、`WORDS`、`SECONDARY`、`SECONDARY_WORDS`、`TRANSLATION`、`TRANSLATION_WORDS` 和 `ROMA`。

### `PATCH`

适合翻译、罗马音、secondary 和逐字增强：

- 候选行数必须与当前歌词相同，行索引保持不变；
- 只应用 `changedLyricFields` 声明的字段；
- 声明 nullable 字段后可以用 `null` 清空；
- 合并后仍会统一校验行和词的时间轴。

### `REPLACE`

适合返回一整份新歌词。候选可以改变行数、时间轴、原文、words、secondary、translation 和 roma。显式的 `null` 或空列表表示明确清空歌词；异常、超时和非法结果不会触发这个清空路径。

例如，翻译插件声明 `TRANSLATION`，原文逐字插件声明 `TEXT/WORDS`，罗马音插件声明 `ROMA`，它们可以在同一批歌词行上合并。

## 时间轴规则

- `begin >= 0`、`end > begin`、`duration == end - begin`；歌词行按 `begin` 非递减排列。
- `words`、`secondaryWords` 和 `translationWords` 中的词必须位于对应行范围内，并按时间升序排列。
- 时间不可靠时，不要写入任何 `*Words` 字段。
- 修改逐字原文时，通常要同时声明并返回匹配的 `WORDS`。
- `PATCH` 必须保持行数和稳定索引；`REPLACE` 受宿主结果大小限制。

处理器应使用 `copy(...)` 创建结果，不要修改输入对象，也不要保存宿主内部 `Song`、Renderer、Canvas、View 或 Xposed 引用。

## `PluginContext` 和媒体信息

`PluginContext` 提供 `config`、`storage`、`cache` 和 `logger`。处理函数收到的 `PluginProcessingContext.mediaInfo` 包含当前媒体的标题、艺术家、专辑、时长和可选 `sourcePackageName`，可用于查询、缓存或请求参数。`sourcePackageName` 只来自本次歌词源的 `LyricMediaMetadata.packageName`；源没有提供时为 `null`，不会由 MediaSession、MediaMetadataHelper、旧 Bridge 状态或歌曲文本推断。

它不是 `MediaMetadataHelper`，不包含 Session Token 或 Xposed 对象。

`sourcePackageName` 是唯一的例外：它是歌词源声明的播放器上下文，不是 `PluginSong` 的固有字段，也不允许据此调用 MediaSession 或宿主 API。

## 设置 Schema

插件通过 Manifest 描述设置，宿主负责生成设置页面。插件不需要依赖 Compose 或 Miuix，也不能自己创建设置页面。

| `type` | 交互 |
| --- | --- |
| `switch` | 开关 |
| `text` | 文本输入 |
| `password` | 密码输入 |
| `select` | 单选 |
| `multiSelect` | 多选 |
| `number` | 整数输入 |
| `slider` | 带 `min`、`max`、`step` 的滑块 |
| `action` | 操作项协议；当前宿主不执行插件自定义动作 |

`title` 和 `summary` 用于显示名称和说明；需要多语言时使用 `titleLocales`、`summaryLocales`。`valuePresentation` 可选择 `endAction`、`summary` 或 `summaryPreview`。`inputType` 可声明 `uri` 或 `number`，`conflictsWith` 可声明互斥设置。可选的 `group` 会让同组设置在宿主 UI 中进入同一个设置 Card；未声明时使用 `default` 组。

Manifest 可以用顶层 `settingGroups` 为设置组声明 `id`、`title` 和可选的 `titleLocales`。宿主会在对应 Card 前使用 Miuix `SmallTitle` 显示组标题；未声明标题的旧插件仍保持原有布局。例如，`group: "generation"` 的设置会归入 `id: "generation"` 的设置组。

如果声明 `activationSettingKey`，插件页顶部的通用开关会同步插件启用状态和这个设置项。配置变化会触发 `onConfigChanged`，但只在下一次正常处理时生效，不会主动重跑当前歌曲。

API Key 等不应进入备份的值必须声明 `backup: false`：

```json
{
  "type": "password",
  "key": "api_key",
  "title": "API Key",
  "default": "",
  "backup": false
}
```

## 存储、缓存和日志

- `config`：只读配置，支持 `getBoolean`、`getString`、`getLong`、`getFloat` 和 `getStringSet`。
- `storage`：保存小型插件状态，支持读、写、删除和清空；它不等同于缓存。
- `cache`：保存可丢弃的结果，支持字符串和字节读写、存在性检查、删除和清空。插件负责 key、序列化、版本和失效策略，宿主负责按插件 ID 隔离和异常保护。
- `logger`：使用 `debug`、`info`、`warn` 和 `error`，不要直接写 Android 日志或自定义日志文件。

缓存损坏、读取失败或超限时，应忽略或删除当前条目，回退到网络或无结果路径，不要清空原始歌词。缓存不要保存整份可能过期的 `PluginSong`；应保存可以基于当前歌曲重新应用的结果。

### 可管理缓存

若需要 App 展示和清理缓存，在 Manifest 增加独立于 UI 框架的作用域：

```json
{
  "cacheScopes": [
    { "id": "translation", "title": "翻译缓存", "summary": "清理翻译结果" }
  ]
}
```

入口在 `onLoad` 中以相同 ID 注册 `PluginCacheExtension`。`listEntries()` 最多应返回最近的 100 条 `PluginCacheEntry`，只包含展示所需的元数据；`entryId` 对 Core/App 是不透明值。插件负责 entryId 与真实 cache key 的映射、索引、删除和全部清理，`clearAll()`/`clearEntry()` 只能影响 `PluginCache`，不能清除配置或 `PluginStorage`。App 通过 RemotePreferences 发送带 requestId 和一次性 response token 的请求；SystemUI Runtime 执行扩展后，将有界结果回传给 App 的受控 Provider，因为目标进程的 RemotePreferences 视图只读。禁用插件时，Runtime 仍可为了缓存管理调用 `onLoad`，但不会调用 `onEnable` 或启用歌词处理器；因此 `onLoad` 只能创建状态和注册扩展，播放相关或主动任务必须放在 `onEnable`。插件不会直接创建 Compose/Miuix 页面，也不应在清理后主动重跑当前歌曲。
