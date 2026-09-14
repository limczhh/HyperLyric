package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.translation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplayPolicy
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs

@Composable
fun LyricTranslationPage() {
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)
    var lyricContentDisplay by remember(prefs) {
        mutableStateOf(LyricContentDisplayPolicy.read(prefs))
    }
    var showLyricContentSheet by remember { mutableStateOf(false) }

    var onlySecondary by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ONLY_SECONDARY,
                RootConstants.DEFAULT_HOOK_ONLY_SECONDARY
            )
        )
    }
    var swapSecondary by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_SWAP_SECONDARY,
                RootConstants.DEFAULT_HOOK_SWAP_SECONDARY
            )
        )
    }
    LyricContentDisplayBottomSheet(
        show = showLyricContentSheet,
        currentSettings = lyricContentDisplay,
        onDismiss = { showLyricContentSheet = false },
        onConfirm = { settings ->
            lyricContentDisplay = settings
            saveConfig(
                RootConstants.KEY_HOOK_LYRIC_SHOW_TRANSLATION,
                settings.showTranslation
            )
            saveConfig(RootConstants.KEY_HOOK_LYRIC_SHOW_ROMA, settings.showRoma)
            saveConfig(
                RootConstants.KEY_HOOK_LYRIC_SHOW_NEXT_LINE,
                settings.showNextLyric
            )
            saveConfig(
                RootConstants.KEY_HOOK_LYRIC_SECONDARY_ORDER,
                LyricContentDisplayPolicy.encodeOrder(settings.order)
            )
        }
    )

    XposedLyricSettingPage(title = stringResource(id = R.string.title_double_line_content)) {
        translationSections(
            lyricContentDisplay = lyricContentDisplay,
            onEditLyricContent = { showLyricContentSheet = true },
            lyricContentSheetVisible = showLyricContentSheet,
            onlySecondary = onlySecondary,
            onOnlySecondaryChange = {
                onlySecondary = it
                saveConfig(RootConstants.KEY_HOOK_ONLY_SECONDARY, it)
                if (it && swapSecondary) {
                    swapSecondary = false
                    saveConfig(RootConstants.KEY_HOOK_SWAP_SECONDARY, false)
                }
            },
            swapSecondary = swapSecondary,
            onSwapSecondaryChange = {
                swapSecondary = it
                saveConfig(RootConstants.KEY_HOOK_SWAP_SECONDARY, it)
                if (it && onlySecondary) {
                    onlySecondary = false
                    saveConfig(RootConstants.KEY_HOOK_ONLY_SECONDARY, false)
                }
            }
        )
    }
}
