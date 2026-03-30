package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.lidesheng.hyperlyric.Constants
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import androidx.core.graphics.toColorInt
import io.github.proify.lyricon.lyric.view.yoyo.animateUpdate
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets

object HookIslandLyric {
    internal lateinit var module: XposedModule
    private val TARGET_PACKAGES = arrayOf("com.android.systemui", "miui.systemui.plugin")

    @Volatile
    private var isHookedSuccess = false

    private const val TAG_LEFT = "hyperlyric_injected_left"
    private const val TAG_RIGHT = "hyperlyric_injected_right"

    private val activeIslandPkgNames = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, String>())

    fun hookSystemUIDynamicIsland(xposedModule: XposedModule, param: PackageLoadedParam) {
        module = xposedModule
        try {
            val classLoadersToHook = arrayOf("dalvik.system.BaseDexClassLoader", "dalvik.system.PathClassLoader", "dalvik.system.DexClassLoader")
            for (clName in classLoadersToHook) {
                try {
                    val clazz = Class.forName(clName)
                    for (c in clazz.declaredConstructors) {
                        module.deoptimize(c)
                        module.hook(c).intercept(DexClassLoaderHooker())
                    }
                } catch (_: Exception) {}
            }
            try {
                doHookRealContentViews(param.defaultClassLoader)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            module.log(Log.ERROR, "HyperLyric", "hookSystemUIDynamicIsland 异常: ${e.message}")
        }
    }

    @Synchronized
    private fun doHookRealContentViews(cl: ClassLoader) {
        if (isHookedSuccess) return
        var hooked = false
        val classesToHook = arrayOf(
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
        )
        for (className in classesToHook) {
            try {
                val clazz = cl.loadClass(className)
                val targetMethod = clazz.declaredMethods.firstOrNull { it.name == "updateBigIslandView" }
                if (targetMethod != null) {
                    module.deoptimize(targetMethod)
                    module.hook(targetMethod).intercept(UpdateBigIslandHooker())
                    hooked = true
                }
            } catch (_: Exception) {
            }
        }

        try {
            val baseClass = cl.loadClass("miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView")
            val animMethod = baseClass.declaredMethods.firstOrNull { it.name == "updateBigIslandLayoutWithAnim" }
            if (animMethod != null) {
                module.deoptimize(animMethod)
                module.hook(animMethod).intercept(UpdateIslandAnimHooker())
                hooked = true
            }
        } catch (_: Exception) {
        }

        if (hooked) {
            isHookedSuccess = true
        }
    }

    class DexClassLoaderHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (isHookedSuccess) return result
            try {
                val cl = chain.thisObject as? ClassLoader ?: return result
                doHookRealContentViews(cl)
            } catch (_: Exception) {}
            return result
        }
    }

    // =====================================================================
    // Hook 1: updateBigIslandView 的 after-hook
    // 职责：仅注入/移除自定义文字内容
    // 禁止：修改 layoutParams.width、调用 calculateBigIslandWidth、forceMeasure
    // =====================================================================
    class UpdateBigIslandHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            try {
                val islandView = chain.thisObject as? ViewGroup ?: return result
                val islandData = chain.args.getOrNull(0)

                var pkgName = ""
                var systemTitle = ""
                try {
                    if (islandData != null) {
                        val getExtrasMethod = islandData.javaClass.getMethod("getExtras")
                        val extras = getExtrasMethod.invoke(islandData) as? android.os.Bundle
                        pkgName = extras?.getString("miui.pkg.name") ?: ""
                        systemTitle = extras?.getString("miui.title") ?: ""
                    }
                } catch (_: Exception) {}

                if (pkgName.isNotEmpty()) {
                    activeIslandPkgNames[islandView] = pkgName
                    if (LyriconDataBridge.activePackageName == pkgName && LyriconDataBridge.currentSongName.isNullOrBlank() && systemTitle.isNotEmpty()) {
                         LyriconDataBridge.currentSongName = systemTitle
                    }
                } else {
                    pkgName = activeIslandPkgNames[islandView] ?: ""
                }
                if (pkgName.isEmpty()) return result

                // ★ 关键：只处理我们正在追踪的音乐包名，不干涉其他 APP 的灵动岛
                val lyriconPkg = LyriconDataBridge.activePackageName
                if (lyriconPkg != pkgName) return result

                val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                val islandLength = prefs.getInt(Constants.KEY_MAX_LEFT_WIDTH, Constants.DEFAULT_MAX_LEFT_WIDTH)
                val songName = LyriconDataBridge.currentSongName ?: systemTitle
                val lyricText = LyriconDataBridge.currentLyric

                if (LyriconDataBridge.isPlaying && !lyricText.isNullOrBlank()) {
                    injectTextToIsland(islandView, songName, lyricText, pkgName, islandLength)
                } else {
                    // 暂停或歌词未到：仅隐藏自定义视图，不碰系统宽度
                    clearTextFromIsland(islandView, islandView.context.resources)
                }
                // ★ 不调用 calculateBigIslandWidth —— 系统刚才已经自己算过了

            } catch (_: Exception) {}
            return result
        }
    }

    // =====================================================================
    // Hook 2: updateBigIslandLayoutWithAnim 的 before-hook
    // 职责：在播放时覆写动画目标宽度字段，暂停时完全放行让系统原生值生效
    // =====================================================================
    class UpdateIslandAnimHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            try {
                // arg [4] is DynamicIslandContentView
                val islandView = chain.args.getOrNull(4) as? ViewGroup

                if (islandView != null) {
                    val pkgName = activeIslandPkgNames[islandView]
                    val lyriconPkg = LyriconDataBridge.activePackageName
                    val lyricText = LyriconDataBridge.currentLyric
                    val hasLyricInfo = !lyricText.isNullOrBlank()
                    
                    // 仅当正在播放 且 存在有效的歌词内容时，才强制拉大灵动岛
                    if (!pkgName.isNullOrEmpty() && pkgName == lyriconPkg && LyriconDataBridge.isPlaying && hasLyricInfo) {
                        val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                        val islandLengthDp = prefs.getInt(Constants.KEY_MAX_LEFT_WIDTH, Constants.DEFAULT_MAX_LEFT_WIDTH)
                        val density = islandView.resources.displayMetrics.density

                        val fixedLeftWidthPx = (islandLengthDp * density).toInt()
                        val fixedRightWidthPx = (islandLengthDp * density).toInt()

                        try {
                            val viewClass = islandView.javaClass
                            val cutoutWidth = viewClass.getMethod("getCutoutWidth").invoke(islandView) as Int

                            // 总宽度 = 物理开孔宽度 + 左文本宽度 + 右文本宽度
                            val totalWidth = cutoutWidth + fixedLeftWidthPx + fixedRightWidthPx

                            // 居中 X 坐标
                            val displayWidth = islandView.resources.displayMetrics.widthPixels
                            val newX = (displayWidth - totalWidth) / 2

                            viewClass.getMethod("setBigIslandLeftWidth", Int::class.javaPrimitiveType).invoke(islandView, fixedLeftWidthPx)
                            viewClass.getMethod("setBigIslandRightWidth", Int::class.javaPrimitiveType).invoke(islandView, fixedRightWidthPx)
                            viewClass.getMethod("setBigIslandViewWidth", Int::class.javaPrimitiveType).invoke(islandView, totalWidth)
                            viewClass.getMethod("setBigIslandX", Int::class.javaPrimitiveType).invoke(islandView, newX)

                            viewClass.getMethod("setBigIslandLeftWidthHasSmallIsland", Int::class.javaPrimitiveType).invoke(islandView, fixedLeftWidthPx)
                            viewClass.getMethod("setBigIslandRightWidthHasSmallIsland", Int::class.javaPrimitiveType).invoke(islandView, fixedRightWidthPx)
                            viewClass.getMethod("setBigIslandViewWidthHasSmallIsland", Int::class.javaPrimitiveType).invoke(islandView, totalWidth)
                            viewClass.getMethod("setBigIslandXHasSmallIsland", Int::class.javaPrimitiveType).invoke(islandView, newX)
                        } catch (e: Exception) {
                            module.log(Log.ERROR, "HyperLyric", "Failed to force island width target: ${e.message}")
                        }
                    }
                    // ★ 暂停时：什么都不做，让系统自己算出的原生值直接传给动画引擎
                }
            } catch (_: Exception) {}
            return chain.proceed()
        }
    }

    // =====================================================================
    // 外部回调入口：歌词/歌曲变更时更新所有活跃岛屿
    // 这里可以安全地 calculateBigIslandWidth，因为这是独立事件而非系统内部调用
    // =====================================================================
    internal fun updateAllActiveIslands(newTitle: String, activePkg: String) {
        val iterator = activeIslandPkgNames.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val islandView = entry.key as? ViewGroup
            val pkg = entry.value
            if (islandView != null && islandView.isAttachedToWindow) {
                if (pkg == activePkg) {
                    val currentSong = if (LyriconDataBridge.activePackageName == pkg) LyriconDataBridge.currentSongName ?: "" else ""
                    val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                    val islandLength = prefs.getInt(Constants.KEY_MAX_LEFT_WIDTH, Constants.DEFAULT_MAX_LEFT_WIDTH)
                    islandView.post {
                        val hasLyricInfo = !newTitle.isBlank()
                        if (LyriconDataBridge.isPlaying && hasLyricInfo) {
                            injectTextToIsland(islandView, currentSong, newTitle, pkg, islandLength)
                        } else {
                            clearTextFromIsland(islandView, islandView.context.resources)
                        }
                        // 歌词/状态变了 → 触发系统完整重算链路
                        triggerSystemRelayout(islandView)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    // =====================================================================
    // 设置变更时强制刷新：清除旧注入 → 用新宽度重新注入 → 触发系统重算
    // =====================================================================
    internal fun refreshAllActiveIslands() {
        val activePkg = LyriconDataBridge.activePackageName ?: return
        val lyricText = LyriconDataBridge.currentLyric
        val songName = LyriconDataBridge.currentSongName ?: ""

        val iterator = activeIslandPkgNames.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val islandView = entry.key as? ViewGroup
            val pkg = entry.value
            if (islandView != null && islandView.isAttachedToWindow) {
                if (pkg == activePkg) {
                    islandView.post {
                        val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                        val islandLength = prefs.getInt(Constants.KEY_MAX_LEFT_WIDTH, Constants.DEFAULT_MAX_LEFT_WIDTH)

                        // 先清除旧的注入（包括恢复旧宽度的 tag）
                        clearTextFromIsland(islandView, islandView.context.resources)
                        
                        // 强制清空内部自定义视图缓存，确保使用最新配置重建
                        arrayOf("island_container_module_image_text_1" to TAG_LEFT, "island_container_module_image_text_2" to TAG_RIGHT).forEach { (idName, tagStr) ->
                            val outerId = findId(idName, islandView.context.resources)
                            if (outerId != 0) {
                                val outerContainer = islandView.findViewById<ViewGroup>(outerId)
                                if (outerContainer != null) {
                                    val textContainerId = findId("island_container_module_text", islandView.context.resources)
                                    val targetContainer = if (textContainerId != 0) outerContainer.findViewById(textContainerId) ?: outerContainer else outerContainer
                                    val frame = targetContainer.findViewWithTag<FrameLayout>("${tagStr}_container")
                                    frame?.removeAllViews() // 强制下次 ensureTextInContainer 时重建
                                }
                            }
                        }

                        if (LyriconDataBridge.isPlaying && !lyricText.isNullOrBlank()) {
                            // 用新的宽度重新注入
                            injectTextToIsland(islandView, songName, lyricText, pkg, islandLength)
                        }

                        // 触发系统完整的 宽度计算 → 布局更新 → 动画 链路
                        triggerSystemRelayout(islandView)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    // =====================================================================
    // 安全触发系统的 宽度计算 → 布局更新 → 动画 完整链路
    // 尝试多个方法名称，确保至少一个触发成功
    // =====================================================================
    private fun triggerSystemRelayout(islandView: ViewGroup) {
        val viewClass = islandView.javaClass
        
        // 优先尝试 updateBigIslandViewWidth()，这是系统最高层入口
        // 它内部会调用 calculateBigIslandWidth() → updateBigIslandLayout() → updateBigIslandLayoutWithAnim()
        try {
            val method = viewClass.getMethod("updateBigIslandViewWidth")
            method.invoke(islandView)
            return
        } catch (_: Exception) {}

        // 降级尝试 calculateBigIslandWidth()
        try {
            val method = viewClass.getMethod("calculateBigIslandWidth")
            method.invoke(islandView)
        } catch (_: Exception) {}
    }

    // =====================================================================
    // 播放状态变化回调：暂停时清理文字并触发系统重算让岛收缩
    // =====================================================================
    internal fun onPlaybackStateChanged(isPlaying: Boolean) {
        val activePkg = LyriconDataBridge.activePackageName ?: return
        val iterator = activeIslandPkgNames.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val islandView = entry.key as? ViewGroup
            val pkg = entry.value
            if (islandView != null && islandView.isAttachedToWindow) {
                if (pkg == activePkg) {
                    islandView.post {
                        if (isPlaying) {
                            // 恢复播放：如果有歌词就注入
                            val lyricText = LyriconDataBridge.currentLyric
                            val songName = LyriconDataBridge.currentSongName ?: ""
                            val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                            val islandLength = prefs.getInt(Constants.KEY_MAX_LEFT_WIDTH, Constants.DEFAULT_MAX_LEFT_WIDTH)
                            if (!lyricText.isNullOrBlank()) {
                                injectTextToIsland(islandView, songName, lyricText, pkg, islandLength)
                            }
                        } else {
                            // 暂停：隐藏自定义文字
                            clearTextFromIsland(islandView, islandView.context.resources)
                        }
                        // 触发系统重算，让岛自然伸缩
                        triggerSystemRelayout(islandView)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    internal fun updateActiveIslandsPosition(position: Long, activePkg: String) {
        val iterator = activeIslandPkgNames.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val islandView = entry.key as? ViewGroup
            val pkg = entry.value
            if (islandView != null && islandView.isAttachedToWindow) {
                if (pkg == activePkg) {
                    val frameId = findId("island_container_module_image_text_2", islandView.context.resources)
                    if (frameId != 0) {
                        val tv = islandView.findViewById<ViewGroup>(frameId)?.findViewWithTag<io.github.proify.lyricon.lyric.view.RichLyricLineView>(TAG_RIGHT)
                        tv?.setPosition(position)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    // =====================================================================
    // 文字注入：往灵动岛容器中塞入自定义文本视图
    // =====================================================================
    private fun injectTextToIsland(bigIslandView: ViewGroup, leftTitle: String, rightTitle: String, pkgName: String, islandLength: Int) {
        if (leftTitle.isBlank() && rightTitle.isBlank()) return
        val context = bigIslandView.context
        val res = context.resources

        val iconId = findId("island_fix_icon", res)
        val hasFixIcon = iconId != 0 && bigIslandView.findViewById<ImageView>(iconId) != null

        val isMusicApp = pkgName.contains("music") || pkgName.contains("player") || pkgName.contains("kugou") || pkgName.contains("kuwo")
        if (!hasFixIcon && pkgName.isNotEmpty() && !isMusicApp) {
            return
        }

        val leftTv = ensureTextInContainer(bigIslandView, res, "island_container_module_image_text_1", TAG_LEFT, islandLength)
        val rightTv = ensureTextInContainer(bigIslandView, res, "island_container_module_image_text_2", TAG_RIGHT, islandLength)

        var needInvalidate = false

        if (leftTv is TextView && leftTv.text?.toString() != leftTitle) {
            leftTv.text = leftTitle
            needInvalidate = true
        }

        if (rightTv is io.github.proify.lyricon.lyric.view.RichLyricLineView) {
            val currentLine = LyriconDataBridge.currentLyricLine
            if (rightTv.line != currentLine) {
                val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                val isAnimEnabled = prefs.getBoolean(Constants.KEY_ANIM_ENABLE, Constants.DEFAULT_ANIM_ENABLE)
                val animId = prefs.getString(Constants.KEY_ANIM_ID, Constants.DEFAULT_ANIM_ID)

                needInvalidate = true

                val applyLineExt: io.github.proify.lyricon.lyric.view.RichLyricLineView.() -> Unit = {
                    line = currentLine
                    post { 
                        if (prefs.getBoolean(Constants.KEY_MARQUEE_MODE, Constants.DEFAULT_MARQUEE_MODE)) {
                            tryStartMarquee()
                        } else {
                            stopMarquee()
                        }
                    }
                }

                if (isAnimEnabled) {
                    val preset = YoYoPresets.getById(animId) ?: YoYoPresets.Default
                    rightTv.animateUpdate(preset, applyLineExt)
                } else {
                    rightTv.applyLineExt()
                }
            }
        }

        if (needInvalidate) {
            bigIslandView.post {
                leftTv?.requestLayout()
                rightTv?.requestLayout()
                leftTv?.invalidate()
                rightTv?.invalidate()
                bigIslandView.requestLayout()
                bigIslandView.invalidate()
            }
        }
    }

    // =====================================================================
    // 清理自定义视图：仅隐藏我们注入的 Frame，恢复原生图标
    // ★ 不修改 layoutParams.width，不调用 calculateBigIslandWidth
    // =====================================================================
    private fun clearTextFromIsland(root: ViewGroup, res: Resources) {
        val leftId = findId("island_container_module_image_text_1", res)
        val rightId = findId("island_container_module_image_text_2", res)

        // 隐藏我们注入的自定义 Frame，恢复容器原始宽度
        arrayOf(TAG_LEFT to leftId, TAG_RIGHT to rightId).forEach { (tagStr, outerId) ->
            if (outerId != 0) {
                val outerContainer = root.findViewById<ViewGroup>(outerId)
                if (outerContainer != null) {
                    val textContainerId = findId("island_container_module_text", res)
                    val targetContainer = if (textContainerId != 0) outerContainer.findViewById(textContainerId) ?: outerContainer else outerContainer

                    // 恢复原始宽度（若曾被我们修改）
                    val key = "lyricon_orig_width".hashCode()
                    val origWidth = targetContainer.getTag(key) as? Int
                    if (origWidth != null) {
                        val lp = targetContainer.layoutParams
                        if (lp != null) {
                            lp.width = origWidth
                            targetContainer.layoutParams = lp
                        }
                    }

                    // 清除调试底色
                    targetContainer.background = null

                    // 隐藏我们的自定义 Frame
                    val frame = targetContainer.findViewWithTag<View>("${tagStr}_container")
                    frame?.visibility = View.GONE
                }
            }
        }

        // 恢复右侧系统原生图标
        if (rightId != 0) {
            val rightOuter = root.findViewById<ViewGroup>(rightId)
            val iconContainerId = findId("island_container_module_icon", res)
            if (iconContainerId != 0 && rightOuter != null) {
                rightOuter.findViewById<View>(iconContainerId)?.visibility = View.VISIBLE
            }
        }
    }

    // =====================================================================
    // 确保文本容器存在并配置好
    // =====================================================================
    private fun ensureTextInContainer(
        root: ViewGroup,
        res: Resources,
        outerContainerIdName: String,
        tagStr: String,
        islandLength: Int
    ): View? {
        val outerId = findId(outerContainerIdName, res)
        if (outerId == 0) return null
        val outerContainer = root.findViewById<ViewGroup>(outerId) ?: return null

        if (tagStr == TAG_RIGHT) {
            val iconContainerId = findId("island_container_module_icon", res)
            if (iconContainerId != 0) {
                outerContainer.findViewById<View>(iconContainerId)?.visibility = View.GONE
            }
        }

        val textContainerId = findId("island_container_module_text", res)
        val targetContainer = if (textContainerId != 0) outerContainer.findViewById(textContainerId) ?: outerContainer else outerContainer

        targetContainer.visibility = View.VISIBLE

        val fixedWidthPx = if (tagStr == TAG_LEFT) {
            // 扣除左侧封面的空间
            ((islandLength - 30).coerceAtLeast(0) * res.displayMetrics.density).toInt()
        } else {
            // 右侧预留圆角空间
            ((islandLength - 10).coerceAtLeast(0) * res.displayMetrics.density).toInt()
        }

        val lpTarget = targetContainer.layoutParams
        if (lpTarget != null) {
            val key = "lyricon_orig_width".hashCode()
            if (targetContainer.getTag(key) == null) {
                targetContainer.setTag(key, lpTarget.width)
            }
            lpTarget.width = fixedWidthPx
            targetContainer.layoutParams = lpTarget
        }

        val containerTag = "${tagStr}_container"
        var frame = targetContainer.findViewWithTag<FrameLayout>(containerTag)
        if (frame == null) {
            frame = FrameLayout(targetContainer.context).apply {
                tag = containerTag
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }
            }
            targetContainer.addView(frame)
        }

        var customTv = frame.findViewWithTag<View>(tagStr)
        if (customTv == null) {
            if (tagStr == TAG_RIGHT) {
                val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                customTv = io.github.proify.lyricon.lyric.view.RichLyricLineView(frame.context).apply {
                    tag = tagStr
                    layoutParams = FrameLayout.LayoutParams(
                        fixedWidthPx,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL
                    )
                    
                    val config = io.github.proify.lyricon.lyric.view.RichLyricLineConfig().apply {
                        fadingEdgeLength = prefs.getInt(Constants.KEY_FADING_EDGE_LENGTH, Constants.DEFAULT_FADING_EDGE_LENGTH)
                        gradientProgressStyle = prefs.getBoolean(Constants.KEY_GRADIENT_PROGRESS, Constants.DEFAULT_GRADIENT_PROGRESS)
                        
                        placeholderFormat = io.github.proify.lyricon.lyric.view.PlaceholderFormat.NONE
                        
                        val fontSize = prefs.getInt(Constants.KEY_TEXT_SIZE, Constants.DEFAULT_TEXT_SIZE)
                        val fontWeight = prefs.getInt(Constants.KEY_FONT_WEIGHT, Constants.DEFAULT_FONT_WEIGHT)
                        val fontItalic = prefs.getBoolean(Constants.KEY_FONT_ITALIC, Constants.DEFAULT_FONT_ITALIC)
                        
                        val tf =
                            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, fontWeight, fontItalic)
                        val isTranslationVisible = LyriconDataBridge.isDisplayTranslation
                        
                        val textSizeRatio = prefs.getFloat(Constants.KEY_TEXT_SIZE_RATIO, Constants.DEFAULT_TEXT_SIZE_RATIO)
                        val primarySizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat(), res.displayMetrics)
                        primary.textSize = primarySizePx
                        primary.typeface = tf
                        secondary.textSize = if (isTranslationVisible) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSize * textSizeRatio, res.displayMetrics) else 0f
                        secondary.typeface = tf
                        if (!isTranslationVisible) {
                            secondary.textColor = intArrayOf(Color.TRANSPARENT)
                        }
                        
                        primary.enableRelativeProgress = prefs.getBoolean(Constants.KEY_SYLLABLE_RELATIVE, Constants.DEFAULT_SYLLABLE_RELATIVE)
                        primary.enableRelativeProgressHighlight = prefs.getBoolean(Constants.KEY_SYLLABLE_HIGHLIGHT, Constants.DEFAULT_SYLLABLE_HIGHLIGHT)
                        primary.isScrollOnly = false
                        
                        syllable.enableSustainGlow = true
                        
                        val isMarqueeEnabled = prefs.getBoolean(Constants.KEY_MARQUEE_MODE, Constants.DEFAULT_MARQUEE_MODE)
                        marquee.disableSyllableScroll = !isMarqueeEnabled
                        if (isMarqueeEnabled) {
                            marquee.scrollSpeed = prefs.getInt(Constants.KEY_MARQUEE_SPEED, Constants.DEFAULT_MARQUEE_SPEED).toFloat()
                            marquee.initialDelay = prefs.getInt(Constants.KEY_MARQUEE_DELAY, Constants.DEFAULT_MARQUEE_DELAY)
                            marquee.loopDelay = prefs.getInt(Constants.KEY_MARQUEE_LOOP_DELAY, Constants.DEFAULT_MARQUEE_LOOP_DELAY)
                            val infinite = prefs.getBoolean(Constants.KEY_MARQUEE_INFINITE, Constants.DEFAULT_MARQUEE_INFINITE)
                            marquee.repeatCount = if (infinite) -1 else 1
                            marquee.stopAtEnd = prefs.getBoolean(Constants.KEY_MARQUEE_STOP_END, Constants.DEFAULT_MARQUEE_STOP_END)
                        } else {
                            marquee.repeatCount = 0
                            marquee.scrollSpeed = 0f
                        }
                    }

                    setStyle(config)
                    updateColor(intArrayOf(Color.WHITE), intArrayOf("#4CFFFFFF".toColorInt()), intArrayOf(Color.WHITE))
                }
            } else {
                val prefs = module.getRemotePreferences(Constants.PREF_NAME)
                customTv = TextView(frame.context).apply {
                    tag = tagStr
                    setTextColor(Color.WHITE)
                    
                    val fontSize = prefs.getInt(Constants.KEY_TEXT_SIZE, Constants.DEFAULT_TEXT_SIZE)
                    val fontWeight = prefs.getInt(Constants.KEY_FONT_WEIGHT, Constants.DEFAULT_FONT_WEIGHT)
                    val fontItalic = prefs.getBoolean(Constants.KEY_FONT_ITALIC, Constants.DEFAULT_FONT_ITALIC)
                    
                    val tf =
                        android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, fontWeight, fontItalic)

                    typeface = tf
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
                    
                    isSingleLine = true
                    ellipsize = null
                    
                    val fadingEdgeDP = prefs.getInt(Constants.KEY_FADING_EDGE_LENGTH, Constants.DEFAULT_FADING_EDGE_LENGTH)
                    isHorizontalFadingEdgeEnabled = fadingEdgeDP > 0
                    setFadingEdgeLength(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fadingEdgeDP.toFloat(), res.displayMetrics).toInt())

                    layoutParams = FrameLayout.LayoutParams(
                        fixedWidthPx,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL
                    )
                }
            }
            frame.addView(customTv)
        }
        frame.visibility = View.VISIBLE

        customTv.visibility = View.VISIBLE
        if (tagStr == TAG_RIGHT) {
            customTv.isSelected = true
        }
        return customTv
    }

    @SuppressLint("DiscouragedApi")
    private fun findId(name: String, res: Resources): Int {
        for (pkg in TARGET_PACKAGES) {
            val id = try { res.getIdentifier(name, "id", pkg) } catch (_: Exception) { 0 }
            if (id != 0) return id
        }
        return 0
    }

}
