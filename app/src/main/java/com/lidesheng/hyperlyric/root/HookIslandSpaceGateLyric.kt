package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.root.utils.xLog
import com.lidesheng.hyperlyric.root.utils.xLogDebug
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants

import com.lidesheng.hyperlyric.root.utils.DynamicFinder
import com.lidesheng.hyperlyric.root.utils.LyricStyleHelper
import com.lidesheng.hyperlyric.root.utils.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.utils.TranslationHelper
import com.lidesheng.hyperlyric.root.utils.xLogError
import com.lidesheng.hyperlyric.root.utils.xLogWarn
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.view.SpaceGateRichLyricLineView
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import io.github.proify.lyricon.lyric.view.yoyo.animateUpdate

object HookIslandSpaceGateLyric {
    lateinit var module: XposedModule

    var activeIslandPkgNames = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, String>())
    var activeContentView: java.lang.ref.WeakReference<ViewGroup>? = null
    
    private val hookedClassLoaders = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    
    @Volatile
    private var isHookedSuccess = false

    private var loggedCutoutInfo = false

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
                    xLog("ModuleInit : 已注入超级岛(SpaceGate): $method")
                }
            }

            // 查找并 Hook 所有名为 calculateBigIslandWidth 的方法
            contentViewClass.methods.filter { it.name == "calculateBigIslandWidth" }.forEach { method ->
                runCatching {
                    module.deoptimize(method)
                    module.hook(method).intercept(PreInjectHooker())
                    hookedMethods.add("calculateBigIslandWidth")
                    xLog("ModuleInit : 已注入超级岛(SpaceGate): $method")
                }
            }

            // 初始化外圈光效处理中心
            HookIslandGlow.init(module, cl)

            if (hookedMethods.isNotEmpty()) {
                isHookedSuccess = true
                val methodsSummary = hookedMethods.distinct().joinToString(", ")
                xLog("ModuleInit : SpaceGate 初始化完成。已注入超级岛: [$methodsSummary]")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                xLogError("ModuleInit : SpaceGate 注入超级岛失败", e)
            }
        }
    }

    class PreInjectHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            runCatching {
                val islandView = chain.thisObject as? ViewGroup ?: return@runCatching
                val prefs = (module as HookEntry).prefs
                val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)
                
                if (!LyriconDataBridge.isPlaying && behavior == 0) {
                    return@runCatching
                }

                val pkgName = activeIslandPkgNames[islandView]
                val activePkg = LyriconDataBridge.activePackageName
                
                if (pkgName != null && pkgName == activePkg && isPackageHookEnabled(activePkg)) {
                    applySettings(islandView)
                    
                    // SpaceGate 模式硬编码强制使用模式 8（歌词穿梭），不跟随内容设置
                    injectToSlot(islandView, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", prefs, pkgName)
                    injectToSlot(islandView, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", prefs, pkgName)
                    linkViews(islandView)
                }
            }.onFailure { e ->
                xLogError("HookIslandSpaceGate : 预注入超级岛失败", e)
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

                if (!LyriconDataBridge.isPlaying && behavior == 0) {
                    IslandViewHelper.clearInjectedViews(viewGroup)
                    return@runCatching
                }

                if (pkgName.isEmpty() || pkgName != activePkg || !isPackageHookEnabled(activePkg)) {
                    return@runCatching
                }

                activeContentView = java.lang.ref.WeakReference(viewGroup)

                applySettings(viewGroup)
                
                // SpaceGate 模式硬编码强制使用模式 8（歌词穿梭），不跟随内容设置
                injectToSlot(viewGroup, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", prefs, pkgName)
                injectToSlot(viewGroup, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", prefs, pkgName)
                linkViews(viewGroup)

                HookIslandGlow.injectAndTriggerGlow(viewGroup, islandData, prefs)
            }.onFailure { e ->
                xLogError("HookIslandSpaceGate : 更新超级岛视图失败", e)
            }

            return result
        }
    }

    fun isPackageHookEnabled(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val prefs = (module as HookEntry).prefs
        val addedList = prefs.getStringSet(RootConstants.KEY_HOOK_ADDED_LIST, null)
        if (addedList.isNullOrEmpty()) return false
        val whitelist = prefs.getStringSet(RootConstants.KEY_HOOK_WHITELIST, emptySet()) ?: emptySet()
        return whitelist.contains(packageName)
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

    private fun linkViews(rootView: ViewGroup) {
        val leftView = rootView.findViewWithTag<SpaceGateRichLyricLineView>("HYPERLYRIC_LEFT_VIEW")
        val rightView = rootView.findViewWithTag<SpaceGateRichLyricLineView>("HYPERLYRIC_RIGHT_VIEW")
        
        leftView?.setSpaceGateConfig(isRightSide = false, sibling = rightView)
        rightView?.setSpaceGateConfig(isRightSide = true, sibling = leftView)
        
        if (!loggedCutoutInfo && leftView != null && rightView != null) {
            val cutoutView = IslandViewHelper.findViewByName(rootView, "area_cutout")
            if (cutoutView != null) {
                val cutoutLoc = IntArray(2)
                cutoutView.getLocationOnScreen(cutoutLoc)
                xLogDebug("SpaceGate : 成功定位摄像头容器(area_cutout), 宽度 = ${cutoutView.width}px, 绝对X = ${cutoutLoc[0]}")
            } else {
                xLogDebug("SpaceGate : 未找到系统 area_cutout 容器，将使用几何居中fallback。")
            }
            loggedCutoutInfo = true
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun injectToSlot(rootView: ViewGroup, parentName: String, tag: String, prefs: SharedPreferences, pkgName: String) {
        val res = rootView.resources
        val density = res.displayMetrics.density
        val parent = IslandViewHelper.findViewByName(rootView, parentName) as? ViewGroup ?: return
        val container = IslandViewHelper.findViewByName(parent, "island_container_module_text") as? ViewGroup ?: parent

        var targetView = container.findViewWithTag<SpaceGateRichLyricLineView>(tag)
        
        val lyriconSongName = LyriconDataBridge.currentSongName
        
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
            
            targetView = SpaceGateRichLyricLineView(rootView.context).apply {
                this.tag = tag
            }
            wrapperView.addView(targetView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
            
            container.addView(wrapperView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
        } else {
            targetView = wrapperView.findViewWithTag(tag) as? SpaceGateRichLyricLineView
            if (targetView == null) {
                wrapperView.removeAllViews()
                targetView = SpaceGateRichLyricLineView(rootView.context).apply {
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
            xLogWarn("HookIslandSpaceGate : 设置 maxWidthPx 失败: ${e.message}")
        }

        val richView = targetView ?: return
        configureRichLyricView(richView, prefs, res)
        
        richView.setPadding((pL * density).toInt(), 0, (pR * density).toInt(), 0)
        richView.visibility = View.VISIBLE
        wrapperView.visibility = View.VISIBLE
        
        // 强制使用歌词穿梭内容（原本 mode 8 的逻辑）
        var rawLine = LyriconDataBridge.currentLyricLine ?: RichLyricLine(text = lyriconSongName, words = emptyList())
        if (TranslationHelper.isTranslationOnly(prefs)) {
            rawLine = TranslationHelper.applyTranslationOnly(rawLine)
        } else if (TranslationHelper.isSwapTranslation(prefs)) {
            rawLine = TranslationHelper.swapTranslation(rawLine)
        }
        richView.line = rawLine

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child != wrapperView) {
                child.visibility = View.GONE
            }
        }
        
        val msW = View.MeasureSpec.makeMeasureSpec((maxWidthDp * density).toInt(), View.MeasureSpec.AT_MOST)
        val msH = View.MeasureSpec.makeMeasureSpec(container.height, if (container.height > 0) View.MeasureSpec.EXACTLY else View.MeasureSpec.UNSPECIFIED)
        wrapperView.measure(msW, msH)
        wrapperView.layout(0, 0, wrapperView.measuredWidth, wrapperView.measuredHeight)

        richView.post {
            if (prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)) {
                richView.requestStartMarquee()
            }
        }
    }

    private fun configureRichLyricView(view: SpaceGateRichLyricLineView, prefs: SharedPreferences, res: android.content.res.Resources) {
        val disableAll = TranslationHelper.isTranslationDisabled(prefs)
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
        view.displayTranslation = LyriconDataBridge.isDisplayTranslation && !disableAll
        view.displayRoma = LyriconDataBridge.isDisplayRoma && !disableAll && !translationOnly

        // 强行使用模式 8 获取样式
        val style = LyricStyleHelper.buildStyle(prefs, res, 8, null)
        view.setStyle(style)
    }

    fun refreshActiveIsland() {
        xLogDebug("HookIslandSpaceGate : 正在刷新超级岛")
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        if (!isPackageHookEnabled(activePkg)) return

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
                        
                        injectToSlot(cv, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", prefs, pkgName)
                        injectToSlot(cv, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", prefs, pkgName)
                        linkViews(cv)
                        
                        val mediaInfo = MediaMetadataHelper.getMediaInfo(cv.context, pkgName)
                        HookIslandGlow.updateMusicGlow(mediaInfo.albumArt, prefs)

                        triggerSystemRelayout(cv)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    fun updateLyricLine() {
        xLogDebug("HookIslandSpaceGate : 正在更新歌词行")
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        if (!isPackageHookEnabled(activePkg)) return

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cv = entry.key as? ViewGroup
            val pkgName = entry.value

            if (cv != null && cv.isAttachedToWindow) {
                if (pkgName == activePkg) {
                    val prefs = (module as HookEntry).prefs
                    updateLyricInSlot(cv, "HYPERLYRIC_LEFT_VIEW", prefs)
                    updateLyricInSlot(cv, "HYPERLYRIC_RIGHT_VIEW", prefs)
                }
            } else {
                iterator.remove()
            }
        }
    }

    private fun updateLyricInSlot(cv: ViewGroup, tag: String, prefs: SharedPreferences) {
        val view = cv.findViewWithTag<SpaceGateRichLyricLineView>(tag) ?: return
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

            val applyLine: SpaceGateRichLyricLineView.() -> Unit = {
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

    fun updatePosition(position: Long) {
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        if (!isPackageHookEnabled(activePkg)) return

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cv = entry.key as? ViewGroup
            val pkgName = entry.value

            if (cv != null && cv.isAttachedToWindow) {
                if (pkgName == activePkg) {
                    cv.post {
                        cv.findViewWithTag<SpaceGateRichLyricLineView>("HYPERLYRIC_LEFT_VIEW")?.setPosition(position)
                        cv.findViewWithTag<SpaceGateRichLyricLineView>("HYPERLYRIC_RIGHT_VIEW")?.setPosition(position)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        xLogDebug("HookIslandSpaceGate : 播放状态变更: isPlaying=$isPlaying")
        val prefs = (module as HookEntry).prefs
        val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)

        if (isPlaying) {
            refreshActiveIsland()
        } else {
            when (behavior) {
                0 -> {
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
                }
            }
        }
    }
}
