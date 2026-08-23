package com.lidesheng.hyperlyric.plugin.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.lidesheng.hyperlyric.plugin.api.HYPERLYRIC_PLUGIN_API_VERSION
import com.lidesheng.hyperlyric.plugin.api.PluginSettingSpec
import com.lidesheng.hyperlyric.plugin.api.PluginSettingType
import com.lidesheng.hyperlyric.plugin.core.PluginArchiveReader
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationCodec
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationRequest
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationResponse
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationType
import com.lidesheng.hyperlyric.plugin.core.PluginCacheResultChannel
import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import com.lidesheng.hyperlyric.plugin.core.PluginManifest
import com.lidesheng.hyperlyric.plugin.core.PluginManifestCodec
import com.lidesheng.hyperlyric.plugin.core.PluginRemoteFileNames
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import io.github.libxposed.service.XposedService
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class InstalledPlugin(
    val manifest: PluginManifest,
    val fileName: String,
    val enabled: Boolean,
)

data class PluginBackupSnapshot(
    val id: String,
    val version: String,
    val fileName: String,
    val enabled: Boolean,
    val settings: JSONObject,
    val archive: ByteArray,
)

internal sealed interface PluginCacheOperationOutcome {
    data class Completed(val response: PluginCacheOperationResponse) : PluginCacheOperationOutcome
    data class Waiting(val reason: String) : PluginCacheOperationOutcome
    data class Rejected(val reason: String) : PluginCacheOperationOutcome
}

/** App-side install/config facade. SystemUI only consumes the remote registry and ZIP files. */
class PluginRepository(private val context: Context) {
    private val registry: SharedPreferences = context.getSharedPreferences(
        PluginConstants.LOCAL_REGISTRY_PREFS,
        Context.MODE_PRIVATE
    )

