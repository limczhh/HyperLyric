package com.lidesheng.hyperlyric.root.mediacard.notification.background

import java.util.WeakHashMap

internal object MediaBackgroundRendererPool {
    private val lock = Any()
    private val renderers = WeakHashMap<ClassLoader, NotificationMediaBackgroundRenderer>()

    fun get(classLoader: ClassLoader): NotificationMediaBackgroundRenderer {
        return synchronized(lock) {
            renderers.getOrPut(classLoader) {
                NotificationMediaBackgroundRenderer(classLoader)
            }
        }
    }

    fun releaseAll() {
        val snapshot = synchronized(lock) {
            renderers.values.toList().also { renderers.clear() }
        }
        snapshot.forEach(NotificationMediaBackgroundRenderer::close)
    }
}
