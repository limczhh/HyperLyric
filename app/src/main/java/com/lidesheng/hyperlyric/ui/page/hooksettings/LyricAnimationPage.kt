package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val animLabelMap = mapOf(
    "default" to "默认",
    "fade_out_fade_in" to "渐隐渐现",
    "fade_out_up_fade_in_up" to "向上渐隐＆向上渐现",
    "fade_out_down_fade_in_down" to "向下渐隐＆向下渐现",
    "fade_out_left_fade_in_right" to "向左渐隐＆右侧渐现",
    "fade_out_left_fade_in_up" to "向左渐隐＆向上渐现",
    "fade_out_left_zoom_in" to "向左渐隐＆缩放渐现",
    "fade_out_left_landing" to "向左渐隐＆柔缓着陆",
    "fade_out_right_fade_in_left" to "向右渐隐＆左侧渐现",
    "fade_out_right_fade_in_up" to "向右渐隐＆向上渐现",
    "fade_out_right_zoom_in" to "向右渐隐＆缩放渐现",
    "fade_out_right_landing" to "向右渐隐＆聚焦着陆",
    "fade_out_left_zoom_in_right" to "向左渐隐＆右侧缩放渐现",
    "fade_out_right_zoom_in_left" to "向右渐隐＆左侧缩放渐现",
    "slide_out_left_slide_in_right" to "左侧滑出＆右侧滑入",
    "slide_out_left_fade_in_up" to "左侧滑出＆向上渐现",
    "slide_out_left_zoom_in" to "左侧滑出＆缩放渐现",
    "slide_out_left_landing" to "左侧滑出＆柔缓着陆",
    "slide_out_right_slide_in_left" to "右侧滑出＆左侧滑入",
    "slide_out_right_fade_in_up" to "右侧滑出＆向上渐现",
    "slide_out_right_zoom_in" to "右侧滑出＆缩放渐现",
    "slide_out_right_landing" to "右侧滑出&柔缓着陆",
    "flip_out_x_flip_in_x" to "X轴翻转",
    "flip_out_y_flip_in_y" to "Y轴翻转",
    "rotate_out_rotate_in" to "旋转",
    "zoom_out_zoom_in" to "缩放",
)

@Composable
fun LyricAnimationPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
    
    var animEnable by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_ANIM_ENABLE, Constants.DEFAULT_ANIM_ENABLE)) }
    var animId by remember { mutableStateOf(prefs.getString(Constants.KEY_ANIM_ID, Constants.DEFAULT_ANIM_ID) ?: Constants.DEFAULT_ANIM_ID) }

    fun saveConfig(key: String, value: Any) {
        prefs.edit {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
            }
        }
        ConfigSync.syncPreference(Constants.PREF_NAME, key, value)
        context.sendBroadcast(Intent("com.lidesheng.hyperlyric.REFRESH_ISLAND"))
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(backgroundColor = MiuixTheme.colorScheme.surface, tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f)))

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = "歌词切换动画",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = "返回") }
                },
                modifier = Modifier.hazeEffect(hazeState) { style = hazeStyle; blurRadius = 25.dp; noiseFactor = 0f }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .hazeSource(state = hazeState)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding())
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        RadioButtonPreference(
                            title = "无动画",
                            selected = !animEnable,
                            onClick = {
                                animEnable = false
                                saveConfig(Constants.KEY_ANIM_ENABLE, false)
                            }
                        )
                        val registry = YoYoPresets.registry
                        val keys = registry.keys.toList()
                        keys.forEach { key ->
                            val label = animLabelMap[key] ?: key
                            RadioButtonPreference(
                                title = label,
                                selected = animEnable && animId == key,
                                onClick = {
                                    animEnable = true
                                    saveConfig(Constants.KEY_ANIM_ENABLE, true)
                                    animId = key
                                    saveConfig(Constants.KEY_ANIM_ID, key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
