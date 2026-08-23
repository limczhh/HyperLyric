package com.lidesheng.hyperlyric.root.plugin

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.plugin.api.HyperLyricExtension
import com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin
import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension
import com.lidesheng.hyperlyric.plugin.api.PluginContext
import com.lidesheng.hyperlyric.plugin.core.PluginManifest
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the immutable processor/cache snapshots derived from loaded plugin instances. */
internal class PluginExtensionRegistry {
    @Volatile
    private var processors: List<RegisteredProcessor> = emptyList()

    @Volatile
    private var cacheExtensions: List<RegisteredCacheExtension> = emptyList()

    fun rebuild(loadedPlugins: List<LoadedPlugin>, enabledPluginIds: Set<String>) {
        val registeredProcessors = mutableListOf<RegisteredProcessor>()
        loadedPlugins
            .filter { it.processingEnabled && it.manifest.id in enabledPluginIds }
            .forEachIndexed { pluginIndex, loadedPlugin ->
                loadedPlugin.extensions.filterIsInstance<LyricProcessorExtension>()
                    .forEachIndexed { extensionIndex, extension ->
                        registeredProcessors += RegisteredProcessor(
                            pluginId = loadedPlugin.manifest.id,
                            pluginIndex = pluginIndex,
                            extensionIndex = extensionIndex,
                            extension = extension
                        )
                    }
            }
        processors = registeredProcessors.sortedWith(
            compareBy<RegisteredProcessor> { it.extension.stage.ordinal }
                .thenBy { it.pluginId }
                .thenBy { it.extension.id }
                .thenBy { it.pluginIndex }
                .thenBy { it.extensionIndex }
        )
        cacheExtensions = loadedPlugins.flatMap { loadedPlugin ->
            loadedPlugin.extensions.filterIsInstance<PluginCacheExtension>()
                .filter { extension -> loadedPlugin.manifest.cacheScopes.any { it.id == extension.id } }
                .map { extension ->
                    RegisteredCacheExtension(
                        pluginId = loadedPlugin.manifest.id,
                        extension = extension
                    )
                }
        }.sortedWith(compareBy<RegisteredCacheExtension> { it.pluginId }.thenBy { it.extension.id })
    }

    fun processingSnapshot(enabledPluginIds: Set<String>): List<RegisteredProcessor> =
        processors.filter { it.pluginId in enabledPluginIds }

    fun processingFingerprint(enabledPluginIds: Set<String>): String =
        processingSnapshot(enabledPluginIds).joinToString("\u001F") { registered ->
            "${registered.pluginId}:${registered.extension.id}:${registered.extension.stage.name}"
        }

    fun removeDisabledProcessors(disabledIds: Set<String>) {
        if (disabledIds.isEmpty()) return
        processors = processors.filter { it.pluginId !in disabledIds }
    }

    fun findCacheExtension(pluginId: String, scopeId: String): PluginCacheExtension? =
        cacheExtensions.firstOrNull {
            it.pluginId == pluginId && it.extension.id == scopeId
        }?.extension

    fun processorCount(): Int = processors.size

    fun cacheExtensionCount(): Int = cacheExtensions.size

    fun clear() {
        processors = emptyList()
        cacheExtensions = emptyList()
    }
}

internal data class LoadedPlugin(
    val manifest: PluginManifest,
    val plugin: HyperLyricPlugin,
    val context: PluginContext,
    val preferences: SharedPreferences,
    val extensions: List<HyperLyricExtension>,
    var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null,
    @Volatile var processingEnabled: Boolean = false,
) {
    val lifecycleActive: AtomicBoolean = AtomicBoolean(true)
}

internal data class RegisteredProcessor(
    val pluginId: String,
    val pluginIndex: Int,
    val extensionIndex: Int,
    val extension: LyricProcessorExtension,
)

internal data class RegisteredCacheExtension(
    val pluginId: String,
    val extension: PluginCacheExtension,
)
