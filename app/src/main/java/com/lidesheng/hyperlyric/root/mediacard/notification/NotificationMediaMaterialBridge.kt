package com.lidesheng.hyperlyric.root.mediacard.notification

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.HookRuntimeRegistry
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaBackgroundController
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** OS4 applies media materials to the header, rather than the view controller. */
internal object NotificationMediaMaterialBridge {
    private const val TAG = "NotificationMediaMaterialBridge"
    private const val EFFECT_PACKAGE =
        "com.android.systemui.statusbar.notification.style.vieweffect."

    private class Effect(val instance: Any, val apply: Method, val clear: Method)
    private class Material(val effect: Effect, val context: Context)
    private class Target(val host: WeakReference<ViewGroup>, val proxy: ViewGroup)

    private val loaders = WeakHashMap<ClassLoader, Boolean>()
    private val materials = WeakHashMap<ViewGroup, Material>()
    // Controller attach/detach owns these entries, including cloned controllers.
    private val targets = IdentityHashMap<Any, Target>()
    private val replaying = ThreadLocal.withInitial { false }

    fun hook(module: XposedModule, loader: ClassLoader): Boolean {
        if (loaders.containsKey(loader)) return true
        val helper = runCatching { loader.loadClass(EFFECT_PACKAGE + "NotificationViewEffectHelper") }
            .getOrNull() ?: return false // Older SystemUI uses updateMediaBackground().
        val handles = mutableListOf<HookHandle>()
        return runCatching {
            val header = loader.loadClass(NotificationMediaHostClasses.MEDIA_HEADER_VIEW)
            header.getDeclaredConstructor(Context::class.java, AttributeSet::class.java)
            header.getDeclaredField("mediaViewHolder")
            val map = helper.getDeclaredField("mediaViewEffectsMap").apply { isAccessible = true }
                .get(null) as Map<*, *>
            val effects = map.values.filterNotNull().associate { effect ->
                val type = effect.javaClass
                type to Effect(
                    effect,
                    type.getDeclaredMethod("apply", Any::class.java, Context::class.java)
                        .apply { isAccessible = true },
                    type.getDeclaredMethod("clear", Any::class.java)
                        .apply { isAccessible = true }
                )
            }
            require(effects.isNotEmpty())
            effects.values.forEach { effect ->
                listOf(effect.apply, effect.clear).forEach { method ->
                    handles += module.managedHook(
                        executable = method,
                        capability = "media.notification.material.${method.declaringClass.simpleName}.${method.name}",
                        hooker = MaterialHook(effect, method.name == "apply")
                    )
                }
            }
            loaders[loader] = true
            true
        }.onFailure { error ->
            handles.forEach { handle ->
                runCatching { handle.unhook() }
                HookRuntimeRegistry.forget(module, handle)
            }
            HookLogger.w(TAG, "通知中心原生材质接口不可用", error)
        }.getOrDefault(false)
    }

    fun isAvailable(controller: Any): Boolean = loaders.containsKey(controller.javaClass.classLoader)

    /** A detached native header is only an argument adapter; it owns no collectors. */
    fun attach(controller: Any, host: ViewGroup? = null) {
        val loader = controller.javaClass.classLoader ?: return
        if (!loaders.containsKey(loader)) return
        runCatching {
            val holder = field(controller, "holder") ?: return
            val player = field(holder, "player") as? View ?: return
            val header = host ?: (player.parent as? ViewGroup) ?: return
            if (header.javaClass.name != NotificationMediaHostClasses.MEDIA_HEADER_VIEW) return
            val existing = targets[controller]
            if (existing?.host?.get() === header &&
                field(existing.proxy, "mediaViewHolder") === holder
            ) return
            val proxy = header.javaClass.getDeclaredConstructor(
                Context::class.java, AttributeSet::class.java
            ).newInstance(header.context, null) as ViewGroup
            proxy.javaClass.getDeclaredField("mediaViewHolder").apply { isAccessible = true }
                .set(proxy, holder)
            targets[controller] = Target(WeakReference(header), proxy)
            restore(controller)
        }.onFailure { HookLogger.w(TAG, "关联媒体卡片原生材质失败", it) }
    }

