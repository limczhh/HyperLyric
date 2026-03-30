package com.lidesheng.hyperlyric.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.runtime.collectAsState
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.service.ForegroundLyricService
import com.lidesheng.hyperlyric.service.LyricTileService
import com.lidesheng.hyperlyric.online.model.DynamicLyricData
import com.lidesheng.hyperlyric.utils.ThemeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

val commonMusicApps = mapOf(
    "com.salt.music" to "Salt Player",
    "com.netease.cloudmusic" to "网易云音乐",
    "com.tencent.qqmusic" to "QQ音乐",
    "cn.kuwo.player" to "酷我音乐",
    "com.kugou.android" to "酷狗音乐",
    "com.apple.android.music" to "Apple Music",
    "com.spotify.music" to "Spotify",
    "cmccwm.mobilemusic" to "咪咕音乐",
    "com.luna.music" to "汽水音乐",
    "com.kugou.android.lite" to "酷狗音乐概念版",
    "com.google.android.apps.youtube.music" to "YouTube Music",
    "cn.wenyu.bodian" to "波点音乐",
    "com.miui.player" to "小米音乐",
    "com.xuncorp.qinalt.music" to "青盐云听"
)

class DynamicIslandNotificationActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                NotificationSettingsScreen()
            }
        }
    }

    @SuppressLint("BatteryLife")
    @Composable
    fun NotificationSettingsScreen() {
        val context = LocalContext.current
        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

        val hazeState = remember { HazeState() }
        val hazeStyle = HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )

        var persistentEnabled by remember {
            mutableStateOf(prefs.getBoolean(Constants.KEY_PERSISTENT_FOREGROUND, false))
        }

        var onlineLyricEnabled by remember {
            mutableStateOf(prefs.getBoolean(Constants.KEY_ONLINE_LYRIC_ENABLED, Constants.DEFAULT_ONLINE_LYRIC_ENABLED))
        }

        var onlineLyricCacheLimit by remember {
            mutableIntStateOf(prefs.getInt(Constants.KEY_ONLINE_LYRIC_CACHE_LIMIT, Constants.DEFAULT_ONLINE_LYRIC_CACHE_LIMIT))
        }
        var showCacheLimitDialog by remember { mutableStateOf(false) }
        var tempCacheLimit by remember { mutableStateOf(onlineLyricCacheLimit.toString()) }

        val tabs = listOf("基础设置", "个性化设置", "歌词白名单")
        val pagerState = rememberPagerState { tabs.size }
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            DynamicLyricData.initWhitelist(context)
        }

        val whitelistSet by DynamicLyricData.whitelistState.collectAsState()
        val whitelist = remember(whitelistSet) { whitelistSet.toList() }

        var showAddWhitelistDialog by remember { mutableStateOf(false) }
        var showDeleteWhitelistDialog by remember { mutableStateOf(false) }
        var tempWhitelistInput by remember { mutableStateOf("") }
        var packageToDelete by remember { mutableStateOf("") }

        LaunchedEffect(pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }

        LaunchedEffect(selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                val message = if (isGranted) "已获取通知权限" else "未获取通知权限"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                ) {
                    TopAppBar(
                        color = Color.Transparent,
                        title = "灵动岛歌词通知",
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(
                                onClick = { finish() }
                            ) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "后退")
                            }
                        }
                    )
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { selectedTabIndex = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = top.yukonga.miuix.kmp.basic.TabRowDefaults.tabRowColors(
                            backgroundColor = Color.Transparent
                        )
                    )
                }
            }
        ) { padding ->



            if (showCacheLimitDialog) {
                WindowDialog(
                    title = "在线歌词缓存上限",
                    show = true,
                    onDismissRequest = { showCacheLimitDialog = false }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = tempCacheLimit,
                            onValueChange = { tempCacheLimit = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                        Row(horizontalArrangement = Arrangement.End) {
                            TextButton(
                                text = "取消",
                                onClick = { showCacheLimitDialog = false },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            TextButton(
                                text = "确认",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val newLimit = tempCacheLimit.toIntOrNull()
                                        ?: Constants.DEFAULT_ONLINE_LYRIC_CACHE_LIMIT
                                    onlineLyricCacheLimit = newLimit
                                    prefs.edit { putInt(Constants.KEY_ONLINE_LYRIC_CACHE_LIMIT, newLimit) }
                                    showCacheLimitDialog = false
                                }
                            )
                        }
                    }
                }
            }

            if (showAddWhitelistDialog) {
                WindowDialog(
                    title = "输入应用包名",
                    show = true,
                    onDismissRequest = { showAddWhitelistDialog = false }
                ) {
                    Column {
                        TextField(
                            value = tempWhitelistInput,
                            onValueChange = { tempWhitelistInput = it },
                            label = "例如 com.netease.cloudmusic",
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                text = "取消",
                                onClick = { showAddWhitelistDialog = false },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            TextButton(
                                text = "保存",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (tempWhitelistInput.isNotBlank()) {
                                        val success =
                                            DynamicLyricData.addPackageToWhitelist(context, tempWhitelistInput)
                                        if (success) {
                                            showAddWhitelistDialog = false
                                        } else {
                                            Toast.makeText(context, "该应用已存在", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "包名不能为空", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showDeleteWhitelistDialog) {
                WindowDialog(
                    title = "确认从白名单中移除应用吗？",
                    show = true,
                    onDismissRequest = { showDeleteWhitelistDialog = false }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showDeleteWhitelistDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        TextButton(
                            text = "确认",
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                DynamicLyricData.removePackageFromWhitelist(context, packageToDelete)
                                showDeleteWhitelistDialog = false
                            }
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                    when (page) {
                        0 -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollEndHaptic()
                                    .hazeSource(state = hazeState)
                                    .overScrollVertical()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    top = padding.calculateTopPadding(),
                                    start = 12.dp,
                                    end = 12.dp,
                                    bottom = padding.calculateBottomPadding()
                                )
                            ) {
                                item {
                                    Column {
                                        SmallTitle(
                                            text = "通知权限",
                                            insideMargin = PaddingValues(10.dp, 4.dp)
                                        )
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column {
                                                ArrowPreference(
                                                    title = "发送歌词通知权限",
                                                    summary = "要与\u201c获取歌词信息权限\u201d同时开启使用",
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                        } else {
                                                            Toast.makeText(context, "Android 13 以下无需申请此权限", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                                ArrowPreference(
                                                    title = "获取歌词信息权限",
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "无法打开通知使用权设置", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )

                                            }
                                        }

                                        SmallTitle(
                                            text = "通知管理",
                                            insideMargin = PaddingValues(10.dp, 4.dp)
                                        )
                                        var sendNormalEnabled by remember {
                                            mutableStateOf(prefs.getBoolean(Constants.KEY_SEND_NORMAL_NOTIFICATION, Constants.DEFAULT_SEND_NORMAL_NOTIFICATION))
                                        }
                                        var sendFocusEnabled by remember {
                                            mutableStateOf(prefs.getBoolean(Constants.KEY_SEND_FOCUS_NOTIFICATION, Constants.DEFAULT_SEND_FOCUS_NOTIFICATION))
                                        }
                                        var pauseListening by remember {
                                            mutableStateOf(prefs.getBoolean(Constants.KEY_PAUSE_LISTENING, Constants.DEFAULT_PAUSE_LISTENING))
                                        }

                                        val sharedPreferenceListener = remember {
                                            android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                                                if (key == Constants.KEY_PAUSE_LISTENING) {
                                                    pauseListening = p.getBoolean(Constants.KEY_PAUSE_LISTENING, Constants.DEFAULT_PAUSE_LISTENING)
                                                }
                                            }
                                        }
                                        LaunchedEffect(Unit) {
                                            prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)
                                        }

                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column {
                                                SwitchPreference(
                                                    title = "暂停媒体监听",
                                                    summary = "关闭所有歌词通知发送",
                                                    checked = pauseListening,
                                                    onCheckedChange = { checked ->
                                                        pauseListening = checked
                                                        prefs.edit { putBoolean(Constants.KEY_PAUSE_LISTENING, checked) }
                                                        val intent = Intent(context, ForegroundLyricService::class.java).apply {
                                                            action = if (checked) LyricTileService.ACTION_PAUSE_TOGGLED else LyricTileService.ACTION_RESUME_TOGGLED
                                                        }
                                                        context.startService(intent)
                                                    }
                                                )
                                                SwitchPreference(
                                                    title = "发送实时通知",
                                                    checked = sendNormalEnabled,
                                                    onCheckedChange = { checked ->
                                                        sendNormalEnabled = checked
                                                        prefs.edit { putBoolean(Constants.KEY_SEND_NORMAL_NOTIFICATION, checked) }
                                                    }
                                                )
                                                SwitchPreference(
                                                    title = "发送焦点通知",
                                                    checked = sendFocusEnabled,
                                                    onCheckedChange = { checked ->
                                                        sendFocusEnabled = checked
                                                        prefs.edit { putBoolean(Constants.KEY_SEND_FOCUS_NOTIFICATION, checked) }
                                                    }
                                                )
                                            }
                                        }

                                        SmallTitle(
                                            text = "高级功能",
                                            insideMargin = PaddingValues(10.dp, 4.dp)
                                        )
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column {
                                                ArrowPreference(
                                                    title = "应用自启动",
                                                    onClick = {
                                                        try {
                                                            val intent = Intent().apply {
                                                                component = android.content.ComponentName(
                                                                    "com.miui.securitycenter",
                                                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                                                )
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            try {
                                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                                    data = "package:${context.packageName}".toUri()
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "无法打开自启动设置", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                )
                                                ArrowPreference(
                                                    title = "忽略电池优化",
                                                    onClick = {
                                                        try {
                                                            val pm = context.getSystemService(POWER_SERVICE) as PowerManager
                                                            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                                    data = "package:${context.packageName}".toUri()
                                                                }
                                                                context.startActivity(intent)
                                                            } else {
                                                                Toast.makeText(context, "已忽略电池优化", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (_: Exception) {
                                                            try {
                                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) {
                                                                Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                )
                                                SwitchPreference(
                                                    title = "保持后台运行",
                                                    summary = "如果没有通知发送，可手动开关一遍该功能强行唤醒服务",
                                                    checked = persistentEnabled,
                                                    onCheckedChange = { checked ->
                                                        persistentEnabled = checked
                                                        prefs.edit {
                                                            putBoolean(
                                                                Constants.KEY_PERSISTENT_FOREGROUND,
                                                                checked
                                                            )
                                                        }
                                                        if (checked) {
                                                            ForegroundLyricService.startPersistent(context)
                                                        } else {
                                                            ForegroundLyricService.stopPersistent(context)
                                                        }
                                                    }
                                                )
                                                SwitchPreference(
                                                    title = "获取在线歌词",
                                                    summary = "实验性功能，谨慎启用\n开启后可关闭音乐软件车载蓝牙功能",
                                                    checked = onlineLyricEnabled,
                                                    onCheckedChange = { checked ->
                                                        onlineLyricEnabled = checked
                                                        prefs.edit {
                                                            putBoolean(Constants.KEY_ONLINE_LYRIC_ENABLED, checked)
                                                        }
                                                    }
                                                )
                                                if (onlineLyricEnabled) {
                                                    ArrowPreference(
                                                        title = "在线歌词缓存上限",
                                                        summary = "$onlineLyricCacheLimit 首",
                                                        onClick = {
                                                            tempCacheLimit = onlineLyricCacheLimit.toString()
                                                            showCacheLimitDialog = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollEndHaptic()
                                    .hazeSource(state = hazeState)
                                    .overScrollVertical()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    top = padding.calculateTopPadding(),
                                    start = 12.dp,
                                    end = 12.dp,
                                    bottom = padding.calculateBottomPadding()
                                )
                            ) {
                                item {
                                    SmallTitle(
                                        text = "小米超级岛设置",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )
                                    var islandLeftAlbumEnabled by remember {
                                        mutableStateOf(prefs.getBoolean(Constants.KEY_ISLAND_LEFT_ALBUM, Constants.DEFAULT_ISLAND_LEFT_ALBUM))
                                    }
                                    var disableLyricSplitEnabled by remember {
                                        mutableStateOf(prefs.getBoolean(Constants.KEY_DISABLE_LYRIC_SPLIT, Constants.DEFAULT_DISABLE_LYRIC_SPLIT))
                                    }
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            SwitchPreference(
                                                title = "超级岛左侧专辑封面",
                                                checked = islandLeftAlbumEnabled,
                                                onCheckedChange = { checked ->
                                                    islandLeftAlbumEnabled = checked
                                                    prefs.edit { putBoolean(Constants.KEY_ISLAND_LEFT_ALBUM, checked) }
                                                    if (!checked) {
                                                        disableLyricSplitEnabled = false
                                                        prefs.edit { putBoolean(Constants.KEY_DISABLE_LYRIC_SPLIT, false) }
                                                    }
                                                }
                                            )
                                            AnimatedVisibility(visible = islandLeftAlbumEnabled) {
                                                SwitchPreference(
                                                    title = "关闭歌词分割",
                                                    checked = disableLyricSplitEnabled,
                                                    onCheckedChange = { checked ->
                                                        disableLyricSplitEnabled = checked
                                                        prefs.edit { putBoolean(Constants.KEY_DISABLE_LYRIC_SPLIT, checked) }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    SmallTitle(
                                        text = "实时通知胶囊设置",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )
                                    var normalNotificationAlbumEnabled by remember {
                                        mutableStateOf(prefs.getBoolean(Constants.KEY_NORMAL_NOTIFICATION_ALBUM, Constants.DEFAULT_NORMAL_NOTIFICATION_ALBUM))
                                    }
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            SwitchPreference(
                                                title = "胶囊左侧专辑封面",
                                                checked = normalNotificationAlbumEnabled,
                                                onCheckedChange = { checked ->
                                                    normalNotificationAlbumEnabled = checked
                                                    prefs.edit { putBoolean(Constants.KEY_NORMAL_NOTIFICATION_ALBUM, checked) }
                                                }
                                            )
                                        }
                                    }

                                    SmallTitle(
                                        text = "通知设置",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )

                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        val focusStyleOptions = listOf("OS2", "OS3")
                                        var focusNotificationType by remember {
                                            mutableIntStateOf(prefs.getInt(Constants.KEY_FOCUS_NOTIFICATION_TYPE, Constants.DEFAULT_FOCUS_NOTIFICATION_TYPE))
                                        }

                                        WindowDropdownPreference(
                                            title = "焦点通知样式",
                                            summary = "此功能仅作用于澎湃系统",
                                            items = focusStyleOptions,
                                            selectedIndex = 1 - focusNotificationType,
                                            onSelectedIndexChange = { index ->
                                                val storedValue = 1 - index
                                                focusNotificationType = storedValue
                                                prefs.edit { putInt(Constants.KEY_FOCUS_NOTIFICATION_TYPE, storedValue) }
                                            }
                                        )

                                        var notificationClickAction by remember {
                                            mutableIntStateOf(prefs.getInt(Constants.KEY_NOTIFICATION_CLICK_ACTION, Constants.DEFAULT_NOTIFICATION_CLICK_ACTION))
                                        }
                                        val clickOptions = listOf("暂停音乐（默认）", "打开HyperLyric", "打开正在播放的媒体应用")

                                        WindowDropdownPreference(
                                            title = "点击通知",
                                            items = clickOptions,
                                            selectedIndex = notificationClickAction,
                                            onSelectedIndexChange = {
                                                notificationClickAction = it
                                                prefs.edit { putInt(Constants.KEY_NOTIFICATION_CLICK_ACTION, it) }
                                            }
                                        )

                                        var progressColorEnabled by remember {
                                            mutableStateOf(prefs.getBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, Constants.DEFAULT_PROGRESS_COLOR_ENABLED))
                                        }
                                        SwitchPreference(
                                            title = "进度条强调色",
                                            summary = "切歌后生效",
                                            checked = progressColorEnabled,
                                            onCheckedChange = { checked ->
                                                progressColorEnabled = checked
                                                prefs.edit { putBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, checked) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scrollEndHaptic()
                                    .hazeSource(state = hazeState)
                                    .overScrollVertical()
                                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                                contentPadding = PaddingValues(
                                    top = padding.calculateTopPadding(),
                                    start = 12.dp,
                                    end = 12.dp,
                                    bottom = padding.calculateBottomPadding()
                                )
                            ) {
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        ArrowPreference(
                                            title = "添加白名单应用",
                                            onClick = {
                                                tempWhitelistInput = ""
                                                showAddWhitelistDialog = true
                                            },
                                            holdDownState = showAddWhitelistDialog
                                        )
                                    }
                                }

                                item {
                                    SmallTitle(
                                        text = "已添加的应用",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )
                                }

                                item {
                                    if (whitelist.isNotEmpty()) {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            Column {
                                                whitelist.forEachIndexed { _, packageName ->
                                                    val appName = commonMusicApps[packageName]
                                                    BasicComponent(
                                                        title = appName ?: packageName,
                                                        summary = if (appName != null) packageName else null,
                                                        endActions = {
                                                            IconButton(
                                                                onClick = {
                                                                    packageToDelete = packageName
                                                                    showDeleteWhitelistDialog = true
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector = MiuixIcons.Delete,
                                                                    contentDescription = "删除",
                                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            packageToDelete = packageName
                                                            showDeleteWhitelistDialog = true
                                                        },
                                                        holdDownState = showDeleteWhitelistDialog && packageToDelete == packageName
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Card(modifier = Modifier.fillMaxWidth()) {
                                            top.yukonga.miuix.kmp.basic.Text(
                                                text = "暂无白名单应用",
                                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }

        }
    }

