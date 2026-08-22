# Packaging, Installation, and Validation

## Module layout and dependencies

Plugin modules live under `Plugins/modules/`, with one Gradle module per plugin:

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

Use `compileOnly` for the API and host-provided libraries, and `implementation` only for runtime libraries owned by the plugin:

```kotlin
dependencies {
    compileOnly(project(":plugins:api"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:<host-version>")
    implementation("com.example:plugin-only-library:<version>")
}
```

Do not depend on `:app`, and do not package `Plugins/api`, the Kotlin runtime, or other libraries already supplied by the host.

## Manifest

The Manifest is at `src/main/plugin/manifest.json`:

```json
{
  "id": "hyperlyric.example.translation",
  "name": "Example Translation Plugin",
  "version": "1.0.0",
  "apiVersion": 1,
  "entry": "com.example.hyperlyric.MyPlugin"
}
```

| Field | Meaning |
| --- | --- |
| `id` | Stable ID for installation, configuration, and storage; do not change it after release. |
| `name` | Display name. |
| `summary` | Optional short description. |
| `author` | Optional author information. |
| `version` | Plugin version. |
| `apiVersion` | Required Plugin API version. |
| `entry` | Entry class implementing `HyperLyricPlugin`. |

The entry class must be public and have a no-argument constructor. See [Plugin API and configuration reference](api.md) for the lifecycle and processor contract.

## R8 and ZIP output

Release plugins must be checked against their runtime boundaries after R8. A plugin module should keep only its own entry points and members reached through the Manifest, reflection, or the Plugin API contract, including at least:

- the Manifest entry class and its no-argument constructor;
- `onLoad`, `onEnable`, `onConfigChanged`, `onUnload`;
- `HyperLyricExtension.id`, `LyricProcessorExtension.stage`, and `processResult`;
- other plugin implementation types that are actually looked up by class name or serialized by the plugin itself.

Do not add duplicate keep rules in plugin modules for `PluginSong`, `PluginSongResult`, field enums, or other Plugin API types. `Plugins/api` is a `compileOnly` dependency and is not packaged into the plugin DEX. The host's [`app/proguard-rules.pro`](../../../app/proguard-rules.pro) keeps the names and method descriptors of these public types stable. Plugin-side rules cannot protect API classes inside the host, and packaging a private copy of the API in the ZIP is not a valid workaround.

Use `Plugins/modules/demo-logger/proguard-rules.pro` as a reference: keep the exact entry class declared by the Manifest and only the members invoked by the host through the protocol. Do not keep the whole plugin package.

A plugin ZIP normally contains:

```text
my-plugin.zip
├─ manifest.json
├─ classes.dex
└─ classes2.dex       # if the build produces multiple DEX files
```

The packaging task extracts every `classes*.dex` from the Release APK and combines them with `manifest.json`. Only the plugin's own `implementation` dependencies are carried by the plugin.

## Build and install

Debug ZIP:

```powershell
.\gradlew.bat :plugins:my-plugin:packageDebugPlugin --max-workers=2
```

Release ZIP:

```powershell
.\gradlew.bat :plugins:my-plugin:packagePlugin --max-workers=2
```

Install the ZIP from HyperLyric's plugin manager, configure it, and enable it. Restart SystemUI after installation, removal, or a code upgrade. Ordinary configuration changes do not require a restart.

## Validation checklist

1. Both Debug and Release ZIPs build, and Runtime can load the R8 entry class.
2. The ZIP contains only the expected Manifest, DEX files, and plugin-owned runtime libraries; it has no duplicate host API classes.
3. Load, enable, configuration changes, and removal are visible in logs.
4. Successful results write the intended fields; unreliable word timing never writes a `*Words` field.
5. Timeouts, exceptions, empty results, parse errors, and corrupt cache entries leave the original lyrics usable.
6. A fast song change cancels old work, and a late result cannot update the new song.
7. Configuration changes work without restart, while code changes require a SystemUI restart.
