package com.lidesheng.hyperlyric.ui.utils

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.material.GlassShadow
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassCardDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.layout.SheetMotion
import com.lidesheng.hyperlyric.ui.component.LocalGlassDialog
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowColors
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
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

    return rememberLayerBackdrop(backgroundColor = MiuixTheme.colorScheme.surface)
}

val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
private val LocalBarBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
private val LocalInGlassBar = staticCompositionLocalOf { false }
private val LocalInGlassBottomContent = staticCompositionLocalOf { false }
private val LocalBottomSheetBlurActive = staticCompositionLocalOf { false }
private val LocalBarScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }
private val LocalBarContentScrolled = staticCompositionLocalOf { false }

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop? = null,
    blurEnabled: Boolean = LocalBlurEnabled.current,
    content: @Composable () -> Unit,
) {
    val actualBlurEnabled = blurEnabled && LocalBlurEnabled.current
    val actualBackdrop = if (actualBlurEnabled) {
        backdrop ?: LocalBarBackdrop.current ?: LocalAppBackdrop.current
    } else {
        null
    }
    Box {
        if (!LocalGlassStyle.current && actualBlurEnabled && actualBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .textureBlur(
                        backdrop = actualBackdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.85f)),
                            ),
                        ),
                    ),
            )
        }
        CompositionLocalProvider(LocalBarBackdrop provides actualBackdrop) { content() }
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
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    scrollBehavior: ScrollBehavior? = null,
    isContentScrolled: Boolean? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val wide = isWideAppWindow()
    val contentScrolled by remember(scrollBehavior, isContentScrolled) {
        derivedStateOf {
            (isContentScrolled ?: ((scrollBehavior?.state?.contentOffset ?: 0f) < -1f)) ||
                (scrollBehavior?.state?.overscrollOffset ?: 0f) < -1f
        }
    }
    if (LocalGlassStyle.current) {
        val rawBackdrop = LocalBarBackdrop.current
        val isBlurActive = rawBackdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()
        val backdrop = if (isBlurActive) rawBackdrop else null
        val surfaceColor = MiuixTheme.colorScheme.surface
        val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
        val resolvedBackgroundAlpha: () -> Float = if (!isBlurActive || scrollBehavior != null || contentScrolled) {
            { 1f }
        } else {
            { 0f }
        }
        GlassTopAppBar(
            title = title,
            contentScrolled,
            modifier = modifier,
            contentModifier = contentModifier,
            expandable = !wide,
            progressiveBlur = isBlurActive,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            backgroundAlpha = resolvedBackgroundAlpha,
            bandBrush = bandBrush,
            bandOverhang = GlassTopAppBarDefaults.BandOverhang,
            navigationIcon = { CompositionLocalProvider(LocalInGlassBar provides true) { navigationIcon() } },
            actions = { CompositionLocalProvider(LocalInGlassBar provides true) { actions() } },
            bottomContent = {
                CompositionLocalProvider(
                    LocalBarBackdrop provides backdrop,
                    LocalInGlassBottomContent provides true,
                    LocalBarScrollBehavior provides scrollBehavior,
                    LocalBarContentScrolled provides contentScrolled,
                ) { bottomContent() }
            },
        )
    } else {
        val resolvedColor = if (color != Color.Unspecified && color != Color.Transparent) {
            color
        } else if (LocalBlurEnabled.current && LocalBarBackdrop.current != null) {
            if (!contentScrolled) MiuixTheme.colorScheme.surface else Color.Transparent
        } else {
            MiuixTheme.colorScheme.surface
        }
        TopAppBar(
            title = title,
            modifier = modifier.then(contentModifier),
            expandable = !wide,
            color = resolvedColor,
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
    val contentScrolled by remember(scrollBehavior, isContentScrolled) {
        derivedStateOf {
            (isContentScrolled ?: ((scrollBehavior?.state?.contentOffset ?: 0f) < -1f)) ||
                (scrollBehavior?.state?.overscrollOffset ?: 0f) < -1f
        }
    }
    if (LocalGlassStyle.current) {
        val rawBackdrop = LocalBarBackdrop.current
        val isBlurActive = rawBackdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()
        val backdrop = if (isBlurActive) rawBackdrop else null
        val surfaceColor = MiuixTheme.colorScheme.surface
        val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
        val resolvedTitleAlpha: () -> Float = if (titleAlpha != null) {
            titleAlpha
        } else if (titleColor != Color.Unspecified && titleColor.alpha < 1f) {
            { titleColor.alpha }
        } else {
            { 1f }
        }
        val resolvedBackgroundAlpha: () -> Float = if (!isBlurActive || scrollBehavior != null || contentScrolled) {
            { 1f }
        } else {
            { 0f }
        }
        GlassTopAppBar(
            title = title,
            contentScrolled,
            modifier = modifier,
            expandable = false,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            progressiveBlur = isBlurActive,
            titleAlpha = resolvedTitleAlpha,
            backgroundAlpha = resolvedBackgroundAlpha,
            bandBrush = bandBrush,
            bandOverhang = GlassTopAppBarDefaults.BandOverhang,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            navigationIcon = { CompositionLocalProvider(LocalInGlassBar provides true) { navigationIcon() } },
            actions = { CompositionLocalProvider(LocalInGlassBar provides true) { actions() } },
        )
    } else {
        val resolvedColor = if (color != Color.Transparent) {
            color
        } else if (LocalBlurEnabled.current && LocalBarBackdrop.current != null) {
            if (!contentScrolled) MiuixTheme.colorScheme.surface else Color.Transparent
        } else {
            MiuixTheme.colorScheme.surface
        }
        top.yukonga.miuix.kmp.basic.SmallTopAppBar(
            title = title,
            modifier = modifier,
            color = resolvedColor,
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
            surfaceAlpha = surfaceAlpha,
            shadow = shadow,
            content = content,
        )
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, holdDownState = holdDownState,
            backgroundColor = backgroundColor, cornerRadius = cornerRadius, minHeight = minHeight, minWidth = minWidth, content = content)
    }
}

