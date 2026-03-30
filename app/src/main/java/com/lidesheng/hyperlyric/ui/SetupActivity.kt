package com.lidesheng.hyperlyric.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.state.ToggleableState
import androidx.core.content.edit
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.online.model.DynamicLyricData
import com.lidesheng.hyperlyric.root.ShellUtils
import com.lidesheng.hyperlyric.utils.ThemeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

class SetupActivity : ComponentActivity() {
    @SuppressLint("CommitPrefEdits")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                SetupScreen(
                    onFinish = {
                        val prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
                        prefs.edit { putBoolean(Constants.KEY_SETUP_COMPLETED, true) }
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun SetupScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val prefs = remember { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
    
    var workMode by remember { 
        val initialMode = prefs.getInt(Constants.KEY_WORK_MODE, 1)
        mutableIntStateOf(if (initialMode == 0) 1 else initialMode) 
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = "引导页面",
                actions = {
                    Text(
                        text = "${pagerState.currentPage + 1} / 4",
                        modifier = Modifier.padding(end = 12.dp),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 14.sp
                    )
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        text = "上一步",
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                val isLastPage = pagerState.currentPage == 3
                val isRootMode = workMode == 0
                
                TextButton(
                    text = when {
                        isLastPage && isRootMode -> "重启系统界面"
                        isLastPage -> "完成"
                        else -> "下一步"
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        if (isLastPage) {
                            if (isRootMode) {
                                scope.launch {
                                    ShellUtils.restartSystemUI()
                                    onFinish()
                                }
                            } else {
                                onFinish()
                            }
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> ModeSelectionPage(
                    selectedMode = workMode,
                    onModeSelected = { 
                        workMode = it
                        prefs.edit { putInt(Constants.KEY_WORK_MODE, it) }
                    }
                )
                1 -> PermissionPage(workMode = workMode)
                2 -> WhitelistPage()
                3 -> CompletionPage()
            }
        }
    }
}

@Composable
fun ModeSelectionPage(selectedMode: Int, onModeSelected: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "选择应用工作模式",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                pressFeedbackType = PressFeedbackType.Tilt,
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "hook模式 (暂不可用)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        text = "由于适配工作，该模式暂时禁用，请关注后续更新",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onModeSelected(1) },
                pressFeedbackType = PressFeedbackType.Tilt,
                colors = CardDefaults.defaultColors(
                    color = if (selectedMode == 1) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "无root模式",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedMode == 1) Color.White else MiuixTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "通过发送实时通知/焦点通知上岛",
                        fontSize = 14.sp,
                        color = if (selectedMode == 1) Color.White.copy(alpha = 0.8f) else MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionPage(workMode: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val isRootGranted = remember { mutableStateOf(false) }
    val isNotificationGranted = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            isNotificationGranted.value = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            delay(1000)
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "授予必要权限",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
        
        if (workMode == 0) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "获取 Root 权限",
                        summary = if (isRootGranted.value) "已获得 Root 权限" else "执行 'su' 命令请求权限",
                        onClick = {
                            scope.launch {
                                val success = ShellUtils.execRootCmdSilent("ls /data")
                                isRootGranted.value = success
                                Toast.makeText(context, if (success) "权限已授予" else "请求失败，请检查授权管理", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ArrowPreference(
                            title = "通知监听权限",
                            summary = if (isNotificationGranted.value) "权限已授予" else "读取播放状态和歌曲信息",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        )
                        ArrowPreference(
                            title = "发送通知权限",
                            summary = "允许app发送通知以显示歌词",
                            onClick = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhitelistPage() {
    val context = LocalContext.current
    val whitelistSet by DynamicLyricData.whitelistState.collectAsState()
    
    LaunchedEffect(Unit) {
        DynamicLyricData.initWhitelist(context)
    }

    Scaffold(
        topBar = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "添加白名单应用",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
                
                Text(
                    text = "勾选需要显示歌词的音乐App",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    ) { padding ->
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                commonMusicApps.forEach { (pkg: String, name: String) ->
                    item {
                        val isChecked = whitelistSet.contains(pkg)
                        BasicComponent(
                            title = name,
                            summary = pkg,
                            onClick = {
                                if (isChecked) {
                                    DynamicLyricData.removePackageFromWhitelist(context, pkg)
                                } else {
                                    DynamicLyricData.addPackageToWhitelist(context, pkg)
                                }
                            },
                            endActions = {
                                Checkbox(
                                    state = ToggleableState(isChecked),
                                    onClick = {
                                        val checked = !isChecked
                                        if (checked) {
                                            DynamicLyricData.addPackageToWhitelist(context, pkg)
                                        } else {
                                            DynamicLyricData.removePackageFromWhitelist(context, pkg)
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompletionPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "基础设置已完成",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
