package com.lidesheng.hyperlyric.root.island.hooks

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Adds previous/next track gestures to the currently visible media island.
 *
 * Xiaomi still owns hit testing, direction detection and the follow animation. We remember which
 * summary big island was touched and add a track-switch command only for a short gesture below
 * Xiaomi's native swipe threshold. A long swipe remains fully native.
 */
internal object IslandMediaSwipeHooker {
    private const val TAG = "IslandMediaSwipeHooker"

    // Xiaomi's 17.1.4.x resource is 50dp. Read the live StateFlow when possible, and use this
    // value only as a compatibility fallback for a SystemUI variant with a different field shape.
    private const val FALLBACK_SWIPE_THRESHOLD_DP = 50f

    private const val TOUCH_INTERACTOR_CLASS =
        "miui.systemui.dynamicisland.touch.domain.interactor.DynamicIslandTouchInteractor"
    private val gestures = WeakHashMap<Any, GestureState>()

    fun hook(module: XposedModule, cl: ClassLoader) {
        try {
            val touchInteractorClass = cl.loadClass(TOUCH_INTERACTOR_CLASS)

            val onInterceptTouchEvent = touchInteractorClass.declaredMethods.firstOrNull {
                it.name == "onInterceptTouchEvent" &&
                        it.parameterTypes.contentEquals(
                            arrayOf(MotionEvent::class.java, String::class.java)
                        ) &&
                        it.returnType == Boolean::class.javaObjectType
            } ?: throw NoSuchMethodException("$TOUCH_INTERACTOR_CLASS.onInterceptTouchEvent")

            val onTouchEvent = touchInteractorClass.declaredMethods.firstOrNull {
                it.name == "onTouchEvent" &&
                        it.parameterTypes.contentEquals(
                            arrayOf(MotionEvent::class.java, String::class.java)
                        ) &&
                        it.returnType == Boolean::class.javaObjectType
            } ?: throw NoSuchMethodException("$TOUCH_INTERACTOR_CLASS.onTouchEvent")

            listOf(onInterceptTouchEvent, onTouchEvent).forEach { method ->
                method.isAccessible = true
                module.deoptimize(method)
            }
            module.hook(onInterceptTouchEvent).intercept(InterceptTouchHook())
            module.hook(onTouchEvent).intercept(TouchEventHook())

            HookLogger.i(TAG, "媒体超级岛横滑切歌 Hook 已初始化")
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "未找到媒体超级岛横滑依赖，跳过切歌 Hook: reason=${e.message}")
        } catch (e: NoSuchMethodException) {
            HookLogger.w(TAG, "未找到媒体超级岛横滑方法，跳过切歌 Hook: reason=${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "安装媒体超级岛横滑切歌 Hook 失败", e)
        }
    }

    private class InterceptTouchHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val interactor = chain.thisObject
            val event = chain.args.getOrNull(0) as? MotionEvent
            val action = event?.actionMasked
            val result = chain.proceed()

