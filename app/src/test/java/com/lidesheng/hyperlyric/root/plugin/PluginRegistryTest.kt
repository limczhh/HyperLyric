package com.lidesheng.hyperlyric.root.plugin

import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginRegistryTest {
    @Test
    fun installedArchivesAreCollectedEvenWhenDisabled() {
        val entries = pluginFileEntries(
            mapOf(
                PluginConstants.REMOTE_ENABLED_IDS_KEY to setOf("enabled.plugin"),
                PluginConstants.remoteFileKey("enabled.plugin") to "enabled.zip",
                PluginConstants.remoteFileKey("disabled.plugin") to "disabled.zip"
            )
        )

        assertEquals(
            mapOf(
                "enabled.plugin" to "enabled.zip",
                "disabled.plugin" to "disabled.zip"
            ),
            entries
        )
    }

    @Test
    fun malformedArchiveEntriesAreIgnored() {
        val entries = pluginFileEntries(
            mapOf(
                PluginConstants.REMOTE_FILE_PREFIX to "missing-id.zip",
                PluginConstants.remoteFileKey("empty.file") to "",
                PluginConstants.remoteFileKey("wrong.type") to setOf("not-a-file"),
                "other_key" to "ignored"
            )
        )

        assertEquals(emptyMap<String, String>(), entries)
    }
}