@Composable
fun AdaptiveTopBarIcon(
    normalIcon: ImageVector,
    glassIcon: ImageVector? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val isGlassStyle = LocalGlassStyle.current
    Icon(
        imageVector = if (isGlassStyle && glassIcon != null) glassIcon else normalIcon,
        contentDescription = contentDescription,
        modifier = modifier.then(if (isGlassStyle) Modifier.size(28.dp) else Modifier),
        tint = tint,
    )
}

@Composable
fun adaptiveBackIcon(): ImageVector = adaptiveTopBarIcon(
    normalIcon = MiuixIcons.Back,
    glassIcon = MiuixIcons.Glass.ChevronBackward,
)

@Composable
private fun adaptiveTopBarIcon(normalIcon: ImageVector, glassIcon: ImageVector): ImageVector =
    if (LocalGlassStyle.current) glassIcon else normalIcon

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
        val inBar = LocalInGlassBar.current || LocalInGlassBottomContent.current || LocalBarScrollBehavior.current != null
        val rawBackdrop = if (inBar) LocalBarBackdrop.current else null
        val isBlurActive = rawBackdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()
        val backdrop = if (isBlurActive) rawBackdrop else NoOpBackdrop
        val surfaceAlpha = if (inBar && isBlurActive && LocalBarContentScrolled.current) 1f else 0f

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

@Stable
class NavPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedIndex by mutableIntStateOf(pagerState.currentPage)
        private set

    val selectedPage: Int
        get() = selectedIndex

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedIndex) return

        navJob?.cancel()
        selectedIndex = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
                    val duration = 100 * distance + 100
                    val layoutInfo = pagerState.layoutInfo
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val currentDistanceInPages =
                        targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val scrollPixels = currentDistanceInPages * pageSize

                    var previousValue = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = scrollPixels,
                        animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }

                if (pagerState.currentPage != targetIndex) {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedIndex = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun scrollToPage(targetIndex: Int) {
        if (targetIndex == selectedIndex) return

        navJob?.cancel()
        selectedIndex = targetIndex
        isNavigating = false

        coroutineScope.launch {
            pagerState.scrollToPage(targetIndex)
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedIndex != pagerState.currentPage) {
            selectedIndex = pagerState.currentPage
        }
    }
}

@Composable
fun rememberNavPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): NavPagerState {
    val state = remember(pagerState, coroutineScope) {
        NavPagerState(pagerState, coroutineScope)
    }
    LaunchedEffect(pagerState.currentPage) {
        state.syncPage()
    }
    return state
}

@Composable
fun AdaptiveBottomSheet(
    show: Boolean,
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.surface,
    enableNestedScroll: Boolean = true,
    insideMargin: DpSize = GlassBottomSheetDefaults.InsideMargin,
    scrollOffset: (() -> Float)? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val glassStyle = LocalGlassStyle.current
    if (glassStyle) {
        val rawBackdrop = LocalAppBackdrop.current
        val isBlurActive = LocalBlurEnabled.current && isRuntimeShaderSupported() && rawBackdrop != null
        GlassBottomSheet(
            onDismissRequest = onDismissRequest,
            show = show,
            backdrop = if (isBlurActive) rawBackdrop else null,
            modifier = modifier,
            enableNestedScroll = enableNestedScroll,
            progressiveBlur = isBlurActive,
            title = title,
            startAction = startAction,
            endAction = endAction,
            insideMargin = insideMargin,
            containerColor = backgroundColor,
            onDismissFinished = onDismissFinished,
            scrollOffset = scrollOffset,
            content = { innerPadding ->
                CompositionLocalProvider(
                    LocalGlassDialog provides true,
                    LocalBottomSheetBlurActive provides isBlurActive,
                ) {
                    content(innerPadding)
                }
            },
        )
    } else {
        OverlayBottomSheet(
            show = show,
            title = title,
            modifier = modifier,
            backgroundColor = backgroundColor,
            dimColor = MiuixTheme.colorScheme.windowDimming,
            insideMargin = insideMargin,
            enableNestedScroll = enableNestedScroll,
            startAction = startAction,
            endAction = endAction,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            motion = SheetMotion.Default,
            content = {
                CompositionLocalProvider(
                    LocalGlassDialog provides false,
                    LocalBottomSheetBlurActive provides false,
                ) {
                    content(PaddingValues(0.dp))
                }
            },
        )
    }
}

