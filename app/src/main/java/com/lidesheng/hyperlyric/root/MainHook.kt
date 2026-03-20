package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isEmpty
import com.lidesheng.hyperlyric.utils.AnimUtils
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

object MainHook {
        private const val LEFT_VIEW_TAG = "MY_ISLAND_TITLE_LEFT"
        private const val RIGHT_VIEW_TAG = "MY_ISLAND_TITLE_RIGHT"
        private const val LANDSCAPE_MAX_WIDTH_DP = 185f
        private const val DEFAULT_CAMERA_GAP_PX = 68
        private const val FIXED_X_OFFSET_PX = 71f
        private const val RIGHT_EXTRA_OFFSET_PX = 6f

        private var cachedConfig: ModuleConfig? = null
        private val scrollerMap = WeakHashMap<TextView, MarqueeController>()
        private var mediaTracker: MediaSessionTracker? = null
        private var activeIslandState: ActiveIslandState? = null
        private var cachedCutoutWidthPortrait: Int = -2   // -2 = 未初始化
        private var cachedCutoutWidthLandscape: Int = -2
        private val clipDisabledViews = WeakHashMap<View, Boolean>()
        private val layoutFixedContainers = WeakHashMap<ViewGroup, Boolean>()

        internal lateinit var module: XposedModule
        private var isListenerRegistered = false

        // 灵动岛活跃 View 的弱引用，用于实时推送歌词更新
        private data class ActiveIslandState(
            val tvLeft: WeakReference<TextView>,
            val tvRight: WeakReference<TextView>,
            val bigIslandView: WeakReference<ViewGroup>,
            val pkgName: String
        ) {
            fun isAlive(): Boolean =
                tvLeft.get() != null && tvRight.get() != null && bigIslandView.get() != null
        }

        data class ModuleConfig(
            val size: Int = 13,
            val marquee: Boolean = true,
            val hideNotch: Boolean = false,
            val maxLeftWidth: Int = 240,
            val speed: Int = 100,
            val delay: Int = 1500,
            val animMode: Int = 0,
            val whitelist: Set<String> = emptySet()
        )

        // 记录初始化标记的全局字段名
        private const val FIELD_HOOKED_FACTORY = "HOOKED_FACTORY_HYPERLYRIC"

