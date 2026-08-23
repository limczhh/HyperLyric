package com.lidesheng.hyperlyric.ui.page.lyricnotification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.ServiceConstants
import com.lidesheng.hyperlyric.lyric.commonMusicApps
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.configSections(
    dynamicIslandEnabled: Boolean,
    onDynamicIslandToggle: (Boolean) -> Unit,
    notificationType: Int,
    onNotificationTypeChange: (Int) -> Unit,
    islandLeftIconStyle: Int,
    onIslandLeftIconStyleChange: (Int) -> Unit,
    disableLyricSplitEnabled: Boolean,
    onDisableLyricSplitToggle: (Boolean) -> Unit,
    limitWidthEnabled: Boolean,
    onLimitWidthToggle: (Boolean) -> Unit,
    maxWidth: Int,
    onMaxWidthChange: (Int) -> Unit,
    highlightColorEnabled: Boolean,
    onHighlightColorToggle: (Boolean) -> Unit,
    songInfoHighlightColorEnabled: Boolean,
    onSongInfoHighlightColorToggle: (Boolean) -> Unit,
    notificationClickAction: Int,
    onNotificationClickActionChange: (Int) -> Unit,
    showProgressEnabled: Boolean,
    onShowProgressToggle: (Boolean) -> Unit,
    progressColorEnabled: Boolean,
    onProgressColorToggle: (Boolean) -> Unit,
    showAlbumArtEnabled: Boolean,
    onShowAlbumArtToggle: (Boolean) -> Unit,
    focusNotificationType: Int,
    onFocusNotificationTypeChange: (Int) -> Unit,
    focusShowNotificationEnabled: Boolean,
    onFocusShowNotificationToggle: (Boolean) -> Unit,
    normalNotificationTitleStyle: Int,
    onNormalNotificationTitleStyleChange: (Int) -> Unit,
    onAutostartClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    lyricSource: Int,
    onLyricSourceChange: (Int) -> Unit,
    bypassFocusLimitEnabled: Boolean,
    onBypassFocusLimitToggle: (Boolean) -> Unit
) {
    item(key = "dynamic_island_enable") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.title_enable),
                checked = dynamicIslandEnabled,
                onCheckedChange = onDynamicIslandToggle
            )
        }
    }

    // Notification Type
    item(key = "service_lyric_source_and_notification_type") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                val lyricSourceOptions = remember {
                    listOf(
                        R.string.option_service_lyric_source_auto,
                        R.string.option_service_lyric_source_online,
                        R.string.option_service_lyric_source_lyricinfo,
                        R.string.option_service_lyric_source_lyric,
                        R.string.option_service_lyric_source_title
                    )
                }.map { stringResource(id = it) }

                WindowDropdownPreference(
                    title = stringResource(R.string.title_service_lyric_source),
                    items = lyricSourceOptions,
                    selectedIndex = lyricSource.takeIf { it in lyricSourceOptions.indices }
                        ?: ServiceConstants.LYRIC_SOURCE_AUTO,
                    enabled = dynamicIslandEnabled,
                    onSelectedIndexChange = onLyricSourceChange
                )

                val notificationTypeOptions = remember {
                    listOf(R.string.option_notification_live, R.string.option_notification_focus)
                }.map { stringResource(id = it) }

                OverlayDropdownPreference(
                    title = stringResource(R.string.title_notification_type),
                    items = notificationTypeOptions,
                    selectedIndex = notificationType,
                    enabled = dynamicIslandEnabled,
                    onSelectedIndexChange = onNotificationTypeChange
                )
            }
        }
    }

    // Island Settings
    item(key = "island_settings_title") {
        SmallTitle(text = stringResource(R.string.title_island_settings))
    }

    item(key = "island_settings_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                val iconStyleOptions = remember(notificationType) {
                    if (notificationType == 1) {
                        listOf(
                            R.string.option_icon_style_note,
                            R.string.option_icon_style_rounded,
                            R.string.option_icon_style_circular,
                            R.string.option_icon_style_none
                        )
                    } else {
                        listOf(
                            R.string.option_icon_style_note,
                            R.string.option_icon_style_rounded,
                            R.string.option_icon_style_circular
                        )
                    }
                }.map { stringResource(id = it) }

                WindowDropdownPreference(
                    title = stringResource(R.string.title_island_left_icon),
                    items = iconStyleOptions,
                    selectedIndex = islandLeftIconStyle,
                    enabled = dynamicIslandEnabled,
                    onSelectedIndexChange = onIslandLeftIconStyleChange
                )

                AnimatedVisibility(visible = notificationType == 1 && islandLeftIconStyle in 0..2) {
                    SwitchPreference(
                        title = stringResource(R.string.title_disable_lyric_split),
                        checked = disableLyricSplitEnabled,
                        enabled = dynamicIslandEnabled,
                        onCheckedChange = onDisableLyricSplitToggle
                    )
                }

                AnimatedVisibility(visible = notificationType == 1) {
                    SwitchPreference(
                        title = stringResource(R.string.title_lyric_highlight_color),
                        checked = highlightColorEnabled,
                        enabled = dynamicIslandEnabled,
                        onCheckedChange = onHighlightColorToggle
                    )
                }

                SwitchPreference(
                    title = stringResource(R.string.title_limit_width),
                    summary = stringResource(R.string.summary_experimental),
                    checked = limitWidthEnabled,
                    enabled = dynamicIslandEnabled,
                    onCheckedChange = onLimitWidthToggle
                )

                AnimatedVisibility(visible = limitWidthEnabled) {
                    var sliderDragValue by remember(maxWidth) { mutableIntStateOf(maxWidth) }
                    BasicComponent(
                        title = stringResource(R.string.title_limit_width_desc),
                        summary = stringResource(R.string.summary_limit_width),
                        enabled = dynamicIslandEnabled,
                        endActions = {
                            Text(
                                "$sliderDragValue",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        bottomAction = {
                            Slider(
                                value = sliderDragValue.toFloat(),
                                enabled = dynamicIslandEnabled,
                                onValueChange = {
                                    sliderDragValue = it.toInt()
                                },
                                onValueChangeFinished = {
                                    onMaxWidthChange(sliderDragValue)
                                },
                                valueRange = 100f..720f
                            )
                        }
                    )
                }
            }
        }
    }

    // Notification Settings
    item(key = "notification_settings_title") {
        SmallTitle(text = stringResource(R.string.title_notification_settings))
    }

    item(key = "notification_settings_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            AnimatedVisibility(visible = notificationType == 1) {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.title_focus_show_notification),
                        summary = stringResource(R.string.summary_focus_show_notification),
                        checked = focusShowNotificationEnabled,
                        enabled = dynamicIslandEnabled,
                        onCheckedChange = onFocusShowNotificationToggle
                    )
                }
            }
            val clickOptions = remember {
                listOf(
                    R.string.option_click_pause,
                    R.string.option_click_open_app,
                    R.string.option_click_open_media
                )
            }.map { stringResource(id = it) }
            WindowDropdownPreference(
                title = stringResource(R.string.title_notification_click),
                items = clickOptions,
                selectedIndex = notificationClickAction,
                enabled = dynamicIslandEnabled,
                onSelectedIndexChange = onNotificationClickActionChange
            )

            SwitchPreference(
                title = stringResource(R.string.title_show_progress),
                summary = stringResource(R.string.summary_show_progress),
                checked = showProgressEnabled,
                enabled = dynamicIslandEnabled,
                onCheckedChange = onShowProgressToggle
            )

            AnimatedVisibility(visible = showProgressEnabled) {
                SwitchPreference(
                    title = stringResource(R.string.title_progress_color),
                    summary = stringResource(R.string.summary_progress_color),
                    checked = progressColorEnabled,
                    enabled = dynamicIslandEnabled,
                    onCheckedChange = onProgressColorToggle
                )
            }

            SwitchPreference(
                title = stringResource(R.string.title_show_album_art),
                checked = showAlbumArtEnabled,
                enabled = dynamicIslandEnabled,
                onCheckedChange = onShowAlbumArtToggle
            )

            val focusStyleOptions = remember { listOf("OS2", "OS3") }
            AnimatedVisibility(visible = notificationType == 1) {
                WindowDropdownPreference(
                    title = stringResource(R.string.title_focus_style),
                    items = focusStyleOptions,
                    selectedIndex = 1 - focusNotificationType,
                    enabled = dynamicIslandEnabled,
                    onSelectedIndexChange = { index ->
                        onFocusNotificationTypeChange(1 - index)
                    }
                )
            }

            val normalTitleOptions = remember {
                listOf(
                    R.string.option_info_none,
                    R.string.option_info_title,
                    R.string.option_info_artist,
                    R.string.option_info_album,
                    R.string.option_info_title_artist,
                    R.string.option_info_artist_title,
                    R.string.option_info_artist_album
                )
            }.map { stringResource(id = it) }
            WindowDropdownPreference(
                title = stringResource(R.string.title_song_info),
                items = normalTitleOptions,
                selectedIndex = normalNotificationTitleStyle,
                enabled = dynamicIslandEnabled,
                onSelectedIndexChange = onNormalNotificationTitleStyleChange
            )

            AnimatedVisibility(visible = notificationType == 1) {
                SwitchPreference(
                    title = stringResource(R.string.title_song_info_highlight_color),
                    checked = songInfoHighlightColorEnabled,
                    enabled = dynamicIslandEnabled,
                    onCheckedChange = onSongInfoHighlightColorToggle
                )
            }
        }
    }

    // Advanced Features
    item(key = "advanced_features_title") {
        SmallTitle(text = stringResource(R.string.title_advanced_features))
    }

    item(key = "advanced_features_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(R.string.title_autostart),
                    enabled = dynamicIslandEnabled,
                    onClick = onAutostartClick
                )
                ArrowPreference(
                    title = stringResource(R.string.title_battery_optimization),
                    enabled = dynamicIslandEnabled,
                    onClick = onBatteryOptimizationClick
                )
                AnimatedVisibility(visible = notificationType == 1) {
                    SwitchPreference(
                        title = stringResource(R.string.title_bypass_focus_limit),
                        summary = stringResource(R.string.summary_bypass_focus_limit),
                        checked = bypassFocusLimitEnabled,
                        enabled = dynamicIslandEnabled,
                        onCheckedChange = onBypassFocusLimitToggle
                    )
                }

            }
        }
    }
}