@Composable
fun adaptiveBottomSheetCardColors(): CardColors =
    if (LocalGlassStyle.current && LocalBottomSheetBlurActive.current) {
        GlassCardDefaults.colors()
    } else {
        CardDefaults.defaultColors()
    }

@Composable
fun AdaptiveOverlayDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    val glassStyle = LocalGlassStyle.current
    val actualEnabled = enabled && items.isNotEmpty()
    var expanded by remember { mutableStateOf(false) }
    val mounted = remember { mutableStateOf(false) }
    var activePopupIsGlass by remember { mutableStateOf<Boolean?>(null) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    val anchor = rememberGlassPopupAnchor()
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)

    val setExpanded: (Boolean) -> Unit = remember {
        { next ->
            if (expanded != next) {
                expanded = next
                if (next) {
                    if (!mounted.value) {
                        activePopupIsGlass = glassStyle
                    }
                    mounted.value = true
                }
                currentOnExpandedChange?.invoke(next)
            }
        }
    }

    LaunchedEffect(actualEnabled) {
        if (!actualEnabled && expanded) {
            setExpanded(false)
        }
    }

    LaunchedEffect(expanded, mounted.value, activePopupIsGlass) {
        if (!expanded && mounted.value && activePopupIsGlass == true) {
            delay(420L)
            mounted.value = false
            activePopupIsGlass = null
        }
    }

    val actionColor = if (actualEnabled) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }

    val overlayEntry = remember(items, selectedIndex) {
        DropdownEntry(
            items.mapIndexed { index, item ->
                DropdownItem(
                    text = item,
                    selected = index == selectedIndex,
                    onClick = { currentOnSelectedIndexChange?.invoke(index) },
                )
            },
        )
    }

    BasicComponent(
        modifier = modifier.onGloballyPositioned { coords ->
            if (coords.isAttached) {
                anchorBounds = coords.boundsInWindow()
            }
        },
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(
                modifier = if (glassStyle || activePopupIsGlass == true) {
                    Modifier.glassPopupAnchorValue(anchor)
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val value = items.getOrNull(selectedIndex)
                if (showValue && !value.isNullOrEmpty()) {
                    Text(
                        text = value,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .weight(1f, fill = false),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = actionColor,
                        textAlign = TextAlign.End,
                    )
                }
                DropdownArrowEndAction(actionColor = actionColor)
            }
            if (items.isNotEmpty() && (!glassStyle || activePopupIsGlass == false)) {
                OverlayDropdownPopup(
                    entry = overlayEntry,
                    show = expanded && actualEnabled && activePopupIsGlass == false,
                    onDismiss = { setExpanded(false) },
                    onDismissFinished = {
                        mounted.value = false
                        if (activePopupIsGlass == false) {
                            activePopupIsGlass = null
                        }
                    },
                    maxHeight = maxHeight,
                    dropdownColors = dropdownColors,
                    renderInRootScaffold = renderInRootScaffold,
                )
            }
        },
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        onClick = {
            if (actualEnabled) {
                val next = !expanded
                setExpanded(next)
                if (next) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            }
        },
        role = Role.DropdownList,
        holdDownState = mounted.value,
        enabled = actualEnabled,
    )

    if (!mounted.value || activePopupIsGlass != true) return

    val rawBackdrop = LocalAppBackdrop.current
    val isBlurActive = rawBackdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()
    val resolvedBackdrop = if (isBlurActive) rawBackdrop else null
    val sizing = remember(maxHeight) {
        if (maxHeight == null) GlassPopupSizing() else GlassPopupSizing(maxHeight = maxHeight)
    }

    PopupLayout(
        visible = mounted,
        enableWindowDim = false,
        enableBackHandler = false,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlassDropdownPopup(
                show = expanded && actualEnabled,
                onDismissRequest = { setExpanded(false) },
                anchorBounds = anchorBounds,
                backdrop = resolvedBackdrop,
                anchor = anchor,
                sizing = sizing,
            ) {
                items.forEachIndexed { index, item ->
                    GlassPopupItem(
                        text = item,
                        selected = selectedIndex == index,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            setExpanded(false)
                            currentOnSelectedIndexChange?.invoke(index)
                        },
                    )
                }
            }
        }
    }
}

/** One floating action button, switching between glass and ordinary style. */
@Composable
fun AdaptiveFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalAppBackdrop.current,
    contentColor: Color = Color.White,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(top.yukonga.miuix.kmp.theme.LocalContentColor provides contentColor) {
        if (LocalGlassStyle.current) {
            val rawBackdrop = backdrop ?: LocalAppBackdrop.current
            val isBlurActive = rawBackdrop != null && LocalBlurEnabled.current && isRuntimeShaderSupported()
            GlassFloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                backdrop = if (isBlurActive) rawBackdrop else null,
                content = content,
            )
        } else {
            FloatingActionButton(
                onClick = onClick,
                modifier = modifier,
                content = content,
            )
        }
    }
}
