package com.lidesheng.hyperlyric.ui.page.hooksettings.media.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import com.lidesheng.hyperlyric.ui.utils.AdaptiveOverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

fun LazyListScope.notificationCenterMediaElementSection(
    coverStyle: Int,
    onCoverStyleChange: (Int) -> Unit,
    hideCoverSource: Boolean,
    onHideCoverSourceChange: (Boolean) -> Unit,
    hideCoverShadow: Boolean,
    onHideCoverShadowChange: (Boolean) -> Unit,
    disableCoverFlip: Boolean,
    onDisableCoverFlipChange: (Boolean) -> Unit,
    hideDeviceSwitch: Boolean,
    onHideDeviceSwitchChange: (Boolean) -> Unit,
    hideCustomActions: Boolean,
    onHideCustomActionsChange: (Boolean) -> Unit,
    hideTime: Boolean,
    onHideTimeChange: (Boolean) -> Unit,
    progressStyle: Int,
    onProgressStyleChange: (Int) -> Unit,
    progressHeadGlow: Boolean,
    onProgressHeadGlowChange: (Boolean) -> Unit,
    thumbStyle: Int,
    onThumbStyleChange: (Int) -> Unit
) {
    item(key = "notification_center_media_card_elements") {
        Column {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                val coverStyleValues = listOf(
                    RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT,
                    RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_CIRCLE,
                    RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_ROTATING_CIRCLE,
                    RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN
                )
                AdaptiveOverlayDropdownPreference(
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
                    title = stringResource(R.string.title_hide_audio_cover_shadow),
                    checked = hideCoverShadow,
                    onCheckedChange = onHideCoverShadowChange
                )
                SwitchPreference(
                    title = stringResource(R.string.title_disable_media_cover_flip),
                    checked = disableCoverFlip,
                    onCheckedChange = onDisableCoverFlipChange
                )
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.title_hide_media_device_switch),
                    checked = hideDeviceSwitch,
                    onCheckedChange = onHideDeviceSwitchChange
                )
                SwitchPreference(
                    title = stringResource(R.string.title_hide_media_custom_actions),
                    checked = hideCustomActions,
                    onCheckedChange = onHideCustomActionsChange
                )
                SwitchPreference(
                    title = stringResource(R.string.title_hide_media_time),
                    checked = hideTime,
                    onCheckedChange = onHideTimeChange
                )
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                val progressStyleValues = listOf(
                    RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT,
                    RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE
                )
                AdaptiveOverlayDropdownPreference(
                    title = stringResource(R.string.title_media_progress_style),
                    items = listOf(
                        stringResource(R.string.option_media_progress_style_default),
                        stringResource(R.string.option_media_progress_style_wave)
                    ),
                    selectedIndex = progressStyleValues.indexOf(progressStyle).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        onProgressStyleChange(progressStyleValues[index])
                    }
                )

                AnimatedVisibility(
                    visible =
                        progressStyle == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT
                ) {
                    Column {
                        SwitchPreference(
                            title = stringResource(R.string.title_media_progress_head_glow),
                            summary = stringResource(R.string.summary_media_progress_head_glow),
                            checked = progressHeadGlow,
                            onCheckedChange = onProgressHeadGlowChange
                        )
                    }
                }

                AnimatedVisibility(
                    visible = progressStyle == RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE
                ) {
                    Column {
                        val thumbStyleValues = listOf(
                            RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT,
                            RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_VERTICAL,
                            RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN
                        )
                        AdaptiveOverlayDropdownPreference(
                            title = stringResource(R.string.title_media_thumb_style),
                            items = listOf(
                                stringResource(R.string.option_media_thumb_style_default),
                                stringResource(R.string.option_media_thumb_style_vertical),
                                stringResource(R.string.option_media_thumb_style_hidden)
                            ),
                            selectedIndex = thumbStyleValues.indexOf(thumbStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                onThumbStyleChange(thumbStyleValues[index])
                            }
                        )
                    }
                }
            }
        }
    }
}

