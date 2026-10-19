package com.lidesheng.hyperlyric.root

import android.content.Context

internal object PluginContextResolver {
    fun resolve(instance: Any): Context? {
        val data = read(instance, "pluginData")
        return (data?.let { read(it, "context") } as? Context)
            ?: (read(instance, "mPluginContext") as? Context)
            ?: (read(instance, "mContext") as? Context)
    }

    private fun read(instance: Any, name: String): Any? {
        var type: Class<*>? = instance.javaClass
        while (type != null) {
            val current = type
            val field = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) return field.apply { isAccessible = true }.get(instance)
            type = current.superclass
        }
        return null
    }
}
