package com.lidesheng.hyperlyric.plugin.core

import com.lidesheng.hyperlyric.plugin.api.HYPERLYRIC_PLUGIN_API_VERSION

object PluginConstants {
    const val API_VERSION = HYPERLYRIC_PLUGIN_API_VERSION

    /** Stable host-side routing identifiers for the first built-in HyperLyric plugin. */
    const val AI_TRANSLATION_PLUGIN_ID = "hyperlyric.ai.translation"

    const val REMOTE_REGISTRY_PREFS = "hyperlyric.plugin.registry"
    const val REMOTE_ENABLED_IDS_KEY = "enabled_ids"
    /** Published remote file names; the file is written before this mapping is updated. */
    const val REMOTE_FILE_PREFIX = "file."
    /** App-side uninstall tombstones consumed by the SystemUI runtime. */
    const val REMOTE_CACHE_CLEAR_TOKENS_KEY = "cache_clear_tokens"
    /** Bounded request/response queues for App-side plugin cache management. */
    const val REMOTE_CACHE_OPERATION_REQUESTS_KEY = "cache_operation_requests"
    const val CACHE_RESULT_PROVIDER_AUTHORITY = "com.lidesheng.hyperlyric.plugin-cache-result"

    const val LOCAL_REGISTRY_PREFS = "hyperlyric_plugin_registry"
    const val LOCAL_INSTALLED_IDS_KEY = "installed_ids"
    const val LOCAL_MANIFEST_PREFIX = "manifest."
    const val LOCAL_FILE_PREFIX = "file."

    const val ZIP_MANIFEST = "manifest.json"
    const val ZIP_DEX = "classes.dex"

    const val MAX_PLUGIN_ZIP_BYTES = 64 * 1024 * 1024
    const val MAX_PLUGIN_DEX_BYTES = 32 * 1024 * 1024
    const val MAX_PLUGIN_DEX_FILES = 16
    const val MAX_PLUGIN_MANIFEST_BYTES = 512 * 1024

    /** Absolute host-side safety gate for one plugin Processor invocation. */
    const val MAX_PROCESSOR_TIMEOUT_MS = 40_000L
    /** End-to-end App/SystemUI deadline for one plugin cache-management operation. */
    const val MAX_CACHE_OPERATION_TIMEOUT_MS = 6_000L

    fun configGroup(pluginId: String): String = "plugin.$pluginId"

    fun remoteFileKey(pluginId: String): String = REMOTE_FILE_PREFIX + pluginId

    fun storagePreferences(pluginId: String): String = "hyperlyric_plugin_data_$pluginId"

    fun cachePreferences(pluginId: String): String = "hyperlyric_plugin_cache_$pluginId"

    fun cacheMetadataPreferences(pluginId: String): String =
        "hyperlyric_plugin_cache_meta_$pluginId"

    fun cacheOperationPreferences(pluginId: String): String =
        "hyperlyric_plugin_cache_operations_$pluginId"
}
