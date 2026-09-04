package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.SuperIslandContentStylePolicy
import com.lidesheng.hyperlyric.common.SuperIslandWidthPolicy
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.PaddingInputDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RangeSlider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun SuperIslandSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs =
        remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }

    fun readContentMode(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue).takeIf {
            it == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                    it == RootConstants.ISLAND_CONTENT_MODE_LYRIC ||
                    it == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
        } ?: defaultValue
    }

    var splitLyric by remember {
        mutableStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_LYRIC_MODE,
                RootConstants.DEFAULT_HOOK_LYRIC_MODE
            ).coerceIn(0, 1) == 1
        )
    }
    var islandContentLeft by remember {
        mutableIntStateOf(
            readContentMode(
                RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
                RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT
            )
        )
    }
    var islandContentRight by remember {
        mutableIntStateOf(
            readContentMode(
                RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
                RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT
            )
        )
    }

    var audioCoverStyle by remember {
        mutableIntStateOf(
            SuperIslandContentStylePolicy.readAlbumCoverStyle(prefs)
        )
    }
    var audioRhythmStyle by remember {
        mutableIntStateOf(
            SuperIslandContentStylePolicy.readMusicWaveStyle(prefs)
        )
    }
    var disableWidthLimit by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
                RootConstants.DEFAULT_HOOK_ISLAND_DISABLE_WIDTH_LIMIT
            )
        )
    }
    val audioCover = SuperIslandContentStylePolicy.isAlbumCoverVisible(audioCoverStyle)
    val audioRhythm = SuperIslandContentStylePolicy.isMusicWaveVisible(audioRhythmStyle)
    val islandWidthMin = SuperIslandWidthPolicy.minIslandWidth(audioCover, audioRhythm)
    val islandWidthMax = SuperIslandWidthPolicy.maxIslandWidth(
        showRhythm = audioRhythm,
        disableWidthLimit = disableWidthLimit
    )
    var leftPaddingLeft by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
                RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT
            )
        )
    }
    var leftPaddingRight by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
                RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT
            )
        )
    }
    var rightPaddingLeft by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
                RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT
            )
        )
    }
    var rightPaddingRight by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
                RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT
            )
        )
    }
    var islandWidth by remember {
        mutableIntStateOf(
            SuperIslandWidthPolicy.normalizeIslandWidth(
                islandWidth = prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH,
                    RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH
                ),
                showAlbum = audioCover,
                showRhythm = audioRhythm,
                disableWidthLimit = disableWidthLimit
            )
        )
    }
    var islandWidthMode by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
                RootConstants.DEFAULT_HOOK_ISLAND_WIDTH_MODE
            ).coerceIn(
                RootConstants.ISLAND_WIDTH_MODE_FIXED,
                RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
            )
        )
    }
    var dynamicWidthRange by remember {
        val initialMin = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH,
            islandWidthMin
        ).coerceIn(islandWidthMin, islandWidthMax)
        val initialMax = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH,
            islandWidth
        ).coerceIn(initialMin, islandWidthMax)
        mutableStateOf(initialMin.toFloat()..initialMax.toFloat())
    }
    var dynamicWidthBasis by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS,
                RootConstants.DEFAULT_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS
            ).coerceIn(
                RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_ALL,
                RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY
            )
        )
    }
    var afterPauseBehavior by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
                RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
            )
        )
    }
    var longPressBehavior by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_LONG_PRESS_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_ISLAND_LONG_PRESS_BEHAVIOR
            ).takeIf {
                it == RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_DEFAULT ||
                        it == RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_LYRIC_SHARE ||
                        it == RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_TOGGLE_PLAYBACK
            } ?: RootConstants.DEFAULT_HOOK_ISLAND_LONG_PRESS_BEHAVIOR
        )
    }
    val longPressBehaviorValues = remember {
        listOf(
            RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_DEFAULT,
            RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_LYRIC_SHARE,
            RootConstants.ISLAND_LONG_PRESS_BEHAVIOR_TOGGLE_PLAYBACK
        )
    }
    var swipeBehavior by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_SWIPE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_ISLAND_SWIPE_BEHAVIOR
            ).takeIf {
                it == RootConstants.ISLAND_SWIPE_BEHAVIOR_DEFAULT ||
                        it == RootConstants.ISLAND_SWIPE_BEHAVIOR_TRACK_SWITCH ||
                        it == RootConstants.ISLAND_SWIPE_BEHAVIOR_TRACK_SWITCH_REVERSED
            } ?: RootConstants.DEFAULT_HOOK_ISLAND_SWIPE_BEHAVIOR
        )
    }
    val swipeBehaviorValues = remember {
        listOf(
            RootConstants.ISLAND_SWIPE_BEHAVIOR_DEFAULT,
            RootConstants.ISLAND_SWIPE_BEHAVIOR_TRACK_SWITCH,
            RootConstants.ISLAND_SWIPE_BEHAVIOR_TRACK_SWITCH_REVERSED
        )
    }
    var extractGlowColor by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
            )
        )
    }
    var progressGlow by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GLOW
            )
        )
    }
    var progressGradient by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GRADIENT
            )
        )
    }
    var progressStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_STYLE
            ).coerceIn(
                RootConstants.ISLAND_PROGRESS_STYLE_TOP_CLOCKWISE,
                RootConstants.ISLAND_PROGRESS_STYLE_BOTTOM_BIDIRECTIONAL
            )
        )
    }

    var showLeftPaddingDialog by remember { mutableStateOf(false) }
    var showRightPaddingDialog by remember { mutableStateOf(false) }
    var showIslandWidthDialog by remember { mutableStateOf(false) }

    fun saveConfig(key: String, value: Any) {
        prefs.edit {
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
            }
        }
        when (value) {
            is Int -> PrefsBridge.putInt(key, value)
            is Boolean -> PrefsBridge.putBoolean(key, value)
        }
    }

    fun commitDynamicWidthRange(
        value: ClosedFloatingPointRange<Float>,
        showAlbum: Boolean = audioCover,
        showRhythm: Boolean = audioRhythm
    ) {
        val minBound = SuperIslandWidthPolicy.minIslandWidth(showAlbum, showRhythm)
        val maxBound = SuperIslandWidthPolicy.maxIslandWidth(showRhythm, disableWidthLimit)
        val minValue = value.start.roundToInt().coerceIn(minBound, maxBound)
        val maxValue = value.endInclusive.roundToInt().coerceIn(minValue, maxBound)
        dynamicWidthRange = minValue.toFloat()..maxValue.toFloat()
        saveConfig(RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH, minValue)
        saveConfig(RootConstants.KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH, maxValue)
    }

    fun clampDynamicWidthRangeIfNeeded(showAlbum: Boolean, showRhythm: Boolean) {
        val minBound = SuperIslandWidthPolicy.minIslandWidth(showAlbum, showRhythm)
        val maxBound = SuperIslandWidthPolicy.maxIslandWidth(showRhythm, disableWidthLimit)
        val minValue = dynamicWidthRange.start.roundToInt().coerceIn(minBound, maxBound)
        val maxValue = dynamicWidthRange.endInclusive.roundToInt().coerceIn(minValue, maxBound)
        val normalized = minValue.toFloat()..maxValue.toFloat()
        if (normalized != dynamicWidthRange) {
            commitDynamicWidthRange(normalized, showAlbum, showRhythm)
        }
    }

    fun commitIslandWidth(
        value: Int,
        showAlbum: Boolean = audioCover,
        showRhythm: Boolean = audioRhythm
    ) {
        islandWidth = SuperIslandWidthPolicy.normalizeIslandWidth(
            islandWidth = value,
            showAlbum = showAlbum,
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, islandWidth)
    }

    fun clampIslandWidthIfNeeded(showAlbum: Boolean, showRhythm: Boolean) {
        val normalizedWidth = SuperIslandWidthPolicy.normalizeIslandWidth(
            islandWidth = islandWidth,
            showAlbum = showAlbum,
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        if (normalizedWidth != islandWidth) {
            commitIslandWidth(normalizedWidth, showAlbum, showRhythm)
        }
    }

    fun saveAudioCoverStyle(style: Int) {
        val visible = SuperIslandContentStylePolicy.isAlbumCoverVisible(style)
        audioCoverStyle = style
        saveConfig(RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE, style)
        clampIslandWidthIfNeeded(showAlbum = visible, showRhythm = audioRhythm)
        clampDynamicWidthRangeIfNeeded(showAlbum = visible, showRhythm = audioRhythm)
    }

    fun saveAudioRhythmStyle(style: Int) {
        val visible = SuperIslandContentStylePolicy.isMusicWaveVisible(style)
        audioRhythmStyle = style
        saveConfig(RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE, style)
        clampIslandWidthIfNeeded(showAlbum = audioCover, showRhythm = visible)
        clampDynamicWidthRangeIfNeeded(showAlbum = audioCover, showRhythm = visible)
    }

    val islandWidthKeyPoints = remember(islandWidthMin, islandWidthMax) {
        (islandWidthMin..islandWidthMax step 20).map(Int::toFloat)
    }
    val islandWidthModeOptions = remember {
        listOf(
            R.string.option_super_island_width_fixed,
            R.string.option_super_island_width_dynamic
        )
    }.map { stringResource(id = it) }
    val dynamicWidthBasisOptions = remember {
        listOf(
            R.string.option_dynamic_width_basis_all,
            R.string.option_dynamic_width_basis_lyric_only
        )
    }.map { stringResource(id = it) }

    val afterPauseOptions = remember {
        listOf(R.string.option_after_pause_default, R.string.option_after_pause_keep)
    }.map { stringResource(id = it) }
    val longPressBehaviorOptions = remember {
        listOf(
            R.string.option_island_long_press_default,
            R.string.option_island_long_press_lyric_share,
            R.string.option_island_long_press_toggle_playback
        )
    }.map { stringResource(id = it) }
    val swipeBehaviorOptions = remember {
        listOf(
            R.string.option_island_swipe_default,
            R.string.option_island_swipe_track_switch,
            R.string.option_island_swipe_track_switch_reversed
        )
    }.map { stringResource(id = it) }
    val audioCoverStyleValues = remember {
        listOf(
            RootConstants.ISLAND_ALBUM_COVER_STYLE_DEFAULT,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_CIRCLE,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_ROTATING_CIRCLE,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_APP_ICON,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_HIDDEN
        )
    }
    val audioCoverStyleOptions = remember {
        listOf(
            R.string.option_audio_cover_style_default,
            R.string.option_audio_cover_style_circle,
            R.string.option_audio_cover_style_rotating_circle,
            R.string.option_audio_cover_style_app_icon,
            R.string.option_island_component_hidden
        )
    }.map { stringResource(id = it) }
    val audioRhythmStyleValues = remember {
        listOf(
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_DEFAULT,
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR,
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT,
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_HIDDEN
        )
    }
    val audioRhythmStyleOptions = remember {
        listOf(
            R.string.option_audio_rhythm_default,
            R.string.option_audio_rhythm_cover_color,
            R.string.option_audio_rhythm_cover_gradient,
            R.string.option_island_component_hidden
        )
    }.map { stringResource(id = it) }
    val contentModeValues = remember {
        listOf(
            RootConstants.ISLAND_CONTENT_MODE_NONE,
            RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO,
            RootConstants.ISLAND_CONTENT_MODE_LYRIC
        )
    }
    val contentOptions = remember {
        listOf(
            R.string.option_content_none,
            R.string.option_content_music_info,
            R.string.option_content_lyric
        )
    }.map { stringResource(id = it) }
    val progressStyleOptions = remember {
        listOf(
            R.string.option_island_progress_top_clockwise,
            R.string.option_island_progress_right_clockwise,
            R.string.option_island_progress_bottom_clockwise,
            R.string.option_island_progress_left_clockwise,
            R.string.option_island_progress_left_bidirectional,
            R.string.option_island_progress_top_bidirectional,
            R.string.option_island_progress_bottom_bidirectional
        )
    }.map { stringResource(id = it) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_super_island),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        NumberInputDialog(
            show = showIslandWidthDialog,
            title = stringResource(id = R.string.title_super_island_width),
            label = stringResource(
                id = R.string.label_content_width_range,
                islandWidthMin,
                islandWidthMax
            ),
            initialValue = islandWidth,
            min = islandWidthMin,
            max = islandWidthMax,
            onDismiss = { showIslandWidthDialog = false },
            onConfirm = { commitIslandWidth(it) }
        )
        PaddingInputDialog(
            show = showLeftPaddingDialog,
            title = stringResource(id = R.string.title_left_padding),
            initialLeft = leftPaddingLeft,
            initialRight = leftPaddingRight,
            onDismiss = { showLeftPaddingDialog = false },
            onConfirm = { l, r ->
                leftPaddingLeft = l; leftPaddingRight = r; saveConfig(
                RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
                l
            ); saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, r)
            })
        PaddingInputDialog(
            show = showRightPaddingDialog,
            title = stringResource(id = R.string.title_right_padding),
            initialLeft = rightPaddingLeft,
            initialRight = rightPaddingRight,
            onDismiss = { showRightPaddingDialog = false },
            onConfirm = { l, r ->
                rightPaddingLeft = l; rightPaddingRight = r; saveConfig(
                RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
                l
            ); saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, r)
            })

        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(top = top, start = 0.dp, end = 0.dp, bottom = bottom)
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding,
            ) {
                item(key = "layout_title") { SmallTitle(text = stringResource(id = R.string.title_layout)) }
                item(key = "layout_content") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_super_island_width_mode),
                                items = islandWidthModeOptions,
                                selectedIndex = islandWidthMode,
                                onSelectedIndexChange = {
                                    islandWidthMode = it
                                    saveConfig(RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE, it)
                                }
                            )
                            AnimatedVisibility(
                                visible = islandWidthMode == RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
                            ) {
                                OverlayDropdownPreference(
                                    title = stringResource(id = R.string.title_dynamic_width_basis),
                                    items = dynamicWidthBasisOptions,
                                    selectedIndex = dynamicWidthBasis,
                                    onSelectedIndexChange = {
                                        dynamicWidthBasis = it
                                        saveConfig(
                                            RootConstants.KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS,
                                            it
                                        )
                                    }
                                )
                            }
                            ArrowPreference(
                                title = stringResource(id = R.string.title_super_island_width),
                                endActions = {
                                    Text(
                                        if (islandWidthMode == RootConstants.ISLAND_WIDTH_MODE_DYNAMIC) {
                                            "${dynamicWidthRange.start.roundToInt()}~${dynamicWidthRange.endInclusive.roundToInt()}"
                                        } else {
                                            "$islandWidth"
                                        },
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                bottomAction = {
                                    if (islandWidthMode == RootConstants.ISLAND_WIDTH_MODE_DYNAMIC) {
                                        RangeSlider(
                                            value = dynamicWidthRange,
                                            onValueChange = {
                                                val start = it.start.roundToInt()
                                                    .coerceIn(islandWidthMin, islandWidthMax)
                                                val end = it.endInclusive.roundToInt()
                                                    .coerceIn(start, islandWidthMax)
                                                dynamicWidthRange = start.toFloat()..end.toFloat()
                                            },
                                            valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                                            steps = 0,
                                            onValueChangeFinished = {
                                                commitDynamicWidthRange(dynamicWidthRange)
                                            },
                                            showKeyPoints = true,
                                            keyPoints = islandWidthKeyPoints,
                                            magnetThreshold = 0f,
                                            hapticEffect = SliderDefaults.SliderHapticEffect.Step
                                        )
                                    } else {
                                        Slider(
                                            value = islandWidth.toFloat(),
                                            onValueChange = {
                                                islandWidth = it.roundToInt()
                                                    .coerceIn(islandWidthMin, islandWidthMax)
                                            },
                                            valueRange = islandWidthMin.toFloat()..islandWidthMax.toFloat(),
                                            steps = 0,
                                            onValueChangeFinished = {
                                                commitIslandWidth(islandWidth)
                                            },
                                            showKeyPoints = true,
                                            keyPoints = islandWidthKeyPoints,
                                            magnetThreshold = 0f,
                                            hapticEffect = SliderDefaults.SliderHapticEffect.Step
                                        )
                                    }
                                },
                                onClick = {
                                    if (islandWidthMode == RootConstants.ISLAND_WIDTH_MODE_FIXED) {
                                        showIslandWidthDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
                item(key = "disable_width_limit") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_super_island_disable_width_limit),
                            checked = disableWidthLimit,
                            onCheckedChange = { enabled ->
                                disableWidthLimit = enabled
                                clampIslandWidthIfNeeded(audioCover, audioRhythm)
                                clampDynamicWidthRangeIfNeeded(audioCover, audioRhythm)
                                saveConfig(
                                    RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT,
                                    enabled
                                )
                            }
                        )
                    }
                }
                item(key = "padding_content") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            ArrowPreference(
                                title = stringResource(id = R.string.title_left_padding),
                                endActions = {
                                    Text(
                                        stringResource(
                                            id = R.string.format_padding_pair,
                                            leftPaddingLeft,
                                            leftPaddingRight
                                        ),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = { showLeftPaddingDialog = true }
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_right_padding),
                                endActions = {
                                    Text(
                                        stringResource(
                                            id = R.string.format_padding_pair,
                                            rightPaddingLeft,
                                            rightPaddingRight
                                        ),
                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                },
                                onClick = { showRightPaddingDialog = true }
                            )
                        }
                    }
                }
                item(key = "content_title") { SmallTitle(text = stringResource(id = R.string.title_content)) }
                item(key = "split_lyric") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_split_lyric),
                            checked = splitLyric,
                            onCheckedChange = {
                                splitLyric = it
                                saveConfig(RootConstants.KEY_HOOK_LYRIC_MODE, if (it) 1 else 0)
                            }
                        )
                    }
                }
                item(key = "content") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_audio_cover),
                                items = audioCoverStyleOptions,
                                selectedIndex = audioCoverStyleValues.indexOf(
                                    audioCoverStyle
                                ).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    saveAudioCoverStyle(audioCoverStyleValues[index])
                                }
                            )
                            if (!splitLyric) {
                                OverlayDropdownPreference(
                                    title = stringResource(id = R.string.title_super_island_left),
                                    items = contentOptions,
                                    selectedIndex = contentModeValues.indexOf(islandContentLeft)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = { index ->
                                        contentModeValues.getOrNull(index)?.let { mode ->
                                            islandContentLeft = mode
                                            saveConfig(
                                                RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
                                                mode
                                            )
                                        }
                                    }
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(id = R.string.title_super_island_right),
                                    items = contentOptions,
                                    selectedIndex = contentModeValues.indexOf(islandContentRight)
                                        .coerceAtLeast(0),
                                    onSelectedIndexChange = { index ->
                                        contentModeValues.getOrNull(index)?.let { mode ->
                                            islandContentRight = mode
                                            saveConfig(
                                                RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
                                                mode
                                            )
                                        }
                                    }
                                )
                            }
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_audio_rhythm),
                                items = audioRhythmStyleOptions,
                                selectedIndex = audioRhythmStyleValues.indexOf(
                                    audioRhythmStyle
                                ).coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    saveAudioRhythmStyle(audioRhythmStyleValues[index])
                                }
                            )
                        }
                    }
                }
                item(key = "interaction_title") {
                    SmallTitle(text = stringResource(id = R.string.title_interaction))
                }
                item(key = "interaction_behavior") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_island_swipe_behavior),
                                items = swipeBehaviorOptions,
                                selectedIndex = swipeBehaviorValues.indexOf(swipeBehavior)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    val behavior = swipeBehaviorValues.getOrNull(index)
                                        ?: RootConstants.DEFAULT_HOOK_ISLAND_SWIPE_BEHAVIOR
                                    swipeBehavior = behavior
                                    saveConfig(
                                        RootConstants.KEY_HOOK_ISLAND_SWIPE_BEHAVIOR,
                                        behavior
                                    )
                                }
                            )
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_island_long_press_behavior),
                                items = longPressBehaviorOptions,
                                selectedIndex = longPressBehaviorValues.indexOf(longPressBehavior)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    val behavior = longPressBehaviorValues.getOrNull(index)
                                        ?: RootConstants.DEFAULT_HOOK_ISLAND_LONG_PRESS_BEHAVIOR
                                    longPressBehavior = behavior
                                    saveConfig(
                                        RootConstants.KEY_HOOK_ISLAND_LONG_PRESS_BEHAVIOR,
                                        behavior
                                    )
                                }
                            )
                        }
                    }
                }
                item(key = "special_features_title") { SmallTitle(text = stringResource(id = R.string.title_special_features)) }
                item(key = "playback_behavior") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_behavior_after_pause),
                                items = afterPauseOptions,
                                selectedIndex = afterPauseBehavior,
                                onSelectedIndexChange = {
                                    afterPauseBehavior = it; saveConfig(
                                    RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
                                    it
                                )
                                }
                            )
                        }
                    }
                }
                item(key = "edge_glow") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            SwitchPreference(
                                title = stringResource(id = R.string.title_glow_cover_color),
                                checked = extractGlowColor,
                                onCheckedChange = {
                                    extractGlowColor = it; saveConfig(
                                    RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                                    it
                                )
                                }
                            )
                            SwitchPreference(
                                title = stringResource(id = R.string.title_island_progress_glow),
                                checked = progressGlow,
                                onCheckedChange = {
                                    progressGlow = it; saveConfig(
                                    RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
                                    it
                                )
                                }
                            )
                            AnimatedVisibility(visible = progressGlow) {
                                Column {
                                    OverlayDropdownPreference(
                                        title = stringResource(id = R.string.title_island_progress_style),
                                        items = progressStyleOptions,
                                        selectedIndex = progressStyle,
                                        onSelectedIndexChange = {
                                            progressStyle = it
                                            saveConfig(
                                                RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
                                                it
                                            )
                                        }
                                    )
                                    AnimatedVisibility(visible = extractGlowColor) {
                                        Column {
                                            SwitchPreference(
                                                title = stringResource(id = R.string.title_island_progress_gradient),
                                                checked = progressGradient,
                                                onCheckedChange = {
                                                    progressGradient = it
                                                    saveConfig(
                                                        RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
                                                        it
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
