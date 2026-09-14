package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.contentlayout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.MusicInfoLayoutPolicy
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplayPolicy
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs

@Composable
fun ContentLayoutPage() {
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)
    var firstLine by remember(prefs) {
        mutableStateOf(
            readFields(
                prefs = prefs,
                key = RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE,
                defaultFields = MusicInfoLayoutPolicy.defaultFirstLine
            )
        )
    }
    var secondLine by remember(prefs) {
        mutableStateOf(
            readFields(
                prefs = prefs,
                key = RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE,
                defaultFields = MusicInfoLayoutPolicy.defaultSecondLine
            )
        )
    }
    var separator by remember(prefs) {
        mutableStateOf(
            ContentLayoutSeparator.values().firstOrNull {
                it.key == MusicInfoLayoutPolicy.readSeparator(prefs)
            } ?: ContentLayoutSeparator.Hyphen
        )
    }
    var centerMusicInfo by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_MUSIC_INFO,
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_CENTER_LYRIC,
                    RootConstants.DEFAULT_HOOK_CENTER_MUSIC_INFO
                )
            )
        )
    }
    var centerLyric by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_CENTER_LYRIC,
                RootConstants.DEFAULT_HOOK_CENTER_LYRIC
            )
        )
    }
    var rightLyric by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_RIGHT_LYRIC,
                RootConstants.DEFAULT_HOOK_RIGHT_LYRIC
            )
        )
    }
    var placeholderFormat by remember(prefs) {
        mutableStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT,
                RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
            ).coerceIn(
                RootConstants.PLACEHOLDER_FORMAT_NONE,
                RootConstants.PLACEHOLDER_FORMAT_COUNTDOWN
            )
        )
    }
    var hideTitleAlias by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS,
                RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS
            )
        )
    }
    var lyricContentDisplay by remember(prefs) {
        mutableStateOf(LyricContentDisplayPolicy.read(prefs))
    }
    var showLyricContentSheet by remember { mutableStateOf(false) }
    var onlySecondary by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ONLY_SECONDARY,
                RootConstants.DEFAULT_HOOK_ONLY_SECONDARY
            )
        )
    }
    var swapSecondary by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_SWAP_SECONDARY,
                RootConstants.DEFAULT_HOOK_SWAP_SECONDARY
            )
        )
    }
    var editingRow by remember { mutableStateOf<Int?>(null) }

    val currentEditingRow = editingRow
    val currentFields = when (currentEditingRow) {
        0 -> firstLine
        1 -> secondLine
        else -> emptyList()
    }
    val availableFields = ContentLayoutField.values().toList()

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

    ContentLayoutEditorBottomSheet(
        show = currentEditingRow != null,
        title = stringResource(
            id = if (currentEditingRow == 1) {
                R.string.title_content_layout_second_line
            } else {
                R.string.title_content_layout_first_line
            }
        ),
        currentFields = currentFields,
        availableFields = availableFields,
        requireSelection = currentEditingRow == 0,
        onDismiss = { editingRow = null },
        onConfirm = { selectedFields ->
            when (currentEditingRow) {
                0 -> {
                    if (selectedFields.isNotEmpty()) {
                        firstLine = selectedFields
                        PrefsBridge.putString(
                            RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE,
                            selectedFields.joinToString(",") { it.key }
                        )
                    }
                }

                1 -> {
                    secondLine = selectedFields
                    PrefsBridge.putString(
                        RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE,
                        selectedFields.joinToString(",") { it.key }
                    )
                }
            }
        }
    )

    XposedLyricSettingPage(title = stringResource(id = R.string.title_content_layout)) {
        contentLayoutSections(
            firstLine = firstLine,
            secondLine = secondLine,
            separator = separator,
            onEditField = { editingRow = it },
            onSeparatorChange = {
                separator = it
                PrefsBridge.putString(
                    RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SEPARATOR,
                    it.key
                )
            },
            centerMusicInfo = centerMusicInfo,
            onCenterMusicInfoChange = {
                centerMusicInfo = it
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_CENTER_MUSIC_INFO, it)
            },
            centerLyric = centerLyric,
            onCenterLyricChange = {
                centerLyric = it
                if (it && rightLyric) {
                    rightLyric = false
                    PrefsBridge.putBoolean(RootConstants.KEY_HOOK_RIGHT_LYRIC, false)
                }
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_CENTER_LYRIC, it)
            },
            rightLyric = rightLyric,
            onRightLyricChange = {
                rightLyric = it
                if (it && centerLyric) {
                    centerLyric = false
                    PrefsBridge.putBoolean(RootConstants.KEY_HOOK_CENTER_LYRIC, false)
                }
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_RIGHT_LYRIC, it)
            },
            placeholderFormat = placeholderFormat,
            onPlaceholderFormatChange = {
                placeholderFormat = it
                PrefsBridge.putInt(RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT, it)
            },
            hideTitleAlias = hideTitleAlias,
            onHideTitleAliasChange = {
                hideTitleAlias = it
                PrefsBridge.putBoolean(
                    RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS,
                    it
                )
            },
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

private fun readFields(
    prefs: android.content.SharedPreferences,
    key: String,
    defaultFields: List<String>
): List<ContentLayoutField> {
    return MusicInfoLayoutPolicy.readFields(prefs, key, defaultFields)
        .mapNotNull { storedKey ->
            ContentLayoutField.values().firstOrNull { it.key == storedKey }
        }
}
