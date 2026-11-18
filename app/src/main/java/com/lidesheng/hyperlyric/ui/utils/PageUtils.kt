package com.lidesheng.hyperlyric.ui.utils

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.glass.glassBottomSheetSurface
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowColors
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun Modifier.pageScrollModifiers(
    enableScrollEndHaptic: Boolean,
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier {
    val state = topAppBarScrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state) { { state.overscrollOffset = it } }
    return this
        .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
        .overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)
        .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
        .fillMaxHeight()
}

val LocalPageStartReserved = staticCompositionLocalOf { false }

@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val safe = WindowInsets.systemBars.union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout).asPaddingValues()
    val startReserved = LocalPageStartReserved.current
    return remember(innerPadding, outerPadding, safe, startReserved, extraTop, extraStart, extraEnd, extraBottom) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
                val reserved = startReserved && layoutDirection == LayoutDirection.Ltr
                val safeSide = if (reserved) 0.dp else safe.calculateLeftPadding(layoutDirection)
                return (if (reserved) 0.dp else maxOf(innerPadding.calculateLeftPadding(layoutDirection),
                    outerPadding.calculateLeftPadding(layoutDirection), safeSide)) +
                    if (layoutDirection == LayoutDirection.Ltr) extraStart else extraEnd
            }
            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
                val reserved = startReserved && layoutDirection == LayoutDirection.Rtl
                val safeSide = if (reserved) 0.dp else safe.calculateRightPadding(layoutDirection)
                return (if (reserved) 0.dp else maxOf(innerPadding.calculateRightPadding(layoutDirection),
                    outerPadding.calculateRightPadding(layoutDirection), safeSide)) +
                    if (layoutDirection == LayoutDirection.Rtl) extraStart else extraEnd
            }
            override fun calculateTopPadding(): Dp = maxOf(innerPadding.calculateTopPadding(),
                outerPadding.calculateTopPadding(), safe.calculateTopPadding()) + extraTop
            override fun calculateBottomPadding(): Dp = maxOf(innerPadding.calculateBottomPadding(),
                outerPadding.calculateBottomPadding(), safe.calculateBottomPadding()) + extraBottom
        }
    }
}

@Composable
fun rememberBlurBackdrop(): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(
            com.lidesheng.hyperlyric.common.UIConstants.PREF_NAME,
            android.content.Context.MODE_PRIVATE
        )
    }

    var isBlurEnabled by remember {
        androidx.compose.runtime.mutableStateOf(
            prefs.getBoolean(
                com.lidesheng.hyperlyric.common.UIConstants.KEY_ENABLE_BLUR,
                com.lidesheng.hyperlyric.common.UIConstants.DEFAULT_ENABLE_BLUR
            )
        )
    }

    val listener = remember {
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == com.lidesheng.hyperlyric.common.UIConstants.KEY_ENABLE_BLUR) {
                isBlurEnabled = p.getBoolean(
                    key,
                    com.lidesheng.hyperlyric.common.UIConstants.DEFAULT_ENABLE_BLUR
                )
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(prefs) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    if (!isBlurEnabled) return null

    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
private val LocalBarBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
private val LocalInGlassBar = staticCompositionLocalOf { false }
private val LocalBarScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
private val LocalBarContentScrolled = staticCompositionLocalOf { false }

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box {
        if (!LocalGlassStyle.current && blurEnabled && backdrop != null) {
            Box(
                modifier = Modifier.matchParentSize().textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors = BlurColors(
                        blendColors = listOf(
                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                        ),
                    ),
                ),
            )
        }
        CompositionLocalProvider(LocalBarBackdrop provides backdrop) { content() }
    }
}

/** Same window-size policy as the ordinary Example navigation shell. */
@Composable
fun isWideAppWindow(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        val width = size.width.toDp()
        val height = size.height.toDp()
        width >= 840.dp || (width >= 600.dp && width > 0.dp && height / width < 1.2f)
    }
}

