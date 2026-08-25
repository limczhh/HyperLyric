package com.lidesheng.hyperlyric.root.island.host

import android.app.Notification
import android.app.PendingIntent
import android.media.session.MediaSession
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.SystemUiEnhancementGate

internal object IslandProbeUtils {
    const val LEFT_PARENT_NAME = "island_container_module_image_text_1"
    const val RIGHT_PARENT_NAME = "island_container_module_image_text_2"
    const val TEXT_CONTAINER_NAME = "island_container_module_text"

    const val LEFT_TEST_VIEW_TAG = "HYPERLYRIC_LEFT_VIEW"
    const val RIGHT_TEST_VIEW_TAG = "HYPERLYRIC_RIGHT_VIEW"
    const val LEFT_TEST_WRAPPER_TAG = "HYPERLYRIC_LEFT_VIEW_WRAPPER"
    const val RIGHT_TEST_WRAPPER_TAG = "HYPERLYRIC_RIGHT_VIEW_WRAPPER"

    fun isSuperIslandEnabled(): Boolean {
        return SystemUiEnhancementGate.isEnabled()
    }

    fun extractMediaIslandInfo(data: Any?): MediaIslandInfo? {
        val extras = extractExtras(data) ?: return null
        val pkgName = extras.getString("miui.pkg.name").orEmpty()
        if (pkgName.isEmpty()) return null
        if (!hasMediaPendingIntent(extras)) return null

        return MediaIslandInfo(
            packageName = pkgName
        )
    }

    fun isMediaIsland(data: Any?): Boolean {
        val extras = extractExtras(data) ?: return false
        return hasMediaPendingIntent(extras)
    }

    fun extractStatusBarNotification(data: Any?): StatusBarNotification? {
        return runCatching {
            extractExtras(data)?.getParcelable(
                EXTRA_MIUI_SBN,
                StatusBarNotification::class.java
            )
        }.getOrNull()
    }

    fun extractMediaSessionToken(data: Any?): MediaSession.Token? {
        val notificationExtras = extractStatusBarNotification(data)
            ?.notification
            ?.extras
            ?: return null
        return runCatching {
            notificationExtras.getParcelable(
                Notification.EXTRA_MEDIA_SESSION,
                MediaSession.Token::class.java
            )
        }.getOrNull()
    }

    fun getCurrentIslandData(contentView: Any?): Any? {
        return contentView.callGetter("getCurrentIslandData")
    }

    fun getHolder(adapter: Any?, moduleType: String?): Any? {
        if (adapter == null || moduleType == null) return null
        val holders = adapter.javaClass.findField("holders")?.let { field ->
            runCatching {
                field.isAccessible = true
                field.get(adapter) as? Map<*, *>
            }.getOrNull()
        }
        return holders?.get(moduleType)
    }

    fun getHolderRootView(holder: Any?): ViewGroup? {
        return runCatching {
            holder?.javaClass?.methods?.find {
                it.name == "getRootView" && it.parameterTypes.isEmpty()
            }?.invoke(holder) as? ViewGroup
        }.getOrNull()
    }

    fun isRealBigIslandModuleArea(rootView: ViewGroup): Boolean {
        val areaName = runCatching {
            rootView.resources.getResourceEntryName(rootView.id)
        }.getOrNull()
        return areaName == "area_left" || areaName == "area_right"
    }

    private fun extractExtras(data: Any?): Bundle? {
        return runCatching {
            data.callGetter("getExtras") as? Bundle
        }.getOrNull()
    }

    private fun hasMediaPendingIntent(extras: Bundle): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            extras.getParcelable(EXTRA_MIUI_PENDING_INTENT) as? PendingIntent
        }.getOrNull() != null
    }

    private const val EXTRA_MIUI_PENDING_INTENT = "miui.pending.intent"
    private const val EXTRA_MIUI_SBN = "miui.sbn"

    private fun Any?.callGetter(name: String): Any? {
        val receiver = this ?: return null
        return runCatching {
            receiver.javaClass.methods.find {
                it.name == name && it.parameterTypes.isEmpty()
            }?.invoke(receiver)
        }.getOrNull()
    }

    private fun Class<*>.findField(name: String): java.lang.reflect.Field? {
        var current: Class<*>? = this
        while (current != null) {
            val field = current.declaredFields.find { it.name == name }
            if (field != null) return field
            current = current.superclass
        }
        return null
    }

    data class MediaIslandInfo(val packageName: String)
}
