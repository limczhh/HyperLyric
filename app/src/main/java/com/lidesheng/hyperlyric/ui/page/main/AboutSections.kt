package com.lidesheng.hyperlyric.ui.page.main

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.aboutPageSections(
    aboutAppVersion: String?,
    aboutDeviceModel: String,
    aboutOsVersion: String,
    aboutAndroidVersion: String,
    onHelpClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onChangelogClick: () -> Unit,
) {
    item(key = "about_header") {
        val version = aboutAppVersion ?: stringResource(R.string.version_unknown)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .squircleClip(20.dp)
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
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "HyperLyric",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = version,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    item(key = "about_header_spacer") {
        Spacer(modifier = Modifier.height(104.dp))
    }

    item(key = "system_info_title") {
        SmallTitle(
            text = stringResource(R.string.title_system_info)
        )
    }

    item(key = "system_info_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                BasicComponent(title = aboutDeviceModel, summary = stringResource(R.string.info_device_model))
                BasicComponent(title = aboutOsVersion, summary = stringResource(R.string.info_os_version))
                BasicComponent(title = aboutAndroidVersion, summary = stringResource(R.string.info_android_version))
            }
        }
    }

    item(key = "help_title") {
        SmallTitle(
            text = stringResource(R.string.title_help)
        )
    }

    item(key = "help_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
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
    }

    item(key = "developer_title") {
        SmallTitle(
            text = stringResource(R.string.title_developer)
        )
    }

    item(key = "developer_content") {
        val context = LocalContext.current
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.dev_name),
                startAction = {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = stringResource(R.string.content_description_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer)
                    )
                },
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = stringResource(R.string.content_description_go),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = {
                    val uri = "https://github.com/limczhh/HyperLyric".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        intent.setPackage("com.coolapk.market")
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        intent.setPackage(null)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.title_licenses),
                summary = stringResource(R.string.summary_licenses),
                onClick = onLicensesClick,
            )
        }
    }
}
