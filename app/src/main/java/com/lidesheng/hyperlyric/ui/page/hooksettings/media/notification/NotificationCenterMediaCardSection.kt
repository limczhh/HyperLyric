package com.lidesheng.hyperlyric.ui.page.hooksettings.media.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.lidesheng.hyperlyric.common.RootConstants
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

fun LazyListScope.notificationCenterMediaCardSection(
    cardTheme: Int,
    onCardThemeChange: (Int) -> Unit,
    coverStyle: Int,
    onCoverStyleChange: (Int) -> Unit,
    hideCoverSource: Boolean,
    onHideCoverSourceChange: (Boolean) -> Unit,
    hideDeviceSwitch: Boolean,
    onHideDeviceSwitchChange: (Boolean) -> Unit,
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
    item(key = "notification_center_media_card") {
        SmallTitle(text = stringResource(R.string.title_notification_center_media_card))
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
        }
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
            OverlayDropdownPreference(
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
                        OverlayDropdownPreference(
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
                        OverlayDropdownPreference(
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
                                    RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
                                    RootConstants.MEDIA_SOFT_COVER_TONE_DARK
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.title_media_soft_cover_tone),
                                    items = listOf(
                                        stringResource(R.string.option_media_soft_cover_tone_light),
                                        stringResource(R.string.option_media_soft_cover_tone_dark)
                                    ),
                                    selectedIndex = toneValues.indexOf(softCoverTone).coerceAtLeast(0),
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
