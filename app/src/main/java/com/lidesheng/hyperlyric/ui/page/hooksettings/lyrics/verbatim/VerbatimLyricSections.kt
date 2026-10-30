package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.verbatim

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.verbatimLyricSections(
    wordEffect: Int,
    onWordEffectChange: (Int) -> Unit,
    wordRevealAnimation: Int,
    onWordRevealAnimationChange: (Int) -> Unit,
    wordRevealSpeedRate: Int,
    onWordRevealSpeedRateChange: (Int) -> Unit,
    gradientStyle: Boolean,
    onGradientStyleChange: (Boolean) -> Unit,
    lineDisplay: Boolean,
    onLineDisplayChange: (Boolean) -> Unit,
    syllableRelative: Boolean,
    onSyllableRelativeChange: (Boolean) -> Unit,
    syllableHighlight: Boolean,
    onSyllableHighlightChange: (Boolean) -> Unit,
    wordMotionEnabled: Boolean,
    onWordMotionEnabledChange: (Boolean) -> Unit,
    wordMotionLatinByCharacter: Boolean,
    onWordMotionLatinByCharacterChange: (Boolean) -> Unit,
    wordMotionCjkLift: Float,
    onWordMotionCjkLiftClick: () -> Unit,
    wordMotionCjkWave: Float,
    onWordMotionCjkWaveClick: () -> Unit,
    wordMotionLatinLift: Float,
    onWordMotionLatinLiftClick: () -> Unit,
    wordMotionLatinWave: Float,
    onWordMotionLatinWaveClick: () -> Unit
) {
    item(key = "verbatim_lyric") {
        val animationSpeedOptions = RootConstants.HOOK_ANIM_SPEED_RATES.map { rate ->
            stringResource(
                id = R.string.format_anim_speed,
                RootConstants.hookAnimSpeedLabel(rate)
            )
        }
        Column {
            SmallTitle(text = stringResource(id = R.string.title_verbatim_lyric))
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    SwitchPreference(
                        title = stringResource(id = R.string.title_syllable_line_display),
                        summary = stringResource(id = R.string.summary_syllable_line_display),
                        checked = lineDisplay,
                        onCheckedChange = onLineDisplayChange
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_syllable_relative),
                        summary = stringResource(id = R.string.summary_syllable_relative),
                        checked = syllableRelative,
                        onCheckedChange = onSyllableRelativeChange
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_syllable_highlight),
                        enabled = syllableRelative,
                        checked = syllableHighlight,
                        onCheckedChange = onSyllableHighlightChange
                    )
                }
            }
            SmallTitle(text = stringResource(id = R.string.title_lyric_effects))
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.title_word_effect),
                    items = listOf(
                        stringResource(id = R.string.option_word_effect_highlight),
                        stringResource(id = R.string.option_word_effect_reveal)
                    ),
                    selectedIndex = wordEffect,
                    onSelectedIndexChange = onWordEffectChange
                )
            }
            AnimatedVisibility(visible = wordEffect == RootConstants.WORD_EFFECT_HIGHLIGHT) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                ) {
                    Column {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_gradient_progress),
                            checked = gradientStyle,
                            onCheckedChange = onGradientStyleChange
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.title_word_motion),
                            checked = wordMotionEnabled,
                            onCheckedChange = onWordMotionEnabledChange
                        )
                        AnimatedVisibility(visible = wordMotionEnabled) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(
                                        id = R.string.title_word_motion_latin_by_character
                                    ),
                                    summary = stringResource(
                                        id = R.string.summary_word_motion_latin_by_character
                                    ),
                                    checked = wordMotionLatinByCharacter,
                                    onCheckedChange = onWordMotionLatinByCharacterChange
                                )
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_word_motion_cjk_lift),
                                    onClick = onWordMotionCjkLiftClick,
                                    endActions = {
                                        Text(
                                            String.format("%.2f", wordMotionCjkLift),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                )
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_word_motion_cjk_wave),
                                    endActions = {
                                        Text(
                                            String.format("%.2f", wordMotionCjkWave),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = onWordMotionCjkWaveClick
                                )
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_word_motion_latin_lift),
                                    endActions = {
                                        Text(
                                            String.format("%.2f", wordMotionLatinLift),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = onWordMotionLatinLiftClick
                                )
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_word_motion_latin_wave),
                                    endActions = {
                                        Text(
                                            String.format("%.2f", wordMotionLatinWave),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    onClick = onWordMotionLatinWaveClick
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = wordEffect == RootConstants.WORD_EFFECT_REVEAL) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth()
                ) {
                    Column {
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.title_word_reveal_animation),
                            items = listOf(
                                stringResource(id = R.string.option_anim_none),
                                stringResource(id = R.string.option_word_reveal_fade_in),
                                stringResource(id = R.string.option_word_reveal_slide_up),
                                stringResource(id = R.string.option_word_reveal_slide_down),
                                stringResource(id = R.string.option_word_reveal_slide_right),
                                stringResource(id = R.string.option_word_reveal_slide_left),
                                stringResource(id = R.string.option_word_reveal_zoom_in),
                                stringResource(id = R.string.option_word_reveal_bounce_in),
                                stringResource(id = R.string.option_word_reveal_flip_in),
                                stringResource(id = R.string.option_word_reveal_wipe_in)
                            ),
                            selectedIndex = wordRevealAnimation,
                            onSelectedIndexChange = onWordRevealAnimationChange
                        )
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.title_anim_speed),
                            items = animationSpeedOptions,
                            selectedIndex = RootConstants.HOOK_ANIM_SPEED_RATES
                                .indexOf(wordRevealSpeedRate),
                            onSelectedIndexChange = { index ->
                                RootConstants.HOOK_ANIM_SPEED_RATES.getOrNull(index)?.let(
                                    onWordRevealSpeedRateChange
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