            if (interactor == null || event == null) return result
            when (action) {
                MotionEvent.ACTION_DOWN ->
                    GestureTracker.begin(interactor, event.getX(), event.getY())

                MotionEvent.ACTION_CANCEL -> GestureTracker.clear(interactor)
            }
            return result
        }
    }

    private class TouchEventHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val interactor = chain.thisObject
            val event = chain.args.getOrNull(0) as? MotionEvent
            val action = event?.actionMasked

            if (interactor == null || event == null) {
                return chain.proceed()
            }

            if (action == MotionEvent.ACTION_UP) {
                GestureTracker.markUp(interactor, event.getX(), event.getY())
            }

            return try {
                val result = chain.proceed()
                if (action == MotionEvent.ACTION_UP) {
                    GestureTracker.takeForCustomSwipe(interactor)?.let(::performShortSwipe)
                }
                result
            } finally {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    GestureTracker.clear(interactor)
                }
            }
        }
    }

    private object GestureTracker {
        fun begin(interactor: Any, downX: Float, downY: Float) {
            val state = runCatching { buildGestureState(interactor, downX, downY) }
                .getOrNull()
                ?: return
            synchronized(gestures) {
                gestures[interactor] = state
            }
        }

        fun markUp(interactor: Any, x: Float, y: Float) {
            synchronized(gestures) {
                val state = gestures[interactor] ?: return
                if (runCatching { readBoolean(interactor, "longClickReceived") }
                        .getOrNull() != false
                ) {
                    // A long press owns this gesture. Do not let a later native ACTION_UP
                    // accidentally become a track-switch command.
                    gestures.remove(interactor)
                    return
                }
                state.finalDx = x - state.downX
                state.finalDy = y - state.downY
            }
        }

        fun takeForCustomSwipe(interactor: Any): GestureState? {
            synchronized(gestures) {
                val state = gestures.remove(interactor) ?: return null
                val dx = state.finalDx
                val dy = state.finalDy
                if (!dx.isFinite() || !dy.isFinite() || dx == 0f) return null

                val absDx = abs(dx)
                if (absDx <= abs(dy) || absDx <= state.nativeTouchSlop) return null

                // Xiaomi's final hide/promote event starts at this threshold. Only gestures below
                // it belong to the custom track-switch action.
                if (!state.nativeSwipeThreshold.isFinite() ||
                    absDx >= state.nativeSwipeThreshold
                ) {
                    return null
                }
                return state
            }
        }

        fun clear(interactor: Any) {
            synchronized(gestures) {
                gestures.remove(interactor)
            }
        }
    }

    private class GestureState(
        val windowView: Any,
        val targetData: Any,
        val nativeTouchSlop: Float,
        val nativeSwipeThreshold: Float,
        val downX: Float,
        val downY: Float
    ) {
        var finalDx: Float = Float.NaN
        var finalDy: Float = Float.NaN
    }

    private fun buildGestureState(
        interactor: Any,
        downX: Float,
        downY: Float
    ): GestureState? {
        val windowView = readField(interactor, "windowView") ?: return null
        val targetData = findMediaTouchTarget(interactor, windowView) ?: return null

        return GestureState(
            windowView = windowView,
            targetData = targetData,
            nativeTouchSlop = resolveTouchSlop(interactor, windowView),
            nativeSwipeThreshold = resolveSwipeThreshold(interactor, windowView),
            downX = downX,
            downY = downY
        )
    }

    private fun performShortSwipe(gesture: GestureState) {
        try {
            val dx = gesture.finalDx
            val absDx = abs(dx)
            val context = (gesture.windowView as? View)?.context ?: return
            val controller = IslandPlaybackControllerResolver.resolveForSwipe(context, gesture.targetData)
            val isNext = dx < 0f
            val skipped = if (controller != null) {
                runCatching {
                    if (isNext) {
                        controller.transportControls.skipToNext()
                    } else {
                        controller.transportControls.skipToPrevious()
                    }
                    true
                }.getOrDefault(false)
            } else {
                false
            }

            HookLogger.d(
                TAG,
                "媒体岛短距离横滑切歌: direction=${if (isNext) "下一首" else "上一首"}, " +
                        "command=$skipped, distance=${absDx.toInt()}, " +
                        "nativeThreshold=${gesture.nativeSwipeThreshold.toInt()}"
            )
        } catch (e: Exception) {
            HookLogger.w(TAG, "媒体岛短距离横滑处理失败", e)
        }
    }

    private fun findMediaTouchTarget(
        interactor: Any,
        windowView: Any
    ): Any? {
        // Keep native media-button, seek-bar, freeform-animation and long-press gestures intact.
        if (readBoolean(interactor, "downInSeekBar") != false ||
            readBoolean(interactor, "downInMedia") != false ||
            readBoolean(interactor, "downInFreeformAnim") != false ||
            readBoolean(interactor, "longClickReceived") != false
        ) {
            return null
        }

        // The feature belongs only to Xiaomi's summary big island. Small/expanded/show-once
        // targets remain fully native, which is important when another island is promoted after
        // the media island is expanded.
        if (readBoolean(interactor, "downInBigIsland") != true) return null

        val view = callNoArg(windowView, "getCurrentBigIslandState") ?: return null
        val data = IslandProbeUtils.getCurrentIslandData(view) ?: return null
        return data.takeIf { IslandProbeUtils.extractMediaIslandInfo(it) != null }
    }

    private fun resolveTouchSlop(interactor: Any, windowView: Any): Float {
        val touchConstants = readField(interactor, "touchConstants")
        val touchSlop = (callNoArg(callNoArg(touchConstants, "getTouchSlop"), "getValue") as? Number)
            ?.toFloat()
            ?.takeIf { it.isFinite() && it > 0f }
        if (touchSlop != null) return touchSlop

        val context = (windowView as? View)?.context ?: return Float.POSITIVE_INFINITY
        return ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }

    private fun resolveSwipeThreshold(interactor: Any, windowView: Any): Float {
        val touchConstants = readField(interactor, "touchConstants")
        val threshold = (callNoArg(callNoArg(touchConstants, "getSwipeThreshold"), "getValue") as? Number)
            ?.toFloat()
            ?.takeIf { it.isFinite() && it > 0f }
        if (threshold != null) return threshold

        val density = (windowView as? View)?.resources?.displayMetrics?.density
            ?.takeIf { it.isFinite() && it > 0f }
            ?: 1f
        return FALLBACK_SWIPE_THRESHOLD_DP * density
    }

    private fun readBoolean(receiver: Any, fieldName: String): Boolean? {
        return readField(receiver, fieldName) as? Boolean
    }

    private fun readField(receiver: Any, fieldName: String): Any? {
        val field = findField(receiver.javaClass, fieldName) ?: return null
        field.isAccessible = true
        return field.get(receiver)
    }

    private fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == fieldName }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun callNoArg(receiver: Any?, name: String): Any? {
        val target = receiver ?: return null
        val method: Method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.isEmpty()
        } ?: return null
        method.isAccessible = true
        return method.invoke(target)
    }
}
