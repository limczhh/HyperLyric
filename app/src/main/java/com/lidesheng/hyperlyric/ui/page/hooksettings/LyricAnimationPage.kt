@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val animLabelResMap = mapOf(
    "default" to R.string.option_anim_default,
    "fade_out_fade_in" to R.string.option_anim_fade,
    "fade_out_up_fade_in_up" to R.string.option_anim_fade_up,
    "fade_out_down_fade_in_down" to R.string.option_anim_fade_down,
    "fade_out_left_fade_in_right" to R.string.option_anim_fade_left_right,
    "fade_out_left_fade_in_up" to R.string.option_anim_fade_left_up,
    "fade_out_left_zoom_in" to R.string.option_anim_fade_left_zoom,
    "fade_out_left_landing" to R.string.option_anim_fade_left_landing,
    "fade_out_right_fade_in_left" to R.string.option_anim_fade_right_left,
    "fade_out_right_fade_in_up" to R.string.option_anim_fade_right_up,
    "fade_out_right_zoom_in" to R.string.option_anim_fade_right_zoom,
    "fade_out_right_landing" to R.string.option_anim_fade_right_landing_focus,
    "fade_out_left_zoom_in_right" to R.string.option_anim_fade_left_zoom_right,
    "fade_out_right_zoom_in_left" to R.string.option_anim_fade_right_zoom_left,
    "slide_out_left_slide_in_right" to R.string.option_anim_slide_left_right,
    "slide_out_left_fade_in_up" to R.string.option_anim_slide_left_up,
    "slide_out_left_zoom_in" to R.string.option_anim_slide_left_zoom,
    "slide_out_left_landing" to R.string.option_anim_slide_left_landing,
    "slide_out_right_slide_in_left" to R.string.option_anim_slide_right_left,
    "slide_out_right_fade_in_up" to R.string.option_anim_slide_right_up,
    "slide_out_right_zoom_in" to R.string.option_anim_slide_right_zoom,
    "slide_out_right_landing" to R.string.option_anim_slide_right_landing,
    "flip_out_x_flip_in_x" to R.string.option_anim_flip_x,
    "flip_out_y_flip_in_y" to R.string.option_anim_flip_y,
    "rotate_out_rotate_in" to R.string.option_anim_rotate,
    "zoom_out_zoom_in" to R.string.option_anim_zoom,
)

@Composable
fun LyricAnimationPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_lyric_anim),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(
                top = top,
                start = 0.dp,
                end = 0.dp,
                bottom = bottom
            )
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(true, true, topAppBarScrollBehavior),
                contentPadding = contentPadding,
            ) {
                animationPageSections()
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

private fun LazyListScope.animationPageSections() {
    item(key = "animation_options") {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }

        var animEnable by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, RootConstants.DEFAULT_HOOK_ANIM_ENABLE)) }
        var animId by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_ANIM_ID, RootConstants.DEFAULT_HOOK_ANIM_ID) ?: RootConstants.DEFAULT_HOOK_ANIM_ID) }

        fun saveConfig(key: String, value: Any) {
            prefs.edit {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                }
            }
            ConfigSync.syncPreference(UIConstants.PREF_NAME, key, value)
            context.sendBroadcast(Intent("com.lidesheng.hyperlyric.UPDATE_LYRIC_ANIM"))
        }
        Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Column {
                RadioButtonPreference(
                    title = stringResource(id = R.string.option_anim_none),
                    selected = !animEnable,
                    onClick = {
                        animEnable = false
                        saveConfig(RootConstants.KEY_HOOK_ANIM_ENABLE, false)
                    }
                )
                YoYoPresets.registry.keys.forEach { key ->
                    val labelRes = animLabelResMap[key]
                    val label = if (labelRes != null) stringResource(id = labelRes) else key
                    RadioButtonPreference(
                        title = label,
                        selected = animEnable && animId == key,
                        onClick = {
                            animEnable = true
                            saveConfig(RootConstants.KEY_HOOK_ANIM_ENABLE, true)
                            animId = key
                            saveConfig(RootConstants.KEY_HOOK_ANIM_ID, key)
                        }
                    )
                }
            }
        }
    }
}
