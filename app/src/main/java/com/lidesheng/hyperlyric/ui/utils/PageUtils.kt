package com.lidesheng.hyperlyric.ui.utils

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

fun Modifier.pageScrollModifiers(
    enableScrollEndHaptic: Boolean,
    showTopAppBar: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier = this
    .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
    .overScrollVertical()
    .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
    .fillMaxHeight()

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

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    Box {
        if (blurEnabled && backdrop != null) {
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
        content()
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

/** The 0.9.4 API expresses the wide pinned bar as SmallTopAppBar. */
@Composable
fun AdaptiveTopAppBar(
    title: String,
    color: Color = MiuixTheme.colorScheme.surface,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (isWideAppWindow()) {
        SmallTopAppBar(title = title, color = color, scrollBehavior = scrollBehavior,
            modifier = Modifier.windowInsetsPadding(WindowInsets.captionBar.only(WindowInsetsSides.Top)),
            navigationIcon = navigationIcon, actions = actions, bottomContent = bottomContent)
    } else {
        TopAppBar(title = title, color = color, scrollBehavior = scrollBehavior,
            modifier = Modifier.windowInsetsPadding(WindowInsets.captionBar.only(WindowInsetsSides.Top)),
            navigationIcon = navigationIcon, actions = actions, bottomContent = bottomContent)
    }
}
