package com.lidesheng.hyperlyric.ui.page.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.homePageSections(
    randomQuote: String,
    onQuoteClick: () -> Unit,
    onQuoteLongPress: () -> Unit,
    enableSuperIsland: Boolean,
    onSuperIslandToggle: (Boolean) -> Unit,
    enableDynamicIsland: Boolean,
    onDynamicIslandToggle: (Boolean) -> Unit,
    onSuperIslandConfigClick: () -> Unit,
    onDynamicIslandConfigClick: () -> Unit,
    onRestartClick: () -> Unit,
    removeFocusWhitelist: Boolean,
    onRemoveFocusWhitelistToggle: (Boolean) -> Unit,
    removeIslandWhitelist: Boolean,
    onRemoveIslandWhitelistToggle: (Boolean) -> Unit,
    onAppSettingsClick: () -> Unit,
) {
    item(key = "quote") {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth(),
            onClick = onQuoteClick,
            onLongPress = onQuoteLongPress,
        ) {
            Text(
                text = randomQuote,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    item(key = "basic_features_title") {
        SmallTitle(
            text = stringResource(R.string.title_basic_features)
        )
    }

    item(key = "basic_features_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                SwitchPreference(
                    title = stringResource(R.string.title_super_island_lyrics),
                    summary = stringResource(R.string.summary_super_island_lyrics),
                    checked = enableSuperIsland,
                    onCheckedChange = onSuperIslandToggle,
                )
                AnimatedVisibility(visible = enableSuperIsland) {
                    ArrowPreference(
                        title = stringResource(R.string.title_super_island_config),
                        onClick = onSuperIslandConfigClick,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.title_dynamic_island_lyrics),
                    summary = stringResource(R.string.summary_dynamic_island_lyrics),
                    checked = enableDynamicIsland,
                    onCheckedChange = onDynamicIslandToggle,
                )
                AnimatedVisibility(visible = enableDynamicIsland) {
                    ArrowPreference(
                        title = stringResource(R.string.title_dynamic_island_config),
                        onClick = onDynamicIslandConfigClick,
                    )
                }
            }
        }
    }

    item(key = "special_features_title") {
        SmallTitle(
            text = stringResource(R.string.title_special_features)
        )
    }

    item(key = "special_features_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                ArrowPreference(
                    title = stringResource(R.string.title_restart_ui),
                    onClick = onRestartClick,
                )
                SwitchPreference(
                    title = stringResource(R.string.title_remove_focus_whitelist),
                    summary = stringResource(R.string.summary_remove_focus_whitelist),
                    checked = removeFocusWhitelist,
                    onCheckedChange = onRemoveFocusWhitelistToggle,
                )
                SwitchPreference(
                    title = stringResource(R.string.title_remove_island_whitelist),
                    checked = removeIslandWhitelist,
                    onCheckedChange = onRemoveIslandWhitelistToggle,
                )
            }
        }
    }

    item(key = "app_settings") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.title_app_settings),
                summary = stringResource(R.string.summary_app_settings),
                onClick = onAppSettingsClick,
            )
        }
    }
}
