package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import com.lidesheng.hyperlyric.root.utils.log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.root.utils.DynamicFinder
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
        log("Entering Preference-Aware Mode.")


        try {
            val contentViewClass = DynamicFinder.findClassByConstantString(cl, "$islandPkg.window.content", "DynamicIslandContentView")
                ?: cl.loadClass("$islandPkg.window.content.DynamicIslandContentView")
            
            contentViewClass.methods.forEach { method ->
                val name = method.name
                if (name == "updateBigIslandView") {
                    try {
                        module.deoptimize(method)
                        module.hook(method).intercept(UpdateBigIslandHooker())
                    } catch (_: Exception) {}
                } else if (name.contains("Width") || name.contains("BigIslandX")) {
                    if (method.parameterTypes.any { it == Int::class.javaPrimitiveType || it == Float::class.javaPrimitiveType }) {
                        try {
                            module.deoptimize(method)
                            module.hook(method).intercept(ViewSetterHooker(name))
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        isHookedSuccess = true
    }

    class ViewSetterHooker(private val methodName: String) : Hooker {
        override fun intercept(chain: Chain): Any? {
            try {
                val arg0 = chain.args[0]
                val view = chain.thisObject as? View ?: return chain.proceed()
                
                val pkgName = activeIslandPkgNames[view]
                val activePkg = LyriconDataBridge.activePackageName
                
                // 增加校验：包名匹配 且 在歌词白名单中开启
                if (pkgName != null && pkgName == activePkg && isPackageHookEnabled(activePkg)) {
                    activeContentView = java.lang.ref.WeakReference(view)
                    
                    val prefs = module.getRemotePreferences(UIConstants.PREF_NAME)
                    val resources = view.resources
                    val density = resources.displayMetrics.density

                    val leftLen = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH)
                    val rightLen = prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH)

                    val leftPx = (leftLen * density).toInt()
                    val rightPx = (rightLen * density).toInt()

                    val cutoutWidth = try { view.javaClass.getMethod("getCutoutWidth").invoke(view) as? Int } catch(_:Exception){null} ?: (80 * density).toInt()
                    val sW = resources.displayMetrics.widthPixels
                    
                    val newViewWidth = leftPx + rightPx + cutoutWidth
                    val newX = (sW / 2) - (cutoutWidth / 2) - leftPx

                    val valueToSet = when {
                        methodName.contains("LeftWidth") -> leftPx
                        methodName.contains("RightWidth") -> rightPx
                        methodName.contains("ViewWidth") -> newViewWidth
                        methodName.contains("BigIslandX") -> newX
                        else -> null
                    }
                    
                    if (valueToSet != null) {
                        if (arg0 is Int) chain.args[0] = valueToSet
                        else if (arg0 is Float) chain.args[0] = valueToSet.toFloat()
                    }
                }
            } catch (_: Exception) {}
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
            
            // 增加校验：包名匹配 且 在歌词白名单中开启
            if (pkgName.isEmpty() || pkgName != activePkg || !isPackageHookEnabled(activePkg)) return result

            activeContentView = java.lang.ref.WeakReference(viewGroup)

            val prefs = module.getRemotePreferences(UIConstants.PREF_NAME)
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
        val prefs = module.getRemotePreferences(UIConstants.PREF_NAME)
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
        val prefs = module.getRemotePreferences(UIConstants.PREF_NAME)
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

        if (mode in 1..8) {
            if (targetView !is RichLyricLineView) {
                targetView?.let { container.removeView(it) }
                targetView = RichLyricLineView(rootView.context).apply {
                    this.tag = tag
                }
                container.addView(targetView)
            }
            configureRichLyricView(targetView, prefs, res, mode)
            
            targetView.line = when(mode) {
                1, 2, 3, 4, 5 -> RichLyricLine(text = singleModeText, words = emptyList())
                6 -> RichLyricLine(text = lyriconSongName, words = emptyList(), secondary = finalArtistName, secondaryWords = emptyList())
                7 -> {
                    val sec = if (finalAlbumName.isEmpty()) finalArtistName else "$finalArtistName - $finalAlbumName"
                    RichLyricLine(text = lyriconSongName, words = emptyList(), secondary = sec, secondaryWords = emptyList())
                }
                8 -> LyriconDataBridge.currentLyricLine ?: RichLyricLine(text = lyriconSongName, words = emptyList())
                else -> null
            }
        }

        val density = res.displayMetrics.density
        val isLeft = parentName.contains("1")
        val maxWidthDp = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH)
                         else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH)
        val pL = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT)
                 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT)
        val pR = if (isLeft) prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT)
                 else prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT)

        targetView.layoutParams = FrameLayout.LayoutParams((maxWidthDp * density).toInt(), FrameLayout.LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        targetView.setPadding((pL * density).toInt(), 0, (pR * density).toInt(), 0)
        targetView.visibility = View.VISIBLE
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
                        val prefs = module.getRemotePreferences(UIConstants.PREF_NAME)
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
                    val prefs = module.getRemotePreferences(UIConstants.PREF_NAME)
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
        refreshActiveIsland()
    }
}