object GlassBottomBarDefaults {
    const val MAX_ALPHA: Float = 0.90f
    private val stops = List(17) { it / 16f }
    private val opacities = stops.map { t ->
        val fade = t * t * t * (t * (t * 6f - 15f) + 10f)
        MAX_ALPHA * fade
    }

    fun bandBrush(color: Color): Brush {
        val colorStops = stops.zip(opacities).map { (stop, opacity) ->
            stop to color.copy(alpha = color.alpha * opacity)
        }.toTypedArray()
        return Brush.verticalGradient(*colorStops)
    }
}

@Composable
fun rememberAdaptiveBottomBarBandBrush(): Brush {
    val color = MiuixTheme.colorScheme.surface
    return remember(color) { GlassBottomBarDefaults.bandBrush(color) }
}

/** One shared scroll owner and one bar, selected by the application style. */
@Composable
fun AdaptiveTopAppBar(
    title: String,
    color: Color = MiuixTheme.colorScheme.surface,
    scrollBehavior: ScrollBehavior? = null,
    isContentScrolled: Boolean? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val wide = isWideAppWindow()
    val inset = Modifier.windowInsetsPadding(WindowInsets.captionBar.only(WindowInsetsSides.Top))
    if (LocalGlassStyle.current) {
        val backdrop = LocalBarBackdrop.current
        val contentScrolled = isContentScrolled ?: ((scrollBehavior?.state?.contentOffset ?: 0f) < 0f)
        GlassTopAppBar(
            title = title,
            isContentScrolled = contentScrolled,
            modifier = inset,
            expandable = !wide,
            progressiveBlur = (backdrop != null),
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            navigationIcon = { CompositionLocalProvider(LocalInGlassBar provides true) { navigationIcon() } },
            actions = { CompositionLocalProvider(LocalInGlassBar provides true) { actions() } },
            bottomContent = { CompositionLocalProvider(
                LocalBarBackdrop provides backdrop,
                LocalBarScrollBehavior provides scrollBehavior,
                LocalBarContentScrolled provides contentScrolled,
            ) { bottomContent() } },
        )
    } else {
        TopAppBar(
            title = title,
            modifier = inset,
            expandable = !wide,
            color = color,
            scrollBehavior = scrollBehavior,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

@Composable
fun AdaptiveSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    titleColor: Color = Color.Unspecified,
    titleAlpha: (() -> Float)? = null,
    subtitle: String = "",
    subtitleColor: Color = Color.Unspecified,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    isContentScrolled: Boolean? = null,
    defaultWindowInsetsPadding: Boolean = true,
) {
    if (LocalGlassStyle.current) {
        val backdrop = LocalBarBackdrop.current
        val contentScrolled = isContentScrolled ?: ((scrollBehavior?.state?.contentOffset ?: 0f) < 0f)
        val resolvedTitleAlpha: () -> Float = if (titleAlpha != null) {
            titleAlpha
        } else if (titleColor != Color.Unspecified && titleColor.alpha < 1f) {
            { titleColor.alpha }
        } else {
            { 1f }
        }
        GlassTopAppBar(
            title = title,
            modifier = modifier,
            expandable = false,
            isContentScrolled = contentScrolled,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            progressiveBlur = (backdrop != null),
            titleAlpha = resolvedTitleAlpha,
            backgroundAlpha = { if (!contentScrolled) 0f else 1f },
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            navigationIcon = { CompositionLocalProvider(LocalInGlassBar provides true) { navigationIcon() } },
            actions = { CompositionLocalProvider(LocalInGlassBar provides true) { actions() } },
        )
    } else {
        top.yukonga.miuix.kmp.basic.SmallTopAppBar(
            title = title,
            modifier = modifier,
            color = color,
            titleColor = if (titleColor != Color.Unspecified) titleColor else MiuixTheme.colorScheme.onSurface,
            subtitle = subtitle,
            subtitleColor = if (subtitleColor != Color.Unspecified) subtitleColor else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        )
    }
}

@Composable
fun AdaptiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    surfaceAlpha: Float = 1f,
    shadow: GlassShadow? = GlassShadows.Regular,
    content: @Composable () -> Unit,
) {
    if (LocalInGlassBar.current && enabled && !holdDownState && backgroundColor == Color.Unspecified &&
        cornerRadius == IconButtonDefaults.CornerRadius && minHeight == IconButtonDefaults.MinHeight && minWidth == IconButtonDefaults.MinWidth) {
        GlassIconButton(
            onClick = onClick,
            modifier = modifier,
            backdrop = LocalBarBackdrop.current,
            surfaceAlpha = surfaceAlpha,
            shadow = shadow,
            content = content,
        )
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, holdDownState = holdDownState,
            backgroundColor = backgroundColor, cornerRadius = cornerRadius, minHeight = minHeight, minWidth = minWidth, content = content)
    }
}

