package com.lidesheng.hyperlyric.root.plugin

import android.app.Application
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import android.util.Base64
import dalvik.system.DelegateLastClassLoader
import com.lidesheng.hyperlyric.plugin.api.HYPERLYRIC_PLUGIN_API_VERSION
import com.lidesheng.hyperlyric.plugin.api.HyperLyricExtension
import com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin
import com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension
import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import com.lidesheng.hyperlyric.plugin.api.PluginContext
import com.lidesheng.hyperlyric.plugin.api.PluginLyricField
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginStorage
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult
import com.lidesheng.hyperlyric.plugin.core.PluginArchive
import com.lidesheng.hyperlyric.plugin.core.PluginArchiveReader
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationCodec
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationRequest
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationReplayTracker
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationResponse
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationType
import com.lidesheng.hyperlyric.plugin.core.PluginCacheResultChannel
import com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout
import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import com.lidesheng.hyperlyric.plugin.core.PluginManifest
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
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** SystemUI-side loader and executor for trusted HyperLyric ZIP plugins. */
class PluginRuntime(
    private val module: XposedModule,
    private val application: Application,
    private val parentClassLoader: ClassLoader =
        HyperLyricPlugin::class.java.classLoader ?: ClassLoader.getSystemClassLoader()
) {
    companion object {
        private const val TAG = "PluginRuntime"
        private const val LAST_CACHE_CLEAR_TOKEN_KEY = "__hyperlyric_core_last_clear_token"
        private const val CACHE_OPERATION_RESULT_PREFIX = "result."
        private const val MAX_STORED_CACHE_OPERATION_RESULTS = 16
        private const val MAX_PLUGIN_ARCHIVE_LOAD_ATTEMPTS = 3
        private const val PLUGIN_ARCHIVE_RETRY_DELAY_MS = 100L

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
    /** Cache operations never share the lyric processor executor. */
    private val cacheOperationExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "HyperLyric-PluginCacheOperation").apply { isDaemon = true }
    }
    private val generation = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private val cacheOperationConsuming = AtomicBoolean(false)
    private val cacheOperationReplayTracker = PluginCacheOperationReplayTracker()
    private var activeJob: Job? = null
    private val loadedPlugins = mutableListOf<LoadedPlugin>()
    private val pluginLoadLock = Any()
    private var registryPreferences: SharedPreferences? = null

    private val registryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY) {
            consumePendingCacheClears(registryPreferences)
        }
        if (key == PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY) {
            consumePendingCacheOperations(registryPreferences)
        }
    }

    @Volatile
    private var processors: List<RegisteredProcessor> = emptyList()
    @Volatile
    private var cacheExtensions: List<RegisteredCacheExtension> = emptyList()
    @Volatile
    private var processorSetFingerprint: String = ""

    fun loadEnabledPlugins() {
        if (closed.get()) return

        val registry = runCatching {
            module.getRemotePreferences(PluginConstants.REMOTE_REGISTRY_PREFS)
        }.getOrElse { error ->
            HookLogger.w(TAG, "读取插件启用状态失败，跳过插件加载", error)
            return
        }
        registryPreferences = registry
        consumePendingCacheClears(registry)
        registry.registerOnSharedPreferenceChangeListener(registryListener)
        val enabledIds = runCatching {
            registry.getStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, emptySet()).orEmpty()
        }.getOrElse { error ->
            HookLogger.w(TAG, "读取插件启用状态失败，跳过插件加载", error)
            emptySet()
        }
        if (enabledIds.isEmpty()) {
            rebuildExtensionRegistries()
            consumePendingCacheOperations(registry)
            HookLogger.d(TAG, "没有启用的 HyperLyric 插件")
            return
        }

        val remoteFiles = runCatching { module.listRemoteFiles().toSet() }.getOrElse { error ->
            HookLogger.w(TAG, "读取插件远程文件列表失败，跳过插件加载", error)
            return
        }

        enabledIds.sorted().forEach { pluginId ->
            val fileName = PluginRemoteFileNames.forId(pluginId)
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
        consumePendingCacheOperations(registry)
        HookLogger.i(
            TAG,
            "插件 Runtime 初始化完成: enabled=${enabledIds.size}, " +
                    "loaded=${loadedPlugins.size}, processors=${processors.size}, " +
                    "cacheExtensions=${cacheExtensions.size}"
        )
    }

    private fun rebuildExtensionRegistries() {
        val loaded = synchronized(pluginLoadLock) { loadedPlugins.toList() }
        val registeredProcessors = mutableListOf<RegisteredProcessor>()
        loaded.filter { it.processingEnabled }.forEachIndexed { pluginIndex, loadedPlugin ->
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
        processorSetFingerprint = processors.joinToString("\u001F") { registered ->
            "${registered.pluginId}:${registered.extension.id}:${registered.extension.stage.name}"
        }
        cacheExtensions = loaded.flatMap { loadedPlugin ->
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

    internal fun processingSetFingerprint(): String = processorSetFingerprint

    internal fun processSong(
        song: PluginSong,
        processingContext: PluginProcessingContext,
        onResult: (PluginProcessingResult?) -> Unit
    ) {
        if (closed.get()) return
        val currentGeneration = generation.incrementAndGet()
        activeJob?.cancel()
        val currentProcessors = processors
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
        val pluginsToClose = synchronized(pluginLoadLock) {
            loadedPlugins.toList().also { loadedPlugins.clear() }
        }
        pluginsToClose.asReversed().forEach { loaded ->
            loaded.listener?.let { listener ->
                runCatching { loaded.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            runCatching { loaded.plugin.onUnload() }.onFailure { error ->
                HookLogger.w(TAG, "插件卸载回调失败: id=${loaded.manifest.id}", error)
            }
        }
        processors = emptyList()
        cacheExtensions = emptyList()
        processorSetFingerprint = ""
        registryPreferences?.let { preferences ->
            runCatching { preferences.unregisterOnSharedPreferenceChangeListener(registryListener) }
        }
        registryPreferences = null
        processorExecutor.shutdownNow()
        cacheOperationExecutor.shutdownNow()
        scope.cancel()
    }

    /**
     * App-side uninstall cannot directly open SystemUI's private preferences. It publishes a
     * one-shot token through the existing remote registry; the host runtime clears its own cache
     * and records the token locally so a later SystemUI restart does not repeat the operation.
     */
    private fun consumePendingCacheClears(registry: SharedPreferences?) {
        val tokens = runCatching {
            registry?.getStringSet(
                PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY,
                emptySet()
            ).orEmpty()
        }.onFailure { error ->
            HookLogger.w(TAG, "读取插件缓存清理请求失败", error)
        }.getOrDefault(emptySet())
        tokens.forEach { encoded ->
            val separator = encoded.indexOf('\u001F')
            if (separator <= 0 || separator == encoded.lastIndex) return@forEach
            val pluginId = encoded.substring(0, separator)
            val token = encoded.substring(separator + 1)
            if (!PluginCacheFileLayout.isValidPluginId(pluginId)) {
                HookLogger.w(TAG, "忽略非法插件缓存清理请求: id=$pluginId")
                return@forEach
            }
            runCatching {
                val marker = application.getSharedPreferences(
                    PluginConstants.cacheMetadataPreferences(pluginId),
                    android.content.Context.MODE_PRIVATE
                )
                if (marker.getString(LAST_CACHE_CLEAR_TOKEN_KEY, null) == token) {
                    return@runCatching
                }
                val cleared = application.getSharedPreferences(
                    PluginConstants.cachePreferences(pluginId),
                    android.content.Context.MODE_PRIVATE
                ).edit().clear().commit()
                check(cleared) { "cache clear commit returned false" }
                cancelActiveProcessing()
                clearLoadedPluginCache(pluginId)
                clearPluginCacheFiles(pluginId)
                val marked = marker.edit().putString(LAST_CACHE_CLEAR_TOKEN_KEY, token).commit()
                check(marked) { "cache clear marker commit returned false" }
                HookLogger.i(TAG, "插件缓存已清理: id=$pluginId")
            }.onFailure { error ->
                HookLogger.w(TAG, "插件缓存清理失败: id=$pluginId", error)
            }
        }
    }

    /**
     * Reads bounded cache-management requests from the App. The target-process
     * RemotePreferences view is read-only, so each result is submitted to the App-owned provider
     * instead of attempting to write the registry from SystemUI.
     */
    private fun consumePendingCacheOperations(registry: SharedPreferences?) {
        if (closed.get() || !cacheOperationConsuming.compareAndSet(false, true)) return
        scope.launch {
            var snapshotRequestIds: Set<String> = emptySet()
            var requestSnapshotRead = false
            try {
                val now = System.currentTimeMillis()
                val pendingRequests = runCatching {
                    registry?.getStringSet(
                        PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
                        emptySet()
                    ).orEmpty()
                }.onSuccess {
                    requestSnapshotRead = true
                }.getOrElse { error ->
                    HookLogger.w(TAG, "读取插件缓存操作请求失败", error)
                    emptySet()
                }.mapNotNull(PluginCacheOperationCodec::decodeRequest)
                snapshotRequestIds = pendingRequests.mapTo(linkedSetOf()) { it.requestId }
                val requests = pendingRequests
                    .sortedBy { it.createdAtEpochMs }
                    .take(PluginCacheOperationCodec.MAX_ACTIVE_REQUESTS)

                requests.forEach { request ->
                    if (closed.get()) return@forEach
                    val response = when {
                        PluginCacheOperationCodec.isRequestExpired(request, now) -> {
                            PluginCacheOperationResponse(
                                requestId = request.requestId,
                                success = false,
                                errorCode = "request_expired"
                            )
                        }

                        else -> storedCacheOperationResponse(request)
                            ?.also(cacheOperationReplayTracker::markCompleted)
                            ?: cacheOperationReplayTracker.completedResponse(request.requestId)
                            ?: executeCacheOperation(request).also {
                                storeCacheOperationResponse(request.pluginId, it)
                                cacheOperationReplayTracker.markCompleted(it)
                            }
                    }
                    if (!PluginCacheResultChannel.publishFromSystemUi(application, request, response)) {
                        HookLogger.w(
                            TAG,
                            "回传插件缓存操作结果失败: plugin=${request.pluginId}, " +
                                    "scope=${request.scopeId}"
                        )
                    }
                }
            } finally {
                cacheOperationConsuming.set(false)
                if (
                    requestSnapshotRead &&
                    hasNewPendingCacheOperation(registry, snapshotRequestIds)
                ) {
                    consumePendingCacheOperations(registry)
                }
            }
        }
    }

    /**
     * A registry listener can fire while the previous snapshot is still executing. Recheck after
     * releasing the single-consumer gate so that newly appended requests are not left waiting for
     * an unrelated preference change.
     */
    private fun hasNewPendingCacheOperation(
        registry: SharedPreferences?,
        snapshotRequestIds: Set<String>
    ): Boolean {
        val now = System.currentTimeMillis()
        return runCatching {
            registry?.getStringSet(
                PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
                emptySet()
            ).orEmpty().mapNotNull(PluginCacheOperationCodec::decodeRequest)
                .any { request ->
                    !PluginCacheOperationCodec.isRequestExpired(request, now) &&
                            request.requestId !in snapshotRequestIds
                }
        }.onFailure { error ->
            HookLogger.w(TAG, "复查插件缓存操作请求失败", error)
        }.getOrDefault(false)
    }

    /** The tombstone is marked only after both the legacy and file-backed cache are cleared. */
    private fun clearPluginCacheFiles(pluginId: String) {
        val directory = PluginCacheFileLayout.directory(application, pluginId)
        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                check(file.delete()) { "cannot delete plugin cache file: ${file.name}" }
            }
        }
    }

    /** Clears plugin-owned memory state before the uninstall tombstone removes its files. */
    private fun clearLoadedPluginCache(pluginId: String) {
        val extensions: List<PluginCacheExtension> = synchronized(pluginLoadLock) {
            val loaded = loadedPlugins.firstOrNull { it.manifest.id == pluginId }
                ?: return@synchronized emptyList()
            loaded.extensions.filterIsInstance<PluginCacheExtension>()
                .filter { extension -> loaded.manifest.cacheScopes.any { it.id == extension.id } }
        }
        extensions.forEach { extension -> extension.clearAll() }
    }

    private fun executeCacheOperation(
        request: PluginCacheOperationRequest
    ): PluginCacheOperationResponse {
        if (PluginCacheOperationCodec.isOperationTimedOut(request, System.currentTimeMillis())) {
            return cacheOperationFailure(request, "operation_timeout")
        }
        if (request.type != PluginCacheOperationType.LIST) {
            // A clear must not race with an in-flight processor and repopulate the cache later.
            cancelActiveProcessing()
        }
        val task = try {
            cacheOperationExecutor.submit<PluginCacheOperationResponse> {
                if (
                    Thread.currentThread().isInterrupted ||
                    PluginCacheOperationCodec.isOperationTimedOut(request, System.currentTimeMillis())
                ) {
                    return@submit cacheOperationFailure(request, "operation_timeout")
                }
                val loaded = findOrLoadPluginForCache(request.pluginId)
                    ?: return@submit cacheOperationFailure(request, "plugin_not_loaded")
                if (
                    Thread.currentThread().isInterrupted ||
                    PluginCacheOperationCodec.isOperationTimedOut(request, System.currentTimeMillis())
                ) {
                    return@submit cacheOperationFailure(request, "operation_timeout")
                }
                if (loaded.manifest.cacheScopes.none { it.id == request.scopeId }) {
                    return@submit cacheOperationFailure(request, "scope_not_declared")
                }
                val extension = cacheExtensions.firstOrNull {
                    it.pluginId == request.pluginId && it.extension.id == request.scopeId
                }?.extension ?: return@submit cacheOperationFailure(request, "scope_not_loaded")

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
        } catch (error: Exception) {
            HookLogger.w(TAG, "无法提交插件缓存操作", error)
            return cacheOperationFailure(request, "runtime_unavailable")
        }

        val timeoutMs = PluginCacheOperationCodec.remainingOperationTimeoutMs(
            request,
            System.currentTimeMillis()
        )
        if (timeoutMs == 0L) {
            task.cancel(true)
            return cacheOperationFailure(request, "operation_timeout")
        }
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            task.cancel(true)
            HookLogger.w(
                TAG,
                "插件缓存操作超时: plugin=${request.pluginId}, scope=${request.scopeId}"
            )
            cacheOperationFailure(request, "operation_timeout")
        } catch (error: Exception) {
            task.cancel(true)
            HookLogger.w(TAG, "插件缓存操作异常", error)
            cacheOperationFailure(request, "runtime_failed")
        }
    }

    /**
     * A disabled plugin does not run lyric processors, but its declared cache remains user-owned
     * data. Load it only when the App asks to manage that cache, without invoking [onEnable].
     */
    private fun findOrLoadPluginForCache(pluginId: String): LoadedPlugin? =
        synchronized(pluginLoadLock) {
            loadedPlugins.firstOrNull { it.manifest.id == pluginId } ?: run {
                val fileName = PluginRemoteFileNames.forId(pluginId)
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

    private fun cacheOperationFailure(
        request: PluginCacheOperationRequest,
        errorCode: String
    ): PluginCacheOperationResponse = PluginCacheOperationResponse(
        requestId = request.requestId,
        success = false,
        errorCode = errorCode
    )

    private fun storedCacheOperationResponse(
        request: PluginCacheOperationRequest
    ): PluginCacheOperationResponse? {
        val preferences = application.getSharedPreferences(
            PluginConstants.cacheOperationPreferences(request.pluginId),
            android.content.Context.MODE_PRIVATE
        )
        return preferences.getString(CACHE_OPERATION_RESULT_PREFIX + request.requestId, null)
            ?.let(PluginCacheOperationCodec::decodeResponse)
            ?.takeIf { it.requestId == request.requestId }
    }

    private fun storeCacheOperationResponse(
        pluginId: String,
        response: PluginCacheOperationResponse
    ) {
        val encoded = runCatching { PluginCacheOperationCodec.encodeResponse(response) }
            .getOrElse { error ->
                HookLogger.w(TAG, "插件缓存操作结果过大或非法", error)
                return
            }
        val preferences = application.getSharedPreferences(
            PluginConstants.cacheOperationPreferences(pluginId),
            android.content.Context.MODE_PRIVATE
        )
        val retainedKeys = preferences.all
            .filterKeys { it.startsWith(CACHE_OPERATION_RESULT_PREFIX) }
            .mapNotNull { (key, value) ->
                (value as? String)?.let(PluginCacheOperationCodec::decodeResponse)
                    ?.let { key to it }
            }
            .sortedByDescending { it.second.completedAtEpochMs }
            .take(MAX_STORED_CACHE_OPERATION_RESULTS - 1)
            .map { it.first }
            .toSet()
        preferences.edit().apply {
            preferences.all.keys
                .filter { it.startsWith(CACHE_OPERATION_RESULT_PREFIX) && it !in retainedKeys }
                .forEach(::remove)
            putString(CACHE_OPERATION_RESULT_PREFIX + response.requestId, encoded)
            apply()
        }
    }

    private fun loadPlugin(
        pluginId: String,
        fileName: String,
        enableProcessing: Boolean,
    ): LoadedPlugin {
        val (archive, archiveFile) = readAndMaterializePluginArchive(pluginId, fileName)
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

        val listener = if (enableProcessing) {
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                scope.launch {
                    runCatching { plugin.onConfigChanged(context.config) }.onFailure { error ->
                        HookLogger.w(TAG, "插件配置回调失败: id=$pluginId", error)
                    }
                }
            }
                .also(preferences::registerOnSharedPreferenceChangeListener)
        } else {
            null
        }
        val loaded = LoadedPlugin(
            manifest = archive.manifest,
            plugin = plugin,
            context = context,
            preferences = preferences,
            listener = listener,
            extensions = context.registeredExtensions(),
            processingEnabled = enableProcessing
        )
        loadedPlugins += loaded
        HookLogger.i(
            TAG,
            "插件已${if (enableProcessing) "启用" else "为缓存管理加载"}: " +
                    "id=$pluginId, version=${archive.manifest.version}, " +
                    "extensions=${context.registeredExtensions().size}"
        )
        return loaded
    }

    /**
     * Remote plugin files can be observed while the app is replacing them. The streaming ZIP
     * reader is intentionally bounded, but it can still accept a prefix containing the local
     * entries before the central directory is complete. Validate the materialized file with the
     * random-access ZIP reader before handing it to ART, and retry a transient replacement race.
     */
    private fun readAndMaterializePluginArchive(
        pluginId: String,
        fileName: String,
    ): Pair<PluginArchive, File> {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_PLUGIN_ARCHIVE_LOAD_ATTEMPTS) {
            try {
                val archiveBytes = module.openRemoteFile(fileName).useReadOnly { input ->
                    PluginArchiveReader.readBounded(input)
                }
                val archive = PluginArchiveReader.read(archiveBytes)
                require(archive.manifest.id == pluginId) {
                    "Plugin id does not match enabled registry"
                }
                require(archive.manifest.apiVersion <= HYPERLYRIC_PLUGIN_API_VERSION) {
                    "Plugin API is newer than host"
                }

                val archiveFile = materializePluginArchive(fileName, archiveBytes)
                validateMaterializedPluginArchive(archiveFile, archive)
                return archive to archiveFile
            } catch (error: Exception) {
                lastError = error
                attempt++
                if (attempt < MAX_PLUGIN_ARCHIVE_LOAD_ATTEMPTS) {
                    try {
                        Thread.sleep(PLUGIN_ARCHIVE_RETRY_DELAY_MS)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw interrupted
                    }
                }
            }
        }
        throw lastError ?: IllegalStateException("Unable to load plugin archive")
    }

    /**
     * ZipFile checks the central directory/local header relationship and reading each entry also
     * verifies its compressed data and CRC. This is the same shape of validation ART performs
     * when it opens a DEX from a ZIP.
     */
    private fun validateMaterializedPluginArchive(
        archiveFile: File,
        archive: PluginArchive,
    ) {
        ZipFile(archiveFile).use { zip ->
            val manifestEntry = zip.getEntry(PluginConstants.ZIP_MANIFEST)
                ?: error("Plugin ZIP has no manifest.json")
            consumePluginZipEntry(
                zip = zip,
                entry = manifestEntry,
                limit = PluginConstants.MAX_PLUGIN_MANIFEST_BYTES,
            )

            archive.dexFiles.forEachIndexed { index, dexBytes ->
                val entryName = if (index == 0) {
                    PluginConstants.ZIP_DEX
                } else {
                    "classes${index + 1}.dex"
                }
                val dexEntry = zip.getEntry(entryName)
                    ?: error("Plugin ZIP has no $entryName")
                require(dexEntry.size < 0L || dexEntry.size == dexBytes.size.toLong()) {
                    "$entryName size does not match the validated archive"
                }
                consumePluginZipEntry(
                    zip = zip,
                    entry = dexEntry,
                    limit = PluginConstants.MAX_PLUGIN_DEX_BYTES,
                )
            }
        }
    }

    private fun consumePluginZipEntry(
        zip: ZipFile,
        entry: ZipEntry,
        limit: Int,
    ) {
        require(entry.size < 0L || entry.size <= limit.toLong()) {
            "Plugin ZIP entry is too large: ${entry.name}"
        }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        zip.getInputStream(entry).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit.toLong()) {
                    "Plugin ZIP entry is too large: ${entry.name}"
                }
            }
        }
        require(entry.size < 0L || total == entry.size) {
            "Plugin ZIP entry size is inconsistent: ${entry.name}"
        }
    }

    /**
     * DelegateLastClassLoader loads dex files from a path, so persist the already validated ZIP
     * before creating the loader. The file is process-local cache data and can be rebuilt after a
     * SystemUI restart.
     */
    private fun materializePluginArchive(fileName: String, archiveBytes: ByteArray): File {
        val directory = File(application.codeCacheDir, "hyperlyric_plugin_dex")
        require(directory.exists() || directory.mkdirs()) {
            "Unable to create plugin dex cache directory"
        }
        val file = File(directory, fileName)
        val atomicFile = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(archiveBytes)
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            output?.let(atomicFile::failWrite)
            throw error
        }
        return file
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

    private data class LoadedPlugin(
        val manifest: PluginManifest,
        val plugin: HyperLyricPlugin,
        val context: RuntimePluginContext,
        val preferences: SharedPreferences,
        val listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        val extensions: List<HyperLyricExtension>,
        val processingEnabled: Boolean = false,
    )

    private data class RegisteredProcessor(
        val pluginId: String,
        val pluginIndex: Int,
        val extensionIndex: Int,
        val extension: LyricProcessorExtension,
    )

    private data class RegisteredCacheExtension(
        val pluginId: String,
        val extension: PluginCacheExtension,
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

private class RuntimePluginContext(
    override val pluginId: String,
    application: Application,
    preferences: SharedPreferences,
) : PluginContext {
    private val extensionLock = Any()
    private val extensions = mutableListOf<HyperLyricExtension>()

    override val hostApiVersion: Int = HYPERLYRIC_PLUGIN_API_VERSION
    override val config: PluginConfig = SharedPreferencesPluginConfig(preferences)
    override val logger: PluginLogger = RuntimePluginLogger(pluginId)
    override val cache: PluginCache = FilePluginCache(
        directory = com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout
            .directory(application, pluginId),
        legacyPreferences = application.getSharedPreferences(
            PluginConstants.cachePreferences(pluginId),
            android.content.Context.MODE_PRIVATE
        ),
        logger = logger.withTag("PluginCache")
    )
    override val storage: PluginStorage = SharedPreferencesPluginStorage(
        application.getSharedPreferences(
            com.lidesheng.hyperlyric.plugin.core.PluginConstants.storagePreferences(pluginId),
            android.content.Context.MODE_PRIVATE
        )
    )

    override fun registerExtension(extension: HyperLyricExtension) {
        require(extension.id.isNotBlank()) { "Plugin extension id is blank" }
        synchronized(extensionLock) {
            require(extensions.none { it.id == extension.id }) {
                "Duplicate plugin extension id: ${extension.id}"
            }
            extensions += extension
        }
    }

    fun registeredExtensions(): List<HyperLyricExtension> = synchronized(extensionLock) {
        extensions.toList()
    }
}

private class SharedPreferencesPluginConfig(
    private val preferences: SharedPreferences
) : PluginConfig {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long =
        preferences.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float =
        preferences.getFloat(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        preferences.getStringSet(key, defaultValue)?.toSet() ?: defaultValue
}

private class SharedPreferencesPluginStorage(
    private val preferences: SharedPreferences
) : PluginStorage {
    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}

/**
 * Private SystemUI file backend for potentially large plugin cache bodies.
 *
 * The legacy preferences remain read-only migration input so upgrading does not discard an
 * existing cache. Individual keys migrate lazily when a plugin next reads or writes them.
 */
private class FilePluginCache(
    private val directory: File,
    private val legacyPreferences: SharedPreferences,
    private val logger: PluginLogger,
) : PluginCache {
    private companion object {
        const val MAX_KEY_LENGTH = 256
        const val MAX_VALUE_BYTES = 2 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 64 * 1024 * 1024
    }

    private val lock = Any()

    init {
        logger.info("插件缓存目录: ${directory.absolutePath}")
    }

    override fun getString(key: String): String? {
        if (!isValidKey(key)) return null
        return synchronized(lock) {
            readFileValue(key) ?: migrateLegacyValue(key)
        }
    }

    override fun putString(key: String, value: String) {
        if (!isValidKey(key) || !isWithinLimit(value.toByteArray(Charsets.UTF_8))) {
            val message = "拒绝超限或非法缓存写入: key=$key"
            logger.warn(message)
            throw IllegalArgumentException(message)
        }
        synchronized(lock) {
            check(writeFileValue(key, value)) { "无法写入插件缓存: key=$key" }
            check(legacyPreferences.edit().remove(key).commit()) {
                "无法移除旧版插件缓存: key=$key"
            }
        }
    }

    override fun getBytes(key: String): ByteArray? {
        val encoded = getString(key) ?: return null
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .map { decoded ->
                if (!isWithinLimit(decoded)) {
                    remove(key)
                    null
                } else {
                    decoded
                }
            }
            .onFailure {
                logger.warn("解析缓存字节失败，删除记录: key=$key", it)
                remove(key)
            }
            .getOrNull()
    }

    override fun putBytes(key: String, value: ByteArray) {
        if (!isValidKey(key) || !isWithinLimit(value)) {
            logger.warn("忽略超限或非法缓存字节写入: key=$key")
            return
        }
        putString(key, Base64.encodeToString(value, Base64.NO_WRAP))
    }

    override fun contains(key: String): Boolean {
        if (!isValidKey(key)) return false
        return synchronized(lock) {
            val file = fileForKey(key)
            hasReadableFile(file) || legacyPreferences.contains(key)
        }
    }

    override fun remove(key: String) {
        if (!isValidKey(key)) return
        synchronized(lock) {
            val file = fileForKey(key)
            deleteFileIfPresent(file)
            deleteFileIfPresent(File(file.path + ".bak"))
            check(legacyPreferences.edit().remove(key).commit()) {
                "无法删除旧版插件缓存: key=$key"
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            directory.listFiles()?.forEach { file ->
                if (file.isFile) deleteFileIfPresent(file)
            }
            check(legacyPreferences.edit().clear().commit()) {
                "无法清空旧版插件缓存"
            }
        }
    }

    private fun readFileValue(key: String): String? {
        val file = fileForKey(key)
        val backup = File(file.path + ".bak")
        if (!file.isFile && !backup.isFile) return null
        return runCatching {
            AtomicFile(file).openRead().use { input ->
                if (!isWithinLimit(file.length())) {
                    error("插件缓存文件超限: key=$key")
                }
                input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            }
        }
            .onFailure {
                logger.warn("读取缓存文件失败: key=$key", it)
                removeFile(file)
            }
            .getOrNull()
    }

    /** Also restores a pending AtomicFile backup before cache metadata asks whether it exists. */
    private fun hasReadableFile(file: File): Boolean {
        val backup = File(file.path + ".bak")
        if (!file.isFile && !backup.isFile) return false
        return runCatching {
            AtomicFile(file).openRead().use { isWithinLimit(file.length()) }
        }.onFailure { error ->
            logger.warn("检查缓存文件失败: file=${file.name}", error)
            removeFile(file)
        }.getOrDefault(false)
    }

    private fun migrateLegacyValue(key: String): String? {
        val legacy = runCatching { legacyPreferences.getString(key, null) }
            .onFailure { logger.warn("读取旧缓存失败: key=$key", it) }
            .getOrNull()
            ?: return null
        if (!isWithinLimit(legacy.toByteArray(Charsets.UTF_8))) {
            legacyPreferences.edit().remove(key).apply()
            return null
        }
        if (writeFileValue(key, legacy)) {
            legacyPreferences.edit().remove(key).apply()
        }
        return legacy
    }

    private fun writeFileValue(key: String, value: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val file = fileForKey(key)
        if (!ensureCapacity(file, bytes.size.toLong())) {
            logger.warn("插件缓存总容量已达上限: key=$key")
            return false
        }
        return runCatching {
            if (!directory.exists() && !directory.mkdirs()) {
                error("无法创建插件缓存目录")
            }
            val atomicFile = AtomicFile(file)
            var output: FileOutputStream? = null
            try {
                output = atomicFile.startWrite()
                output.write(bytes)
                output.fd.sync()
                atomicFile.finishWrite(output)
            } catch (error: Throwable) {
                output?.let(atomicFile::failWrite)
                throw error
            }
            true
        }.onFailure { logger.warn("写入缓存文件失败: key=$key", it) }
            .getOrDefault(false)
    }

    private fun ensureCapacity(replacing: File, newSizeBytes: Long): Boolean {
        val existingSize = replacing.takeIf(File::isFile)?.length() ?: 0L
        val totalSize = directory.listFiles()
            ?.asSequence()
            ?.filter {
                it.isFile && it.name.endsWith(
                    com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout.CACHE_FILE_EXTENSION
                )
            }
            ?.sumOf(File::length)
            ?: 0L
        return totalSize - existingSize + newSizeBytes <= MAX_TOTAL_BYTES
    }

    private fun fileForKey(key: String): File = File(
        directory,
        com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout.fileNameForKey(key)
    )

    private fun removeFile(file: File) {
        runCatching {
            file.delete()
            File(file.path + ".bak").delete()
        }
    }

    private fun deleteFileIfPresent(file: File) {
        if (file.exists()) {
            check(file.delete()) { "无法删除插件缓存文件: ${file.name}" }
        }
    }

    private fun isValidKey(key: String): Boolean =
        key.isNotBlank() && key.length <= MAX_KEY_LENGTH

    private fun isWithinLimit(value: ByteArray): Boolean = value.size <= MAX_VALUE_BYTES

    private fun isWithinLimit(value: Long): Boolean = value in 0..MAX_VALUE_BYTES.toLong()
}

private class RuntimePluginLogger(private val pluginId: String) : PluginLogger {
    private fun tag() = pluginId

    override fun debug(message: String) = HookLogger.d(tag(), message)

    override fun info(message: String) = HookLogger.i(tag(), message)

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.w(tag(), message) else HookLogger.w(tag(), message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.e(tag(), message) else HookLogger.e(tag(), message, throwable)
    }

    override fun withTag(tag: String): PluginLogger =
        TaggedRuntimePluginLogger(pluginId, tag)
}

private class TaggedRuntimePluginLogger(
    private val pluginId: String,
    private val componentTag: String,
) : PluginLogger {
    private val logTag = "$pluginId/$componentTag"

    override fun debug(message: String) = HookLogger.d(logTag, message)

    override fun info(message: String) = HookLogger.i(logTag, message)

    override fun warn(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.w(logTag, message)
        else HookLogger.w(logTag, message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        if (throwable == null) HookLogger.e(logTag, message)
        else HookLogger.e(logTag, message, throwable)
    }

    override fun withTag(tag: String): PluginLogger =
        TaggedRuntimePluginLogger(pluginId, "$componentTag/$tag")
}

private inline fun <T> ParcelFileDescriptor.useReadOnly(block: (java.io.InputStream) -> T): T {
    return ParcelFileDescriptor.AutoCloseInputStream(this).use(block)
}
