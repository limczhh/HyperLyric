package com.lidesheng.hyperlyric.ui.page.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val ABOUT_LOGO_SPACER_KEY = "about_logo_spacer"

@Composable
fun AboutHeader(
    aboutAppVersion: String?,
    topPadding: Dp,
    scrollProgressProvider: () -> Float,
    onHeightChanged: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val version = aboutAppVersion ?: stringResource(R.string.version_unknown)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.onSizeChanged { size ->
                with(density) {
                    onHeightChanged(size.height.toDp())
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val iconProgress = ((scrollProgressProvider() - 0.35f) / 0.15f)
                            .coerceIn(0f, 1f)
                        clip = true
                        shape = RoundedCornerShape(24.dp)
                        alpha = 1f - iconProgress
                        scaleX = 1f - (iconProgress * 0.05f)
                        scaleY = 1f - (iconProgress * 0.05f)
                    }
                    .squircleClip(24.dp)
                    .background(colorResource(R.color.app_icon_red)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.5f),
                )
            }
            Text(
                text = "HyperLyric",
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val projectNameProgress = ((scrollProgressProvider() - 0.20f) / 0.15f)
                            .coerceIn(0f, 1f)
                        alpha = 1f - projectNameProgress
                        scaleX = 1f - (projectNameProgress * 0.05f)
                        scaleY = 1f - (projectNameProgress * 0.05f)
                    },
            )
            Text(
                text = version,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val versionProgress = ((scrollProgressProvider() - 0.05f) / 0.15f)
                            .coerceIn(0f, 1f)
                        alpha = 1f - versionProgress
                        scaleX = 1f - (versionProgress * 0.05f)
                        scaleY = 1f - (versionProgress * 0.05f)
                    },
            )
        }
    }
}

fun LazyListScope.aboutPageSections(
    onOpenLink: (String) -> Unit,
    logoSpacerHeight: Dp,
    minimumContentHeight: Dp,
    contentBottomPadding: Dp,
    aboutDeviceModel: String,
    aboutOsVersion: String,
    aboutAndroidVersion: String,
    onHelpClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onChangelogClick: () -> Unit,
    onContributorsClick: () -> Unit,
) {
    item(key = ABOUT_LOGO_SPACER_KEY) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(logoSpacerHeight),
        )
    }

    item(key = "about_content") {
        Column(
            modifier = Modifier
                .heightIn(min = minimumContentHeight)
                .padding(bottom = contentBottomPadding),
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    BasicComponent(
                        title = aboutDeviceModel,
                        summary = stringResource(R.string.info_device_model)
                    )
                    BasicComponent(
                        title = aboutOsVersion,
                        summary = stringResource(R.string.info_os_version)
                    )
                    BasicComponent(
                        title = aboutAndroidVersion,
                        summary = stringResource(R.string.info_android_version)
                    )
                }
            }

            SmallTitle(
                text = stringResource(R.string.title_help)
            )

            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.title_help),
                        onClick = onHelpClick,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_changelog),
                        onClick = onChangelogClick,
                    )
                }
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.title_contributors),
                    onClick = onContributorsClick,
                )
                ArrowPreference(
                    title = stringResource(R.string.title_project),
                    onClick = {
                        onOpenLink("https://github.com/limczhh/HyperLyric")
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.title_licenses),
                    onClick = onLicensesClick,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
