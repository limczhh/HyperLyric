package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import com.lidesheng.hyperlyric.root.utils.xLog
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.root.utils.DynamicFinder
import com.lidesheng.hyperlyric.root.utils.xLogError
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.view.RichLyricLineConfig
import io.github.proify.lyricon.lyric.view.RichLyricLineView
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import io.github.proify.lyricon.lyric.view.yoyo.animateUpdate

object HookIslandLyric {
    lateinit var module: XposedModule

    var activeContentView: java.lang.ref.WeakReference<View>? = null
    
    @Volatile
    private var isHookedSuccess = false
    
    private val activeIslandPkgNames = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, String>())

    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    fun hook(xposedModule: XposedModule, cl: ClassLoader) {
        if (isHookedSuccess) return
        module = xposedModule
        

        val islandPkg = "miui.systemui.dynamicisland"

        val contentViewClass = try {
            DynamicFinder.findClassByConstantString(cl, "$islandPkg.window.content", "DynamicIslandContentView")
                ?: cl.loadClass("$islandPkg.window.content.DynamicIslandContentView")
        } catch (_: Exception) {
            // 如果此 ClassLoader 中没有这个类，直接返回，稍后尝试其它 ClassLoader
            return
        }

        try {
            contentViewClass.methods.forEach { method ->
                val name = method.name
                if (name == "updateBigIslandView") {
                    try {
                        module.deoptimize(method)
                        module.hook(method).intercept(UpdateBigIslandHooker())
                    } catch (e: Exception) {
                        xLogError("Failed to hook updateBigIslandView", e)
                    }
                } else if (name == "calculateBigIslandWidth") {
                    try {
                        module.deoptimize(method)
                        module.hook(method).intercept(PreInjectHooker())
                    } catch (e: Exception) {
                        xLogError("Failed to hook calculateBigIslandWidth", e)
                    }
                }
            }


            isHookedSuccess = true
            xLog("HyperLyric active: Super Island hooked successfully")
        } catch (e: Exception) {
            xLogError("Exception in HookIslandLyric.hook method processing", e)
        }
    }

    class PreInjectHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            try {
                val islandView = chain.thisObject as? ViewGroup
                if (islandView != null) {
                    val prefs = (module as HookEntry).prefs
                    val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)
                    
                    if (!LyriconDataBridge.isPlaying && behavior == 0) {
                        return chain.proceed()
                    }

                    val pkgName = activeIslandPkgNames[islandView]
                    val activePkg = LyriconDataBridge.activePackageName
                    
                    if (pkgName != null && pkgName == activePkg && isPackageHookEnabled(activePkg)) {
                        applySettings(islandView)
                        val leftMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)
                        val rightMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)
                        
                        injectToSlot(islandView, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", leftMode, prefs, pkgName)
                        injectToSlot(islandView, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", rightMode, prefs, pkgName)
                    }
                }
            } catch (e: Exception) {
                xLogError("Exception in PreInjectHooker", e)
            }
            return chain.proceed()
        }
    }

    class UpdateBigIslandHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val viewGroup = chain.thisObject as? ViewGroup ?: return result
            
            val islandData = chain.args.getOrNull(0)
            var pkgName = ""
            if (islandData != null) {
                try {
                    val getExtrasMethod = islandData.javaClass.getMethod("getExtras")
                    val extras = getExtrasMethod.invoke(islandData) as? android.os.Bundle
                    pkgName = extras?.getString("miui.pkg.name") ?: ""
                } catch (_: Exception) {}
            }
            
            if (pkgName.isNotEmpty()) {
                activeIslandPkgNames[viewGroup] = pkgName
            } else {
                pkgName = activeIslandPkgNames[viewGroup] ?: ""
            }

            val activePkg = LyriconDataBridge.activePackageName
            val prefs = (module as HookEntry).prefs
            val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)

            if (!LyriconDataBridge.isPlaying && behavior == 0) {
                clearInjectedViews(viewGroup)
                return result
            }

            // 增加校验：包名匹配 且 在歌词白名单中开启
            if (pkgName.isEmpty() || pkgName != activePkg || !isPackageHookEnabled(activePkg)) {
                return result
            }

            activeContentView = java.lang.ref.WeakReference(viewGroup)

            applySettings(viewGroup)
            val leftMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)
            val rightMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)
            
            injectToSlot(viewGroup, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", leftMode, prefs, pkgName)
            injectToSlot(viewGroup, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", rightMode, prefs, pkgName)

            return result
        }
    }

    private fun isPackageHookEnabled(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val prefs = (module as HookEntry).prefs
        // 通过已添加列表判断用户是否配置过白名单
        // 如果 addedList 为空，说明用户从未添加过任何应用，默认不放行
        val addedList = prefs.getStringSet(RootConstants.KEY_HOOK_ADDED_LIST, null)
        if (addedList.isNullOrEmpty()) return false
        // 用户已配置过白名单，严格校验：只有在启用集合中的包名才放行
        val whitelist = prefs.getStringSet(RootConstants.KEY_HOOK_WHITELIST, emptySet()) ?: emptySet()
        return whitelist.contains(packageName)
    }

    private fun triggerSystemRelayout(islandView: ViewGroup) {
        val viewClass = islandView.javaClass
        try {
            viewClass.getMethod("updateBigIslandViewWidth").invoke(islandView)
            return
        } catch (_: Exception) {}

        try {
            viewClass.getMethod("calculateBigIslandWidth").invoke(islandView)
        } catch (_: Exception) {}
    }

    private fun applySettings(rootView: ViewGroup) {
        val prefs = (module as HookEntry).prefs
        val showAlbum = prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM)
        val showRhythm = prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON)
        

        toggleContainer(rootView, "island_container_module_image_text_1", "island_container_module_icon", showAlbum)
        toggleContainer(rootView, "island_container_module_image_text_2", "island_container_module_icon", showRhythm)
        
        toggleContainer(rootView, "island_container_module_image_text_1", "island_container_module_text", true)
        toggleContainer(rootView, "island_container_module_image_text_2", "island_container_module_text", true)

        if (!showAlbum) {
            clearTextContainerMargin(rootView, "island_container_module_image_text_1", clearStart = true, clearEnd = false)
        }
        if (!showRhythm) {
            clearTextContainerMargin(rootView, "island_container_module_image_text_2", clearStart = false, clearEnd = true)
        }
    }


    @SuppressLint("DiscouragedApi")
    private fun toggleContainer(root: ViewGroup, parentName: String, containerName: String, show: Boolean) {
        try {
            val res = root.resources
            val pkgNames = arrayOf("miui.systemui.plugin", "com.android.systemui")
            
            var parent: ViewGroup? = null
            for (pkg in pkgNames) {
                val id = res.getIdentifier(parentName, "id", pkg)
                if (id != 0) {
                    parent = root.findViewById(id)
                    if (parent != null) break
                }
            }
            
            if (parent != null) {
                for (pkg in pkgNames) {
                    val id = res.getIdentifier(containerName, "id", pkg)
                    if (id != 0) {
                        parent.findViewById<View>(id)?.visibility = if (show) View.VISIBLE else View.GONE
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("DiscouragedApi")
    private fun clearTextContainerMargin(root: ViewGroup, parentName: String, clearStart: Boolean, clearEnd: Boolean) {
        try {
            val res = root.resources
            val pkgNames = arrayOf("miui.systemui.plugin", "com.android.systemui")
            
            var parent: ViewGroup? = null
            for (pkg in pkgNames) {
                val id = res.getIdentifier(parentName, "id", pkg)
                if (id != 0) {
                    parent = root.findViewById(id)
                    if (parent != null) break
                }
            }
            
            if (parent != null) {
                for (pkg in pkgNames) {
                    val id = res.getIdentifier("island_container_module_text", "id", pkg)
                    if (id != 0) {
                        val textContainer = parent.findViewById<View>(id)
                        if (textContainer != null) {
                            val lp = textContainer.layoutParams as? ViewGroup.MarginLayoutParams
                            if (lp != null) {
                                if (clearStart) lp.marginStart = 0
                                if (clearEnd) lp.marginEnd = 0
                                textContainer.layoutParams = lp
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("DiscouragedApi")
    private fun injectToSlot(rootView: ViewGroup, parentName: String, tag: String, mode: Int, prefs: SharedPreferences, pkgName: String) {
        val res = rootView.resources
        val slotId = res.getIdentifier(parentName, "id", "miui.systemui.plugin")
        if (slotId == 0) return
        val parent = rootView.findViewById<ViewGroup>(slotId) ?: return
        
        val textSlotId = res.getIdentifier("island_container_module_text", "id", "miui.systemui.plugin")
        val container = if (textSlotId != 0) parent.findViewById(textSlotId) ?: parent else parent

        var targetView = container.findViewWithTag<View>(tag)
        
        if (mode == 0) {
            targetView?.visibility = View.GONE
            return
        }

        val lyriconSongName = LyriconDataBridge.currentSongName
        var metadataSongName = ""
        var finalArtistName = ""
        var finalAlbumName = ""

        val targetPkg = LyriconDataBridge.activePackageName ?: pkgName
        if (targetPkg.isNotEmpty()) {
            try {
                val mediaSessionManager = rootView.context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
                val controllers = mediaSessionManager.getActiveSessions(null)
                val controller = controllers.find { it.packageName == targetPkg }
                val mMetadata = controller?.metadata
                if (mMetadata != null) {
                    metadataSongName = mMetadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    finalArtistName = mMetadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    finalAlbumName = mMetadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                }
            } catch (_: Exception) {}
        }

        val singleModeText = when(mode) {
            1 -> metadataSongName
            2 -> lyriconSongName ?: ""
            3 -> finalArtistName
            4 -> finalAlbumName
            5 -> if (finalArtistName.isEmpty()) lyriconSongName ?: "" else "${lyriconSongName ?: ""} - $finalArtistName"
            else -> ""
        }

        val density = res.displayMetrics.density
        val isLeft = parentName.contains("1")
        val maxWidthDp = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH)
                         else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH)
        val pL = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT)
                 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT)
        val pR = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT)
                 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT)

        if (mode in 1..8) {
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
                wrapperView.addView(targetView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
                
                container.addView(wrapperView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
            } else {
                targetView = wrapperView.findViewWithTag(tag) as? RichLyricLineView
                if (targetView == null) {
                    wrapperView.removeAllViews()
                    targetView = RichLyricLineView(rootView.context).apply {
                        this.tag = tag
                    }
                    wrapperView.addView(targetView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    })
                }
            }

            try {
                val maxField = wrapperView.javaClass.getDeclaredField("maxWidthPx")
                maxField.isAccessible = true
                maxField.setInt(wrapperView, (maxWidthDp * density).toInt())
            } catch (_: Exception) {}

            val richView = targetView as RichLyricLineView
            configureRichLyricView(richView, prefs, res, mode)
            
            // 设置内边距和可见性，必须在 measure/layout 之前，否则会导致一帧的位移抖动
            richView.setPadding((pL * density).toInt(), 0, (pR * density).toInt(), 0)
            richView.visibility = View.VISIBLE
            wrapperView.visibility = View.VISIBLE
            
            richView.line = when(mode) {
                1, 2, 3, 4, 5 -> RichLyricLine(text = singleModeText, words = emptyList())
                6 -> RichLyricLine(text = lyriconSongName, words = emptyList(), secondary = finalArtistName, secondaryWords = emptyList())
                7 -> {
                    val sec = if (finalAlbumName.isEmpty()) finalArtistName else "$finalArtistName - $finalAlbumName"
                    RichLyricLine(text = lyriconSongName, words = emptyList(), secondary = sec, secondaryWords = emptyList())
                }
                8 -> LyriconDataBridge.currentLyricLine ?: RichLyricLine(text = lyriconSongName, words = emptyList())
                else -> null
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
        }
    }

    // removed configureTextView

    private fun configureRichLyricView(view: RichLyricLineView, prefs: SharedPreferences, res: android.content.res.Resources, mode: Int) {
        val config = RichLyricLineConfig().apply {
            fadingEdgeLength = prefs.getInt(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, RootConstants.DEFAULT_HOOK_FADING_EDGE_LENGTH)
            gradientProgressStyle = prefs.getBoolean(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS)
            
            placeholderFormat = io.github.proify.lyricon.lyric.view.PlaceholderFormat.NONE
            
            val fontSize = prefs.getInt(RootConstants.KEY_HOOK_TEXT_SIZE, RootConstants.DEFAULT_HOOK_TEXT_SIZE)
            val fontWeight = prefs.getInt(RootConstants.KEY_HOOK_FONT_WEIGHT, RootConstants.DEFAULT_HOOK_FONT_WEIGHT)
            val fontItalic = prefs.getBoolean(RootConstants.KEY_HOOK_FONT_ITALIC, RootConstants.DEFAULT_HOOK_FONT_ITALIC)
            val tf = Typeface.create(Typeface.DEFAULT, fontWeight, fontItalic)
            
            val textSizeRatio = prefs.getFloat(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO)
            val primarySizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat(), res.displayMetrics)
            
            primary.textSize = primarySizePx
            primary.typeface = tf

            val isMetadataDualLine = (mode == 6 || mode == 7)
            val isTranslationVisible = LyriconDataBridge.isDisplayTranslation
            val showSecondary = isMetadataDualLine || isTranslationVisible
            
            secondary.textSize = if (showSecondary) primarySizePx * textSizeRatio else 0f
            secondary.typeface = tf
            if (!showSecondary) {
                secondary.textColor = intArrayOf(Color.TRANSPARENT)
            }
            
            primary.enableRelativeProgress = prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE)
            primary.enableRelativeProgressHighlight = prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT)
            primary.isScrollOnly = false
            
            syllable.enableSustainGlow = true
            
            val isMarqueeEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)
            marquee.disableSyllableScroll = !isMarqueeEnabled
            if (isMarqueeEnabled) {
                marquee.scrollSpeed = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_SPEED).toFloat()
                marquee.initialDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_DELAY)
                marquee.loopDelay = prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_LOOP_DELAY)
                val infinite = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_INFINITE)
                marquee.repeatCount = if (infinite) -1 else 1
                marquee.stopAtEnd = prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_STOP_END, RootConstants.DEFAULT_HOOK_MARQUEE_STOP_END)
            } else {
                marquee.repeatCount = 0
                marquee.scrollSpeed = 0f
            }
        }
        view.setStyle(config)
        view.updateColor(intArrayOf(Color.WHITE), intArrayOf(0x4CFFFFFF), intArrayOf(Color.WHITE))
    }

    fun refreshActiveIsland() {
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        // 增加白名单校验
        if (!isPackageHookEnabled(activePkg)) return

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val cv = entry.key as? ViewGroup
            val pkgName = entry.value

            if (cv != null && cv.isAttachedToWindow) {
                if (pkgName == activePkg) {
                    cv.post {
                        val prefs = (module as HookEntry).prefs
                        cv.findViewWithTag<View>("HYPERLYRIC_LEFT_VIEW_WRAPPER")?.let { (it.parent as? ViewGroup)?.removeView(it) }
                        cv.findViewWithTag<View>("HYPERLYRIC_RIGHT_VIEW_WRAPPER")?.let { (it.parent as? ViewGroup)?.removeView(it) }
                        cv.findViewWithTag<View>("HYPERLYRIC_LEFT_VIEW")?.let { (it.parent as? ViewGroup)?.removeView(it) }
                        cv.findViewWithTag<View>("HYPERLYRIC_RIGHT_VIEW")?.let { (it.parent as? ViewGroup)?.removeView(it) }
                        applySettings(cv)
                        val leftMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)
                        val rightMode = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)
                        injectToSlot(cv, "island_container_module_image_text_1", "HYPERLYRIC_LEFT_VIEW", leftMode, prefs, pkgName)
                        injectToSlot(cv, "island_container_module_image_text_2", "HYPERLYRIC_RIGHT_VIEW", rightMode, prefs, pkgName)
                        triggerSystemRelayout(cv)
                    }
                }
            } else {
                iterator.remove()
            }
        }
    }

    fun updateLyricLine() {
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        // 增加白名单校验
        if (!isPackageHookEnabled(activePkg)) return

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
        if (mode != 8) return
        val view = cv.findViewWithTag<RichLyricLineView>(tag) ?: return
        val targetLine = LyriconDataBridge.currentLyricLine

        cv.post {
            configureRichLyricView(view, prefs, cv.resources, mode)
            val isAnimEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, RootConstants.DEFAULT_HOOK_ANIM_ENABLE)
            val animId = prefs.getString(RootConstants.KEY_HOOK_ANIM_ID, RootConstants.DEFAULT_HOOK_ANIM_ID)

            val applyLine: RichLyricLineView.() -> Unit = {
                line = targetLine
                post {
                    if (prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)) {
                        tryStartMarquee()
                    } else {
                        stopMarquee()
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
    fun updatePosition(position: Long) {
        val iterator = activeIslandPkgNames.entries.iterator()
        val activePkg = LyriconDataBridge.activePackageName ?: return
        
        // 增加白名单校验
        if (!isPackageHookEnabled(activePkg)) return

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
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        val prefs = (module as HookEntry).prefs
        val behavior = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)

        if (isPlaying) {
            refreshActiveIsland()
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
                                clearInjectedViews(cv)
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

    private fun clearInjectedViews(rootView: ViewGroup) {
        rootView.findViewWithTag<View>("HYPERLYRIC_LEFT_VIEW")?.visibility = View.GONE
        rootView.findViewWithTag<View>("HYPERLYRIC_LEFT_VIEW_WRAPPER")?.visibility = View.GONE
        rootView.findViewWithTag<View>("HYPERLYRIC_RIGHT_VIEW")?.visibility = View.GONE
        rootView.findViewWithTag<View>("HYPERLYRIC_RIGHT_VIEW_WRAPPER")?.visibility = View.GONE
        
        // 恢复系统原有组件的可见性
        toggleContainer(rootView, "island_container_module_image_text_1", "island_container_module_icon", true)
        toggleContainer(rootView, "island_container_module_image_text_2", "island_container_module_icon", true)
        
        showOriginalTexts(rootView, "island_container_module_image_text_1")
        showOriginalTexts(rootView, "island_container_module_image_text_2")
    }

    private fun showOriginalTexts(rootView: ViewGroup, parentName: String) {
        val res = rootView.resources
        val slotId = res.getIdentifier(parentName, "id", "miui.systemui.plugin")
        if (slotId == 0) return
        val parent = rootView.findViewById<ViewGroup>(slotId) ?: return
        
        val textSlotId = res.getIdentifier("island_container_module_text", "id", "miui.systemui.plugin")
        val container = if (textSlotId != 0) parent.findViewById(textSlotId) ?: parent else parent

        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val tag = child.tag as? String ?: ""
                if (!tag.startsWith("HYPERLYRIC")) {
                    child.visibility = View.VISIBLE
                }
            }
        }
    }
}