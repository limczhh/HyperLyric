package com.lidesheng.hyperlyric.plugin.core

import java.security.MessageDigest

object PluginRemoteFileNames {
    fun forId(pluginId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(pluginId.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "hyperlyric_plugin_$digest.zip"
    }

    /**
     * A new immutable name for one installed archive. The registry publishes this name only
     * after the complete archive has been written, so readers never observe a file being
     * replaced in place.
     */
    fun forRevision(pluginId: String, revision: String): String =
        "${forId(pluginId).removeSuffix(".zip")}_$revision.zip"
}