    fun listInstalled(): List<InstalledPlugin> {
        val ids = registry.getStringSet(PluginConstants.LOCAL_INSTALLED_IDS_KEY, emptySet()).orEmpty()
        val enabled = registry.getStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, emptySet()).orEmpty()
        return ids.mapNotNull { id ->
            val manifest = registry.getString(PluginConstants.LOCAL_MANIFEST_PREFIX + id, null)
                ?.let { runCatching { PluginManifestCodec.decode(it) }.getOrNull() }
                ?: return@mapNotNull null
            InstalledPlugin(
                manifest = manifest,
                fileName = registry.getString(
                    PluginConstants.LOCAL_FILE_PREFIX + id,
                    PluginRemoteFileNames.forId(id)
                ) ?: PluginRemoteFileNames.forId(id),
                enabled = id in enabled
            )
        }.sortedBy { it.manifest.name }
    }

    fun exportPluginBackups(): List<PluginBackupSnapshot> {
        val installed = listInstalled()
        if (installed.isEmpty()) return emptyList()
        val service = requireService()
        return installed.map { installedPlugin ->
            val archive = readRemoteFile(service, installedPlugin.fileName)
            require(PluginArchiveReader.read(archive).manifest.id == installedPlugin.manifest.id) {
                "插件包与安装记录不匹配: ${installedPlugin.manifest.id}"
            }
            PluginBackupSnapshot(
                id = installedPlugin.manifest.id,
                version = installedPlugin.manifest.version,
                fileName = installedPlugin.fileName,
                enabled = installedPlugin.enabled,
                settings = encodeBackupSettings(installedPlugin.manifest),
                archive = archive
            )
        }
    }

    fun install(uri: Uri): InstalledPlugin {
        val bytes = context.contentResolver.openInputStream(uri)?.use(PluginArchiveReader::readBounded)
            ?: throw IllegalArgumentException("无法读取插件 ZIP")
        return installArchive(bytes)
    }

    fun installArchive(bytes: ByteArray): InstalledPlugin {
        val service = requireService()
        val archive = PluginArchiveReader.read(bytes)
        require(archive.manifest.apiVersion <= HYPERLYRIC_PLUGIN_API_VERSION) {
            "插件 API 版本高于当前 HyperLyric"
        }

        val pluginId = archive.manifest.id
        val wasInstalled = pluginId in registry.getStringSet(
            PluginConstants.LOCAL_INSTALLED_IDS_KEY,
            emptySet()
        ).orEmpty()
        val previousFileName = if (wasInstalled) {
            registry.getString(
                PluginConstants.LOCAL_FILE_PREFIX + pluginId,
                PluginRemoteFileNames.forId(pluginId)
            )
        } else {
            null
        }
        val fileName = PluginRemoteFileNames.forRevision(
            pluginId = pluginId,
            revision = UUID.randomUUID().toString().replace("-", "")
        )
        writeRemoteFile(service, fileName, bytes)

        val wasEnabled = pluginId in registry.getStringSet(
            PluginConstants.REMOTE_ENABLED_IDS_KEY,
            emptySet()
        ).orEmpty()
        val installedIds = registry.getStringSet(
            PluginConstants.LOCAL_INSTALLED_IDS_KEY,
            emptySet()
        ).orEmpty().toMutableSet().apply { add(archive.manifest.id) }
        registry.edit()
            .putStringSet(PluginConstants.LOCAL_INSTALLED_IDS_KEY, installedIds)
            .putString(
                PluginConstants.LOCAL_MANIFEST_PREFIX + pluginId,
                PluginManifestCodec.encode(archive.manifest)
            )
            .putString(PluginConstants.LOCAL_FILE_PREFIX + pluginId, fileName)
            .putStringSet(
                PluginConstants.REMOTE_ENABLED_IDS_KEY,
                registry.getStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, emptySet()).orEmpty()
            )
            .apply()

        ensureDefaults(archive.manifest)
        syncConfig(service, archive.manifest)
        syncRegistry(service)
        if (previousFileName != null && previousFileName != fileName) {
            runCatching { service.deleteRemoteFile(previousFileName) }
        }

        return InstalledPlugin(archive.manifest, fileName, wasEnabled)
    }

    fun restorePluginSettings(manifest: PluginManifest, values: JSONObject) {
        val local = configPreferences(manifest.id)
        val editor = local.edit()
        manifest.settings.settings.forEach { setting ->
            if (!setting.backup || !values.has(setting.key)) return@forEach
            decodeBackupValue(values.opt(setting.key), setting.type)?.let { value ->
                putTypedValue(editor, setting.key, value)
            }
        }
        editor.apply()
        syncConfig(requireService(), manifest)
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val installed = listInstalled().firstOrNull { it.manifest.id == pluginId }
            ?: throw IllegalArgumentException("插件未安装: $pluginId")
        val service = requireService()
        // The registry is the host-side load and execution gate. Keep the activation setting in
        // sync as a plugin-level defense so the plugin can also stop work at its own boundary.
        val ids = registry.getStringSet(
            PluginConstants.REMOTE_ENABLED_IDS_KEY,
            emptySet()
        ).orEmpty().toMutableSet()
        if (enabled) ids += pluginId else ids -= pluginId
        service.getRemotePreferences(PluginConstants.REMOTE_REGISTRY_PREFS)
            .edit()
            .putStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, ids)
            .apply()
        registry.edit().putStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, ids).apply()

        installed.manifest.activationSettingKey?.let { key ->
            configPreferences(pluginId).edit().putBoolean(key, enabled).apply()
            service.getRemotePreferences(PluginConstants.configGroup(pluginId))
                .edit()
                .putBoolean(key, enabled)
                .apply()
        }
    }

    /**
     * Sends one bounded cache operation to SystemUI and waits only for its request-ID-matched
     * response. Call from a background dispatcher; App code never invokes plugin objects directly.
     */
    internal fun listPluginCache(pluginId: String, scopeId: String): PluginCacheOperationOutcome =
        requestPluginCacheOperation(pluginId, scopeId, PluginCacheOperationType.LIST)

    internal fun clearPluginCache(pluginId: String, scopeId: String): PluginCacheOperationOutcome =
        requestPluginCacheOperation(pluginId, scopeId, PluginCacheOperationType.CLEAR_ALL)

    internal fun clearPluginCacheEntry(
        pluginId: String,
        scopeId: String,
        entryId: String
    ): PluginCacheOperationOutcome = requestPluginCacheOperation(
        pluginId = pluginId,
        scopeId = scopeId,
        type = PluginCacheOperationType.CLEAR_ENTRY,
        entryId = entryId
    )

    /** Read-only Root fallback for inspecting SystemUI cache files before the injected runtime reloads. */
    internal suspend fun queryPluginCacheFilesWithRoot(
        pluginId: String
    ): ShellUtils.RootPluginCacheQuery = ShellUtils.querySystemUiPluginCacheFiles(pluginId)

    fun uninstall(pluginId: String) {
        val installed = listInstalled().firstOrNull { it.manifest.id == pluginId }
            ?: throw IllegalArgumentException("插件未安装: $pluginId")
        val service = requireService()
        service.deleteRemoteFile(installed.fileName)
        runCatching {
            service.getRemotePreferences(PluginConstants.configGroup(pluginId))
                .edit()
                .clear()
                .apply()
            service.deleteRemotePreferences(PluginConstants.configGroup(pluginId))
        }
        runCatching {
            service.getRemotePreferences(PluginConstants.storagePreferences(pluginId))
                .edit()
                .clear()
                .apply()
            service.deleteRemotePreferences(PluginConstants.storagePreferences(pluginId))
        }
        configPreferences(pluginId).edit().clear().commit()

        val ids = registry.getStringSet(PluginConstants.LOCAL_INSTALLED_IDS_KEY, emptySet())
            .orEmpty().toMutableSet().apply { remove(pluginId) }
        val enabled = registry.getStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, emptySet())
            .orEmpty().toMutableSet().apply { remove(pluginId) }
        val cacheClearTokens = registry.getStringSet(
            PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY,
            emptySet()
        ).orEmpty().filterNot { token -> token.substringBefore('\u001F') == pluginId }
            .toMutableSet()
            .apply { add("$pluginId\u001F${UUID.randomUUID()}") }
        registry.edit()
            .putStringSet(PluginConstants.LOCAL_INSTALLED_IDS_KEY, ids)
            .putStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, enabled)
            .putStringSet(PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY, cacheClearTokens)
            .remove(PluginConstants.LOCAL_MANIFEST_PREFIX + pluginId)
            .remove(PluginConstants.LOCAL_FILE_PREFIX + pluginId)
            .apply()
        syncRegistry(service)
    }

    private fun requestPluginCacheOperation(
        pluginId: String,
        scopeId: String,
        type: PluginCacheOperationType,
        entryId: String? = null
    ): PluginCacheOperationOutcome {
        val installed = listInstalled().firstOrNull { it.manifest.id == pluginId }
            ?: return PluginCacheOperationOutcome.Rejected("plugin_not_installed")
        if (installed.manifest.cacheScopes.none { it.id == scopeId }) {
            return PluginCacheOperationOutcome.Rejected("scope_not_declared")
        }
        val service = requireServiceOrNull()
            ?: return PluginCacheOperationOutcome.Waiting("xposed_service_unavailable")
        val request = PluginCacheOperationRequest(
            requestId = UUID.randomUUID().toString(),
            responseToken = UUID.randomUUID().toString(),
            pluginId = pluginId,
            scopeId = scopeId,
            type = type,
            entryId = entryId
        )
        if (runCatching { PluginCacheOperationCodec.encodeRequest(request) }.isFailure) {
            return PluginCacheOperationOutcome.Rejected("invalid_request")
        }

        PluginCacheResultChannel.registerPending(context, request)

        val remote = runCatching {
            service.getRemotePreferences(PluginConstants.REMOTE_REGISTRY_PREFS)
        }.getOrElse {
            PluginCacheResultChannel.clear(context, request.requestId)
            return PluginCacheOperationOutcome.Waiting("xposed_service_unavailable")
        }
        val now = System.currentTimeMillis()
        val queued = remote.getStringSet(
            PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
            emptySet()
        ).orEmpty().mapNotNull(PluginCacheOperationCodec::decodeRequest)
            .filterNot { PluginCacheOperationCodec.isRequestExpired(it, now) }
            .take(PluginCacheOperationCodec.MAX_ACTIVE_REQUESTS)
            .toMutableList()
        if (queued.size >= PluginCacheOperationCodec.MAX_ACTIVE_REQUESTS) {
            PluginCacheResultChannel.clear(context, request.requestId)
            return PluginCacheOperationOutcome.Waiting("request_queue_full")
        }
        queued += request
        runCatching {
            remote.edit()
                .putStringSet(
                    PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
                    queued.map(PluginCacheOperationCodec::encodeRequest).toSet()
                )
                .apply()
        }.getOrElse {
            PluginCacheResultChannel.clear(context, request.requestId)
            return PluginCacheOperationOutcome.Waiting("request_write_failed")
        }

        val deadline = PluginCacheOperationCodec.operationDeadlineEpochMs(request)
        while (System.currentTimeMillis() < deadline) {
            val response = PluginCacheResultChannel.consumeResponse(context, request.requestId)
            if (response != null) {
                cleanupCacheOperationRecords(remote, request.requestId)
                return PluginCacheOperationOutcome.Completed(response)
            }
            try {
                Thread.sleep(CACHE_OPERATION_RESPONSE_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                cleanupCacheOperationRecords(remote, request.requestId)
                return PluginCacheOperationOutcome.Waiting("request_interrupted")
            }
        }
        cleanupCacheOperationRecords(remote, request.requestId)
        return PluginCacheOperationOutcome.Waiting("system_ui_not_responding")
    }

    private fun cleanupCacheOperationRecords(
        remote: SharedPreferences,
        requestId: String
    ) {
        val now = System.currentTimeMillis()
        runCatching {
            val requests = remote.getStringSet(
                PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
                emptySet()
            ).orEmpty().mapNotNull(PluginCacheOperationCodec::decodeRequest)
                .filter { it.requestId != requestId }
                .filterNot { PluginCacheOperationCodec.isRequestExpired(it, now) }
                .take(PluginCacheOperationCodec.MAX_ACTIVE_REQUESTS)
                .map(PluginCacheOperationCodec::encodeRequest)
                .toSet()
            remote.edit()
                .putStringSet(PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY, requests)
                .apply()
        }
        PluginCacheResultChannel.clear(context, requestId)
    }

    fun configPreferences(pluginId: String): SharedPreferences = context.getSharedPreferences(
        PluginConstants.configGroup(pluginId),
        Context.MODE_PRIVATE
    )

    fun ensureDefaults(manifest: PluginManifest) {
        val preferences = configPreferences(manifest.id)
        val editor = preferences.edit()
        var changed = false
        manifest.settings.settings.forEach { setting ->
            val currentValue = preferences.all[setting.key]
            if (currentValue != null && isCompatible(currentValue, setting.type)) {
                return@forEach
            }
            if (currentValue != null) editor.remove(setting.key)
            putTypedValue(editor, setting.key, setting)
            changed = true
        }
        if (changed) editor.apply()
    }

    fun setSettingValue(manifest: PluginManifest, setting: PluginSettingSpec, rawValue: String) {
        val value: Any = when (setting.type) {
            PluginSettingType.SWITCH -> rawValue.toBoolean()
            PluginSettingType.NUMBER -> rawValue.toLongOrNull() ?: return
            PluginSettingType.SLIDER -> rawValue.toFloatOrNull() ?: return
            PluginSettingType.MULTI_SELECT -> rawValue.split(',').filter(String::isNotBlank).toSet()
            else -> rawValue
        }
        val local = configPreferences(manifest.id)
        putTypedValue(local.edit(), setting.key, value).apply()
        runCatching {
            requireServiceOrNull()?.getRemotePreferences(PluginConstants.configGroup(manifest.id))
                ?.edit()
                ?.let { putTypedValue(it, setting.key, value).apply() }
        }
    }

    fun syncAllRemote(service: XposedService): Boolean {
        return runCatching {
            listInstalled().forEach { installed ->
                ensureDefaults(installed.manifest)
                syncConfig(service, installed.manifest)
            }
            syncRegistry(service)
            true
        }.getOrDefault(false)
    }

    private fun syncRegistry(service: XposedService) {
        val enabled = registry.getStringSet(
            PluginConstants.REMOTE_ENABLED_IDS_KEY,
            emptySet()
        ).orEmpty()
        val installedFiles = listInstalled().associate { it.manifest.id to it.fileName }
        val remote = service.getRemotePreferences(PluginConstants.REMOTE_REGISTRY_PREFS)
        val editor = remote.edit()
        remote.all.keys
            .filter { key ->
                key.startsWith(PluginConstants.REMOTE_FILE_PREFIX) &&
                    key.removePrefix(PluginConstants.REMOTE_FILE_PREFIX) !in installedFiles
            }
            .forEach(editor::remove)
        installedFiles.forEach { (pluginId, fileName) ->
            editor.putString(PluginConstants.remoteFileKey(pluginId), fileName)
        }
        editor
            .putStringSet(PluginConstants.REMOTE_ENABLED_IDS_KEY, enabled)
            .putStringSet(
                PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY,
                registry.getStringSet(PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY, emptySet())
                    .orEmpty()
            )
            .apply()
    }

    private fun syncConfig(service: XposedService, manifest: PluginManifest) {
        val local = configPreferences(manifest.id)
        val remote = service.getRemotePreferences(PluginConstants.configGroup(manifest.id))
        val editor = remote.edit()
        local.all.forEach { (key, value) -> putTypedValue(editor, key, value) }
        editor.apply()
    }

    private fun putTypedValue(
        editor: SharedPreferences.Editor,
        key: String,
        value: Any?
    ): SharedPreferences.Editor {
        when (value) {
            null -> editor.remove(key)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
        return editor
    }

    private fun putTypedValue(
        editor: SharedPreferences.Editor,
        key: String,
        setting: PluginSettingSpec
    ): SharedPreferences.Editor {
        val defaultValue = setting.defaultValue ?: when (setting.type) {
            PluginSettingType.SWITCH -> "false"
            PluginSettingType.NUMBER,
            PluginSettingType.SLIDER -> "0"
            else -> ""
        }
        val value: Any = when (setting.type) {
            PluginSettingType.SWITCH -> defaultValue.toBoolean()
            PluginSettingType.NUMBER -> defaultValue.toLongOrNull() ?: 0L
            PluginSettingType.SLIDER -> defaultValue.toFloatOrNull() ?: 0f
            PluginSettingType.MULTI_SELECT -> defaultValue
                .split(',')
                .filter(String::isNotBlank)
                .toSet()
            else -> defaultValue
        }
        return putTypedValue(editor, key, value)
    }

    private fun encodeBackupSettings(manifest: PluginManifest): JSONObject {
        val preferences = configPreferences(manifest.id)
        return JSONObject().apply {
            manifest.settings.settings.forEach { setting ->
                if (!setting.backup) return@forEach
                val value = preferences.all[setting.key] ?: return@forEach
                when (setting.type) {
                    PluginSettingType.SWITCH -> (value as? Boolean)?.let { put(setting.key, it) }
                    PluginSettingType.NUMBER -> (value as? Number)?.let { put(setting.key, it.toLong()) }
                    PluginSettingType.SLIDER -> (value as? Number)?.let { put(setting.key, it.toFloat()) }
                    PluginSettingType.MULTI_SELECT -> {
                        val values = (value as? Set<*>)
                            ?.filterIsInstance<String>()
                            ?.takeIf { it.isNotEmpty() }
                        if (values != null) put(setting.key, JSONArray(values))
                    }

                    PluginSettingType.TEXT,
                    PluginSettingType.PASSWORD,
                    PluginSettingType.SELECT,
                    PluginSettingType.ACTION -> (value as? String)?.let { put(setting.key, it) }
                }
            }
        }
    }

    private fun decodeBackupValue(value: Any?, type: PluginSettingType): Any? {
        if (value == null || value === JSONObject.NULL) return null
        return when (type) {
            PluginSettingType.SWITCH -> value as? Boolean
            PluginSettingType.NUMBER -> (value as? Number)?.toLong()
            PluginSettingType.SLIDER -> (value as? Number)?.toFloat()
            PluginSettingType.MULTI_SELECT -> (value as? JSONArray)?.let { array ->
                buildSet(array.length()) {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
            }

            PluginSettingType.TEXT,
            PluginSettingType.PASSWORD,
            PluginSettingType.SELECT,
            PluginSettingType.ACTION -> value as? String
        }
    }

    private fun isCompatible(value: Any, type: PluginSettingType): Boolean = when (type) {
        PluginSettingType.SWITCH -> value is Boolean
        PluginSettingType.TEXT,
        PluginSettingType.PASSWORD,
        PluginSettingType.SELECT,
        PluginSettingType.ACTION -> value is String
        PluginSettingType.MULTI_SELECT -> value is Set<*>
        PluginSettingType.NUMBER -> value is Long
        PluginSettingType.SLIDER -> value is Float
    }

    private fun requireService(): XposedService = requireServiceOrNull()
        ?: throw IllegalStateException("Xposed Service 未连接")

    private fun requireServiceOrNull(): XposedService? =
        com.lidesheng.hyperlyric.root.RootApplication.xposedService

    private fun writeRemoteFile(service: XposedService, fileName: String, bytes: ByteArray) {
        val descriptor = service.openRemoteFile(fileName)
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    private fun readRemoteFile(service: XposedService, fileName: String): ByteArray {
        val descriptor = service.openRemoteFile(fileName)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use(PluginArchiveReader::readBounded)
    }

    private companion object {
        const val CACHE_OPERATION_RESPONSE_POLL_MS = 200L
    }
}
