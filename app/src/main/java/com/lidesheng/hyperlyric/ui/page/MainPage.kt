package com.lidesheng.hyperlyric.ui.page

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.Quotes
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.service.LiveLyricService
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun MainPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    var randomQuote by rememberSaveable { mutableStateOf(Quotes.list.random()) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var showRestartDialog by remember { mutableStateOf(false) }

    val navItems = listOf(
        NavigationItem("主页", MiuixIcons.Settings),
        NavigationItem("关于", MiuixIcons.Info)
    )

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )
    
    val prefs = remember { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
    var floatingNavBarEnabled by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_FLOATING_NAV_BAR, Constants.DEFAULT_FLOATING_NAV_BAR)) }
    var enableSuperIsland by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_ENABLE_SUPER_ISLAND, Constants.DEFAULT_ENABLE_SUPER_ISLAND)) }
    var enableDynamicIsland by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, Constants.DEFAULT_ENABLE_DYNAMIC_ISLAND)) }

    var showPermissionSheet by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            val message = if (isGranted) "已获取通知权限" else "未获取通知权限"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    )

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                Constants.KEY_FLOATING_NAV_BAR -> floatingNavBarEnabled = p.getBoolean(Constants.KEY_FLOATING_NAV_BAR, Constants.DEFAULT_FLOATING_NAV_BAR)
                Constants.KEY_ENABLE_SUPER_ISLAND -> enableSuperIsland = p.getBoolean(Constants.KEY_ENABLE_SUPER_ISLAND, Constants.DEFAULT_ENABLE_SUPER_ISLAND)
                Constants.KEY_ENABLE_DYNAMIC_ISLAND -> enableDynamicIsland = p.getBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, Constants.DEFAULT_ENABLE_DYNAMIC_ISLAND)
            }
        }
    }

    LaunchedEffect(Unit) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val isDynamicIslandEnabled = prefs.getBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, Constants.DEFAULT_ENABLE_DYNAMIC_ISLAND)

        if (hasListenerPermission && isDynamicIslandEnabled) {
            LiveLyricService.ensureListenerBound(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = if (pagerState.currentPage == 0) "HyperLyric" else "关于",
                scrollBehavior = scrollBehavior,
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 25.dp
                    noiseFactor = 0f
                }
            )
        },
        bottomBar = {
            if (floatingNavBarEnabled) {
                FloatingNavigationBar(
                    mode = FloatingNavigationBarDisplayMode.IconOnly,
                    showDivider = true,
                    color = Color.Transparent,
                    modifier = Modifier
                        .hazeEffect(hazeState) {
                            style = hazeStyle
                            blurRadius = 5.dp
                            noiseFactor = 0f
                        }
                ) {
                    navItems.forEachIndexed { index, item ->
                        FloatingNavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            } else {
                NavigationBar(
                    mode = NavigationBarDisplayMode.IconWithSelectedLabel,
                    color = Color.Transparent,
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            }
        }
    ) { padding ->
        WindowDialog(
            title = "是否重启系统界面？",
            summary = "重启后也要重启音乐软件哦",
            show = showRestartDialog,
            onDismissRequest = { showRestartDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showRestartDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "确认",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showRestartDialog = false
                        scope.launch {
                            val success = ShellUtils.restartSystemUI()
                            if (!success) {
                                Toast.makeText(context, "应用未获取root权限", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->

            if (page == 0) {
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
                             Card(
                                 modifier = Modifier.fillMaxWidth(),
                                 onClick = {
                                     randomQuote = Quotes.list.random()
                                 },
                                 onLongPress = {
                                     navigator.navigate(Route.Poetry)
                                 }
                             ) {
                                Text(
                                    text = randomQuote,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            SmallTitle(
                                text = "基础功能",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    SwitchPreference(
                                        title = "小米超级岛歌词",
                                        summary = "仅支持已安装lsposed的HyperOS3设备",
                                        checked = enableSuperIsland,
                                        onCheckedChange = {
                                            enableSuperIsland = it
                                            prefs.edit { putBoolean(Constants.KEY_ENABLE_SUPER_ISLAND, it) }
                                            ConfigSync.syncPreference(Constants.PREF_NAME, Constants.KEY_ENABLE_SUPER_ISLAND, it)
                                        }
                                    )
                                    AnimatedVisibility(visible = enableSuperIsland) {
                                        ArrowPreference(
                                            title = "小米超级岛歌词自定义配置",
                                            onClick = {
                                                navigator.navigate(Route.HookSettings)
                                            }
                                        )
                                    }
                                    SwitchPreference(
                                        title = "通知型灵动岛歌词",
                                        summary = "利用通知实现灵动岛歌词效果，适用于无root设备",
                                        checked = enableDynamicIsland,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                val hasPostNotification =
                                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

                                                if (hasPostNotification && hasListenerPermission) {
                                                    enableDynamicIsland = true
                                                    prefs.edit { putBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, true) }
                                                    ConfigSync.syncPreference(Constants.PREF_NAME, Constants.KEY_ENABLE_DYNAMIC_ISLAND, true)
                                                    LiveLyricService.ensureListenerBound(context)
                                                } else {
                                                    showPermissionSheet = true
                                                }
                                            } else {
                                                enableDynamicIsland = false
                                                prefs.edit { putBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, false) }
                                                ConfigSync.syncPreference(Constants.PREF_NAME, Constants.KEY_ENABLE_DYNAMIC_ISLAND, false)
                                            }
                                        }
                                    )
                                    AnimatedVisibility(visible = enableDynamicIsland) {
                                        ArrowPreference(
                                            title = "通知型灵动岛歌词自定义配置",
                                            onClick = {
                                                navigator.navigate(Route.DynamicIslandNotification)
                                            }
                                        )
                                    }
                                }
                            }

                            SmallTitle(
                                text = "特殊功能",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    ArrowPreference(
                                        title = "重启系统界面",
                                        onClick = { showRestartDialog = true }
                                    )
                                    var removeFocusWhitelist by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_REMOVE_FOCUS_WHITELIST, Constants.DEFAULT_REMOVE_FOCUS_WHITELIST)) }
                                    SwitchPreference(
                                        title = "移除焦点通知白名单",
                                        summary = "不要和其他模块的相同功能冲突使用",
                                        checked = removeFocusWhitelist,
                                        onCheckedChange = {
                                            removeFocusWhitelist = it
                                            prefs.edit { putBoolean(Constants.KEY_REMOVE_FOCUS_WHITELIST, it) }
                                            ConfigSync.syncPreference(Constants.PREF_NAME, Constants.KEY_REMOVE_FOCUS_WHITELIST, it)
                                        }
                                    )
                                    var removeIslandWhitelist by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_REMOVE_ISLAND_WHITELIST, Constants.DEFAULT_REMOVE_ISLAND_WHITELIST)) }
                                    SwitchPreference(
                                        title = "移除下拉小窗白名单",
                                        checked = removeIslandWhitelist,
                                        onCheckedChange = {
                                            removeIslandWhitelist = it
                                            prefs.edit { putBoolean(Constants.KEY_REMOVE_ISLAND_WHITELIST, it) }
                                            ConfigSync.syncPreference(Constants.PREF_NAME, Constants.KEY_REMOVE_ISLAND_WHITELIST, it)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Card(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = "应用设置",
                                    summary = "个性化、备份与恢复等",
                                    onClick = {
                                        navigator.navigate(Route.Settings)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
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
                        AboutContent(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    WindowBottomSheet(
        show = showPermissionSheet,
        title = "通知权限",
        allowDismiss = false,
        startAction = {
            IconButton(onClick = { showPermissionSheet = false }) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "Close",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            IconButton(onClick = {
                val hasPostNotification =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                if (hasPostNotification && hasListenerPermission) {
                    showPermissionSheet = false
                    enableDynamicIsland = true
                    prefs.edit { putBoolean(Constants.KEY_ENABLE_DYNAMIC_ISLAND, true) }
                    ConfigSync.syncPreference(Constants.PREF_NAME, Constants.KEY_ENABLE_DYNAMIC_ISLAND, true)
                    LiveLyricService.ensureListenerBound(context)
                } else {
                    Toast.makeText(context, "权限还未授予", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = "OK",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = {
            showPermissionSheet = false
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "发送歌词通知权限",
                    onClick = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                ArrowPreference(
                    title = "获取歌词信息权限",
                    onClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "无法打开通知使用权设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AboutContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName
            val vCode = PackageInfoCompat.getLongVersionCode(pInfo)
            "v $vName-$vCode"
        } catch (_: Exception) {
            "v Unknown"
        }
    }


    val deviceModel = remember {
        getSystemProperty("ro.product.marketname") ?: Build.MODEL
    }
    val osVersion = remember {
        getSystemProperty("ro.build.version.incremental") ?: Build.DISPLAY
    }
    val androidVersion = Build.VERSION.RELEASE

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "HyperLyric",
            style = MiuixTheme.textStyles.title1,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = appVersion,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Column {
        SmallTitle(
            text = "系统信息",
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                BasicComponent(title = deviceModel, summary = "设备型号")
                BasicComponent(title = osVersion, summary = "系统版本")
                BasicComponent(title = androidVersion, summary = "Android版本")
            }
        }

        SmallTitle(
            text = "使用帮助",
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                BasicComponent(title = "小米超级岛歌词", summary = "前往github下载对应歌词提供器并勾选推荐应用，重启系统界面和音乐软件后即可使用。")
                BasicComponent(title = "通知型灵动岛歌词", summary = "将音乐软件包名加入白名单，并在音乐软件里打开蓝牙歌词功能，连接蓝牙设备\n已支持 Salt Player，更多应用等你发现...")
            }
        }

        SmallTitle(
            text = "开发者",
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = "李得胜",
                startAction = {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer)
                    )
                },
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = "Go",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = {
                    val uri = "https://github.com/limczhh/HyperLyric".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        intent.setPackage("com.coolapk.market")
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        intent.setPackage(null)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            ArrowPreference(
                title = "项目引用与参考",
                summary = "感谢@FrancOS 和@于逸风 的帮助，以及一些没列出的项目",
                onClick = {
                    navigator.navigate(Route.Licenses)
                }
            )
        }
    }
}

fun getSystemProperty(key: String): String? {
    return try {
        val process = Runtime.getRuntime().exec("getprop $key")
        BufferedReader(InputStreamReader(process.inputStream)).use {
            val line = it.readLine()
            if (line.isNullOrEmpty()) null else line
        }
    } catch (_: Exception) {
        null
    }
}
