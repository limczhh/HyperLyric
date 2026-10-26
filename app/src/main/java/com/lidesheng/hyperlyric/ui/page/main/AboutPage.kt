package com.lidesheng.hyperlyric.ui.page.main

import com.lidesheng.hyperlyric.ui.utils.rememberExternalLinkOpener
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutPage(
    outerPadding: PaddingValues,
    aboutAppVersion: String?,
    aboutDeviceModel: String,
    aboutOsVersion: String,
    aboutAndroidVersion: String,
    onHelpClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onChangelogClick: () -> Unit,
    onContributorsClick: () -> Unit,
) {
    val backdrop = rememberBlurBackdrop()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    var logoHeightDp by remember { mutableStateOf(300.dp) }

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f

                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == ABOUT_LOGO_SPACER_KEY }
                    if (spacer != null && spacer.size > 0) {
                        (
                            lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size
                            ).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) {
        derivedStateOf { backdrop != null && scrollProgress == 1f }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val onOpenLink = rememberExternalLinkOpener(snackbarHostState)
    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
            }
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            BlurredBar(backdrop, blurActive) {
                SmallTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.about),
                    titleColor = titleColor,
                    defaultWindowInsetsPadding = false,
                    scrollBehavior = topAppBarScrollBehavior,
                )
            }
        }
    ) { innerPadding ->
        val contentPadding = pageContentPadding(
            innerPadding = innerPadding,
            outerPadding = outerPadding,
            isWideScreen = false,
        )

        BoxWithConstraints(
            modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
        ) {
            AboutHeader(
                aboutAppVersion = aboutAppVersion,
                topPadding = contentPadding.calculateTopPadding() + 92.dp,
                scrollProgressProvider = { scrollProgress },
                onHeightChanged = { logoHeightDp = it },
            )
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                ),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                ),
            ) {
                aboutPageSections(
                    onOpenLink = onOpenLink,
                    logoSpacerHeight = logoHeightDp + 218.dp,
                    minimumContentHeight = maxHeight,
                    contentBottomPadding = contentPadding.calculateBottomPadding(),
                    aboutDeviceModel = aboutDeviceModel,
                    aboutOsVersion = aboutOsVersion,
                    aboutAndroidVersion = aboutAndroidVersion,
                    onHelpClick = onHelpClick,
                    onLicensesClick = onLicensesClick,
                    onChangelogClick = onChangelogClick,
                    onContributorsClick = onContributorsClick,
                )
            }
        }
    }
}
