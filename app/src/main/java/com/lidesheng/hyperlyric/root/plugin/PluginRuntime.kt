package com.lidesheng.hyperlyric.root.plugin

import android.app.Application
import android.content.SharedPreferences
import dalvik.system.DelegateLastClassLoader
import com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin
import com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension
import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginLyricField
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationCodec
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationRequest
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationResponse
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationType
import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import com.lidesheng.hyperlyric.plugin.core.PluginRemoteFileNames
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** SystemUI-side loader and executor for trusted HyperLyric ZIP plugins. */
class PluginRuntime(
    private val module: XposedModule,
    private val application: Application,
    private val parentClassLoader: ClassLoader =
        HyperLyricPlugin::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
) {
    companion object {
        private const val TAG = "PluginRuntime"
        private val creatingPluginLoaderDepth = ThreadLocal.withInitial<Int> { 0 }

        /** Prevent the existing SystemUI plugin hook from treating our API loader as a host one. */
        @JvmStatic
        fun isCreatingPluginClassLoader(): Boolean = currentPluginLoaderDepth() > 0

        private inline fun <T> withPluginClassLoaderCreation(block: () -> T): T {
            creatingPluginLoaderDepth.set(currentPluginLoaderDepth() + 1)
            return try {
                block()
            } finally {
                creatingPluginLoaderDepth.set(
                    (currentPluginLoaderDepth() - 1).coerceAtLeast(0)
                )
            }
        }

        private fun currentPluginLoaderDepth(): Int = creatingPluginLoaderDepth.get() ?: 0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processorExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "HyperLyric-PluginProcessor").apply { isDaemon = true }
    }
    private val generation = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private var activeJob: Job? = null
    private val loadedPlugins = mutableListOf<LoadedPlugin>()
    private val extensionRegistry = PluginExtensionRegistry()
    private val archiveLoader = PluginArchiveLoader(module, application)
    private val pluginLoadLock = Any()
    private val cacheCoordinator = PluginCacheCoordinator(
        application = application,
        scope = scope,
        isClosed = { closed.get() },
        cancelActiveProcessing = ::cancelActiveProcessing,
        clearLoadedPluginCache = ::clearLoadedPluginCache,
        executeOperation = ::executeCacheOperationWithPluginLock
    )
    private var registryPreferences: SharedPreferences? = null
    @Volatile
    private var enabledPluginIds: Set<String> = emptySet()
    private val enabledPluginReconcilePending = AtomicBoolean(false)

    private val registryListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == PluginConstants.REMOTE_ENABLED_IDS_KEY) {
            // A global toggle is a boundary for the next song, not a cancellation signal for the
            // current request. Keep this callback limited to the lightweight processor gate;
            // lifecycle cleanup is deferred until the next processSong call.
            val updatedEnabledIds = readEnabledPluginIds(preferences)
            enabledPluginIds = updatedEnabledIds
            enabledPluginReconcilePending.set(true)
        }
        if (key == PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY) {
            cacheCoordinator.consumePendingCacheClears(registryPreferences)
        }
        if (key == PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY) {
            cacheCoordinator.consumePendingCacheOperations(registryPreferences)
        }
    }

    fun loadEnabledPlugins() {
        if (closed.get()) return

        val registry = runCatching {
            module.getRemotePreferences(PluginConstants.REMOTE_REGISTRY_PREFS)
        }.getOrElse { error ->
            HookLogger.w(TAG, "读取插件启用状态失败，跳过插件加载", error)
            return
        }
        registryPreferences = registry
        cacheCoordinator.consumePendingCacheClears(registry)
        val enabledIds = runCatching {
            readEnabledPluginIds(registry)
        }.getOrElse { error ->
            HookLogger.w(TAG, "读取插件启用状态失败，跳过插件加载", error)
            emptySet()
        }
        enabledPluginIds = enabledIds
        enabledPluginReconcilePending.set(false)
        registry.registerOnSharedPreferenceChangeListener(registryListener)
        if (enabledIds.isEmpty()) {
            rebuildExtensionRegistries()
            cacheCoordinator.consumePendingCacheOperations(registry)
            HookLogger.d(TAG, "没有启用的 HyperLyric 插件")
            return
        }

        val remoteFiles = runCatching { module.listRemoteFiles().toSet() }.getOrElse { error ->
            HookLogger.w(TAG, "读取插件远程文件列表失败，跳过插件加载", error)
            return
        }

        enabledIds.sorted().forEach { pluginId ->
            val fileName = resolvePluginFileName(registry, pluginId)
            if (fileName !in remoteFiles) {
                HookLogger.w(TAG, "插件文件不存在: id=$pluginId, file=$fileName")
                return@forEach
            }
            synchronized(pluginLoadLock) {
                if (loadedPlugins.any { it.manifest.id == pluginId }) return@synchronized
                runCatching { loadPlugin(pluginId, fileName, enableProcessing = true) }
                    .onFailure { error ->
                        HookLogger.w(TAG, "插件加载失败: id=$pluginId", error)
                    }
            }
        }

        rebuildExtensionRegistries()
        cacheCoordinator.consumePendingCacheOperations(registry)
        HookLogger.i(
            TAG,
            "插件 Runtime 初始化完成: enabled=${enabledIds.size}, " +
                "loaded=${loadedPlugins.size}, processors=${extensionRegistry.processorCount()}, " +
                    "cacheExtensions=${extensionRegistry.cacheExtensionCount()}"
        )
    }

    private fun readEnabledPluginIds(registry: SharedPreferences): Set<String> =
        registry.getStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, emptySet()).orEmpty().toSet()

    private fun reconcileEnabledPlugins(registry: SharedPreferences) {
        if (closed.get()) return

        val currentEnabledIds = runCatching { readEnabledPluginIds(registry) }
            .onFailure { error ->
                HookLogger.w(TAG, "读取插件启用状态失败，跳过运行时同步", error)
            }
            .getOrElse { emptySet() }
        enabledPluginIds = currentEnabledIds

        val disabledIds = synchronized(pluginLoadLock) {
            loadedPlugins
                .filter { it.processingEnabled && it.manifest.id !in currentEnabledIds }
                .mapTo(linkedSetOf()) { it.manifest.id }
        }
        if (disabledIds.isNotEmpty()) {
            removeDisabledProcessors(disabledIds)
            synchronized(pluginLoadLock) {
                val pluginsToUnload = loadedPlugins.filter { loaded ->
                    loaded.processingEnabled &&
                            loaded.manifest.id in disabledIds &&
                            loaded.manifest.id !in enabledPluginIds
                }
                loadedPlugins.removeAll(pluginsToUnload.toSet())
                pluginsToUnload.asReversed().forEach(::unloadLoadedPlugin)
            }
        }

        if (closed.get() || currentEnabledIds.isEmpty()) {
            rebuildExtensionRegistries()
            return
        }

        val remoteFiles = runCatching { module.listRemoteFiles().toSet() }
            .getOrElse { error ->
                HookLogger.w(TAG, "读取插件远程文件列表失败，跳过运行时同步", error)
                rebuildExtensionRegistries()
                return
            }

        currentEnabledIds.sorted().forEach { pluginId ->
            if (closed.get()) return@forEach
            val fileName = resolvePluginFileName(registry, pluginId)
            if (fileName !in remoteFiles) {
                HookLogger.w(TAG, "插件文件不存在: id=$pluginId, file=$fileName")
                return@forEach
            }
            synchronized(pluginLoadLock) {
                if (closed.get()) return@synchronized
                if (pluginId !in enabledPluginIds) return@synchronized
                val loaded = loadedPlugins.firstOrNull { it.manifest.id == pluginId }
                if (loaded == null) {
                    runCatching { loadPlugin(pluginId, fileName, enableProcessing = true) }
                        .onFailure { error ->
                            HookLogger.w(TAG, "插件运行时启用失败: id=$pluginId", error)
                        }
                } else if (!loaded.processingEnabled) {
                    runCatching { enableLoadedPlugin(loaded) }
                        .onFailure { error ->
                            HookLogger.w(TAG, "插件运行时启用失败: id=$pluginId", error)
                        }
                }
            }
        }

        rebuildExtensionRegistries()
    }

    private fun removeDisabledProcessors(disabledIds: Set<String>) {
        extensionRegistry.removeDisabledProcessors(disabledIds)
    }

    private fun rebuildExtensionRegistries() {
        val loaded = synchronized(pluginLoadLock) { loadedPlugins.toList() }
        extensionRegistry.rebuild(loaded, enabledPluginIds)
    }

    internal fun processingSetFingerprint(): String =
        extensionRegistry.processingFingerprint(enabledPluginIds)

    internal fun processSong(
        song: PluginSong,
        processingContext: PluginProcessingContext,
        onResult: (PluginProcessingResult?) -> Unit
    ) {
        if (closed.get()) return
        val currentGeneration = generation.incrementAndGet()
        activeJob?.cancel()
        if (enabledPluginReconcilePending.compareAndSet(true, false)) {
            // The previous request has already reached its normal next-song cancellation point.
            // Cleanup can now run asynchronously without interrupting the new request.
            registryPreferences?.let { registry ->
                scope.launch { reconcileEnabledPlugins(registry) }
            }
        }
        val currentProcessors = extensionRegistry.processingSnapshot(enabledPluginIds)
        if (currentProcessors.isEmpty()) {
            runCatching { onResult(null) }.onFailure { error ->
                HookLogger.w(TAG, "插件结果回调失败", error)
            }
            return
        }

        activeJob = scope.launch {
            var current = song
            val changedFields = linkedSetOf<PluginSongField>()
            val changedLyricFields = linkedSetOf<PluginLyricField>()
            var lyricsUpdateMode: PluginLyricsUpdateMode? = null
            for (registered in currentProcessors) {
                if (!isActive || currentGeneration != generation.get()) return@launch
                val result = runProcessor(
                    processor = registered.extension,
                    song = current,
                    processingContext = processingContext
                ) ?: continue
                val merged = PluginSongMapper.mergePluginSong(
                    base = current,
                    result = result
                )
                if (merged == null) {
                    HookLogger.w(
                        TAG,
                        "插件结果非法，保留当前快照: extension=${registered.extension.id}"
                    )
                    continue
                }
                if (merged != current) {
                    current = merged
                    changedFields += result.changedFields
                    if (PluginSongField.LYRICS in result.changedFields) {
                        if (result.lyricsUpdateMode == PluginLyricsUpdateMode.REPLACE) {
                            // A full replacement already contains every lyric field. Any patch
                            // that follows is applied to this complete snapshot and the final
                            // callback can safely remain a REPLACE result.
                            lyricsUpdateMode = PluginLyricsUpdateMode.REPLACE
                            changedLyricFields.clear()
                        } else if (lyricsUpdateMode != PluginLyricsUpdateMode.REPLACE) {
                            lyricsUpdateMode = PluginLyricsUpdateMode.PATCH
                            changedLyricFields += result.changedLyricFields
                        }
                    }
                }
            }
            if (!isActive || currentGeneration != generation.get()) {
                return@launch
            }
            if (changedFields.isEmpty()) {
                runCatching { onResult(null) }.onFailure { error ->
                    HookLogger.w(TAG, "插件结果回调失败", error)
                }
                return@launch
            }
            runCatching {
                onResult(
                    PluginProcessingResult(
                        result = PluginSongResult(
                            song = current,
                            changedFields = changedFields.toSet(),
                            lyricsUpdateMode = if (
                                PluginSongField.LYRICS in changedFields &&
                                lyricsUpdateMode != PluginLyricsUpdateMode.REPLACE
                            ) {
                                PluginLyricsUpdateMode.PATCH
                            } else {
                                PluginLyricsUpdateMode.REPLACE
                            },
                            changedLyricFields = changedLyricFields.toSet()
                        )
                    )
                )
            }.onFailure { error ->
                HookLogger.w(TAG, "插件结果回调失败", error)
            }
        }
    }

    fun cancelActiveProcessing() {
        generation.incrementAndGet()
        activeJob?.cancel()
        activeJob = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        cancelActiveProcessing()
        synchronized(pluginLoadLock) {
            val pluginsToClose = loadedPlugins.toList()
            loadedPlugins.clear()
            pluginsToClose.asReversed().forEach(::unloadLoadedPlugin)
        }
        extensionRegistry.clear()
        cacheCoordinator.close()
        registryPreferences?.let { preferences ->
            runCatching { preferences.unregisterOnSharedPreferenceChangeListener(registryListener) }
        }
        registryPreferences = null
        processorExecutor.shutdownNow()
        scope.cancel()
    }

    /** Clears plugin-owned memory state before the uninstall tombstone removes its files. */
    private fun clearLoadedPluginCache(pluginId: String) {
        synchronized(pluginLoadLock) {
            val loaded = loadedPlugins.firstOrNull { it.manifest.id == pluginId }
                ?: return@synchronized
            loaded.extensions.filterIsInstance<PluginCacheExtension>()
                .filter { extension -> loaded.manifest.cacheScopes.any { it.id == extension.id } }
                .forEach { extension -> extension.clearAll() }
        }
    }

    private fun executeCacheOperationWithPluginLock(
        request: PluginCacheOperationRequest
    ): PluginCacheOperationResponse = synchronized(pluginLoadLock) {
        val loaded = findOrLoadPluginForCache(request.pluginId)
            ?: return@synchronized cacheOperationFailure(request, "plugin_not_loaded")
        if (
            Thread.currentThread().isInterrupted ||
            PluginCacheOperationCodec.isOperationTimedOut(request, System.currentTimeMillis())
        ) {
            return@synchronized cacheOperationFailure(request, "operation_timeout")
        }
        if (loaded.manifest.cacheScopes.none { it.id == request.scopeId }) {
            return@synchronized cacheOperationFailure(request, "scope_not_declared")
        }
        val extension = extensionRegistry.findCacheExtension(
            pluginId = request.pluginId,
            scopeId = request.scopeId
        ) ?: return@synchronized cacheOperationFailure(request, "scope_not_loaded")

        try {
            when (request.type) {
                PluginCacheOperationType.LIST -> PluginCacheOperationResponse(
                    requestId = request.requestId,
                    success = true,
                    entries = PluginCacheOperationCodec.sanitizeEntries(
                        extension.listEntries()
                    )
                )

                PluginCacheOperationType.CLEAR_ALL -> {
                    extension.clearAll()
                    PluginCacheOperationResponse(
                        requestId = request.requestId,
                        success = true
                    )
                }

                PluginCacheOperationType.CLEAR_ENTRY ->
                    PluginCacheOperationCodec.clearEntryResponse(
                        request,
                        extension.clearEntry(request.entryId.orEmpty())
                    )
            }
        } catch (error: Throwable) {
            HookLogger.w(
                TAG,
                "插件缓存操作失败: plugin=${request.pluginId}, scope=${request.scopeId}",
                error
            )
            cacheOperationFailure(request, "extension_failed")
        }
    }

    /**
     * A disabled plugin does not run lyric processors, but its declared cache remains user-owned
     * data. Load it only when the App asks to manage that cache, without invoking [onEnable].
     */
    private fun findOrLoadPluginForCache(pluginId: String): LoadedPlugin? =
        synchronized(pluginLoadLock) {
            loadedPlugins.firstOrNull { it.manifest.id == pluginId } ?: run {
                val fileName = resolvePluginFileName(registryPreferences, pluginId)
                val remoteFiles = runCatching { module.listRemoteFiles().toSet() }
                    .getOrElse { error ->
                        HookLogger.w(TAG, "读取插件远程文件列表失败，无法管理缓存: id=$pluginId", error)
                        return@synchronized null
                    }
                if (fileName !in remoteFiles) {
                    HookLogger.w(TAG, "插件文件不存在，无法管理缓存: id=$pluginId, file=$fileName")
                    return@synchronized null
                }
                runCatching {
                    loadPlugin(pluginId, fileName, enableProcessing = false).also {
                        rebuildExtensionRegistries()
                    }
                }.onFailure { error ->
                    HookLogger.w(TAG, "为缓存管理加载插件失败: id=$pluginId", error)
                }.getOrNull()
            }
        }

    private fun resolvePluginFileName(
        registry: SharedPreferences?,
        pluginId: String,
    ): String = registry
        ?.getString(PluginConstants.remoteFileKey(pluginId), null)
        ?.takeIf(String::isNotBlank)
        ?: PluginRemoteFileNames.forId(pluginId)

    private fun cacheOperationFailure(
        request: PluginCacheOperationRequest,
        errorCode: String
    ): PluginCacheOperationResponse = PluginCacheOperationResponse(
        requestId = request.requestId,
        success = false,
        errorCode = errorCode
    )

    private fun loadPlugin(
        pluginId: String,
        fileName: String,
        enableProcessing: Boolean,
    ): LoadedPlugin {
        val (archive, archiveFile) = archiveLoader.load(pluginId, fileName)
        val classLoader = withPluginClassLoaderCreation {
            DelegateLastClassLoader(archiveFile.path, parentClassLoader)
        }
        val entryClass = classLoader.loadClass(archive.manifest.entry)
        require(HyperLyricPlugin::class.java.isAssignableFrom(entryClass)) {
            "Plugin entry does not implement HyperLyricPlugin"
        }
        val plugin = entryClass.getDeclaredConstructor().apply { isAccessible = true }
            .newInstance() as HyperLyricPlugin
        val preferences = module.getRemotePreferences(PluginConstants.configGroup(pluginId))
        val context = RuntimePluginContext(pluginId, application, preferences)

        try {
            plugin.onLoad(context)
            if (enableProcessing) plugin.onEnable()
        } catch (error: Throwable) {
            runCatching { plugin.onUnload() }
            throw error
        }

        val loaded = LoadedPlugin(
            manifest = archive.manifest,
            plugin = plugin,
            context = context,
            preferences = preferences,
            extensions = context.registeredExtensions(),
            processingEnabled = enableProcessing
        )
        loadedPlugins += loaded
        try {
            if (enableProcessing) registerConfigListener(loaded)
        } catch (error: Throwable) {
            loadedPlugins.remove(loaded)
            unloadLoadedPlugin(loaded)
            throw error
        }
        HookLogger.i(
            TAG,
            "插件已${if (enableProcessing) "启用" else "为缓存管理加载"}: " +
                    "id=$pluginId, version=${archive.manifest.version}, " +
                    "extensions=${context.registeredExtensions().size}"
        )
        return loaded
    }

    private fun registerConfigListener(loaded: LoadedPlugin) {
        if (loaded.listener != null) return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            if (loaded.processingEnabled && loaded.lifecycleActive.get() && !closed.get()) {
                scope.launch {
                    if (!loaded.processingEnabled || !loaded.lifecycleActive.get() || closed.get()) {
                        return@launch
                    }
                    runCatching { loaded.plugin.onConfigChanged(loaded.context.config) }
                        .onFailure { error ->
                            HookLogger.w(
                                TAG,
                                "插件配置回调失败: id=${loaded.manifest.id}",
                                error
                            )
                        }
                }
            }
        }
        loaded.preferences.registerOnSharedPreferenceChangeListener(listener)
        loaded.listener = listener
    }

    private fun enableLoadedPlugin(loaded: LoadedPlugin) {
        if (loaded.processingEnabled) return
        check(loaded.lifecycleActive.get()) { "Cannot enable an unloaded plugin" }
        try {
            loaded.plugin.onEnable()
            loaded.processingEnabled = true
            registerConfigListener(loaded)
        } catch (error: Throwable) {
            loadedPlugins.remove(loaded)
            unloadLoadedPlugin(loaded)
            throw error
        }
    }

    private fun unloadLoadedPlugin(loaded: LoadedPlugin) {
        if (!loaded.lifecycleActive.compareAndSet(true, false)) return
        loaded.processingEnabled = false
        loaded.listener?.let { listener ->
            runCatching {
                loaded.preferences.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        loaded.listener = null
        runCatching { loaded.plugin.onUnload() }.onFailure { error ->
            HookLogger.w(TAG, "插件卸载回调失败: id=${loaded.manifest.id}", error)
        }
    }

    private suspend fun runProcessor(
        processor: LyricProcessorExtension,
        song: PluginSong,
        processingContext: PluginProcessingContext
    ): PluginSongResult? = runPluginProcessorCancellable(
        executor = processorExecutor,
        processor = processor,
        song = song,
        processingContext = processingContext,
        timeoutMs = PluginConstants.MAX_PROCESSOR_TIMEOUT_MS,
        onPluginFailure = { error ->
            HookLogger.w(TAG, "插件处理失败: extension=${processor.id}", error)
        },
        onTimeout = {
            HookLogger.w(
                TAG,
                "插件处理超时: extension=${processor.id}, " +
                        "timeoutMs=${PluginConstants.MAX_PROCESSOR_TIMEOUT_MS}"
            )
        }
    )

}

internal fun invokePluginProcessorSafely(
    processor: LyricProcessorExtension,
    song: PluginSong,
    processingContext: PluginProcessingContext,
    onFailure: (Throwable) -> Unit,
): PluginSongResult? = try {
    processor.processResult(song, processingContext)
} catch (error: Throwable) {
    onFailure(error)
    null
}

/**
 * Runs one trusted plugin processor while keeping coroutine and Future cancellation linked.
 *
 * The processor itself runs on [executor]. [runInterruptible] makes cancellation interrupt the
 * thread waiting in Future.get; the cancellation handler then cancels the actual processor task
 * with interruption as well. This prevents a stale song from occupying a plugin worker after a
 * newer song has invalidated the processing job.
 */
internal suspend fun runPluginProcessorCancellable(
    executor: ExecutorService,
    processor: LyricProcessorExtension,
    song: PluginSong,
    processingContext: PluginProcessingContext,
    timeoutMs: Long,
    onPluginFailure: (Throwable) -> Unit,
    onTimeout: () -> Unit,
): PluginSongResult? {
    val future: Future<PluginSongResult?> = try {
        executor.submit<PluginSongResult?> {
            invokePluginProcessorSafely(
                processor = processor,
                song = song,
                processingContext = processingContext,
                onFailure = onPluginFailure
            )
        }
    } catch (_: Exception) {
        return null
    }

    return try {
        runInterruptible {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        }
    } catch (error: CancellationException) {
        future.cancel(true)
        throw error
    } catch (_: TimeoutException) {
        future.cancel(true)
        onTimeout()
        null
    } catch (_: InterruptedException) {
        future.cancel(true)
        Thread.currentThread().interrupt()
        null
    } catch (error: ExecutionException) {
        onPluginFailure(error.cause ?: error)
        null
    } catch (error: Exception) {
        onPluginFailure(error)
        null
    }
}

internal data class PluginProcessingResult(
    val result: PluginSongResult,
)
