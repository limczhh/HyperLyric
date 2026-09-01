package com.lidesheng.hyperlyric.root.island.effects.color

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the status-bar tint computed by SystemUI's DarkIconDispatcher.
 *
 * The captured color is consumed only by HyperLyric's injected island lyric views.
 */
internal object StatusBarTextColorHooker {
    private const val TAG = "StatusBarTextColorHooker"
    private const val DISPATCHER_CLASS =
        "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl"
    private const val RECEIVER_CLASS =
        "com.android.systemui.plugins.DarkIconDispatcher\$DarkReceiver"

    private val hookedDispatcherClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val dispatcherFieldCache = Collections.synchronizedMap(
        WeakHashMap<Class<*>, Array<Field?>>()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameScheduled = AtomicBoolean(false)
    private val frameCallback = Choreographer.FrameCallback {
        frameScheduled.set(false)
        val color = textColor
        if (dispatchedTextColor != color) {
            dispatchedTextColor = color
            textColorChangedListener?.invoke()
        }
        if (textColor != color) scheduleDispatch()
    }

    @Volatile
    private var textColor = Color.WHITE

    @Volatile
    private var dispatchedTextColor = Color.WHITE

    @Volatile
    private var followStatusBarEnabled = false

    @Volatile
    private var textColorChangedListener: (() -> Unit)? = null

    @Volatile
    private var tintAreas: List<Rect> = emptyList()

    @Volatile
    private var darkIntensity = 0f

    @Volatile
    private var iconTint = Color.WHITE

    @Volatile
    private var lightModeIconColor = Color.WHITE

    @Volatile
    private var darkModeIconColor = Color.BLACK

    @Volatile
    private var useTint = true

    private var activeDispatcher: WeakReference<Any>? = null
    private var receiver: Any? = null
    private var receiverRegistered = false

    fun setFollowStatusBarEnabled(enabled: Boolean) {
        followStatusBarEnabled = enabled
        if (enabled && textColor != dispatchedTextColor && textColorChangedListener != null) {
            scheduleDispatch()
        }
    }

    fun setTextColorChangedListener(listener: (() -> Unit)?) {
        textColorChangedListener = listener
        if (listener != null && followStatusBarEnabled && textColor != dispatchedTextColor) {
            scheduleDispatch()
        }
    }

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (hookDispatcher(module, classLoader)) {
            HookLogger.d(TAG, "状态栏颜色源已初始化: source=DarkIconDispatcherImpl")
            return
        }
        HookLogger.w(TAG, "未找到 DarkIconDispatcherImpl，状态栏颜色跟随已停用")
    }

    fun currentTextColor(): Int = textColor

    fun restoreTextColor(color: Int) {
        textColor = color
        dispatchedTextColor = color
    }

    fun createReplacement(constructor: Constructor<*>): Hooker? {
        return if (constructor.declaringClass.name == DISPATCHER_CLASS) {
            DispatcherConstructorHooker()
        } else {
            null
        }
    }

    fun createReplacement(method: Method): Hooker? {
        return DispatcherApplyHooker().takeIf { isDispatcherApply(method) }
    }

    private fun hookDispatcher(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(DISPATCHER_CLASS) }.getOrNull()
            ?: return false
        if (!hookedDispatcherClasses.add(clazz)) return true

