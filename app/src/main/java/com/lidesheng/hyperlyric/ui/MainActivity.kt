package com.lidesheng.hyperlyric.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.Quotes
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.root.ConfigSync
import com.lidesheng.hyperlyric.root.ShellUtils
import com.lidesheng.hyperlyric.utils.ThemeUtils
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
        val setupCompleted = prefs.getBoolean(Constants.KEY_SETUP_COMPLETED, Constants.DEFAULT_SETUP_COMPLETED)
        if (!setupCompleted) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        val excludeFromRecents = prefs.getBoolean(Constants.KEY_EXCLUDE_FROM_RECENTS, Constants.DEFAULT_EXCLUDE_FROM_RECENTS)
        if (excludeFromRecents) {
            setExcludeFromRecents(true)
        }

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                MainScreen()
            }
        }
    }

    fun setExcludeFromRecents(exclude: Boolean) {
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            am.appTasks?.forEach {
                it.setExcludeFromRecents(exclude)
            }
        } catch (_: Exception) { }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
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

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == Constants.KEY_FLOATING_NAV_BAR) {
                floatingNavBarEnabled = p.getBoolean(Constants.KEY_FLOATING_NAV_BAR, Constants.DEFAULT_FLOATING_NAV_BAR)
            }
        }
    }

    LaunchedEffect(Unit) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
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
            show = showRestartDialog,
            onDismissRequest = { showRestartDialog = false }
        ) {
            Text(
                text = "更新应用后才需要重启哦",
                modifier = Modifier.padding(bottom = 16.dp),
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                fontSize = 14.sp
            )
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
                                     context.startActivity(Intent(context, PoetryActivity::class.java))
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
                                    ArrowPreference(
                                        title = "小米超级岛hook自定义配置",
                                        summary = "需要root，仅支持HyperOS3设备",
                                        onClick = {
                                            context.startActivity(Intent(context, HookSettingsActivity::class.java))
                                        }
                                    )
                                    ArrowPreference(
                                        title = "灵动岛歌词通知",
                                        summary = "适用于无root设备",
                                        onClick = {
                                            context.startActivity(Intent(context, DynamicIslandNotificationActivity::class.java))
                                        }
                                    )
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
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Card(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = "应用设置",
                                    summary = "个性化、备份与恢复等",
                                    onClick = {
                                        context.startActivity(Intent(context, SettingsActivity::class.java))
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
}

@Composable
fun AboutContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
            text = "无root模式使用提示",
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp),
        )
        {
            Column {
                Text(
                    text = "需要将音乐软件包名加入白名单，并在音乐软件里打开蓝牙歌词功能，连接蓝牙设备\n\n已支持 Salt Player、QQ音乐\n更多应用等你发现...",
                    style = MiuixTheme.textStyles.body2
                )
            }
        }

        SmallTitle(
            text = "hook模式使用提示",
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "前往github下载对应歌词提供器并勾选推荐应用，重启系统界面和音乐软件后即可使用。",
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(16.dp),
            )
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
                    context.startActivity(Intent(context, LicensesActivity::class.java))
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