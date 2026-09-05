package com.lidesheng.hyperlyric.root.plugin

import com.lidesheng.hyperlyric.plugin.core.PluginConstants

/** Extracts the published archive map without treating enabled IDs as the installed set. */
internal fun pluginFileEntries(values: Map<String, *>): Map<String, String> =
    values.asSequence()
        .mapNotNull { (key, value) ->
            if (!key.startsWith(PluginConstants.REMOTE_FILE_PREFIX)) return@mapNotNull null
            val pluginId = key.removePrefix(PluginConstants.REMOTE_FILE_PREFIX)
                .takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val fileName = (value as? String)?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            pluginId to fileName
        }
        .toMap()
