package com.lidesheng.hyperlyric.ui.page.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.homePageSections(
    quote: String,
    isDateSpecificQuote: Boolean,
    onQuoteClick: () -> Unit,
    onQuoteLongPress: () -> Unit,
    onSuperIslandConfigClick: () -> Unit,
    onMediaCardConfigClick: () -> Unit,
    onDynamicIslandConfigClick: () -> Unit,
    onRestartClick: () -> Unit,
    removeFocusWhitelist: Boolean,
    onRemoveFocusWhitelistToggle: (Boolean) -> Unit,
    onAppSettingsClick: () -> Unit,
) {
    item(key = "quote") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
            colors = if (isDateSpecificQuote) {
                CardDefaults.defaultColors(
                    color = colorResource(R.color.quote_special_card_red),
                    contentColor = colorResource(R.color.quote_special_text_red),
                )
            } else {
                CardDefaults.defaultColors()
            },
            onClick = onQuoteClick,
            onLongPress = onQuoteLongPress,
        ) {
            Text(
                text = quote,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = if (isDateSpecificQuote) {
                    colorResource(R.color.quote_special_text_red)
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    item(key = "basic_features_title") {
        SmallTitle(
            text = stringResource(R.string.title_basic_features)
        )
    }

    item(key = "basic_features_content_super_island") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(R.string.title_super_island_lyrics),
                    onClick = onSuperIslandConfigClick,
                )
                ArrowPreference(
                    title = stringResource(R.string.title_media_cards),
                    onClick = onMediaCardConfigClick,
                )
            }
        }
    }

    item(key = "special_features_title") {
        SmallTitle(
            text = stringResource(R.string.title_special_features)
        )
    }

    item(key = "special_features_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(R.string.title_restart_ui),
                    onClick = onRestartClick,
                )
                ArrowPreference(
                    title = stringResource(R.string.title_dynamic_island_lyrics),
                    summary = stringResource(R.string.summary_dynamic_island_lyrics),
                    onClick = onDynamicIslandConfigClick,
                )
                SwitchPreference(
                    title = stringResource(R.string.title_remove_focus_whitelist),
                    summary = stringResource(R.string.summary_remove_focus_whitelist),
                    checked = removeFocusWhitelist,
                    onCheckedChange = onRemoveFocusWhitelistToggle,
                )
            }
        }
    }

    item(key = "app_settings") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            ArrowPreference(
                title = stringResource(R.string.title_app_settings),
                summary = stringResource(R.string.summary_app_settings),
                onClick = onAppSettingsClick,
            )
        }
    }
}