    fun detach(controller: Any) {
        targets.remove(controller)
    }

    fun clear(controller: Any) {
        val target = targets[controller] ?: return
        val material = materials[target.host.get()] ?: return
        runCatching {
            guarded { material.effect.clear.invoke(material.effect.instance, target.proxy) }
        }.onFailure { HookLogger.w(TAG, "清除媒体卡片原生材质失败", it) }
    }

    fun restore(controller: Any) {
        val target = targets[controller] ?: return
        val material = materials[target.host.get()] ?: return
        if (NotificationMediaBackgroundController.shouldSuppressNativeBackground(controller)) return
        runCatching {
            guarded {
                val context = NotificationMediaAmbientFlowHooker.materialContext(material.context)
                material.effect.apply.invoke(material.effect.instance, target.proxy, context)
                applyGlassOverride(target.proxy, material.effect, context)
            }
        }.onFailure { HookLogger.w(TAG, "恢复媒体卡片原生材质失败", it) }
    }

    private fun applyGlassOverride(header: ViewGroup, effect: Effect, context: Context) {
        if (!NotificationMediaAmbientFlowHooker.usesDarkGlassMaterial()) return
        val holder = field(header, "mediaViewHolder") ?: return
        if (!NotificationMediaGlassMaterial.apply(holder, effect.instance.javaClass.simpleName, context)) {
            guarded { effect.apply.invoke(effect.instance, header, context) }
        }
    }

    private class MaterialHook(private val effect: Effect, private val applying: Boolean) : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (replaying.get() == true) return chain.proceed()
            val header = chain.args.firstOrNull() as? ViewGroup ?: return chain.proceed()
            val affected = targets.entries.filter { it.value.host.get() === header }
                .map { it.key to it.value }
            val holder = field(header, "mediaViewHolder")
            val original = affected.firstOrNull { field(it.second.proxy, "mediaViewHolder") === holder }
                ?.first
            if (applying) {
                val context = chain.args.getOrNull(1) as? Context ?: return chain.proceed()
                materials[header] = Material(effect, context)
            } else {
                materials.remove(header)
            }
            val suppressNative = applying && original != null &&
                NotificationMediaBackgroundController.shouldSuppressNativeBackground(original)
            val context = if (applying) {
                NotificationMediaAmbientFlowHooker.materialContext(chain.args[1] as Context)
            } else null
            val result = if (suppressNative) {
                null
            } else if (applying) {
                chain.proceed(arrayOf(header, requireNotNull(context)))
            } else {
                chain.proceed()
            }
            if (applying && !suppressNative) {
                runCatching { applyGlassOverride(header, effect, requireNotNull(context)) }
                    .onFailure { HookLogger.w(TAG, "同步原生媒体卡片玻璃材质失败", it) }
            }
            affected.forEach { (controller, target) ->
                runCatching {
                    if (applying) {
                        // The original header has already been processed by chain.proceed().
                        if (controller !== original) restore(controller)
                        NotificationMediaBackgroundController.onUiModeChanged(controller)
                    } else if (controller !== original) {
                        guarded { effect.clear.invoke(effect.instance, target.proxy) }
                    }
                }.onFailure { HookLogger.w(TAG, "同步媒体卡片原生材质失败", it) }
            }
            return result
        }
    }

    private fun guarded(block: () -> Unit) {
        val previous = replaying.get() == true
        replaying.set(true)
        try { block() } finally { replaying.set(previous) }
    }

    private fun field(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val current = type
            val member = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (member != null) return member.apply { isAccessible = true }.get(target)
            type = type.superclass
        }
        return null
    }
}
