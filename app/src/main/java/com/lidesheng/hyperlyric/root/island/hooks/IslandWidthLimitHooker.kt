package com.lidesheng.hyperlyric.root.island.hooks

import android.content.Context
import android.content.res.Resources
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

private fun ClassLoader.loadClassOrNull(name: String): Class<*>? {
    return runCatching { loadClass(name) }.getOrNull()
}

/**
 * Removes Xiaomi's native maximum-width calculation when explicitly enabled.
 *
 * The phone implementation has three relevant width inputs: the max width supplied to the real
 * island, the display width used to measure both side areas, and an additional clock/battery
 * calculation used when a small island is present. The phone branch replaces all three inputs
 * with the physical display width while keeping Xiaomi's content measurements intact.
 */
internal object IslandWidthLimitHooker {
    private const val TAG = "IslandWidthLimitHooker"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val PHONE_HELPER_CLASS =
        "miui.systemui.dynamicisland.window.content.helpers.DynamicIslandContentViewPhoneHelper"
    private const val DYNAMIC_ISLAND_UTILS_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandUtils"
    private const val COMMON_UTILS_CLASS = "miui.systemui.util.CommonUtils"
    private const val FOLD_UTILS_CLASS = "miui.systemui.util.FoldUtils"
    private const val FOLD_LAYOUT_LARGE_METHOD = "isFoldScreenLayoutLarge"
    private const val SCREEN_WIDTH_METHOD = "getScreenWidth"
    private const val SCREEN_WIDTH_OLD_METHOD = "getScreenWidthOld"

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    /**
     * Native calculateBigIslandWidth() calls DynamicIslandUtils before it calls getMaxWidth(). A
     * ThreadLocal lets the utility hook know which concrete content view initiated the complete
     * calculation, so it cannot accidentally alter Pad or large-fold layout calculations.
     */
    private val activePhoneCalculation = ThreadLocal<View>()

    fun hook(module: XposedModule, cl: ClassLoader) {
        if (!hookedClassLoaders.add(cl)) return

        var installed = 0
        val installedCapabilities = mutableListOf<String>()
        try {
            val widthProvider = DisplayWidthProvider()
            val variantDetector = HostVariantDetector(cl)

            val contentViewClass = cl.loadClassOrNull(CONTENT_VIEW_CLASS)
            val calculateBigIslandWidth = contentViewClass?.methods?.firstOrNull {
                it.name == "calculateBigIslandWidth" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == Void.TYPE
            }
            if (calculateBigIslandWidth != null) {
                calculateBigIslandWidth.isAccessible = true
                module.deoptimize(calculateBigIslandWidth)
                module.hook(calculateBigIslandWidth).intercept(
                    PhoneCalculationScopeHook(variantDetector)
                )
                installed++
                installedCapabilities += "phone-scope"
            } else {
                HookLogger.w(TAG, "未找到 DynamicIslandContentView.calculateBigIslandWidth()")
            }

            val utilsClass = cl.loadClassOrNull(DYNAMIC_ISLAND_UTILS_CLASS)
            val screenWidthMethods = utilsClass?.methods
                ?.filter {
                    it.name == SCREEN_WIDTH_METHOD || it.name == SCREEN_WIDTH_OLD_METHOD
                }
                ?.filter {
                    it.parameterTypes.size == 1 &&
                            Context::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                            (it.returnType == Int::class.javaPrimitiveType ||
                                    it.returnType == Int::class.java)
                }
                ?.distinctBy { method ->
                    method.name + method.parameterTypes.joinToString(",") { type -> type.name }
                }
                .orEmpty()
            screenWidthMethods.forEach { method ->
                method.isAccessible = true
                module.deoptimize(method)
                module.hook(method).intercept(
                    NativeScreenWidthHook(
                        widthProvider = widthProvider,
                        variantDetector = variantDetector,
                        allowUnscopedPhoneFallback = calculateBigIslandWidth == null
                    )
                )
                installed++
                installedCapabilities += method.name
            }
            if (screenWidthMethods.isEmpty()) {
                HookLogger.w(TAG, "未找到 DynamicIslandUtils 屏幕宽度方法")
            }

            val baseContentViewClass = cl.loadClassOrNull(BASE_CONTENT_VIEW_CLASS)
            val getMaxWidth = baseContentViewClass?.declaredMethods?.firstOrNull {
                it.name == "getMaxWidth" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == Float::class.javaPrimitiveType
            }
            if (getMaxWidth != null) {
                getMaxWidth.isAccessible = true
                module.deoptimize(getMaxWidth)
                module.hook(getMaxWidth).intercept(
                    GetMaxWidthHook(
                        widthProvider = widthProvider,
                        variantDetector = variantDetector
                    )
                )
                installed++
                installedCapabilities += "getMaxWidth"
            } else {
                HookLogger.w(TAG, "未找到 DynamicIslandBaseContentView.getMaxWidth()")
            }

            val phoneHelperClass = cl.loadClassOrNull(PHONE_HELPER_CLASS)
            val calculateMaxWidthWithSmall = phoneHelperClass?.declaredMethods?.firstOrNull {
                it.name == "calculateMaxWidthWithSmall" &&
                        it.parameterTypes.size == 2 &&
                        it.parameterTypes[1] == StringBuilder::class.java &&
                        it.returnType == Float::class.javaPrimitiveType
            }
            if (calculateMaxWidthWithSmall != null) {
                calculateMaxWidthWithSmall.isAccessible = true
                module.deoptimize(calculateMaxWidthWithSmall)
                module.hook(calculateMaxWidthWithSmall).intercept(
                    CalculateMaxWidthWithSmallHook(
                        calculationMethod = calculateMaxWidthWithSmall,
                        widthProvider = widthProvider,
                        variantDetector = variantDetector
                    )
                )
                installed++
                installedCapabilities += "phone-small"
            } else {
                HookLogger.w(
                    TAG,
                    "未找到 DynamicIslandContentViewPhoneHelper.calculateMaxWidthWithSmall"
                )
            }

            if (installed > 0) {
                HookLogger.i(
                    TAG,
                    "解除超级岛长度限制 Hook 已初始化: branch=phone, " +
                            "hooks=$installed, capabilities=${installedCapabilities.joinToString(",")}"
                )
            } else {
                hookedClassLoaders.remove(cl)
            }
        } catch (e: Exception) {
            hookedClassLoaders.remove(cl)
            throw e
        }
    }

