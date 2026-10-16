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
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
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
    var musicInfoAlignment by remember(prefs) {
        mutableStateOf(
            ContentLayoutAlignment.fromValue(
                RootConstants.normalizeHookContentAlignment(
                    prefs.getInt(
                        RootConstants.KEY_HOOK_MUSIC_INFO_ALIGNMENT,
                        RootConstants.DEFAULT_HOOK_MUSIC_INFO_ALIGNMENT
                    )
                )
            )
        )
    }
    var lyricAlignment by remember(prefs) {
        mutableStateOf(
            ContentLayoutAlignment.fromValue(
                RootConstants.normalizeHookContentAlignment(
                    prefs.getInt(
                        RootConstants.KEY_HOOK_LYRIC_ALIGNMENT,
                        RootConstants.DEFAULT_HOOK_LYRIC_ALIGNMENT
                    )
                )
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
    var noLyricsBehavior by remember(prefs) {
        mutableStateOf(
            RootConstants.normalizeNoLyricsLyricBehavior(
                prefs.getInt(
                    RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR,
                    RootConstants.DEFAULT_HOOK_NO_LYRICS_LYRIC_BEHAVIOR
                )
            )
        )
    }
    var tailTimeoutBehavior by remember(prefs) {
        mutableStateOf(
            RootConstants.normalizeTailTimeoutBehavior(
                prefs.getInt(
                    RootConstants.KEY_HOOK_TAIL_TIMEOUT_BEHAVIOR,
                    RootConstants.DEFAULT_HOOK_TAIL_TIMEOUT_BEHAVIOR
                )
            )
        )
    }
    var tailTimeoutDelaySeconds by remember(prefs) {
        mutableStateOf(
            RootConstants.normalizeTailTimeoutDelaySeconds(
                prefs.getInt(
                    RootConstants.KEY_HOOK_TAIL_TIMEOUT_DELAY_SECONDS,
                    RootConstants.DEFAULT_HOOK_TAIL_TIMEOUT_DELAY_SECONDS
                )
            )
        )
    }
    var showLongInterludeCountdown by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_LONG_INTERLUDE_COUNTDOWN,
                RootConstants.DEFAULT_HOOK_LYRIC_LONG_INTERLUDE_COUNTDOWN
            )
        )
    }
    var lyricPreviewEnabled by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_PREVIEW,
                RootConstants.DEFAULT_HOOK_LYRIC_PREVIEW
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
    var autoDuet by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_AUTO_DUET,
                RootConstants.DEFAULT_HOOK_LYRIC_AUTO_DUET
            )
        )
    }
    var editingRow by remember { mutableStateOf<Int?>(null) }
    var showTailTimeoutDelayDialog by remember { mutableStateOf(false) }

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
                RootConstants.KEY_HOOK_LYRIC_SHOW_BACKGROUND_VOCAL,
                settings.showBackgroundVocal
            )
            saveConfig(
                RootConstants.KEY_HOOK_LYRIC_SHOW_OVERLAPPING_LINE,
                settings.showOverlappingLine
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

    NumberInputDialog(
        show = showTailTimeoutDelayDialog,
        title = stringResource(id = R.string.title_tail_timeout_delay),
        label = stringResource(id = R.string.label_tail_timeout_delay_range),
        summary = stringResource(id = R.string.summary_tail_timeout_delay),
        initialValue = tailTimeoutDelaySeconds,
        min = RootConstants.MIN_HOOK_TAIL_TIMEOUT_DELAY_SECONDS,
        max = RootConstants.MAX_HOOK_TAIL_TIMEOUT_DELAY_SECONDS,
        onDismiss = { showTailTimeoutDelayDialog = false },
        onConfirm = { value ->
            tailTimeoutDelaySeconds = value
            PrefsBridge.putInt(RootConstants.KEY_HOOK_TAIL_TIMEOUT_DELAY_SECONDS, value)
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
            musicInfoAlignment = musicInfoAlignment,
            onMusicInfoAlignmentChange = {
                musicInfoAlignment = it
                PrefsBridge.putInt(RootConstants.KEY_HOOK_MUSIC_INFO_ALIGNMENT, it.value)
            },
            lyricAlignment = lyricAlignment,
            onLyricAlignmentChange = {
                lyricAlignment = it
                PrefsBridge.putInt(RootConstants.KEY_HOOK_LYRIC_ALIGNMENT, it.value)
            },
            placeholderFormat = placeholderFormat,
            onPlaceholderFormatChange = {
                placeholderFormat = it
                PrefsBridge.putInt(RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT, it)
            },
            noLyricsBehavior = noLyricsBehavior,
            onNoLyricsBehaviorChange = {
                noLyricsBehavior = it
                PrefsBridge.putInt(RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR, it)
            },
            tailTimeoutBehavior = tailTimeoutBehavior,
            onTailTimeoutBehaviorChange = {
                tailTimeoutBehavior = it
                PrefsBridge.putInt(RootConstants.KEY_HOOK_TAIL_TIMEOUT_BEHAVIOR, it)
            },
            tailTimeoutDelaySeconds = tailTimeoutDelaySeconds,
            onTailTimeoutDelayClick = { showTailTimeoutDelayDialog = true },
            lyricPreviewEnabled = lyricPreviewEnabled,
            onLyricPreviewEnabledChange = {
                lyricPreviewEnabled = it
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_LYRIC_PREVIEW, it)
            },
            showLongInterludeCountdown = showLongInterludeCountdown,
            onLongInterludeCountdownChange = {
                showLongInterludeCountdown = it
                PrefsBridge.putBoolean(
                    RootConstants.KEY_HOOK_LYRIC_LONG_INTERLUDE_COUNTDOWN,
                    it
                )
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
            },
            autoDuet = autoDuet,
            onAutoDuetChange = {
                autoDuet = it
                saveConfig(RootConstants.KEY_HOOK_LYRIC_AUTO_DUET, it)
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
