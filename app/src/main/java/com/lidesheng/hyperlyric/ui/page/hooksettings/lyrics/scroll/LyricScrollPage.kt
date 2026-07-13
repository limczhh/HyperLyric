package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.scroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs

@Composable
fun LyricScrollPage() {
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)

    val lyricMode by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)) }
    var marqueeMode by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)) }
    var marqueeSpeed by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_SPEED)) }
    var marqueeDelay by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_DELAY)) }
    var marqueeInfinite by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_INFINITE)) }
    var marqueeLoop by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_LOOP_DELAY)) }
    var marqueeStopEnd by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_STOP_END, RootConstants.DEFAULT_HOOK_MARQUEE_STOP_END)) }
    var marqueeMetadataMode by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE)) }
    var marqueeMetadataSpeed by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_SPEED)) }
    var marqueeMetadataDelay by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_DELAY)) }
    var marqueeMetadataInfinite by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_INFINITE)) }
    var marqueeMetadataLoopDelay by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY)) }

    var showMarqueeSpeedDialog by remember { mutableStateOf(false) }
    var showMarqueeDelayDialog by remember { mutableStateOf(false) }
    var showMarqueeLoopDialog by remember { mutableStateOf(false) }
    var showMarqueeMetadataSpeedDialog by remember { mutableStateOf(false) }
    var showMarqueeMetadataDelayDialog by remember { mutableStateOf(false) }
    var showMarqueeMetadataLoopDialog by remember { mutableStateOf(false) }

    NumberInputDialog(
        show = showMarqueeSpeedDialog,
        title = stringResource(id = R.string.title_marquee_speed),
        label = stringResource(id = R.string.label_marquee_speed_range),
        initialValue = marqueeSpeed,
        min = 5,
        max = 100,
        onDismiss = { showMarqueeSpeedDialog = false },
        onConfirm = { value ->
            marqueeSpeed = value
            saveConfig(RootConstants.KEY_HOOK_MARQUEE_SPEED, value)
        }
    )
    NumberInputDialog(
        show = showMarqueeDelayDialog,
        title = stringResource(id = R.string.title_marquee_delay),
        label = stringResource(id = R.string.label_marquee_delay_range),
        initialValue = marqueeDelay,
        min = 0,
        max = 5000,
        onDismiss = { showMarqueeDelayDialog = false },
        onConfirm = { value ->
            marqueeDelay = value
            saveConfig(RootConstants.KEY_HOOK_MARQUEE_DELAY, value)
        }
    )
    NumberInputDialog(
        show = showMarqueeLoopDialog,
        title = stringResource(id = R.string.title_marquee_loop),
        label = stringResource(id = R.string.label_marquee_loop_range),
        initialValue = marqueeLoop,
        min = 0,
        max = 5000,
        onDismiss = { showMarqueeLoopDialog = false },
        onConfirm = { value ->
            marqueeLoop = value
            saveConfig(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, value)
        }
    )
    NumberInputDialog(
        show = showMarqueeMetadataSpeedDialog,
        title = stringResource(id = R.string.title_marquee_metadata_speed),
        label = stringResource(id = R.string.label_marquee_speed_range),
        initialValue = marqueeMetadataSpeed,
        min = 5,
        max = 100,
        onDismiss = { showMarqueeMetadataSpeedDialog = false },
        onConfirm = { value ->
            marqueeMetadataSpeed = value
            saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED, value)
        }
    )
    NumberInputDialog(
        show = showMarqueeMetadataDelayDialog,
        title = stringResource(id = R.string.title_marquee_metadata_delay),
        label = stringResource(id = R.string.label_marquee_delay_range),
        initialValue = marqueeMetadataDelay,
        min = 0,
        max = 10000,
        onDismiss = { showMarqueeMetadataDelayDialog = false },
        onConfirm = { value ->
            marqueeMetadataDelay = value
            saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY, value)
        }
    )
    NumberInputDialog(
        show = showMarqueeMetadataLoopDialog,
        title = stringResource(id = R.string.title_marquee_metadata_loop),
        label = stringResource(id = R.string.label_marquee_loop_range),
        initialValue = marqueeMetadataLoopDelay,
        min = 0,
        max = 10000,
        onDismiss = { showMarqueeMetadataLoopDialog = false },
        onConfirm = { value ->
            marqueeMetadataLoopDelay = value
            saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY, value)
        }
    )

    XposedLyricSettingPage(title = stringResource(id = R.string.title_marquee)) {
        lyricScrollSections(
            lyricMode = lyricMode,
            marqueeMode = marqueeMode,
            onMarqueeModeChange = {
                marqueeMode = it
                saveConfig(RootConstants.KEY_HOOK_MARQUEE_MODE, it)
            },
            marqueeSpeed = marqueeSpeed,
            onMarqueeSpeedClick = { showMarqueeSpeedDialog = true },
            marqueeDelay = marqueeDelay,
            onMarqueeDelayClick = { showMarqueeDelayDialog = true },
            marqueeInfinite = marqueeInfinite,
            onMarqueeInfiniteChange = {
                marqueeInfinite = it
                saveConfig(RootConstants.KEY_HOOK_MARQUEE_INFINITE, it)
            },
            marqueeLoop = marqueeLoop,
            onMarqueeLoopClick = { showMarqueeLoopDialog = true },
            marqueeStopEnd = marqueeStopEnd,
            onMarqueeStopEndChange = {
                marqueeStopEnd = it
                saveConfig(RootConstants.KEY_HOOK_MARQUEE_STOP_END, it)
            },
            marqueeMetadataMode = marqueeMetadataMode,
            onMarqueeMetadataModeChange = {
                marqueeMetadataMode = it
                saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, it)
            },
            marqueeMetadataSpeed = marqueeMetadataSpeed,
            onMarqueeMetadataSpeedClick = { showMarqueeMetadataSpeedDialog = true },
            marqueeMetadataDelay = marqueeMetadataDelay,
            onMarqueeMetadataDelayClick = { showMarqueeMetadataDelayDialog = true },
            marqueeMetadataInfinite = marqueeMetadataInfinite,
            onMarqueeMetadataInfiniteChange = {
                marqueeMetadataInfinite = it
                saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE, it)
            },
            marqueeMetadataLoopDelay = marqueeMetadataLoopDelay,
            onMarqueeMetadataLoopClick = { showMarqueeMetadataLoopDialog = true }
        )
    }
}
