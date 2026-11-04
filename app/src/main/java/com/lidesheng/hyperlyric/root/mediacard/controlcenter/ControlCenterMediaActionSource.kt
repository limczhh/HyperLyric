package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field

internal data class ControlCenterMediaAction(
    val icon: Drawable,
    val description: CharSequence?,
    val run: Runnable,
)

internal data class ControlCenterMediaActions(
    val left: ControlCenterMediaAction? = null,
    val right: ControlCenterMediaAction? = null,
)

/** Includes entries with NO actions: they must never fall through to another app. */
internal class ControlCenterMediaActionStore {
    internal data class Selection(val token: Any, val actions: ControlCenterMediaActions)
    private data class Entry(
        val packageName: String, val token: Any, val title: String, val artist: String,
        val actions: ControlCenterMediaActions,
    )
    private val entries = linkedMapOf<Any, Entry>()

    fun update(key: Any, packageName: String, token: Any?, title: String, artist: String,
        actions: ControlCenterMediaActions) {
        if (token == null) { entries.remove(key); return }
        entries.remove(key)
        entries[key] = Entry(packageName, token, title.trim(), artist.trim(), actions)
        while (entries.size > 32) entries.remove(entries.keys.first())
    }

    fun resolve(packageName: String?, title: String, artist: String): ControlCenterMediaActions {
        return select(packageName, title, artist)?.actions ?: ControlCenterMediaActions()
    }

    internal fun select(packageName: String?, title: String, artist: String,
        boundToken: Any? = null): Selection? {
        if (packageName.isNullOrBlank()) return null
        val candidates = entries.values.toList().asReversed().filter {
            it.packageName == packageName
        }.distinctBy { it.token }
        // Notification metadata and MiPlay labels update independently. Once
        // bound, follow the SAME live session through either update order.
        candidates.firstOrNull { it.token == boundToken }?.let {
            return Selection(it.token, it.actions)
        }
        val matching = candidates.filter { title.isBlank() || it.title == title.trim() }
        val selected = matching.singleOrNull() ?: matching.filter {
            artist.isNotBlank() && it.artist == artist.trim()
        }.singleOrNull()
        return selected?.let { Selection(it.token, it.actions) }
    }
}

/** Session identity belongs to each displayed Header, never to the global cache. */
internal class ControlCenterMediaActionBinding(private val store: ControlCenterMediaActionStore) {
    private var packageName: String? = null
    private var token: Any? = null

    fun resolve(sourcePackage: String?, title: String, artist: String): ControlCenterMediaActions {
        val selected = store.select(sourcePackage, title, artist,
            boundToken = token.takeIf { sourcePackage == packageName })
        packageName = sourcePackage
        token = selected?.token
        return selected?.actions ?: ControlCenterMediaActions()
    }
}

/** Reuses the notification card's semantic custom0/custom1 and native click Runnables. */
internal object ControlCenterMediaActionSource {
    private class Capture(val data: Any, var left: Any? = null, var right: Any? = null)
    private val store = ControlCenterMediaActionStore()
    private val handles = mutableMapOf<Class<*>, List<HookHandle>>()
    private val fields = mutableMapOf<Pair<Class<*>, String>, Field?>()
    private val listeners = mutableSetOf<() -> Unit>()

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun forHeader(header: View, title: TextView, artist: TextView): () -> ControlCenterMediaActions {
        val binding = ControlCenterMediaActionBinding(store)
        val resolver = runCatching {
            val model = header.javaClass.classLoader!!.loadClass("com.android.systemui.MiPlayDetailViewModel")
            val instance = model.getField("INSTANCE").get(null)
            val getPackage = model.getMethod("getSourcePackage")
            val resolve: () -> String? = { getPackage.invoke(instance) as? String }
            resolve
        }.getOrNull()
        return {
            val packageName = runCatching { resolver?.invoke() }.getOrNull()
            binding.resolve(packageName, title.text.toString(), artist.text.toString())
        }
    }

    @Synchronized
    fun install(module: XposedModule, loader: ClassLoader) {
        val controller = runCatching { loader.loadClass(
            "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
        ) }.getOrNull() ?: return
        if (handles.containsKey(controller)) return
        val installed = mutableListOf<HookHandle>()
        try {
            val bind = controller.declaredMethods.single { it.name == "bindMediaData" && it.parameterCount == 1 }
            val active = ThreadLocal<Capture>()
            val previous = mutableMapOf<Any, Capture>()
            val utils = loader.loadClass(
                "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaActionButtonUtils")
            val common = utils.declaredMethods.single { it.name == "bindButtonsCommon" && it.parameterCount == 3 }
            installed += module.managedHook(common, "media.controlcenter.custom_actions.slots", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val capture = active.get()
                    val button = chain.args[0] as? ImageButton
                    if (capture != null && button != null) {
                        when (runCatching { button.resources.getResourceEntryName(button.id) }.getOrNull()) {
                            "action0" -> capture.left = chain.args[1]
                            "action4" -> capture.right = chain.args[1]
                        }
                    }
                    return chain.proceed()
                }
            })
            installed += module.managedHook(bind, "media.controlcenter.custom_actions.media_data", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val data = chain.args[0] ?: return chain.proceed()
                    val token = read(data, "token")
                    val key = (read(data, "notificationKey") as? String)?.takeIf { it.isNotBlank() }
                        ?: token ?: return chain.proceed()
                    val semantic = read(data, "semanticActions")
                    val old = previous[key]
                    val capture = if (semantic != null) Capture(data, read(semantic, "custom0"), read(semantic, "custom1"))
                        else if (old != null && read(old.data, "actions") == read(data, "actions"))
                            Capture(data, old.left, old.right) else Capture(data)
                    val outer = active.get()
                    active.set(capture)
                    return try {
                        val result = chain.proceed()
                        previous[key] = capture
                        while (previous.size > 32) previous.remove(previous.keys.first())
                        store.update(key, read(data, "packageName") as? String ?: "", token,
                            read(data, "song")?.toString().orEmpty(), read(data, "artist")?.toString().orEmpty(),
                            ControlCenterMediaActions(action(capture.left), action(capture.right)))
                        listeners.toList().forEach { it() }
                        result
                    } finally {
                        if (outer == null) active.remove() else active.set(outer)
                    }
                }
            })
            handles[controller] = installed
        } catch (error: Throwable) {
            installed.asReversed().forEach { runCatching { it.unhook() } }
            HookLogger.w("ControlCenterMediaLayout", "接入通知媒体动作失败", error)
        }
    }

    private fun action(value: Any?): ControlCenterMediaAction? {
        value ?: return null
        return ControlCenterMediaAction(read(value, "icon") as? Drawable ?: return null,
            read(value, "contentDescription") as? CharSequence,
            read(value, "action") as? Runnable ?: return null)
    }

    private fun read(value: Any, name: String): Any? = runCatching {
        val field = fields.getOrPut(value.javaClass to name) {
            generateSequence(value.javaClass) { it.superclass }.mapNotNull { type ->
                runCatching { type.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
            }.firstOrNull()
        }
        field?.get(value)
    }.getOrNull()
}
