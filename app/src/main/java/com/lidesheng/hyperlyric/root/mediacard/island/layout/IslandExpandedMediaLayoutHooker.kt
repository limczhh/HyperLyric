package com.lidesheng.hyperlyric.root.mediacard.island.layout

import android.content.Context
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros.IslandExpandedMediaColorOsLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros.IslandExpandedMediaColorOsMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.ios.IslandExpandedMediaIosLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.island.layout.ios.IslandExpandedMediaIosMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui.IslandExpandedMediaOneUiLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.island.layout.miui.IslandExpandedMediaMiuiLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel.IslandExpandedMediaPixelLayoutPreset
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object IslandExpandedMediaLayoutHooker {
    private const val TAG = "IslandExpandedMediaLayoutHooker"
    private const val CONSTRAINT_SET_CLASS = "androidx.constraintlayout.widget.ConstraintSet"
    private const val TARGET_XML = "miui_media_session_island_normal"

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val nativeApis = Collections.synchronizedMap(WeakHashMap<ClassLoader, NativeApi>())
    private val unavailableNativeApis = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

        val api = try {
            NativeApi.create(classLoader)
        } catch (error: Exception) {
            hookedClassLoaders.remove(classLoader)
            if (unavailableNativeApis.add(classLoader)) {
                HookLogger.w(TAG, "跳过超级岛展开态媒体布局 Hook: reason=${error.message}")
            }
            return
        }
        nativeApis[classLoader] = api

        val handles = mutableListOf<HookHandle>()
        api.constraintSetLoadMethods.forEach { method ->
            handles += xposedModule.managedHook(
                executable = method,
                capability = "media.island.layout.constraint_set",
                hooker = ConstraintSetLoadHook(),
            )
        }
        // Keep the same two layout lifecycle points as XiaomiHelper: the media
        // controller reinflates its cached set, and each player creates its own
        // set.  Both the real and dummy player are covered without touching the
        // Fake View animation path.
        api.reInflateMethods.forEach { method ->
            handles += xposedModule.managedHook(
                executable = method,
                capability = "media.island.layout.reinflate",
                hooker = ReinflateHook(api),
            )
        }
        api.playerConstructors.forEach { constructor ->
            handles += xposedModule.managedHook(
                executable = constructor,
                capability = "media.island.layout.player_constructor",
                hooker = PlayerConstructorHook(api),
            )
        }
        HookLogger.d(TAG, "超级岛展开态媒体布局 Hook 已初始化: methods=${handles.size}")
    }

    private class ConstraintSetLoadHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val resourceId = chain.args.getOrNull(0) as? Int ?: return chain.proceed()
            val context = chain.args.getOrNull(1) as? Context ?: return chain.proceed()
            if (!context.isTargetIslandLayout(resourceId)) return chain.proceed()

            val result = chain.proceed()
            val constraintSet = chain.thisObject ?: return result
            val api = resolveApi(constraintSet.javaClass.classLoader) ?: return result
            api.applyCurrentLayout(constraintSet, context)
            return result
        }
    }

    /** Mutates the rebuilt set, then applies the same set to real and dummy players. */
    private class ReinflateHook(private val api: NativeApi) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result
            runCatching {
                val controller = chain.thisObject ?: return@runCatching
                val context = api.readContext(controller) ?: return@runCatching
                val layout = api.readNormalLayout(controller) ?: return@runCatching
                api.applyCurrentLayout(layout, context)
                api.applyToPlayers(controller, layout, context)
                api.hideCoverSourceOnPlayers(controller)
            }.onFailure { HookLogger.e(TAG, "同步超级岛 real/dummy 媒体布局失败", it) }
            return result
        }
    }

    /** Covers a player created after the controller has already cached the layout set. */
    private class PlayerConstructorHook(private val api: NativeApi) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result
            runCatching {
                val player = chain.thisObject ?: return@runCatching
                val context = chain.args.firstOrNull() as? Context ?: return@runCatching
                api.readNormalLayout(player)?.let { layout ->
                    api.applyCurrentLayout(layout, context)
                    api.syncAlbumArt(player, context)
                }
            }.onFailure { HookLogger.e(TAG, "初始化超级岛媒体布局失败", it) }
            return result
        }
    }

    private class NativeApi private constructor(
        val constraintSetLoadMethods: List<Method>,
        val reInflateMethods: List<Method>,
        val playerConstructors: List<Constructor<*>>,
        private val connectMethod: Method,
        private val setMarginMethod: Method,
        private val setGoneMarginMethod: Method?,
        private val clearMethod: Method,
        private val setVisibilityMethod: Method?,
        private val setScaleXMethod: Method?,
        private val setScaleYMethod: Method?,
        private val constrainWidthMethod: Method?,
        private val constrainHeightMethod: Method?,
        private val applyToMethod: Method?
    ) : IslandExpandedMediaConstraintBridge {
        override val supportsDeviceSlotReplacement: Boolean
            get() = setVisibilityMethod != null

        override val supportsDimensionTuning: Boolean
            get() = constrainWidthMethod != null && constrainHeightMethod != null

        fun applyCurrentLayout(constraintSet: Any, context: Context) {
            if (!MediaCardRuntimeConfig.current.enabled) return
            runCatching {
                val ids = IslandExpandedMediaLayoutResourceIds.from(context)
                val environment = IslandExpandedMediaLayoutEnvironment(
                    bridge = this,
                    layout = constraintSet,
                    ids = ids,
                    context = context,
                    coverHidden =
                        MediaCardRuntimeConfig.current.islandExpanded.coverStyle ==
                            RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN,
                    hideDeviceSwitch = hideDeviceSwitch()
                )
                when (MediaCardRuntimeConfig.current.islandExpanded.layoutStyle) {
                    RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_IOS ->
                        IslandExpandedMediaIosLayoutPreset.apply(environment)

                    RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS ->
                        IslandExpandedMediaColorOsLayoutPreset.apply(environment)

                    RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI ->
                        IslandExpandedMediaOneUiLayoutPreset.apply(environment)

                    RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_MIUI ->
                        IslandExpandedMediaMiuiLayoutPreset.apply(environment)

                    RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_PIXEL ->
                        IslandExpandedMediaPixelLayoutPreset.apply(environment)
                }
                applyElementOverrides(constraintSet, context, ids)
            }.onFailure { HookLogger.e(TAG, "应用超级岛媒体布局失败", it) }
        }

        fun readContext(controller: Any): Context? =
            findField(controller.javaClass, "context")?.get(controller) as? Context

        fun readNormalLayout(owner: Any): Any? =
            findField(owner.javaClass, "normalLayoutIsland")?.get(owner)

        fun applyToPlayers(controller: Any, constraintSet: Any, context: Context) {
            val style = MediaCardRuntimeConfig.current.islandExpanded.layoutStyle
            val albumArtSizeDp = albumArtSizeDp(style)
            if (
                albumArtSizeDp == null &&
                style != RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI &&
                style != RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_MIUI &&
                style != RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_PIXEL
            ) return
            val ids = runCatching {
                IslandExpandedMediaLayoutResourceIds.from(context)
            }.getOrNull() ?: return
            listOf("miuiPlayerHolder", "miuiDummyPlayerHolder")
                .mapNotNull { fieldName ->
                    findField(controller.javaClass, fieldName)?.get(controller)
                }
                .mapNotNull { holder -> findField(holder.javaClass, "player")?.get(holder) }
                .forEach { player ->
                    applyTo(constraintSet, player)
                    (player as? View)?.let {
                        albumArtSizeDp?.let { sizeDp ->
                            IslandExpandedMediaAlbumArtSync.apply(
                                player = it,
                                context = context,
                                ids = ids,
                                sizeDp = sizeDp
                            )
                        }
                    }
                }
        }

        fun syncAlbumArt(player: Any, context: Context) {
            val sizeDp = albumArtSizeDp(
                MediaCardRuntimeConfig.current.islandExpanded.layoutStyle
            ) ?: return
            val view = player as? View ?: return
            val ids = runCatching { IslandExpandedMediaLayoutResourceIds.from(context) }
                .getOrNull() ?: return
            IslandExpandedMediaAlbumArtSync.apply(view, context, ids, sizeDp)
        }

        private fun albumArtSizeDp(style: Int): Float? = when (style) {
            RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_IOS ->
                IslandExpandedMediaIosMetrics.COVER_SIZE_DP

            RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS ->
                IslandExpandedMediaColorOsMetrics.COVER_SIZE_DP

            RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI ->
                null

            RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_PIXEL ->
                null

            else -> null
        }

        fun applyTo(constraintSet: Any, player: Any) {
            applyToMethod?.invoke(constraintSet, player)
        }

        fun hideCoverSourceOnPlayers(controller: Any) {
            if (!MediaCardRuntimeConfig.current.islandExpanded.hideCoverSource) return
            listOf("miuiPlayerHolder", "miuiDummyPlayerHolder")
                .mapNotNull { fieldName ->
                    findField(controller.javaClass, fieldName)?.get(
                        controller
                    )
                }
                .mapNotNull { holder ->
                    findField(
                        holder.javaClass,
                        "appIcon"
                    )?.get(holder) as? View
                }
                .forEach { source -> source.visibility = View.GONE }
        }

        fun applyElementOverrides(
            constraintSet: Any,
            context: Context,
            ids: IslandExpandedMediaLayoutResourceIds
        ) {
            val config = MediaCardRuntimeConfig.current.islandExpanded
            val standardMargin = context.islandExpandedMediaDp(26f)
            val action0 = ids.actionButtons[0]
            val action1 = ids.actionButtons[1]
            val action2 = ids.actionButtons[2]
            val action3 = ids.actionButtons[3]
            val action4 = ids.actionButtons[4]
            val ordered = when (config.actionOrder) {
                RootConstants.ISLAND_EXPANDED_MEDIA_ACTION_ORDER_CUSTOM_RIGHT ->
                    listOf(action1, action2, action3, action0, action4)

                RootConstants.ISLAND_EXPANDED_MEDIA_ACTION_ORDER_PLAY_LEFT ->
                    listOf(action2, action1, action3, action0, action4)

                else -> emptyList()
            }
            if (ordered.isNotEmpty()) {
                ordered.forEachIndexed { index, viewId ->
                    val leftTarget = ordered.getOrNull(index - 1) ?: ids.actions
                    val rightTarget = ordered.getOrNull(index + 1) ?: ids.actions
                    val leftSide = if (index == 0) {
                        IslandExpandedMediaConstraintSide.LEFT
                    } else {
                        IslandExpandedMediaConstraintSide.RIGHT
                    }
                    val rightSide = if (index == ordered.lastIndex) {
                        IslandExpandedMediaConstraintSide.RIGHT
                    } else {
                        IslandExpandedMediaConstraintSide.LEFT
                    }
                    connect(
                        constraintSet,
                        viewId,
                        IslandExpandedMediaConstraintSide.LEFT,
                        leftTarget,
                        leftSide
                    )
                    connect(
                        constraintSet,
                        viewId,
                        IslandExpandedMediaConstraintSide.RIGHT,
                        rightTarget,
                        rightSide
                    )
                }
                setMargin(
                    constraintSet,
                    action0,
                    IslandExpandedMediaConstraintSide.START,
                    0
                )
                setMargin(
                    constraintSet,
                    ordered.first(),
                    IslandExpandedMediaConstraintSide.START,
                    context.islandExpandedMediaDp(6f)
                )
            }
            if (config.actionAlignLeft) {
                clear(
                    constraintSet,
                    action4,
                    IslandExpandedMediaConstraintSide.RIGHT
                )
            }
            if (config.hideCustomActions) {
                setVisibility(constraintSet, action0, View.INVISIBLE)
                val keepAction4Slot =
                    config.layoutStyle in setOf(
                        RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_IOS,
                        RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS
                    ) && !config.hideDeviceSwitch
                if (!keepAction4Slot) {
                    setVisibility(constraintSet, action4, View.INVISIBLE)
                }
            }
            if (config.coverStyle == RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN) {
                // This is the same ConstraintSet-based hidden-album layout used
                // by XiaomiHelper.  It survives creation of the dummy player,
                // unlike changing the bound View after the fact.
                setGoneMargin(
                    constraintSet,
                    ids.headerTitle,
                    IslandExpandedMediaConstraintSide.START,
                    standardMargin
                )
                setGoneMargin(
                    constraintSet,
                    ids.headerArtist,
                    IslandExpandedMediaConstraintSide.START,
                    standardMargin
                )
                setGoneMargin(
                    constraintSet,
                    ids.actions,
                    IslandExpandedMediaConstraintSide.TOP,
                    context.islandExpandedMediaDp(67.5f)
                )
                setGoneMargin(
                    constraintSet,
                    action0,
                    IslandExpandedMediaConstraintSide.TOP,
                    context.islandExpandedMediaDp(78.5f)
                )
                setVisibility(constraintSet, ids.albumArt, View.GONE)
            }
            if (config.hideDeviceSwitch) {
                // ColorOS uses the top seamless slot for the app identity icon;
                // only the native output-device affordance is moved to Action4.
                if (config.layoutStyle !=
                    RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS &&
                    config.layoutStyle != RootConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI
                ) {
                    setVisibility(constraintSet, ids.mediaSeamless, View.GONE)
                    setGoneMargin(
                        constraintSet,
                        ids.headerTitle,
                        IslandExpandedMediaConstraintSide.END,
                        standardMargin
                    )
                    setGoneMargin(
                        constraintSet,
                        ids.headerArtist,
                        IslandExpandedMediaConstraintSide.END,
                        standardMargin
                    )
                }
            }
            if (config.hideTime) {
                connect(
                    constraintSet,
                    ids.mediaProgressBar,
                    IslandExpandedMediaConstraintSide.LEFT,
                    0,
                    IslandExpandedMediaConstraintSide.LEFT
                )
                connect(
                    constraintSet,
                    ids.mediaProgressBar,
                    IslandExpandedMediaConstraintSide.RIGHT,
                    0,
                    IslandExpandedMediaConstraintSide.RIGHT
                )
                setMargin(
                    constraintSet,
                    ids.mediaProgressBar,
                    IslandExpandedMediaConstraintSide.LEFT,
                    context.islandExpandedMediaDp(26f)
                )
                setMargin(
                    constraintSet,
                    ids.mediaProgressBar,
                    IslandExpandedMediaConstraintSide.RIGHT,
                    context.islandExpandedMediaDp(26f)
                )
                setVisibility(constraintSet, ids.mediaElapsedTime, View.GONE)
                setVisibility(constraintSet, ids.mediaTotalTime, View.GONE)
            }
        }

        override fun connect(
            layout: Any,
            startId: Int,
            startSide: Int,
            endId: Int,
            endSide: Int
        ) {
            connectMethod.invoke(layout, startId, startSide, endId, endSide)
        }

        override fun setMargin(layout: Any, viewId: Int, side: Int, margin: Int) {
            setMarginMethod.invoke(layout, viewId, side, margin)
        }

        override fun setGoneMargin(layout: Any, viewId: Int, side: Int, margin: Int) {
            setGoneMarginMethod?.invoke(layout, viewId, side, margin)
        }

        override fun clear(layout: Any, viewId: Int, side: Int) {
            clearMethod.invoke(layout, viewId, side)
        }

        override fun setVisibility(layout: Any, viewId: Int, visibility: Int) {
            setVisibilityMethod?.invoke(layout, viewId, visibility)
        }

        override fun setScale(layout: Any, viewId: Int, scale: Float) {
            setScaleXMethod?.invoke(layout, viewId, scale)
            setScaleYMethod?.invoke(layout, viewId, scale)
        }

        override fun constrainWidth(layout: Any, viewId: Int, width: Int) {
            constrainWidthMethod?.invoke(layout, viewId, width)
        }

        override fun constrainHeight(layout: Any, viewId: Int, height: Int) {
            constrainHeightMethod?.invoke(layout, viewId, height)
        }

        companion object {
            fun create(classLoader: ClassLoader): NativeApi {
                val constraintSetClass = classLoader.loadClass(CONSTRAINT_SET_CLASS)
                val load = constraintSetClass.getDeclaredMethod(
                    "load",
                    Int::class.javaPrimitiveType,
                    Context::class.java
                ).apply { isAccessible = true }
                val integer = requireNotNull(Int::class.javaPrimitiveType)
                val float = requireNotNull(Float::class.javaPrimitiveType)
                val islandControllerClass = classLoader.loadClassOrNull(
                    "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaControllerImpl"
                )
                val playerClass = classLoader.loadClassOrNull(
                    "com.android.systemui.statusbar.notification.mediaisland.PlayerIslandConstraintLayout"
                )
                return NativeApi(
                    constraintSetLoadMethods = listOf(load),
                    reInflateMethods = islandControllerClass?.declaredMethods
                        ?.filter { it.name.startsWith("reInflateView") }
                        ?.onEach { it.isAccessible = true }
                        .orEmpty(),
                    playerConstructors = playerClass?.declaredConstructors
                        ?.filter { it.parameterCount == 3 }
                        ?.onEach { it.isAccessible = true }
                        .orEmpty(),
                    connectMethod = constraintSetClass.getDeclaredMethod(
                        "connect",
                        integer,
                        integer,
                        integer,
                        integer
                    ).apply { isAccessible = true },
                    setMarginMethod = constraintSetClass.getDeclaredMethod(
                        "setMargin",
                        integer,
                        integer,
                        integer
                    ).apply { isAccessible = true },
                    setGoneMarginMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "setGoneMargin" &&
                                method.parameterTypes.contentEquals(
                                    arrayOf(
                                        integer,
                                        integer,
                                        integer
                                    )
                                )
                    }?.apply { isAccessible = true },
                    clearMethod = constraintSetClass.getDeclaredMethod(
                        "clear",
                        integer,
                        integer
                    ).apply { isAccessible = true },
                    setVisibilityMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "setVisibility" &&
                                method.parameterTypes.contentEquals(arrayOf(integer, integer))
                    }?.apply { isAccessible = true },
                    setScaleXMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "setScaleX" &&
                                method.parameterTypes.contentEquals(arrayOf(integer, float))
                    }?.apply { isAccessible = true },
                    setScaleYMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "setScaleY" &&
                                method.parameterTypes.contentEquals(arrayOf(integer, float))
                    }?.apply { isAccessible = true },
                    constrainWidthMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "constrainWidth" &&
                                method.parameterTypes.contentEquals(arrayOf(integer, integer))
                    }?.apply { isAccessible = true },
                    constrainHeightMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "constrainHeight" &&
                                method.parameterTypes.contentEquals(arrayOf(integer, integer))
                    }?.apply { isAccessible = true },
                    applyToMethod = constraintSetClass.declaredMethods.find { method ->
                        method.name == "applyTo" && method.parameterCount == 1
                    }?.apply { isAccessible = true }
                )
            }

            private fun ClassLoader.loadClassOrNull(name: String): Class<*>? =
                runCatching { loadClass(name) }.getOrNull()

            private fun findField(type: Class<*>, name: String): Field? {
                var current: Class<*>? = type
                while (current != null) {
                    runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                        field.isAccessible = true
                        return field
                    }
                    current = current.superclass
                }
                return null
            }
        }
    }

    private fun resolveApi(classLoader: ClassLoader?): NativeApi? {
        classLoader ?: return null
        synchronized(nativeApis) { nativeApis[classLoader] }?.let { return it }
        return try {
            NativeApi.create(classLoader).also {
                nativeApis[classLoader] = it
                unavailableNativeApis.remove(classLoader)
            }
        } catch (error: Exception) {
            if (unavailableNativeApis.add(classLoader)) {
                HookLogger.w(TAG, "超级岛展开态媒体布局原生接口不可用: reason=${error.message}")
            }
            null
        }
    }

    private fun hideDeviceSwitch(): Boolean {
        return MediaCardRuntimeConfig.current.islandExpanded.hideDeviceSwitch
    }

    private fun Context.isTargetIslandLayout(resourceId: Int): Boolean {
        return runCatching {
            packageName == "com.android.systemui" &&
                    resources.getResourceTypeName(resourceId) == "xml" &&
                    resources.getResourceEntryName(resourceId) == TARGET_XML
        }.getOrDefault(false)
    }

}
