package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.root.utils.HookLogger
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.common.RootConstants

import com.lidesheng.hyperlyric.root.utils.DynamicFinder
import com.lidesheng.hyperlyric.root.utils.LyricStyleHelper
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.source.IslandRenderer
import com.lidesheng.hyperlyric.root.utils.TranslationHelper
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.yoyo.YoYoPresets
import com.lidesheng.hyperlyric.lyric.view.yoyo.animateUpdate

object HookIslandLyric : IslandRenderer {
    lateinit var module: XposedModule

    var activeIslandPkgNames = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, String>())
    var activeContentView: java.lang.ref.WeakReference<ViewGroup>? = null
    
    private val hookedClassLoaders = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    
    @Volatile
    private var isHookedSuccess = false

    /**
     * 执行 Hook 逻辑。该方法可能在多个进程、多个 ClassLoader 中被调用。
     */
    fun hook(xposedModule: XposedModule, cl: ClassLoader) {
        if (cl.javaClass.name.contains("BootClassLoader")) return
        if (!hookedClassLoaders.add(cl)) return
        
        module = xposedModule
        
        val islandPkg = "miui.systemui.dynamicisland"

        // 尝试加载目标类，不成功则静默返回
        val contentViewClass = runCatching { 
            cl.loadClass("$islandPkg.window.content.DynamicIslandContentView")
        }.getOrNull() ?: DynamicFinder.findClassByConstantString(cl, "$islandPkg.window.content", "DynamicIslandContentView") ?: return

        runCatching {
            val hookedMethods = mutableListOf<String>()
            
            // 查找并 Hook 所有名为 updateBigIslandView 的方法（处理不同版本的重载或协程实现）
            contentViewClass.methods.filter { it.name == "updateBigIslandView" }.forEach { method ->
                runCatching {
                    module.deoptimize(method)
                    module.hook(method).intercept(UpdateBigIslandHooker())
                    hookedMethods.add("updateBigIslandView")
                    HookLogger.i("HookIslandLyric","已注入超级岛: $method")
                }
            }

            // 查找并 Hook 所有名为 calculateBigIslandWidth 的方法
            contentViewClass.methods.filter { it.name == "calculateBigIslandWidth" }.forEach { method ->
                runCatching {
                    module.deoptimize(method)
                    module.hook(method).intercept(PreInjectHooker())
                    hookedMethods.add("calculateBigIslandWidth")
                    HookLogger.i("HookIslandLyric","已注入超级岛: $method")
                }
            }

            // 初始化外圈光效处理中心
            HookIslandGlow.init(module, cl)

            if (hookedMethods.isNotEmpty()) {
                isHookedSuccess = true
                val methodsSummary = hookedMethods.distinct().joinToString(", ")
                HookLogger.i("HookIslandLyric","初始化完成。已注入超级岛: [$methodsSummary]")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e("HookIslandLyric", "ModuleInit : 注入超级岛失败", e)
            }
        }
    }

    class PreInjectHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val islandView = chain.thisObject as? ViewGroup ?: return@runCatching
                val prefs = (module as HookEntry).prefs
                val pkgName = activeIslandPkgNames[islandView]
                val activePkg = LyriconDataBridge.activePackageName
                val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)

                if (activePkg.isNullOrEmpty() || (!MediaMetadataHelper.isPackagePlaying(islandView.context, activePkg) && behavior == 0)) {
                    return@runCatching
                }

                if (pkgName != null && pkgName == activePkg) {
                    applySettings(islandView)
                    val leftMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)
                    val rightMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)
                    
                    injectToSlot(islandView, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", leftMode, prefs, pkgName)
                    injectToSlot(islandView, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", rightMode, prefs, pkgName)
                }
            }.onFailure { e ->
                HookLogger.e("HookIslandLyric", "预注入超级岛失败", e)
            }
            return chain.proceed()
        }
    }

    class UpdateBigIslandHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val viewGroup = chain.thisObject as? ViewGroup ?: return@runCatching
                
                val islandData = chain.args.getOrNull(0)
                var pkgName = runCatching {
                    // 使用更加鲁棒的方法寻找 getExtras (无参且返回 Bundle)
                    val getExtrasMethod = islandData?.javaClass?.methods?.find { 
                        it.name == "getExtras" && it.parameterTypes.isEmpty() && it.returnType.name.contains("Bundle")
                    }
                    val extras = getExtrasMethod?.invoke(islandData) as? android.os.Bundle
                    extras?.getString("miui.pkg.name")
                }.getOrNull() ?: ""
                
                if (pkgName.isNotEmpty()) {
                    activeIslandPkgNames[viewGroup] = pkgName
                } else {
                    pkgName = activeIslandPkgNames[viewGroup] ?: ""
                }

                val activePkg = LyriconDataBridge.activePackageName
                val prefs = (module as HookEntry).prefs
                val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)

                if (activePkg.isNullOrEmpty() || (!MediaMetadataHelper.isPackagePlaying(viewGroup.context, activePkg) && behavior == 0)) {
                    IslandViewHelper.clearInjectedViews(viewGroup)
                    return@runCatching
                }

                if (pkgName.isEmpty() || pkgName != activePkg) {
                    return@runCatching
                }

                activeContentView = java.lang.ref.WeakReference(viewGroup)

                applySettings(viewGroup)
                val leftMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)
                val rightMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)
                
                injectToSlot(viewGroup, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", leftMode, prefs, pkgName)
                injectToSlot(viewGroup, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", rightMode, prefs, pkgName)

                // 注入光效 Bundle 标记 + 主动触发 BigIslandView 的 startGlowEffect
                HookIslandGlow.injectAndTriggerGlow(viewGroup, islandData, prefs)
            }.onFailure { e ->
                HookLogger.e("HookIslandLyric", "更新超级岛视图失败", e)
            }

            return result
        }
    }


    private fun triggerSystemRelayout(islandView: ViewGroup) {
        IslandViewHelper.triggerSystemRelayout(islandView)
    }

    private fun applySettings(rootView: ViewGroup) {
        val prefs = (module as HookEntry).prefs
        val showAlbum = prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM)
        val showRhythm = prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON)
        

        IslandViewHelper.toggleContainer(rootView, "island_container_module_image_text_1", "island_container_module_icon", showAlbum)
        IslandViewHelper.toggleContainer(rootView, "island_container_module_image_text_2", "island_container_module_icon", showRhythm)
        
        IslandViewHelper.toggleContainer(rootView, "island_container_module_image_text_1", "island_container_module_text", true)
        IslandViewHelper.toggleContainer(rootView, "island_container_module_image_text_2", "island_container_module_text", true)

        if (!showAlbum) {
            IslandViewHelper.clearTextContainerMargin(rootView, "island_container_module_image_text_1", clearStart = true, clearEnd = false)
        }
        if (!showRhythm) {
            IslandViewHelper.clearTextContainerMargin(rootView, "island_container_module_image_text_2", clearStart = false, clearEnd = true)
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun injectToSlot(rootView: ViewGroup, parentName: String, tag: String, mode: Int, prefs: SharedPreferences, pkgName: String) {
        val res = rootView.resources
        val density = res.displayMetrics.density
        val parent = IslandViewHelper.findViewByName(rootView, parentName) as? ViewGroup ?: return
        val container = IslandViewHelper.findViewByName(parent, "island_container_module_text") as? ViewGroup ?: parent

        var targetView = container.findViewWithTag<RichLyricLineView>(tag)
        
        if (mode == 0) {
            targetView?.visibility = View.GONE
            return
        }

        val targetPkg = LyriconDataBridge.activePackageName ?: pkgName
        val mediaInfo = MediaMetadataHelper.getMediaInfo(rootView.context, targetPkg, HookLogger)

        val songName = mediaInfo.title
        val artistName = mediaInfo.artist
        val albumName = mediaInfo.album
        val albumBitmap = mediaInfo.albumArt

        val singleModeText = when(mode) {
            1 -> songName
            2 -> artistName
            3 -> albumName
            4 -> "$songName - $artistName"
            else -> ""
        }
        val isLeft = parentName.contains("1")
        val maxWidthDp = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH)
                         else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH)
        val pL = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT)
                 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT)
        val pR = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT)
                 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT)

        if (maxWidthDp <= 0) {
            val wrapperTag = tag + "_WRAPPER"
            container.findViewWithTag<FrameLayout>(wrapperTag)?.visibility = View.GONE
            targetView?.visibility = View.GONE
            return
        }

        if (mode in 1..7) {
            val wrapperTag = tag + "_WRAPPER"
            var wrapperView = container.findViewWithTag<FrameLayout>(wrapperTag)
            
            if (wrapperView == null) {
                targetView?.let { container.removeView(it) }
                
                wrapperView = object : FrameLayout(rootView.context) {
                    var maxWidthPx = -1
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        val givenWidth = MeasureSpec.getSize(widthMeasureSpec)
                        val newWidth = if (maxWidthPx > 0 && (givenWidth == 0 || givenWidth > maxWidthPx)) maxWidthPx else givenWidth
                        super.onMeasure(MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.AT_MOST), heightMeasureSpec)
                    }
                }.apply {
                    this.tag = wrapperTag
                }
                
                targetView = RichLyricLineView(rootView.context).apply {
                    this.tag = tag
                }
                wrapperView.addView(targetView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
                
                container.addView(wrapperView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
            } else {
                targetView = wrapperView.findViewWithTag(tag) as? RichLyricLineView
                if (targetView == null) {
                    wrapperView.removeAllViews()
                    targetView = RichLyricLineView(rootView.context).apply {
                        this.tag = tag
                    }
                    wrapperView.addView(targetView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    })
                }
            }

            try {
                val maxField = wrapperView.javaClass.getDeclaredField("maxWidthPx")
                maxField.isAccessible = true
                maxField.setInt(wrapperView, (maxWidthDp * density).toInt())
            } catch (e: Exception) {
                HookLogger.w("HookIslandLyric","设置 maxWidthPx 失败: ${e.message}")
            }

            val richView = targetView ?: return
            configureRichLyricView(richView, prefs, res, mode, albumBitmap)
            
            // 设置内边距和可见性，必须在 measure/layout 之前，否则会导致一帧的位移抖动
            richView.setPadding((pL * density).toInt(), 0, (pR * density).toInt(), 0)
            richView.visibility = View.VISIBLE
            wrapperView.visibility = View.VISIBLE
            
            richView.line = when(mode) {
                1, 2, 3, 4 -> RichLyricLine(text = singleModeText, words = emptyList())
                5 -> RichLyricLine(text = songName, words = emptyList(), secondary = artistName, secondaryWords = emptyList())
                6 -> {
                    val sec = if (albumName.isEmpty()) artistName else "$artistName - $albumName"
                    RichLyricLine(text = songName, words = emptyList(), secondary = sec, secondaryWords = emptyList())
                }
                7 -> {
                    var rawLine = LyriconDataBridge.currentLyricLine ?: RichLyricLine(text = songName, words = emptyList())
                    if (TranslationHelper.isTranslationOnly(prefs)) {
                        rawLine = TranslationHelper.applyTranslationOnly(rawLine)
                    } else if (TranslationHelper.isSwapTranslation(prefs)) {
                        rawLine = TranslationHelper.swapTranslation(rawLine)
                    }
                    rawLine
                }
                else -> null
            }
            HookLogger.d("HookIslandLyric","注入完成: mode=$mode, 标题=${singleModeText.take(20)}, 歌词=${(LyriconDataBridge.currentLyric ?: "").take(20)}")

            // mode 1~6 是歌曲信息，使用独立的跑马灯参数覆盖歌词默认值
            if (mode in 1..6 && prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE)) {
                val mdSpeed = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_SPEED).toFloat()
                val mdDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_DELAY)
                val mdLoopDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY)
                val mdInfinite = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_INFINITE)
                richView.setMetadataMarqueeConfig(
                    speed = mdSpeed,
                    initialDelay = mdDelay,
                    loopDelay = mdLoopDelay,
                    repeatCount = if (mdInfinite) -1 else 1,
                    stopAtEnd = true  // 歌曲信息始终在尾部停顿，无限循环时自动启用双行尾部同步
                )
                // 设置对端行宽度，用于双行尾部同步
                richView.main.setPeerLineWidth(richView.secondary.lineWidth)
                richView.secondary.setPeerLineWidth(richView.main.lineWidth)
            }

            // 强制隐藏原生容器中除我们 wrapperView 之外的所有原生文本视图
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child != wrapperView) {
                    child.visibility = View.GONE
                }
            }
            
            // 强制即时测排 (Force Immediate Layout)
            val msW = View.MeasureSpec.makeMeasureSpec((maxWidthDp * density).toInt(), View.MeasureSpec.AT_MOST)
            val msH = View.MeasureSpec.makeMeasureSpec(container.height, if (container.height > 0) View.MeasureSpec.EXACTLY else View.MeasureSpec.UNSPECIFIED)
            wrapperView.measure(msW, msH)
            wrapperView.layout(0, 0, wrapperView.measuredWidth, wrapperView.measuredHeight)

            // 如果开启了跑马灯，请求开始滚动
            richView.post {
                val shouldMarquee = if (mode in 1..6) {
                    prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE)
                } else {
                    prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)
                }
                if (shouldMarquee) {
                    richView.requestStartMarquee()
                }
            }
        }
    }

    private fun configureRichLyricView(view: RichLyricLineView, prefs: SharedPreferences, res: android.content.res.Resources, mode: Int, albumBitmap: android.graphics.Bitmap? = null) {
        // Sync display flags to the view's internal rendering engine
        val disableAll = TranslationHelper.isTranslationDisabled(prefs)
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
        view.displayTranslation = LyriconDataBridge.isDisplayTranslation && !disableAll
        view.displayRoma = LyriconDataBridge.isDisplayRoma && !disableAll && !translationOnly

        val style = LyricStyleHelper.buildStyle(prefs, res, mode, albumBitmap)
        view.setStyle(style)
    }

    override fun refreshActiveIsland() {
        HookLogger.d("HookIslandLyric","正在刷新超级岛")
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cv = entry.key as? ViewGroup
            val pkgName = entry.value

            if (cv != null && cv.isAttachedToWindow) {
                if (pkgName == activePkg) {
                    cv.post {
                        val prefs = (module as HookEntry).prefs
                        IslandViewHelper.clearInjectedViews(cv)
                        CoverColorHelper.clearCache()
                        applySettings(cv)
                        val leftMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)
                        val rightMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)
                        injectToSlot(cv, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", leftMode, prefs, pkgName)
                        injectToSlot(cv, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", rightMode, prefs, pkgName)
                        
                        // 刷新时同步更新光效
                        val mediaInfo = MediaMetadataHelper.getMediaInfo(cv.context, pkgName, HookLogger)
                        HookIslandGlow.updateMusicGlow(mediaInfo.albumArt, prefs)

                        triggerSystemRelayout(cv)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    override fun updateLyricLine() {
        HookLogger.d("HookIslandLyric","正在更新歌词行")
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cv = entry.key as? ViewGroup
            val pkgName = entry.value

            if (cv != null && cv.isAttachedToWindow) {
                if (pkgName == activePkg) {
                    val prefs = (module as HookEntry).prefs
                    updateLyricInSlot(cv, "HYPERLYRIC_LEFT_VIEW", prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT), prefs)
                    updateLyricInSlot(cv, "HYPERLYRIC_RIGHT_VIEW", prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT), prefs)
                }
            } else {
                iterator.remove()
            }
        }
    }

    private fun updateLyricInSlot(cv: ViewGroup, tag: String, mode: Int, prefs: SharedPreferences) {
        if (mode != 7) return
        val view = cv.findViewWithTag<RichLyricLineView>(tag) ?: return
        val rawLine = LyriconDataBridge.currentLyricLine
        val targetLine = if (rawLine != null) {
            if (TranslationHelper.isTranslationOnly(prefs)) {
                TranslationHelper.applyTranslationOnly(rawLine)
            } else if (TranslationHelper.isSwapTranslation(prefs)) {
                TranslationHelper.swapTranslation(rawLine)
            } else {
                rawLine
            }
        } else {
            null
        }

        cv.post {
            val isAnimEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, RootConstants.DEFAULT_HOOK_ANIM_ENABLE)
            val animId = prefs.getString(RootConstants.KEY_HOOK_ANIM_ID, RootConstants.DEFAULT_HOOK_ANIM_ID)

            val applyLine: RichLyricLineView.() -> Unit = {
                val disableAll = TranslationHelper.isTranslationDisabled(prefs)
                val translationOnly = TranslationHelper.isTranslationOnly(prefs)
                displayTranslation = LyriconDataBridge.isDisplayTranslation && !disableAll
                displayRoma = LyriconDataBridge.isDisplayRoma && !disableAll && !translationOnly
                line = targetLine
                post {
                    if (prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)) {
                        requestStartMarquee()
                    }
                }
            }

            if (isAnimEnabled) {
                val preset = YoYoPresets.getById(animId) ?: YoYoPresets.Default
                view.animateUpdate(preset, applyLine)
            } else {
                view.applyLine()
            }
        }
    }

    /**
     * 播放进度同步到 RichLyricLineView，驱动逐字/逐音节高亮
     */
    override fun updatePosition(position: Long) {
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cv = entry.key as? ViewGroup
            val pkgName = entry.value

            if (cv != null && cv.isAttachedToWindow) {
                if (pkgName == activePkg) {
                    cv.post {
                        cv.findViewWithTag<RichLyricLineView>("HYPERLYRIC_LEFT_VIEW")?.setPosition(position)
                        cv.findViewWithTag<RichLyricLineView>("HYPERLYRIC_RIGHT_VIEW")?.setPosition(position)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    /**
     * 播放/暂停状态变化回调
     */
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        HookLogger.d("HookIslandLyric","播放状态变更: isPlaying=$isPlaying")
        val prefs = (module as HookEntry).prefs
        val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)

        if (isPlaying) {
            val activePkg = LyriconDataBridge.activePackageName
            val hasInjection = activePkg != null && activeIslandPkgNames.values.any { it == activePkg }
            if (hasInjection) {
                updateLyricLine()
            } else {
                refreshActiveIsland()
            }
        } else {
            when (behavior) {
                0 -> {
                    // 恢复系统默认
                    val iterator = activeIslandPkgNames.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        val cv = entry.key as? ViewGroup
                        if (cv != null && cv.isAttachedToWindow) {
                            cv.post {
                                IslandViewHelper.clearInjectedViews(cv)
                                triggerSystemRelayout(cv)
                            }
                        } else {
                            iterator.remove()
                        }
                    }
                }
                1 -> {
                    // 保持现状，不做处理
                }
            }
        }
    }
}