    /**
     * Re-enters Xiaomi's width calculation for an already-created real island. The preference
     * hook changes what the calculation reads; this call makes an existing instance consume the
     * new value without requiring the island to be destroyed and recreated.
     */
    fun refresh(contentView: Any): Boolean {
        return runCatching {
            val method = contentView.javaClass.methods.firstOrNull {
                it.name == "updateBigIslandViewWidth" && it.parameterTypes.isEmpty()
            } ?: return@runCatching false
            method.isAccessible = true
            method.invoke(contentView)
            true
        }.onFailure { error ->
            HookLogger.w(TAG, "刷新现有超级岛宽度失败", error)
        }.getOrDefault(false)
    }

    private fun isEnabled(): Boolean {
        return runCatching {
            HookEntry.instance?.prefs?.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
                RootConstants.DEFAULT_HOOK_ISLAND_DISABLE_WIDTH_LIMIT
            ) == true
        }.getOrDefault(false)
    }

    /**
     * Returns the largest display-width candidate instead of WindowConfiguration.maxBounds. The
     * latter is the width of the current window/container on this SystemUI build (916px), while
     * the phone's actual root/display width is 1440px.
     */
    private class DisplayWidthProvider {
        @Volatile
        private var cachedContextWidthPx = -1

        @Volatile
        private var cachedSystemWidthPx = -1

        @Volatile
        private var cachedViewWidthPx = -1

        @Volatile
        private var cachedWidth: Float? = null

        fun width(context: Context, viewWidthPx: Int = 0): Float? {
            val contextWidthPx = context.resources.displayMetrics.widthPixels
            val systemWidthPx = Resources.getSystem().displayMetrics.widthPixels
            val previousWidth = cachedWidth
            if (contextWidthPx == cachedContextWidthPx &&
                systemWidthPx == cachedSystemWidthPx &&
                viewWidthPx == cachedViewWidthPx &&
                previousWidth != null
            ) {
                return previousWidth
            }

            val width = maxOf(contextWidthPx, systemWidthPx, viewWidthPx)
                .toFloat()
                .takeIf { it > 0f }
            cachedContextWidthPx = contextWidthPx
            cachedSystemWidthPx = systemWidthPx
            cachedViewWidthPx = viewWidthPx
            cachedWidth = width
            if (width != null) {
                HookLogger.dState(
                    stateId = "$TAG:display-width",
                    tag = TAG,
                    state = "$contextWidthPx/$systemWidthPx/$viewWidthPx/${width.toInt()}"
                ) {
                    "手机分支使用显示宽度: context=$contextWidthPx, system=$systemWidthPx, " +
                            "view=$viewWidthPx, selected=${width.toInt()}"
                }
            }
            return width
        }

        fun systemWidth(): Float? {
            return Resources.getSystem().displayMetrics.widthPixels
                .toFloat()
                .takeIf { it > 0f }
        }
    }

    /**
     * Identifies the active helper rather than guessing from the APK's class list. The same plugin
     * contains Phone and Pad helpers, but only the helper returned by the current content view is
     * the active branch.
     */
    private class HostVariantDetector(classLoader: ClassLoader) {
        private val foldLayoutLargeMethod: Method?
        private val foldUtilsOwner: Any?
        private val tabletMethod: Method?
        private val tabletUtilsOwner: Any?
        private val helperMethods = Collections.synchronizedMap(
            WeakHashMap<Class<*>, Method?>()
        )

        init {
            val foldUtilsClass = classLoader.loadClassOrNull(FOLD_UTILS_CLASS)
            foldLayoutLargeMethod = foldUtilsClass?.methods?.firstOrNull {
                it.name == FOLD_LAYOUT_LARGE_METHOD &&
                        it.parameterTypes.size == 1 &&
                        View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                        (it.returnType == Boolean::class.javaPrimitiveType ||
                                it.returnType == Boolean::class.java)
            }?.apply { isAccessible = true }
            foldUtilsOwner = singletonOwner(foldUtilsClass, foldLayoutLargeMethod)

            val commonUtilsClass = classLoader.loadClassOrNull(COMMON_UTILS_CLASS)
            tabletMethod = commonUtilsClass?.methods?.firstOrNull {
                it.name == "getIS_TABLET" &&
                        it.parameterTypes.isEmpty() &&
                        (it.returnType == Boolean::class.javaPrimitiveType ||
                                it.returnType == Boolean::class.java)
            }?.apply { isAccessible = true }
            tabletUtilsOwner = singletonOwner(commonUtilsClass, tabletMethod)
        }

        fun isPhoneContentView(view: View): Boolean {
            val helper = helperOf(view) ?: return false
            val name = helper.javaClass.name
            return name == PHONE_HELPER_CLASS ||
                    name.endsWith(".DynamicIslandContentViewPhoneHelper")
        }

        fun isLargeFold(view: View): Boolean? {
            val method = foldLayoutLargeMethod ?: return null
            return runCatching {
                method.invoke(foldUtilsOwner, view) as? Boolean
            }.getOrNull()
        }

        fun isLargeFold(params: Any, fallbackView: View?): Boolean? {
            val method = params.javaClass.methods.firstOrNull {
                (it.name == FOLD_LAYOUT_LARGE_METHOD ||
                        it.name == "getIsFoldScreenLayoutLarge") &&
                        it.parameterTypes.isEmpty() &&
                        (it.returnType == Boolean::class.javaPrimitiveType ||
                                it.returnType == Boolean::class.java)
            }?.apply { isAccessible = true }
            val fromParams = runCatching {
                method?.invoke(params) as? Boolean
            }.getOrNull()
            return fromParams ?: fallbackView?.let(::isLargeFold)
        }

        fun isTablet(context: Context): Boolean {
            val reflected: Boolean? = runCatching {
                tabletMethod?.invoke(tabletUtilsOwner) as? Boolean
            }.getOrNull()
            return reflected ?: (context.resources.configuration.smallestScreenWidthDp >= 600)
        }

        private fun helperOf(view: View): Any? {
            val method = synchronized(helperMethods) {
                if (helperMethods.containsKey(view.javaClass)) {
                    helperMethods[view.javaClass]
                } else {
                    view.javaClass.methods.firstOrNull {
                        it.name == "getHelper" && it.parameterTypes.isEmpty()
                    }?.apply { isAccessible = true }.also {
                        helperMethods[view.javaClass] = it
                    }
                }
            }
            return runCatching { method?.invoke(view) }.getOrNull()
        }

        private fun singletonOwner(clazz: Class<*>?, method: Method?): Any? {
            if (clazz == null || method == null || Modifier.isStatic(method.modifiers)) {
                return null
            }
            return runCatching {
                clazz.getDeclaredField("INSTANCE").apply {
                    isAccessible = true
                }.get(null)
            }.getOrNull()
        }
    }

    private class PhoneCalculationScopeHook(
        private val variantDetector: HostVariantDetector
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.thisObject as? View
            if (view == null ||
                !variantDetector.isPhoneContentView(view) ||
                variantDetector.isLargeFold(view) == true
            ) {
                return chain.proceed()
            }

            val previous = activePhoneCalculation.get()
            activePhoneCalculation.set(view)
            return try {
                chain.proceed()
            } finally {
                if (previous == null) {
                    activePhoneCalculation.remove()
                } else {
                    activePhoneCalculation.set(previous)
                }
            }
        }
    }

    private class NativeScreenWidthHook(
        private val widthProvider: DisplayWidthProvider,
        private val variantDetector: HostVariantDetector,
        private val allowUnscopedPhoneFallback: Boolean
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!isEnabled()) return chain.proceed()
            val context = chain.args.getOrNull(0) as? Context ?: return chain.proceed()
            val activeView = activePhoneCalculation.get()
            val shouldOverride = if (activeView != null) {
                variantDetector.isPhoneContentView(activeView) &&
                        variantDetector.isLargeFold(activeView) != true
            } else {
                allowUnscopedPhoneFallback && !variantDetector.isTablet(context)
            }
            if (!shouldOverride) return chain.proceed()

            val width = widthProvider.width(context, activeView?.width ?: 0)
                ?.takeIf { it > 0f }
                ?: return chain.proceed()
            HookLogger.dState(
                stateId = "$TAG:native-screen-width",
                tag = TAG,
                state = "${chain.executable.name}:$width"
            ) {
                "已替换原生屏幕宽度: method=${chain.executable.name}, width=${width.toInt()}"
            }
            return width.toInt()
        }
    }

    private class GetMaxWidthHook(
        private val widthProvider: DisplayWidthProvider,
        private val variantDetector: HostVariantDetector
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!isEnabled()) return chain.proceed()
            val view = chain.thisObject as? View ?: return chain.proceed()
            val isPhone = activePhoneCalculation.get() === view ||
                    variantDetector.isPhoneContentView(view)
            if (!isPhone || variantDetector.isLargeFold(view) == true) {
                return chain.proceed()
            }
            val width = widthProvider.width(view.context, view.width)
                ?.takeIf { it > 0f }
                ?: return chain.proceed()
            return width
        }
    }

    private class CalculateMaxWidthWithSmallHook(
        calculationMethod: Method,
        private val widthProvider: DisplayWidthProvider,
        private val variantDetector: HostVariantDetector
    ) : Hooker {
        private val calculationScreenWidthMethod = calculationMethod.parameterTypes
            .firstOrNull()
            ?.methods
            ?.firstOrNull {
                it.name == "getScreenWidth" && it.parameterTypes.isEmpty()
            }
            ?.apply { isAccessible = true }
        private val helperContextField: Field? = calculationMethod.declaringClass.declaredFields
            .firstOrNull { Context::class.java.isAssignableFrom(it.type) }
            ?.apply { isAccessible = true }

        override fun intercept(chain: Chain): Any? {
            if (!isEnabled()) return chain.proceed()
            val params = chain.args.getOrNull(0) ?: return chain.proceed()
            val helper = chain.thisObject ?: return chain.proceed()
            if (variantDetector.isLargeFold(params, activePhoneCalculation.get()) == true) {
                return chain.proceed()
            }

            val context = runCatching {
                helperContextField?.get(helper) as? Context
            }.getOrNull()
            val width = context?.let(widthProvider::width)
                ?: widthProvider.systemWidth()
                ?: runCatching {
                    calculationScreenWidthMethod?.invoke(params) as? Number
                }.getOrNull()?.toFloat()
            return width?.takeIf { it > 0f } ?: chain.proceed()
        }
    }

}