private object NoOpBackdrop : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackdrop(
        density: androidx.compose.ui.unit.Density,
        coordinates: androidx.compose.ui.layout.LayoutCoordinates?,
        layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {}
}

@Composable
fun AdaptiveTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: TabRowColors = TabRowDefaults.tabRowColors(),
) {
    if (LocalGlassStyle.current) {
        val rawBackdrop = LocalBarBackdrop.current ?: LocalAppBackdrop.current
        val backdrop = rawBackdrop ?: NoOpBackdrop
        val inBar = LocalInGlassBar.current || LocalBarScrollBehavior.current != null
        val contentScrolled = LocalBarContentScrolled.current
        val scrollBehavior = LocalBarScrollBehavior.current

        // 浮起动画与过度滚动（overscroll）弹性感知
        val transition = updateTransition(contentScrolled, label = "adaptiveTabRowFloat")
        val scrolledMaterialProgress = transition.animateFloat(
            transitionSpec = { GlassMotion.topBarButtonFloat() },
            label = "adaptiveTabRowMaterial",
        ) { if (it) 1f else 0f }
        val ramp = GlassTopAppBarDefaults.collapseRamp(scrollBehavior)
        val materialProgress by remember(scrolledMaterialProgress, scrollBehavior, ramp) {
            derivedStateOf {
                val elasticProgress = if (scrollBehavior != null && scrollBehavior.state.overscrollOffset != 0f) {
                    ramp.value
                } else {
                    0f
                }
                maxOf(scrolledMaterialProgress.value, elasticProgress)
            }
        }
        val surfaceAlpha = if (inBar) materialProgress else 1f

        GlassSegmentedTabRow(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            onSelect = onTabSelected,
            backdrop = backdrop,
            surfaceAlpha = surfaceAlpha,
            modifier = modifier,
        )
    } else {
        TabRow(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = modifier,
            colors = colors,
        )
    }
}

@Composable
fun AdaptiveBottomSheet(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.surface,
    enableNestedScroll: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val glassStyle = LocalGlassStyle.current
    val backdrop = LocalAppBackdrop.current
    OverlayBottomSheet(
        show = show,
        title = title,
        modifier = modifier,
        backgroundColor = backgroundColor,
        enableNestedScroll = enableNestedScroll,
        startAction = startAction,
        endAction = endAction,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        surface = if (glassStyle) glassBottomSheetSurface(backdrop = backdrop) else null,
        content = content,
    )
}

@Composable
fun AdaptiveOverlayDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    if (LocalGlassStyle.current) {
        GlassDropdownPreference(
            onSelectedIndexChange = { index -> onSelectedIndexChange?.invoke(index) },
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            backdrop = LocalAppBackdrop.current,
            modifier = modifier,
            enabled = enabled,
            showValue = showValue,
            renderInRootScaffold = renderInRootScaffold,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            sizing = if (maxHeight == null) {
                GlassPopupSizing()
            } else {
                GlassPopupSizing(maxHeight = maxHeight)
            },
            onExpandedChange = onExpandedChange,
        )
    } else {
        top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            dropdownColors = dropdownColors,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            renderInRootScaffold = renderInRootScaffold,
            onExpandedChange = onExpandedChange,
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}