        var count = 0
        clazz.declaredConstructors.forEach { constructor ->
            if (install(module, constructor, DispatcherConstructorHooker())) count++
        }
        clazz.declaredMethods.filter(::isDispatcherApply).forEach { method ->
            if (install(module, method, DispatcherApplyHooker())) count++
        }
        if (count == 0) hookedDispatcherClasses.remove(clazz)
        return count > 0
    }

    private fun install(module: XposedModule, executable: Executable, hooker: Hooker): Boolean {
        return try {
            executable.isAccessible = true
            module.deoptimize(executable)
            module.hook(executable).intercept(hooker)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isDispatcherApply(method: Method): Boolean {
        return method.declaringClass.name == DISPATCHER_CLASS &&
            method.name == "applyIconTint" && method.parameterCount == 0
    }

    private class DispatcherConstructorHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val displayId = chain.args.firstOrNull { it is Int } as? Int
            if (displayId == null || displayId == 0) {
                registerReceiver(chain.thisObject)
            }
            return result
        }
    }

    private class DispatcherApplyHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val dispatcher = chain.thisObject ?: return result
            val active = activeDispatcher?.get()
            if (!receiverRegistered && (active == null || active === dispatcher)) {
                activeDispatcher = WeakReference(dispatcher)
                captureDispatcher(dispatcher)
            }
            return result
        }
    }

    private fun registerReceiver(dispatcher: Any?) {
        if (dispatcher == null) return
        if (receiverRegistered && activeDispatcher?.get() === dispatcher) return

        activeDispatcher = WeakReference(dispatcher)
        receiverRegistered = false
        receiver = null
        try {
            val loader = dispatcher.javaClass.classLoader ?: return
            val receiverClass = loader.loadClass(RECEIVER_CLASS)
            var proxy: Any? = null
            proxy = Proxy.newProxyInstance(
                receiverClass.classLoader ?: loader,
                arrayOf(receiverClass)
            ) { _, method, args ->
                when (method.name) {
                    "onDarkChanged" -> {
                        updateDarkState(args)
                        null
                    }

                    "onLightDarkTintChanged" -> {
                        updateLightDarkState(args)
                        null
                    }

                    "toString" -> "$TAG.DarkReceiver"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> args?.getOrNull(0) === proxy
                    else -> null
                }
            }
            val addReceiver = dispatcher.javaClass.methods.firstOrNull { method ->
                method.name == "addDarkReceiver" && method.parameterCount == 1 &&
                    method.parameterTypes[0].isAssignableFrom(receiverClass)
            } ?: dispatcher.javaClass.declaredMethods.firstOrNull { method ->
                method.name == "addDarkReceiver" && method.parameterCount == 1
            } ?: throw NoSuchMethodException("addDarkReceiver")

            addReceiver.isAccessible = true
            addReceiver.invoke(dispatcher, proxy)
            receiver = proxy
            receiverRegistered = true
            HookLogger.d(TAG, "DarkIconDispatcher DarkReceiver 已注册")
        } catch (e: Exception) {
            HookLogger.w(TAG, "注册 DarkReceiver 失败，改用 applyIconTint 字段读取: ${e.message}")
            captureDispatcher(dispatcher)
        }
    }

    private fun updateDarkState(args: Array<out Any?>?) {
        @Suppress("UNCHECKED_CAST")
        (args?.getOrNull(0) as? List<Rect>)?.let { tintAreas = it }
        (args?.getOrNull(1) as? Number)?.let { darkIntensity = it.toFloat() }
        (args?.getOrNull(2) as? Number)?.let { iconTint = it.toInt() }
        publishEffectiveTint()
    }

    private fun updateLightDarkState(args: Array<out Any?>?) {
        (args?.getOrNull(0) as? Number)?.let { lightModeIconColor = it.toInt() }
        (args?.getOrNull(1) as? Number)?.let { darkModeIconColor = it.toInt() }
        (args?.getOrNull(2) as? Boolean)?.let { useTint = it }
        publishEffectiveTint()
    }

    private fun captureDispatcher(dispatcher: Any) {
        try {
            val fields = dispatcherFieldCache.getOrPut(dispatcher.javaClass) {
                arrayOf(
                    "mTintAreas",
                    "mDarkIntensity",
                    "mIconTint",
                    "mLightModeIconColorSingleTone",
                    "mDarkModeIconColorSingleTone",
                    "mUseTint"
                ).map { findField(dispatcher.javaClass, it) }.toTypedArray()
            }
            @Suppress("UNCHECKED_CAST")
            (fields[0]?.get(dispatcher) as? List<Rect>)?.let { tintAreas = it }
            (fields[1]?.get(dispatcher) as? Number)?.let { darkIntensity = it.toFloat() }
            (fields[2]?.get(dispatcher) as? Number)?.let { iconTint = it.toInt() }
            (fields[3]?.get(dispatcher) as? Number)?.let { lightModeIconColor = it.toInt() }
            (fields[4]?.get(dispatcher) as? Number)?.let { darkModeIconColor = it.toInt() }
            (fields[5]?.get(dispatcher) as? Boolean)?.let { useTint = it }
            publishEffectiveTint()
        } catch (e: Exception) {
            HookLogger.w(TAG, "读取 DarkIconDispatcherImpl 状态失败: ${e.message}")
        }
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).also { it.isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun publishEffectiveTint() {
        val areas = tintAreas
        val centerX = Resources.getSystem().displayMetrics.widthPixels / 2
        val centerCovered = areas.isEmpty() || areas.any { area ->
            area.top <= 0 && centerX >= area.left && centerX < area.right
        }
        val color = when {
            !centerCovered -> if (useTint) Color.WHITE else lightModeIconColor
            useTint -> iconTint
            darkIntensity > 0f -> darkModeIconColor
            else -> lightModeIconColor
        }
        updateTextColor(color)
    }

    private fun updateTextColor(color: Int) {
        if (textColor == color) return
        textColor = color
        if (followStatusBarEnabled) scheduleDispatch()
    }

    private fun scheduleDispatch() {
        if (!frameScheduled.compareAndSet(false, true)) return
        mainHandler.post { Choreographer.getInstance().postFrameCallback(frameCallback) }
    }

}
