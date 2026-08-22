# 打包、安装与验证

## 模块结构和依赖

插件模块位于 `Plugins/modules/`，每个插件使用独立的 Gradle 模块：

```text
Plugins/
├─ api/
└─ modules/
   └─ my-plugin/
      ├─ build.gradle.kts
      ├─ proguard-rules.pro
      └─ src/main/
         ├─ java/.../MyPlugin.kt
         └─ plugin/manifest.json
```

API 和宿主已经提供的运行库使用 `compileOnly`，插件自己的网络库、解析库或其他运行时依赖使用 `implementation`：

```kotlin
dependencies {
    compileOnly(project(":plugins:api"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:<host-version>")
    implementation("com.example:plugin-only-library:<version>")
}
```

插件不能依赖 `:app`，也不要把 `Plugins/api`、Kotlin 运行库或宿主已有运行库重复打入 ZIP。

## Manifest

Manifest 位于 `src/main/plugin/manifest.json`：

```json
{
  "id": "hyperlyric.example.translation",
  "name": "示例翻译插件",
  "version": "1.0.0",
  "apiVersion": 1,
  "entry": "com.example.hyperlyric.MyPlugin"
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `id` | 稳定 ID，用于安装识别、配置和存储；发布后不要修改。 |
| `name` | 插件显示名称。 |
| `summary` | 简短说明，可选。 |
| `author` | 作者信息，可选。 |
| `version` | 插件版本。 |
| `apiVersion` | 所需 Plugin API 版本。 |
| `entry` | 实现 `HyperLyricPlugin` 的入口类。 |

入口类必须是公共无参数类。完整的生命周期和处理器写法见[Plugin API 与配置参考](api.md)。

## R8 和 ZIP

Release 插件必须验证 R8 后的运行时边界。插件模块只保护插件自身由 Manifest、反射或 Plugin API 协议访问的入口和成员，至少包括：

- Manifest 中的入口类及无参数构造函数；
- `onLoad`、`onEnable`、`onConfigChanged`、`onUnload`；
- `HyperLyricExtension.id`、`LyricProcessorExtension.stage` 和 `processResult`；
- 其他确实按类名反射或由插件自行序列化的插件实现类型。

不要在插件模块中为 `PluginSong`、`PluginSongResult`、字段枚举或其他 Plugin API 类型重复添加 keep 规则。`Plugins/api` 是 `compileOnly` 依赖，不会打入插件 DEX；这些公共类型的名称和方法描述符由宿主的 [`app/proguard-rules.pro`](../../app/proguard-rules.pro) 负责稳定。插件侧的同名规则既不能保护宿主中的 API 类，也不应通过把 API 打入 ZIP 来解决。

可以参考 `Plugins/modules/demo-logger/proguard-rules.pro`：精确保留 Manifest 声明的入口类，并只保留宿主通过协议调用的成员；不要把整个插件包都设为 keep。

插件 ZIP 通常包含：

```text
my-plugin.zip
├─ manifest.json
├─ classes.dex
└─ classes2.dex       # 如果构建产生多 DEX
```

打包任务会从 Release APK 提取全部 `classes*.dex`，再与 `manifest.json` 合并。插件自己的 `implementation` 依赖才由插件携带。

## 构建和安装

Debug ZIP：

```powershell
.\gradlew.bat :plugins:my-plugin:packageDebugPlugin --max-workers=2
```

Release ZIP：

```powershell
.\gradlew.bat :plugins:my-plugin:packagePlugin --max-workers=2
```

在 HyperLyric 的插件管理中安装 ZIP，完成配置并启用。安装、卸载或代码升级后重启 SystemUI；普通配置修改不需要重启。

## 验证清单

1. Debug 和 Release ZIP 都能生成，R8 后的入口类能被 Runtime 加载。
2. ZIP 只包含预期的 Manifest、DEX 和插件自己的运行库，没有重复的宿主 API。
3. 加载、启用、配置变更和卸载都能在日志中确认。
4. 成功结果写入正确字段；不可靠的词级时间信息不写入任何 `*Words` 字段。
5. 超时、异常、空结果、解析错误和缓存损坏时，原始歌词仍然可用。
6. 快速切歌时旧任务会取消，迟到结果不会写回新歌曲。
7. 配置更新无需重启，代码更新需要重启 SystemUI。
