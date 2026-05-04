@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.widget.Toast
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.lyric.DynamicLyricData
import com.lidesheng.hyperlyric.lyric.commonMusicApps
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricWhitelistPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val msgAppExists = stringResource(id = R.string.toast_app_exists)
    val msgPkgEmpty = stringResource(id = R.string.toast_pkg_empty)

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    LaunchedEffect(Unit) { DynamicLyricData.initWhitelist(context) }

    val addedSet by DynamicLyricData.hookAddedState.collectAsState()
    val activeSet by DynamicLyricData.hookWhitelistState.collectAsState()
    val whitelist = remember(addedSet) { addedSet.toList() }

    var showAddWhitelistDialog by remember { mutableStateOf(false) }
    var showDeleteWhitelistDialog by remember { mutableStateOf(false) }
    var tempWhitelistInput by remember { mutableStateOf("") }
    var packageToDelete by remember { mutableStateOf("") }

    TextInputDialog(
        show = showAddWhitelistDialog,
        title = stringResource(id = R.string.dialog_add_whitelist_title),
        initialValue = tempWhitelistInput,
        label = stringResource(id = R.string.dialog_add_whitelist_hint),
        confirmText = stringResource(id = R.string.save),
        onDismiss = { showAddWhitelistDialog = false },
        onConfirm = { input ->
            if (input.isNotBlank()) {
                val success = DynamicLyricData.addPackageToHookList(context, input)
                if (success) showAddWhitelistDialog = false
                else Toast.makeText(context, msgAppExists, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, msgPkgEmpty, Toast.LENGTH_SHORT).show()
            }
        }
    )

    SimpleDialog(
        show = showDeleteWhitelistDialog,
        title = stringResource(id = R.string.dialog_delete_whitelist_title),
        onDismiss = { showDeleteWhitelistDialog = false },
        onConfirm = {
            DynamicLyricData.removePackageFromHookPage(context, packageToDelete)
            showDeleteWhitelistDialog = false
        }
    )

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_lyric_whitelist),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { tempWhitelistInput = ""; showAddWhitelistDialog = true }
            ) {
                Icon(imageVector = MiuixIcons.Add, contentDescription = stringResource(id = R.string.add), tint = Color.White)
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(
                top = top,
                start = 12.dp,
                end = 12.dp,
                bottom = bottom + 80.dp
            )
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(true, true, topAppBarScrollBehavior),
                contentPadding = contentPadding,
            ) {
                whitelistSections(whitelist, activeSet, context)
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

private fun LazyListScope.whitelistSections(whitelist: List<String>, activeSet: Set<String>, context: android.content.Context) {
    item(key = "whitelist_content") {
        if (whitelist.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    whitelist.forEach { packageName ->
                        val appName = commonMusicApps[packageName]
                        SwitchPreference(
                            title = appName ?: packageName,
                            summary = if (appName != null) packageName else null,
                            checked = activeSet.contains(packageName),
                            onCheckedChange = { isChecked ->
                                DynamicLyricData.toggleHookStatus(context, packageName, isChecked)
                            }
                        )
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(id = R.string.title_no_whitelist),
                    summary = stringResource(id = R.string.summary_no_whitelist)
                )
            }
        }
    }
}
