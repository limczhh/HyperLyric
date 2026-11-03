package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.app.PendingIntent
import android.os.Bundle
import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaNativeFactory
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method

/** Native expanded Views and binding only; no island controller, publication or removal. */
internal class IslandMediaBrowsingCard(
    private val native: NotificationMediaNativeFactory.Controller,
    val templatePlayer: View,
    player: View,
    dummy: View,
) {
    val key: String? get() = content.key
    val content = IslandMediaExpandedContent(player, dummy, Bundle()) {
        try {
            method("detach", 0).invoke(native.instance)
        } finally {
            native.close()
            HookLogger.d("IslandMediaCardSwitcher", "单岛浏览绑定器已释放: key=$key")
        }
    }
    private val bind = method("bindMediaData", 1)
    private val packageNameField = dataField("packageName")
    private val userIdField = dataField("userId")
    private val clickIntentField = dataField("clickIntent")

    @Suppress("DEPRECATION")
    fun bind(key: String, data: Any) {
        native.prepareBind(data)
        bind.invoke(native.instance, data)
        content.key = key
        // These are the launch fields populated by Xiaomi's media island controller.
        // Keep compact template/identity data owned by the original island.
        val pkg = packageNameField.get(data) as String
        val userId = userIdField.getInt(data)
        val intent = clickIntentField.get(data) as? PendingIntent
        val previous = content.extras
        if (previous.getString("miui.pkg.name") != pkg || previous.getInt("miui.user.id") != userId ||
            previous.getParcelable<PendingIntent>("miui.pending.intent") != intent
        ) {
            content.extras = Bundle().apply {
                putString("miui.pkg.name", pkg)
                putInt("miui.user.id", userId)
                putParcelable("miui.pending.intent", intent)
                putParcelable("miui.sbn", null)
            }
        }
    }

    private fun method(name: String, count: Int): Method = native.instance.javaClass.methods.first {
        it.name == name && it.parameterCount == count
    }

    private fun dataField(name: String) = bind.parameterTypes.single().getDeclaredField(name)
        .apply { isAccessible = true }
}

/** Stable view pair; replacing extras marks a new binding without replacing the Views. */
internal class IslandMediaExpandedContent(
    val view: View,
    val fakeView: View,
    var extras: Bundle,
    private val dispose: () -> Unit = {},
) {
    var key: String? = null
    private var closed = false
    var retired = false
        private set

    fun retire() { retired = true }

    fun close() {
        if (closed) return
        closed = true
        dispose()
    }
}