        fun hookSystemUIDynamicIsland(xposedModule: XposedModule, param: PackageLoadedParam) {
            module = xposedModule
            module.log("[HyperLyric] ★ hookSystemUIDynamicIsland start")
            
            // 首次加载初始化配置
            getSmartConfig()

            try {
                // ... (rest of hook logic remains same)
                // 1. 尝试直接加载（如果类在 base classloader 中，这一步就成功了）
                try {
                    val factoryClass = param.defaultClassLoader.loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
                    doHookFactory(factoryClass)
                } catch (_: ClassNotFoundException) {
                    module.log("[HyperLyric] 目标类尚未加载，将注册 ClassLoader 构造函数 hook 等待动态加载插件")
                    // 2. 对于动态加载的插件类，绝对不能 Hook findClass 或 loadClass（高频造成死机）。
                    // 我们改为 Hook 各种 DexClassLoader 的构造函数。这个动作极少发生（仅加载插件时执行）。
                    val classLoadersToHook = arrayOf(
                        "dalvik.system.BaseDexClassLoader",
                        "dalvik.system.PathClassLoader",
                        "dalvik.system.DexClassLoader",
                        "dalvik.system.DelegateLastClassLoader"
                    )

                    for (clName in classLoadersToHook) {
                        try {
                            val clazz = Class.forName(clName)
                            for (c in clazz.declaredConstructors) {
                                module.deoptimize(c)
                                module.hook(c).intercept(DexClassLoaderHooker())
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                module.log("[HyperLyric] [E] Module injection failed: ${e.message}")
            }
        }

        @Synchronized
        private fun doHookFactory(factoryClass: Class<*>) {
            try {
                // 防止多次注入
                val hookedField = try {
                    factoryClass.classLoader?.javaClass?.getDeclaredField(FIELD_HOOKED_FACTORY)
                        ?.apply { isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    null
                }

                if (hookedField != null && hookedField.get(factoryClass.classLoader) == true) {
                    return
                }
                hookedField?.set(factoryClass.classLoader, true)

                hookFactoryMethodDynamic(factoryClass)
                module.log("[HyperLyric] IslandTemplateFactory hook 完成")
            } catch (e: Exception) {
                module.log("[HyperLyric] [E] doHookFactory 异常: ${e.message}")
            }
        }

        /**
         * Hooker: 拦截 DexClassLoader 的构造函数，初始化完毕后去寻找我们的目标工厂类
         */
        class DexClassLoaderHooker : Hooker {
            override fun intercept(chain: Chain): Any? {
                val result = chain.proceed()
                try {
                    val cl = chain.thisObject as? ClassLoader ?: return result
                    val factoryClass = cl.loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
                    doHookFactory(factoryClass)
                } catch (_: ClassNotFoundException) {
                    // 该类加载器里没有，忽略
                } catch (_: Exception) {}
                return result
            }
        }


        /**
         * Hooker: 拦截 createBigIslandTemplateView，注入歌词 View
         */
        class CreateBigIslandHooker : Hooker {
            @SuppressLint("DiscouragedApi")
            override fun intercept(chain: Chain): Any? {
                val result = chain.proceed()
                try {
                    // 跳过协程恢复调用（suspend 函数恢复时参数全为 null）
                    if (chain.args.getOrNull(0) == null) return result
                    // 跳过 fake 视图（arg[4]=true，用于动画过渡，不需要注入歌词）
                    if (chain.args.getOrNull(4) == true) return result
                    val bigIslandView = result as? ViewGroup ?: return result
                        val context = bigIslandView.context
                        val config = getSmartConfig()


                        if (mediaTracker == null) {
                            mediaTracker = MediaSessionTracker(context).apply {
                                onTitleChanged =
                                    { pkg, title -> onMediaTitleChanged(pkg, title) }
                            }
                        }

                        val extraInfo = chain.args.getOrNull(2)
                        val pkgName = extraInfo?.let { info ->
                            try {
                                val bundle = info.javaClass.getMethod("getExtras")
                                    .invoke(info) as? Bundle
                                bundle?.getString("miui.pkg.name")
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (pkgName == null) return result
                        if (pkgName !in config.whitelist) return result

                        val songTitle = mediaTracker?.getSongTitle(pkgName) ?: ""
                        if (songTitle.isEmpty()) return result
                        if (bigIslandView.isEmpty()) return result

                        val mainRowContainer = bigIslandView.getChildAt(0) as? ViewGroup
                        if (mainRowContainer == null) {
                            module.log("[HyperLyric] [E] bigIslandView 子视图结构异常，无法获取 mainRowContainer")
                            return result
                        }

                        // 针对根节点进行清空权重和宽度的重置
                        fixContainerForWrapContent(bigIslandView)
                        fixContainerForWrapContent(mainRowContainer)

                        if (mainRowContainer.isEmpty()) return result
                        val realAlbumContainer = mainRowContainer.getChildAt(0) as? ViewGroup
                        val areaCutout = bigIslandView.getChildAt(1) as? ViewGroup

                        if (realAlbumContainer != null) fixContainerForWrapContent(realAlbumContainer)
                        if (areaCutout != null) fixContainerForWrapContent(areaCutout)

                        if (realAlbumContainer == null || areaCutout == null) {
                            module.log("[HyperLyric] [E] 视图结构不完整: realAlbumContainer=${realAlbumContainer != null}, areaCutout=${areaCutout != null}")
                            return result
                        }

                        val (tvLeft, tvRight, isNewView) = ensureTextViews(
                            context, config, realAlbumContainer, areaCutout
                        )

                        if (isNewView) {
                            setupLayoutListeners(
                                tvLeft,
                                tvRight,
                                bigIslandView,
                                mainRowContainer,
                                realAlbumContainer,
                                areaCutout
                            )
                        }

                        activeIslandState = ActiveIslandState(
                            WeakReference(tvLeft), WeakReference(tvRight),
                            WeakReference(bigIslandView), pkgName
                        )

                        if (tvLeft.contentDescription == songTitle) return result
                        tvLeft.contentDescription = songTitle
                        applyLyricContent(
                            tvLeft,
                            tvRight,
                            songTitle,
                            config,
                            context,
                            bigIslandView
                        )
                    } catch (e: Exception) {
                        module.log("[HyperLyric] [E] createBigIslandTemplateView hook 异常: ${e.message}")
                    }
                return result
            }
        }

        // 读取由 UI 界面通过普通 SharedPreferences 保存的远程配置文件
        @SuppressLint("WorldReadableFiles")
        internal fun getSmartConfig(): ModuleConfig {
            cachedConfig?.let { return it }
            return try {
                // 使用 libxposed API 从宿主 Module (本 App) 获取共享配置，抛弃 Root
                val prefs = module.getRemotePreferences("com.lidesheng.hyperlyric_preferences")

                // 注册实时监听器，实现免重启更新
                if (!isListenerRegistered) {
                    prefs.registerOnSharedPreferenceChangeListener { p, key ->
                        module.log("[HyperLyric] 远程配置项变更: $key")
                        val newConfig = ModuleConfig(
                            size = p.getInt("key_text_size", cachedConfig?.size ?: 13),
                            marquee = p.getBoolean("key_marquee_mode", cachedConfig?.marquee ?: true),
                            hideNotch = p.getBoolean("key_hide_notch", cachedConfig?.hideNotch ?: false),
                            maxLeftWidth = p.getInt("key_max_left_width", cachedConfig?.maxLeftWidth ?: 240),
                            speed = p.getInt("marquee_speed", cachedConfig?.speed ?: 100),
                            delay = p.getInt("marquee_delay", cachedConfig?.delay ?: 1500),
                            animMode = p.getInt("key_anim_mode", cachedConfig?.animMode ?: 0),
                            whitelist = p.getStringSet("key_whitelist_packages", cachedConfig?.whitelist ?: emptySet()) ?: emptySet()
                        )
                        cachedConfig = newConfig

                        // 立即推送到当前正在显示的 View
                        activeIslandState?.let { state ->
                            if (state.isAlive()) {
                                try {
                                    val tvLeft = state.tvLeft.get() ?: return@let
                                    val tvRight = state.tvRight.get() ?: return@let
                                    val bigIslandView = state.bigIslandView.get() ?: return@let
                                    val context = tvLeft.context
                                    val title = tvLeft.contentDescription?.toString() ?: ""
                                    applyLyricContent(tvLeft, tvRight, title, newConfig, context, bigIslandView)
                                } catch (e: Exception) {
                                    module.log("[HyperLyric] 实时更新 View 失败: ${e.message}")
                                }
                            }
                        }
                    }
                    isListenerRegistered = true
                }

                ModuleConfig(
                    size = prefs.getInt("key_text_size", 13),
                    marquee = prefs.getBoolean("key_marquee_mode", true),
                    hideNotch = prefs.getBoolean("key_hide_notch", false),
                    maxLeftWidth = prefs.getInt("key_max_left_width", 240),
                    speed = prefs.getInt("marquee_speed", 100),
                    delay = prefs.getInt("marquee_delay", 1500),
                    animMode = prefs.getInt("key_anim_mode", 0),
                    whitelist = prefs.getStringSet("key_whitelist_packages", emptySet()) ?: emptySet()
                ).also { cachedConfig = it }
            } catch (e: Exception) {
                module.log("[HyperLyric] [E] RemotePref 解析失败: ${e.message}")
                cachedConfig ?: ModuleConfig()
            }
        }

        // 获取屏幕中央挖孔的宽度（px），按屏幕方向分别缓存
        private fun getNaturalCutoutWidth(view: View): Int {
            val landscape = isLandscape(view.context)
            val cached = if (landscape) cachedCutoutWidthLandscape else cachedCutoutWidthPortrait
            if (cached != -2) return cached
            val rects = view.rootWindowInsets?.displayCutout?.boundingRects ?: return -1
            if (rects.isEmpty()) return -1
            val screenCenter = view.resources.displayMetrics.widthPixels / 2
            val result =
                rects.firstOrNull { abs((it.left + it.right) / 2 - screenCenter) < 200 }?.width()
                    ?: rects[0].width()
            if (landscape) cachedCutoutWidthLandscape = result else cachedCutoutWidthPortrait =
                result
            return result
        }

        private fun calcRealGapPx(view: View): Int {
            val detected = getNaturalCutoutWidth(view)
            return if (detected > 0) detected else DEFAULT_CAMERA_GAP_PX
        }

        private fun calcDynamicOffset(view: View): Float =
            FIXED_X_OFFSET_PX + (calcRealGapPx(view) - DEFAULT_CAMERA_GAP_PX).toFloat()

        private fun disableClipOnParents(view: View, rootParent: View?) {
            if (clipDisabledViews[view] == true) return
            var p = view.parent
            while (p is ViewGroup) {
                p.clipChildren = false
                p.clipToPadding = false
                if (p == rootParent) break
                p = p.parent
            }
            clipDisabledViews[view] = true
        }

        private fun onMediaTitleChanged(pkg: String, newTitle: String) {
            try {
                val state = activeIslandState ?: return
                if (state.pkgName != pkg || !state.isAlive()) return

                val tvLeft = state.tvLeft.get() ?: return
                val tvRight = state.tvRight.get() ?: return
                val bigIslandView = state.bigIslandView.get() ?: return
                val config = getSmartConfig()

                if (newTitle.isEmpty() || tvLeft.contentDescription == newTitle) return

                // 强制在主线程更新，避免 API 101 的事件回调不在主 UI 线程时导致的失效
                tvLeft.post {
                    tvLeft.contentDescription = newTitle
                    applyLyricContent(tvLeft, tvRight, newTitle, config, tvLeft.context, bigIslandView)
                }
            } catch (e: Exception) {
                module.log("[HyperLyric] onMediaTitleChanged 异常: ${e.message}")
            }
        }

        private fun showLeftOnly(tvLeft: TextView, tvRight: TextView) {
            tvRight.text = ""
            tvRight.visibility = View.GONE
            tvLeft.visibility = View.VISIBLE
            tvLeft.bringToFront()
        }

        private fun hookFactoryMethodDynamic(factoryClass: Class<*>) {
            val methods = factoryClass.declaredMethods
            val targetMethod = methods.firstOrNull { it.name == "createBigIslandTemplateView" }

            if (targetMethod == null) {
                module.log("[HyperLyric] [E] 未找到 createBigIslandTemplateView 方法，可能系统版本不兼容")
                module.log("[HyperLyric] 当前工厂类可用方法: ${methods.joinToString { it.name }}")
                return
            }

            module.deoptimize(targetMethod)
            module.hook(targetMethod).intercept(CreateBigIslandHooker())
        }

        private fun fixContainerForWrapContent(container: ViewGroup) {
            if (layoutFixedContainers[container] == true) return

            if (container is LinearLayout) {
                if (container.gravity != (Gravity.START or Gravity.CENTER_VERTICAL)) {
                    container.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val lp = child.layoutParams
                if (lp != null) {
                    var changed = false
                    if (lp is LinearLayout.LayoutParams && lp.weight > 0) {
                        lp.weight = 0f
                        changed = true
                    }
                    if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        changed = true
                    }
                    if (changed) {
                        child.layoutParams = lp
                    }
                }
            }
            layoutFixedContainers[container] = true
        }

        private data class TextViewPair(val left: TextView, val right: TextView, val isNew: Boolean)

        private fun ensureTextViews(
            context: Context, config: ModuleConfig,
            leftContainer: ViewGroup, rightContainer: ViewGroup
        ): TextViewPair {
            var isNew = false
            val tvLeft = leftContainer.findViewWithTag(LEFT_VIEW_TAG)
                ?: createBaseTextView(context, LEFT_VIEW_TAG, config.size).also {
                    leftContainer.addView(it); isNew = true
                }
            val tvRight = rightContainer.findViewWithTag(RIGHT_VIEW_TAG)
                ?: createBaseTextView(context, RIGHT_VIEW_TAG, config.size).also {
                    rightContainer.addView(it); isNew = true
                }
            return TextViewPair(tvLeft, tvRight, isNew)
        }

        private fun setupLayoutListeners(
            tvLeft: TextView, tvRight: TextView,
            bigIslandView: ViewGroup, mainRowContainer: ViewGroup,
            realAlbumContainer: ViewGroup, areaCutout: ViewGroup
        ) {
            arrayOf(mainRowContainer, realAlbumContainer, areaCutout).forEach {
                it.elevation = 1000f
                it.translationZ = 1000f
            }

            tvLeft.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                try {
                    val leftView = v as TextView
                    val rootParent = bigIslandView.parent as? View
                    disableClipOnParents(leftView, rootParent)
                    disableClipOnParents(tvRight, rootParent)

                    if (isLandscape(leftView.context)) {
                        leftView.translationX =
                            (calcRealGapPx(leftView) - DEFAULT_CAMERA_GAP_PX).toFloat()
                    } else {
                        leftView.translationX = calcDynamicOffset(leftView)
                    }
                    leftView.translationY = 0f
                } catch (e: Exception) {
                    module.log("[HyperLyric] tvLeft layout 异常: ${e.message}")
                }
            }

            tvRight.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                try {
                    v.translationX = calcDynamicOffset(v) + RIGHT_EXTRA_OFFSET_PX
                    v.translationY = 0f
                } catch (e: Exception) {
                    module.log("[HyperLyric] tvRight layout 异常: ${e.message}")
                }
            }
        }

        private fun applyLyricContent(
            tvLeft: TextView, tvRight: TextView,
            songTitle: String, config: ModuleConfig,
            context: Context, bigIslandView: ViewGroup
        ) {
            try {
                stopMarquee(tvLeft)
                stopMarquee(tvRight)

                tvLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.size.toFloat())
                tvRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.size.toFloat())

                val paint = tvLeft.paint
                val totalTextWidthPx = paint.measureText(songTitle)
                val realGapPx = calcRealGapPx(tvLeft)
                val dynamicOffset = calcDynamicOffset(tvLeft)

                tvLeft.setPadding(0, 0, 0, 0)
                (tvLeft.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                    it.rightMargin = 0
                    tvLeft.layoutParams = it
                }
                disableClipOnParents(tvLeft, bigIslandView.parent as? View)

                when {
                    config.hideNotch -> applyHideNotchMode(
                        tvLeft,
                        tvRight,
                        songTitle,
                        config,
                        context,
                        totalTextWidthPx,
                        realGapPx,
                        dynamicOffset
                    )

                    isLandscape(context) -> applyLandscapeMode(
                        tvLeft,
                        tvRight,
                        songTitle,
                        config,
                        context,
                        totalTextWidthPx
                    )

                    else -> applyPortraitSplitMode(
                        tvLeft,
                        tvRight,
                        songTitle,
                        config,
                        paint,
                        totalTextWidthPx,
                        realGapPx,
                        dynamicOffset
                    )
                }
            } catch (e: Exception) {
                module.log("[HyperLyric] applyLyricContent 异常: ${e.message}")
            }
        }

        // 向上层遍历 SystemUI 的根容器，寻找诸如刷新位置/测量/重绘弹簧边界等内置的方法来破解它的宽度缓存不变问题。
        private fun notifyIslandBoundsUpdate(view: View) {
            view.post {
                var current: View? = view
                var rootParent: View? = null

                // 向上寻找灵动岛相关的控制 ViewGroup
                while (current != null) {
                    current.requestLayout()

                    val className = current.javaClass.name.lowercase()
                    if (className.contains("systemui") || className.contains("dynamicisland")) {
                        rootParent = current
                    }
                    if (current.parent is View) {
                        current = current.parent as View
                    } else {
                        break
                    }
                }

                // 针对捕捉到的可能是 Island 的顶层 ViewGroup 施加魔法
                rootParent?.let { target ->
                    target.requestLayout()
                    target.invalidate()
                    target.forceLayout()
                    try {
                        // 常见的方法名探索调用，一旦有一条命中就可能打断弹簧缓存
                        val methodsToTry = arrayOf(
                            "updateWindowBounds",
                            "updateLayout",
                            "updateBounds",
                            "refreshLayout",
                            "animateBounds"
                        )
                        for (methodName in methodsToTry) {
                            try {
                                val method = target.javaClass.getDeclaredMethod(methodName)
                                method.isAccessible = true
                                method.invoke(target)
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        module.log("[HyperLyric] 刷新弹簧边界异常: ${e.message}")
                    }
                }
            }
        }

        private fun applyHideNotchMode(
            tvLeft: TextView, tvRight: TextView,
            title: String, config: ModuleConfig, context: Context,
            totalWidthPx: Float, realGapPx: Int, offset: Float
        ) {
            showLeftOnly(tvLeft, tvRight)
            val maxLeftWidthPx = config.maxLeftWidth
            val halfOverflow = (totalWidthPx / 2) > (maxLeftWidthPx - realGapPx)

            val containerLength = if (halfOverflow) maxLeftWidthPx
            else ((totalWidthPx.toInt() + realGapPx) / 2 - dp2px(context, 2f))

            val actualViewWidth =
                if (halfOverflow) (containerLength * 2 - realGapPx + dp2px(context, 4f))
                else ceil(totalWidthPx).toInt()

            tvLeft.layoutParams = tvLeft.layoutParams.apply {
                width = actualViewWidth
                (this as? ViewGroup.MarginLayoutParams)?.rightMargin =
                    containerLength - actualViewWidth
            }

            AnimUtils.applyTextWithAnim(tvLeft, title, config.animMode, offset) {
                if (config.marquee) startMarquee(tvLeft, config)
                else tvLeft.ellipsize = TextUtils.TruncateAt.END
            }
        }

        private fun applyLandscapeMode(
            tvLeft: TextView, tvRight: TextView,
            title: String, config: ModuleConfig, context: Context,
            totalWidthPx: Float
        ) {
            showLeftOnly(tvLeft, tvRight)
            val maxLandWidthPx = dp2px(context, LANDSCAPE_MAX_WIDTH_DP)
            val containerWidth =
                if (totalWidthPx < maxLandWidthPx) ceil(totalWidthPx).toInt() else maxLandWidthPx

            tvLeft.layoutParams = tvLeft.layoutParams.apply {
                width = containerWidth
                (this as? ViewGroup.MarginLayoutParams)?.rightMargin = 0
            }

            val landscapeOffset = (calcRealGapPx(tvLeft) - DEFAULT_CAMERA_GAP_PX).toFloat()
            AnimUtils.applyTextWithAnim(tvLeft, title, config.animMode, landscapeOffset) {
                startMarquee(tvLeft, config)
            }
        }

        private fun applyPortraitSplitMode(
            tvLeft: TextView, tvRight: TextView,
            title: String, config: ModuleConfig,
            paint: android.graphics.Paint, totalWidthPx: Float,
            realGapPx: Int, offset: Float
        ) {
            val maxLeftPx = config.maxLeftWidth.toFloat()
            val leftCapPx = minOf(totalWidthPx / 2f, maxLeftPx)
            var splitIndex = paint.breakText(title, true, leftCapPx, null)

            if (splitIndex < title.length - splitIndex && splitIndex + 1 <= title.length) {
                if (paint.measureText(title, 0, splitIndex + 1) <= maxLeftPx) splitIndex++
            }
            splitIndex = splitIndex.coerceIn(0, title.length)

            splitIndex = adjustForWordBoundary(title, splitIndex, paint, maxLeftPx)

            val strLeft = title.take(splitIndex)
            val strRight = title.substring(splitIndex)
            val leftTextRawW = ceil(paint.measureText(strLeft)).toInt()
            val rightTextRawW = ceil(paint.measureText(strRight)).toInt()

            val rightDisplayWidth = if (rightTextRawW > config.maxLeftWidth) {
                val fitCount = paint.breakText(strRight, true, maxLeftPx, null)
                ceil(paint.measureText(strRight, 0, fitCount)).toInt()
            } else rightTextRawW

            val leftContentW = maxOf(leftTextRawW, rightDisplayWidth)
            tvLeft.layoutParams = tvLeft.layoutParams.apply { width = leftContentW + realGapPx }
            tvLeft.setPadding(0, 0, realGapPx, 0)
            tvRight.layoutParams = tvRight.layoutParams.apply { width = rightDisplayWidth }

            tvLeft.ellipsize = TextUtils.TruncateAt.END
            tvRight.ellipsize = if (config.marquee) TextUtils.TruncateAt.END else null

            AnimUtils.applyTextWithAnim(tvLeft, strLeft, config.animMode, offset) { }
            AnimUtils.applyTextWithAnim(
                tvRight,
                strRight,
                config.animMode,
                offset + RIGHT_EXTRA_OFFSET_PX
            ) {
                if (config.marquee && rightTextRawW > rightDisplayWidth) startMarquee(
                    tvRight,
                    config
                )
            }

            tvLeft.visibility = View.VISIBLE
            tvRight.visibility = View.VISIBLE
            tvLeft.bringToFront()
            tvRight.bringToFront()
        }

        private fun adjustForWordBoundary(
            text: String, originalIndex: Int,
            paint: android.graphics.Paint, maxLeftPx: Float
        ): Int {
            var idx = originalIndex
            if (idx <= 0 || idx >= text.length) return idx.coerceIn(0, text.length)

            val isAsciiAlnum = { c: Char -> c.isLetterOrDigit() && c.code < 128 }
            if (!isAsciiAlnum(text[idx - 1]) || !isAsciiAlnum(text[idx])) return idx

            var backSplit = idx
            while (backSplit > 0 && isAsciiAlnum(text[backSplit - 1])) backSplit--

            var forwardSplit = idx
            while (forwardSplit < text.length && isAsciiAlnum(text[forwardSplit])) forwardSplit++

            val forwardPx = paint.measureText(text, 0, forwardSplit)

            idx = if (backSplit > 0) {
                if (forwardPx <= maxLeftPx) forwardSplit else backSplit
            } else {
                if (forwardPx <= maxLeftPx) forwardSplit
                else paint.breakText(text, true, maxLeftPx, null)
            }
            return idx.coerceIn(0, text.length)
        }

        private fun startMarquee(textView: TextView, config: ModuleConfig) {
            textView.ellipsize = null
            textView.isSelected = false
            stopMarquee(textView)
            textView.post {
                if (textView.paint.measureText(textView.text.toString()) > textView.width) {
                    MarqueeController(textView, config.speed, config.delay).also {
                        scrollerMap[textView] = it
                        it.start()
                    }
                }
            }
        }

        private fun stopMarquee(textView: TextView) {
            scrollerMap.remove(textView)?.stop()
            textView.scrollTo(0, 0)
        }

        class MarqueeController(
            private val view: TextView,
            private val speedPxPerSec: Int,
            private val delayMs: Int
        ) : Choreographer.FrameCallback {

            private companion object {
                const val PAUSE_AT_END_MS = 1000
            }

            private var currentScrollX = 0f
            private var isRunning = false
            private var startTimeNanos = 0L
            private var lastFrameTimeNanos = 0L
            private val choreographer = Choreographer.getInstance()
            private var state = 0

            private var cachedTextWidth = 0f
            private var cachedMaxScroll = 0f

            fun start() {
                if (isRunning) return
                cachedTextWidth = view.paint.measureText(view.text.toString())
                cachedMaxScroll = max(0f, cachedTextWidth - view.width.toFloat())

                if (cachedMaxScroll <= 0) return

                isRunning = true
                currentScrollX = 0f
                state = 0
                startTimeNanos = 0
                choreographer.postFrameCallback(this)
            }

            fun stop() {
                isRunning = false
                choreographer.removeFrameCallback(this)
            }

            override fun doFrame(frameTimeNanos: Long) {
                if (!isRunning) return

                if (startTimeNanos == 0L) {
                    startTimeNanos = frameTimeNanos
                    lastFrameTimeNanos = frameTimeNanos
                }

                if (cachedMaxScroll <= 0) {
                    stop(); return
                }

                val elapsedMs = { (frameTimeNanos - startTimeNanos) / 1_000_000 }

                when (state) {
                    0 -> if (elapsedMs() >= delayMs) {
                        state = 1; lastFrameTimeNanos = frameTimeNanos
                    }

                    1 -> {
                        currentScrollX += speedPxPerSec * ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                        if (currentScrollX >= cachedMaxScroll) {
                            currentScrollX = cachedMaxScroll; state = 2; startTimeNanos =
                                frameTimeNanos
                        }
                        view.scrollTo(currentScrollX.toInt(), 0)
                    }

                    2 -> if (elapsedMs() > PAUSE_AT_END_MS) {
                        currentScrollX = 0f; view.scrollTo(0, 0); state = 0; startTimeNanos =
                            frameTimeNanos
                    }
                }

                lastFrameTimeNanos = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }

        private fun createBaseTextView(
            context: Context,
            viewTag: String,
            textSizeSp: Int
        ): TextView =
            TextView(context).apply {
                tag = viewTag
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp.toFloat())
                includeFontPadding = false
                isSingleLine = true
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                ellipsize = null
                isSelected = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }
                elevation = 2000f
                translationZ = 2000f
            }

        private class MediaSessionTracker(context: Context) {
            private val manager =
                context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

            // 记录所有活跃的控制器及其对应的独立回调
            private val trackedControllers = mutableMapOf<MediaController, MediaController.Callback>()

            private val cachedTitles = mutableMapOf<String, String>()

            var onTitleChanged: ((String, String) -> Unit)? = null

            init {
                try {
                    manager.addOnActiveSessionsChangedListener(
                        { onActiveSessionsChanged(it) },
                        null
                    )
                    refreshActiveSessions()
                } catch (e: Exception) {
                    module.log("[HyperLyric] [E] MediaSessionTracker init 异常: ${e.message}，媒体监听失败")
                }
            }

            private fun refreshActiveSessions() {
                try {
                    onActiveSessionsChanged(manager.getActiveSessions(null))
                } catch (e: Exception) {
                    module.log("[HyperLyric] [W] 刷新媒体会话失败: ${e.message}")
                }
            }

            // 为每个活跃的 Controller 创建专属回调
            private fun createCallback(controller: MediaController): MediaController.Callback {
                return object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        try {
                            updateCachedTitle(controller)
                        } catch (e: Exception) {
                            module.log("[HyperLyric] onMetadataChanged 异常: ${e.message}")
                        }
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        try {
                            updateCachedTitle(controller)
                        } catch (e: Exception) {
                            module.log("[HyperLyric] onPlaybackStateChanged 异常: ${e.message}")
                        }
                    }

                    override fun onSessionDestroyed() {
                        try {
                            refreshActiveSessions()
                        } catch (e: Exception) {
                            module.log("[HyperLyric] onSessionDestroyed 异常: ${e.message}")
                        }
                    }
                }
            }

            private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
                if (controllers == null) return

                val currentSessions = controllers.toSet()

                // 移除已经无效的追踪器
                val toRemove = trackedControllers.keys.filter { it !in currentSessions }
                for (deadController in toRemove) {
                    try {
                        val callback = trackedControllers.remove(deadController)
                        if (callback != null) deadController.unregisterCallback(callback)
                    } catch (_: Exception) {}
                }

                // 注册尚未追踪的存活控制器
                for (activeController in currentSessions) {
                    if (!trackedControllers.containsKey(activeController)) {
                        try {
                            val newCallback = createCallback(activeController)
                            activeController.registerCallback(newCallback)
                            trackedControllers[activeController] = newCallback
                            // 初始抓取一次歌名
                            updateCachedTitle(activeController)
                        } catch (_: Exception) {}
                    }
                }
            }

            private fun updateCachedTitle(controller: MediaController?) {
                controller ?: return
                val pkg = controller.packageName ?: return
                val newTitle = (controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: "").substringBefore("\n")
                val oldTitle = cachedTitles[pkg]
                cachedTitles[pkg] = newTitle

                if (oldTitle != newTitle && newTitle.isNotEmpty()) {
                    onTitleChanged?.invoke(pkg, newTitle)
                }
            }

            fun getSongTitle(targetPkg: String): String {
                cachedTitles[targetPkg]?.let { if (it.isNotEmpty()) return it }
                return try {
                    val sessions = manager.getActiveSessions(null)
                    // 优先找正在播放状态的指定包名，没有的话就找同包名的第一个
                    val targetSession = sessions.firstOrNull {
                        it.packageName == targetPkg && it.playbackState?.state == PlaybackState.STATE_PLAYING
                    } ?: sessions.firstOrNull { it.packageName == targetPkg }

                    targetSession?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?.substringBefore("\n")
                        ?.also { if (it.isNotEmpty()) cachedTitles[targetPkg] = it }
                        ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    private fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun dp2px(context: Context, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
