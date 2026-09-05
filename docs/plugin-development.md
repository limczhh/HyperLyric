# HyperLyric 插件开发文档

如果你想为 HyperLyric 接入新的歌词服务、翻译能力或歌词处理逻辑，请从这里开始。本文先讲清楚适配流程，再把具体字段和打包规则分别放到参考页面。

## 先确认适配边界

当前 Plugin API 是歌词处理插件接口。插件运行在 HyperLyric 注入的 SystemUI 进程中，接收已经生成完整歌词的 `PluginSong`，然后返回翻译、罗马音、逐字信息或其他歌词修改。

它不是独立 App 的 MediaSession 或在线歌词源接口。独立 App 的媒体监听和歌词源仍由 App 自己管理；如果以后要给独立 App 接入新的歌词源，需要单独设计 App Runtime 和 `LyricSourceExtension`，不要把当前的 Processor API 硬套过去。

插件只能使用 `Plugins/api` 暴露的类型，不能依赖 `:app`，也不能直接访问宿主的 `Song`、Renderer、Canvas、SystemUI View、MediaSession、`Context` 或 Xposed 对象。

## 插件是怎么工作的

```text
插件 ZIP
  → App 安装、配置并同步插件文件
  → SystemUI 启动时加载全部已安装插件
  → enabled_ids 只决定处理器是否进入歌词流程
  → 插件注册处理器
  → 收到只读 PluginSong
  → 返回 PluginSongResult
  → 宿主校验并合并结果
```

需要记住四点：

- 原始歌词会先显示，插件处理在后台进行。
- SystemUI 重启时，已安装但禁用的插件也会执行 `onLoad` 并完成代码加载；它不会被选入歌词处理器链。
- 插件返回 `null`、报错、超时或结果不合法时，宿主保留当前歌词，并继续处理其他插件。
- 插件按 `LYRIC_REPLACEMENT`、`TRANSLATION_ENHANCEMENT` 等阶段执行；后一个处理器会收到前一个处理器合并后的结果。
- 切歌、歌词源停止或媒体信息变化时，宿主会取消旧任务。插件必须响应线程中断或协程取消，迟到结果也不能写回新歌曲。

单个处理器的宿主等待上限是 40 秒。网络请求、模型调用和缓存读取应设置更短的超时。配置修改会同步给插件，但只在下一次正常处理时生效，不会主动重跑当前歌曲。

## 开始适配

### 1. 创建插件模块

在 `Plugins/modules/` 下创建独立 Gradle 模块。插件只依赖 `Plugins/api` 暴露的接口；API 和宿主已经提供的库使用 `compileOnly`，插件自己的运行时库使用 `implementation`。具体目录和依赖写法见[打包、安装与验证](plugins/packaging.md)。

### 2. 写入口并注册处理器

入口类实现 `HyperLyricPlugin`，在 `onLoad` 中注册 `LyricProcessorExtension`。处理器从 `PluginSong` 读取数据，创建新的 DTO，并返回 `PluginSongResult`；不要修改收到的对象，也不要保存宿主内部对象的引用。

入口、处理器和 DTO 的完整写法见[Plugin API 与配置参考](plugins/api.md)。

### 3. 选择正确的写回方式

| 需求 | 写法 |
| --- | --- |
| 只补翻译、罗马音、secondary 或逐字信息 | `LYRICS + PATCH`，声明对应的 `PluginLyricField` |
| 返回一整份新的歌词 | `LYRICS + REPLACE` |
| 修改标题、艺术家、专辑等顶层字段 | 在 `changedFields` 中声明对应的 `PluginSongField` |
| 没有可靠结果 | 返回 `null`，不要返回半成品 |

`PATCH` 必须保持行数和行索引不变；`REPLACE` 可以改变行数和时间轴。只有时间轴可靠时，才写入 `WORDS`、`TRANSLATION_WORDS` 等逐字字段。字段声明和合并规则见[Plugin API 与配置参考](plugins/api.md)。

### 4. 把设置交给宿主

插件设置写在 Manifest 的 Settings Schema 中，由宿主生成设置页面。不要在插件里自己依赖 Compose、Miuix 或创建 Android 页面。API Key 等敏感值要声明 `backup: false`。

设置、存储、缓存和日志的写法见[Plugin API 与配置参考](plugins/api.md)。需要缓存管理时，在 Manifest 使用 `cacheScopes` 声明 `id`、插件自定义的 `title` 和可选 `summary`，再以相同 `id` 注册 `PluginCacheExtension`。宿主以 `title` 生成入口与页面标题，`summary` 只是可选元数据，不保证显示。插件自己维护缓存索引、entryId 映射与条目展示元数据；宿主只通过带 requestId 和一次性 response token 的跨进程请求调用列表与清理，由 SystemUI 向 App 受控 Provider 回传有界结果，绝不解析插件缓存 JSON。缓存正文、API Key 和完整翻译不能放入 `PluginCacheEntry`。

### 5. 打包并验证

先构建 Debug ZIP，再验证 Release 和 R8 版本。安装、卸载和代码升级后需要重启 SystemUI；启用/禁用和普通配置修改会实时同步，不需要重启。

提交前至少测试：

1. 插件能正常加载、启用、停用和卸载。
2. 网络失败、超时、空结果、解析错误和缓存损坏时，原始歌词仍然可用。
3. 快速切歌后，旧任务会取消，旧结果不会写到新歌曲。
4. 修改配置后，下一次处理使用新配置，当前歌曲不会被自动重跑。
5. Release/R8 ZIP 能被 Runtime 加载，且没有重复打包宿主 API。

具体命令和 R8 规则见[打包、安装与验证](plugins/packaging.md)。

## 参考实现

- `Plugins/modules/ai-translation`：网络请求、配置、缓存、队列和翻译 PATCH 的完整示例。
- `Plugins/modules/demo-logger`：用于观察入口、生命周期、字段合并和逐字时间轴。

这两个模块适合用来对照 API 用法，但新插件仍应只依赖 `Plugins/api`，不要复制宿主内部实现。