fun LazyListScope.notificationCenterMediaBackgroundSection(
    cardTheme: Int,
    onCardThemeChange: (Int) -> Unit,
    backgroundStyle: Int,
    onBackgroundStyleChange: (Int) -> Unit,
    backgroundColorAnimation: Boolean,
    onBackgroundColorAnimationChange: (Boolean) -> Unit,
    backgroundBlur: Int,
    onBackgroundBlurChange: (Int) -> Unit,
    backgroundAutoInvert: Boolean,
    onBackgroundAutoInvertChange: (Boolean) -> Unit,
    softCoverTone: Int,
    onSoftCoverToneChange: (Int) -> Unit,
    ambientFlowMode: Int,
    onAmbientFlowModeChange: (Int) -> Unit
) {
    item(key = "notification_center_media_card_background") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            val backgroundStyleValues = listOf(
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT,
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_COVER_ART,
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_BLURRED_COVER,
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_RADIAL_GRADIENT,
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT,
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER
            )
            AdaptiveOverlayDropdownPreference(
                title = stringResource(R.string.title_notification_media_background_style),
                items = listOf(
                    stringResource(R.string.option_notification_media_background_default),
                    stringResource(R.string.option_notification_media_background_cover_art),
                    stringResource(R.string.option_notification_media_background_blurred_cover),
                    stringResource(R.string.option_notification_media_background_radial_gradient),
                    stringResource(R.string.option_notification_media_background_linear_gradient),
                    stringResource(R.string.option_notification_media_background_soft_cover)
                ),
                selectedIndex = backgroundStyleValues.indexOf(backgroundStyle).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    onBackgroundStyleChange(backgroundStyleValues[index])
                }
            )
            val customBackground =
                backgroundStyle != RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT
            Column {
                AnimatedVisibility(
                    visible = !customBackground
                ) {
                    Column {
                        val themeValues = listOf(
                            RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
                            RootConstants.MEDIA_CARD_THEME_ALWAYS_LIGHT,
                            RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK
                        )
                        AdaptiveOverlayDropdownPreference(
                            title = stringResource(R.string.title_media_card_background_theme),
                            items = listOf(
                                stringResource(R.string.option_media_card_theme_follow_system_default),
                                stringResource(R.string.option_media_card_theme_always_light),
                                stringResource(R.string.option_media_card_theme_always_dark)
                            ),
                            selectedIndex = themeValues.indexOf(cardTheme).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                onCardThemeChange(themeValues[index])
                            }
                        )
                        val modeValues = listOf(
                            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC,
                            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR,
                            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL,
                            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
                        )
                        AdaptiveOverlayDropdownPreference(
                            title = stringResource(R.string.title_notification_media_ambient_flow_mode),
                            items = listOf(
                                stringResource(R.string.option_notification_media_ambient_flow_dynamic),
                                stringResource(R.string.option_notification_media_ambient_flow_cover_color),
                                stringResource(R.string.option_media_ambient_flow_custom_full),
                                stringResource(R.string.option_notification_media_ambient_flow_disabled)
                            ),
                            selectedIndex = modeValues.indexOf(ambientFlowMode).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                onAmbientFlowModeChange(modeValues[index])
                            }
                        )
                    }
                }
                AnimatedVisibility(
                    visible = customBackground
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = backgroundStyle ==
                                    RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER
                        ) {
                            Column {
                                val toneValues = listOf(
                                    RootConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM,
                                    RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
                                    RootConstants.MEDIA_SOFT_COVER_TONE_DARK
                                )
                                AdaptiveOverlayDropdownPreference(
                                    title = stringResource(R.string.title_media_soft_cover_tone),
                                    items = listOf(
                                        stringResource(
                                            R.string.option_media_soft_cover_tone_follow_system
                                        ),
                                        stringResource(R.string.option_media_soft_cover_tone_light),
                                        stringResource(R.string.option_media_soft_cover_tone_dark)
                                    ),
                                    selectedIndex = toneValues.indexOf(softCoverTone)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = { index ->
                                        onSoftCoverToneChange(toneValues[index])
                                    }
                                )
                            }
                        }
                        SwitchPreference(
                            title = stringResource(
                                R.string.title_notification_media_background_color_animation
                            ),
                            checked = backgroundColorAnimation,
                            onCheckedChange = onBackgroundColorAnimationChange
                        )
                        AnimatedVisibility(
                            visible = backgroundStyle ==
                                    RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_BLURRED_COVER
                        ) {
                            Column {
                                var sliderValue by remember(backgroundBlur) {
                                    mutableIntStateOf(backgroundBlur)
                                }
                                BasicComponent(
                                    title = stringResource(
                                        R.string.title_notification_media_background_blur
                                    ),
                                    endActions = {
                                        Text(
                                            text = sliderValue.toString(),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    bottomAction = {
                                        Slider(
                                            value = sliderValue.toFloat(),
                                            onValueChange = {
                                                sliderValue = it.roundToInt().coerceIn(1, 20)
                                            },
                                            onValueChangeFinished = {
                                                onBackgroundBlurChange(sliderValue)
                                            },
                                            valueRange = 1f..20f,
                                            steps = 18
                                        )
                                    }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = backgroundStyle ==
                                    RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT
                        ) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(
                                        R.string.title_notification_media_background_auto_invert
                                    ),
                                    checked = backgroundAutoInvert,
                                    onCheckedChange = onBackgroundAutoInvertChange
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.notificationCenterMediaLayoutSection(
    layoutStyle: Int,
    onLayoutStyleChange: (Int) -> Unit,
    layoutPromptDismissed: Boolean,
    onLayoutPromptDismissed: () -> Unit,
    actionAlignLeft: Boolean,
    onActionAlignLeftChange: (Boolean) -> Unit,
    actionOrder: Int,
    onActionOrderChange: (Int) -> Unit
) {
    item(key = "notification_center_media_layout_style") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            AdaptiveOverlayDropdownPreference(
                title = stringResource(R.string.title_notification_media_layout_style),
                items = listOf(
                    stringResource(R.string.option_notification_media_layout_system),
                    stringResource(R.string.option_notification_media_layout_ios),
                    stringResource(R.string.option_notification_media_layout_coloros),
                    stringResource(R.string.option_notification_media_layout_oneui),
                    stringResource(R.string.option_notification_media_layout_miui),
                    stringResource(R.string.option_notification_media_layout_pixelos)
                ),
                selectedIndex = notificationMediaLayoutStyles.indexOf(layoutStyle)
                    .coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    onLayoutStyleChange(notificationMediaLayoutStyles[index])
                }
            )
        }
    }

    item(key = "notification_center_media_layout_prompt") {
        AnimatedVisibility(
            visible = !layoutPromptDismissed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.tertiaryContainer,
                    contentColor = MiuixTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        )
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.prompt_media_layout_style),
                        color = MiuixTheme.colorScheme.onTertiaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    IconButton(
                        onClick = onLayoutPromptDismissed,
                        minWidth = 16.dp,
                        minHeight = 16.dp
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MiuixTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    item(key = "notification_center_media_card_layout") {
        Column {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.title_media_action_align_left),
                    checked = actionAlignLeft,
                    onCheckedChange = onActionAlignLeftChange
                )

                val actionOrderValues = listOf(
                    RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT,
                    RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_CUSTOM_RIGHT,
                    RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT
                )
                AdaptiveOverlayDropdownPreference(
                    title = stringResource(R.string.title_media_action_order),
                    items = listOf(
                        stringResource(R.string.option_media_action_order_default),
                        stringResource(R.string.option_media_action_order_custom_right),
                        stringResource(R.string.option_media_action_order_play_left)
                    ),
                    selectedIndex = actionOrderValues.indexOf(actionOrder).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        onActionOrderChange(actionOrderValues[index])
                    }
                )
            }
        }
    }
}

internal val notificationMediaLayoutStyles = listOf(
    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM,
    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS,
    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS,
    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI,
    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI,
    RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL
)
