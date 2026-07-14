package com.lidesheng.hyperlyric.ui.page.hooksettings.media.island

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

fun LazyListScope.islandExpandedMediaCardSection(
    cardTheme: Int,
    onCardThemeChange: (Int) -> Unit,
    coverStyle: Int,
    onCoverStyleChange: (Int) -> Unit,
    hideCoverSource: Boolean,
    onHideCoverSourceChange: (Boolean) -> Unit,
    hideDeviceSwitch: Boolean,
    onHideDeviceSwitchChange: (Boolean) -> Unit,
    ambientFlowMode: Int,
    onAmbientFlowModeChange: (Int) -> Unit
) {
    item(key = "island_expanded_media_card") {
        SmallTitle(text = stringResource(R.string.title_island_expanded_media_card))
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            val coverStyleValues = listOf(
                RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_CIRCLE,
                RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_ROTATING_CIRCLE,
                RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.title_audio_cover_style),
                items = listOf(
                    stringResource(R.string.option_audio_cover_style_default),
                    stringResource(R.string.option_audio_cover_style_circle),
                    stringResource(R.string.option_audio_cover_style_rotating_circle),
                    stringResource(R.string.option_audio_cover_style_hidden)
                ),
                selectedIndex = coverStyleValues.indexOf(coverStyle).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    onCoverStyleChange(coverStyleValues[index])
                }
            )
            SwitchPreference(
                title = stringResource(R.string.title_hide_audio_cover_source),
                checked = hideCoverSource,
                onCheckedChange = onHideCoverSourceChange
            )
            SwitchPreference(
                title = stringResource(R.string.title_hide_media_device_switch),
                checked = hideDeviceSwitch,
                onCheckedChange = onHideDeviceSwitchChange
            )
            val themeValues = listOf(
                RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
                RootConstants.MEDIA_CARD_THEME_ALWAYS_LIGHT,
                RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.title_media_card_background_theme),
                items = listOf(
                    stringResource(R.string.option_media_card_theme_follow_system),
                    stringResource(R.string.option_media_card_theme_always_light),
                    stringResource(R.string.option_media_card_theme_always_dark_default)
                ),
                selectedIndex = themeValues.indexOf(cardTheme).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    onCardThemeChange(themeValues[index])
                }
            )
            val modeValues = listOf(
                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR,
                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.title_island_media_ambient_flow_mode),
                items = listOf(
                    stringResource(R.string.option_island_media_ambient_flow_default),
                    stringResource(R.string.option_island_media_ambient_flow_cover_color),
                    stringResource(R.string.option_island_media_ambient_flow_disabled)
                ),
                selectedIndex = modeValues.indexOf(ambientFlowMode).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    onAmbientFlowModeChange(modeValues[index])
                }
            )
        }
    }
}