fun LazyListScope.whitelistSections(
    whitelist: List<String>,
    onAddClick: () -> Unit,
    showAddDialog: Boolean,
    onDeleteClick: (String) -> Unit,
    showDeleteDialog: Boolean,
    packageToDelete: String
) {
    item(key = "add_whitelist_button") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            ArrowPreference(
                title = stringResource(R.string.title_add_whitelist),
                onClick = onAddClick,
                holdDownState = showAddDialog
            )
        }
    }

    item(key = "added_apps_title") {
        SmallTitle(text = stringResource(R.string.title_added_apps))
    }

    if (whitelist.isNotEmpty()) {
        whitelist.forEach { packageName ->
            item(key = packageName) {
                val appName = commonMusicApps[packageName]
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                ) {
                    BasicComponent(
                        title = appName ?: packageName,
                        summary = if (appName != null) packageName else null,
                        endActions = {
                            IconButton(onClick = { onDeleteClick(packageName) }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            }
                        },
                        onClick = { onDeleteClick(packageName) },
                        holdDownState = showDeleteDialog && packageToDelete == packageName
                    )
                }
            }
        }
    } else {
        item(key = "no_whitelist") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                BasicComponent(
                    title = stringResource(R.string.title_no_whitelist),
                )
            }
        }
    }
    item(key = "whitelist_bottom_spacer") {
        Spacer(modifier = Modifier.height(20.dp))
    }
}
