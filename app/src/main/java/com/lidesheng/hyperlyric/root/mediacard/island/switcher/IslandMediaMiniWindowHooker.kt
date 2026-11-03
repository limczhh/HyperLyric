package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.island.hooks.SodaIslandMiniWindowCompatHooker
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.abs

/** A browsed session has no native island to return to. Launch its windows independently. */
internal object IslandMediaMiniWindowHooker {
    private const val TAG = "IslandMediaMiniWindow"
    private val gestures = WeakHashMap<Any, Gesture>()
    private val freeformOptions by lazy {
        Class.forName("android.util.MiuiMultiWindowUtils").getDeclaredMethod(
            "getActivityOptions", Context::class.java, String::class.java,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        )
    }

    fun hook(module: XposedModule, loader: ClassLoader) {
        val config = MediaCardRuntimeConfig.current.islandExpanded
        if (!config.cardSwitcherEnabled || config.cardSwitcherMode == RootConstants.ISLAND_MEDIA_CARD_SWITCHER_MODE_MULTI) return
        val fake = loader.loadClass("miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView")
        val threshold = fake.getDeclaredField("mMaxTriggerThreshold").apply { isAccessible = true }
        val touch = fake.getDeclaredMethod("handleTouchEvent", MotionEvent::class.java)
        module.managedHook(touch, "media.island_switcher.ordinary_window.touch", object : Hooker {
            override fun intercept(chain: Chain): Any? {
                val owner = chain.thisObject ?: return chain.proceed()
                val event = chain.args.firstOrNull() as? MotionEvent ?: return chain.proceed()
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    gestures.remove(owner)
                    val real = call(owner, "getRealView") ?: return chain.proceed()
                    val target = target(real) ?: return chain.proceed()
                    val events = call(real, "getDynamicIslandEventCoordinator") ?: return false
                    // Bar visibility already reflects native whitelist/lock-screen/Soda checks.
                    val canTrack = events.javaClass.methods.first {
                        it.name == "canExpandedViewTrack" && it.parameterCount == 1
                    }
                    val eligible = call(owner, "getMiniBarVisible") == true && windowIdle(events) &&
                        canTrack.invoke(events, real) == true
                    val distance = threshold.getFloat(owner)
                    gestures[owner] = Gesture(target, event.downTime, event.x, event.y, distance, eligible)
                    val pkg = target.extras.getString("miui.pkg.name")
                    HookLogger.dState(
                        stateId = "$TAG.eligibility", tag = TAG, state = "$pkg/$eligible/$distance",
                    ) { "浏览会话使用普通小窗: pkg=$pkg, eligible=$eligible, threshold=${distance.toInt()}" }
                    return eligible
                }
                val gesture = gestures[owner] ?: return chain.proceed()
                if (gesture.downTime != event.downTime) {
                    gestures.remove(owner)
                    return chain.proceed()
                }
                if (event.pointerCount != 1) gesture.cancelled = true
                when (event.actionMasked) {
                    MotionEvent.ACTION_CANCEL -> gestures.remove(owner)
                    MotionEvent.ACTION_UP -> {
                        gestures.remove(owner)
                        val dx = event.x - gesture.x
                        val dy = event.y - gesture.y
                        // Use Xiaomi's full-drag threshold, without entering its FakeView lifecycle.
                        if (!gesture.cancelled && gesture.eligible && dy >= gesture.threshold && dy > abs(dx)) {
                            gesture.target.open(freeform = true)
                            return true
                        }
                    }
                }
                return false
            }
        })
        val content = loader.loadClass("miui.systemui.dynamicisland.window.content.DynamicIslandContentView")
        module.managedHook(content.getDeclaredMethod("onIslandClick"), "media.island_switcher.ordinary_window.click", object : Hooker {
            override fun intercept(chain: Chain): Any? {
                val real = chain.thisObject ?: return chain.proceed()
                call(real, "getFakeView")?.let { gestures.remove(it) }
                val target = target(real) ?: return chain.proceed()
                val events = call(real, "getDynamicIslandEventCoordinator") ?: return null
                if (windowIdle(events)) target.open(freeform = false)
                return null
            }
        })
    }

    private class Target(
        window: View,
        val bridge: Function<Array<Any?>, Any?>,
        val extras: Bundle,
    ) {
        private val window = WeakReference(window)
        fun open(freeform: Boolean): Boolean = runCatching {
            val window = requireNotNull(window.get()) { "Island window detached" }
            check(window.isAttachedToWindow) { "Island window detached" }
            val collapse = window.javaClass.getMethod("collapse", String::class.java)
            bridge.apply(arrayOf("openOrdinaryWindow", extras, freeform, Runnable {
                collapse.invoke(window, "single media ordinary window")
            })) == true
        }.onFailure { HookLogger.w(TAG, "执行浏览会话窗口操作失败", it) }.getOrDefault(false)
    }

    private class Gesture(
        val target: Target,
        val downTime: Long,
        val x: Float,
        val y: Float,
        val threshold: Float,
        val eligible: Boolean,
    ) {
        var cancelled = false
    }

    private fun target(real: Any): Target? {
        val events = call(real, "getDynamicIslandEventCoordinator") ?: return null
        val window = call(events, "getWindowView") as? View ?: return null
        val bridge = IslandMediaSessionCoordinator.connect(window, IslandProbeUtils.getCurrentIslandData(real)) ?: return null
        val extras = bridge.apply(arrayOf("ordinaryWindowTarget", real)) as? Bundle ?: return null
        return Target(window, bridge, extras)
    }

    private fun windowIdle(events: Any): Boolean {
        val window = call(events, "getWindowView") ?: return false
        val controller = call(window, "getWindowViewController") ?: return false
        fun running(name: String) = call(controller, name)?.let { call(it, "getValue") } == true
        return call(controller, "windowAnimating") != true &&
            !running("isAppAnimRunning") && !running("isFreeformAnimRunning")
    }

    /** Native policy reads the browsed app only during this synchronous query, never in its state machine. */
    fun withEligibility(context: Context, real: Any, target: Bundle, action: Supplier<*>): Any? {
        val native = call(real, "getCurrentIslandData") ?: return action.get()
        val query = native.javaClass.getDeclaredConstructor().newInstance()
        listOf("Key", "Priority", "Properties", "TickerData", "View", "FakeView").forEach { name ->
            set(query, "set$name", call(native, "get$name"))
        }
        val extras = Bundle(call(native, "getExtras") as? Bundle ?: Bundle()).apply { putAll(target) }
        SodaIslandMiniWindowCompatHooker.resolveFreeformIntent(context, extras)?.let {
            extras.putParcelable("miui.pending.intent", it)
        }
        set(query, "setExtras", extras)
        set(real, "setCurrentIslandData", query)
        return try {
            action.get()
        } finally {
            if (call(real, "getCurrentIslandData") === query) set(real, "setCurrentIslandData", native)
        }
    }

    /** Same framework freeform options as SystemUI, with ordinary window animation. */
    @Suppress("DEPRECATION")
    fun launch(context: Context, extras: Bundle, freeform: Boolean, beforeLaunch: () -> Unit): Boolean = runCatching {
        val pkg = requireNotNull(extras.getString("miui.pkg.name"))
        val intent = requireNotNull(if (freeform) {
            SodaIslandMiniWindowCompatHooker.resolveFreeformIntent(context, extras)
        } else extras.getParcelable<PendingIntent>("miui.pending.intent")) { "Media launch intent unavailable: $pkg" }
        val options = if (freeform) {
            check(intent.isActivity) { "Freeform launch requires an Activity PendingIntent: $pkg" }
            requireNotNull(freeformOptions.invoke(null, context, pkg, true, false) as? ActivityOptions) {
                "System freeform options unavailable: $pkg"
            }
        } else ActivityOptions.makeBasic()
        val bundle = options.toBundle().apply {
            putInt("android.pendingIntent.backgroundActivityAllowed", 1)
        }
        // Finish browsing before the framework observes the new window.
        beforeLaunch()
        intent.send(context, 0, null, null, null, null, bundle)
        HookLogger.d(TAG, "已独立打开浏览会话: pkg=$pkg, freeform=$freeform")
    }.onFailure { HookLogger.w(TAG, "打开浏览会话窗口失败: freeform=$freeform", it) }.isSuccess

    private fun call(target: Any, name: String): Any? = target.javaClass.methods.first {
        it.name == name && it.parameterCount == 0
    }.invoke(target)

    private fun set(target: Any, name: String, value: Any?) = target.javaClass.methods.first {
        it.name == name && it.parameterCount == 1
    }.invoke(target, value)
}
