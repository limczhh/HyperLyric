@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.plugin

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.plugin.api.PluginSettingGroup
import com.lidesheng.hyperlyric.plugin.api.PluginSettingSpec
import com.lidesheng.hyperlyric.plugin.api.PluginSettingInputType
import com.lidesheng.hyperlyric.plugin.api.PluginSettingType
import com.lidesheng.hyperlyric.plugin.api.PluginSettingValuePresentation
import com.lidesheng.hyperlyric.plugin.app.InstalledPlugin
import com.lidesheng.hyperlyric.plugin.app.PluginRepository
import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import com.lidesheng.hyperlyric.root.RootApplication
import com.lidesheng.hyperlyric.ui.component.MultiSelectDialog
import com.lidesheng.hyperlyric.ui.component.MultiSelectDialogOption
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_PLUGIN_SETTING_GROUP_ID = "default"

@Composable
fun PluginManagerPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val repository = remember { PluginRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var installed by remember { mutableStateOf(repository.listInstalled()) }
    val registry = remember {
        context.getSharedPreferences(PluginConstants.LOCAL_REGISTRY_PREFS, Context.MODE_PRIVATE)
    }

    DisposableEffect(registry) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == PluginConstants.LOCAL_INSTALLED_IDS_KEY ||
                key == PluginConstants.REMOTE_ENABLED_IDS_KEY ||
                key?.startsWith(PluginConstants.LOCAL_MANIFEST_PREFIX) == true ||
                key?.startsWith(PluginConstants.LOCAL_FILE_PREFIX) == true
            ) {
                installed = repository.listInstalled()
            }
        }
        registry.registerOnSharedPreferenceChangeListener(listener)
        onDispose { registry.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun showError(error: Throwable) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    R.string.toast_plugin_operation_failed,
                    error.message ?: error.javaClass.simpleName
                ),
                duration = SnackbarDuration.Custom(3000L)
            )
        }
    }

    val installLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.install(uri) }
            }
            if (result.isSuccess) {
                installed = repository.listInstalled()
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.toast_plugin_install_success),
                    duration = SnackbarDuration.Custom(2500L)
                )
            } else {
                result.exceptionOrNull()?.let(::showError)
            }
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_plugins),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (RootApplication.xposedService == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(
                                                R.string.toast_plugin_requires_xposed
                                            ),
                                            duration = SnackbarDuration.Custom(2500L)
                                        )
                                    }
                                } else {
                                    installLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream")
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = stringResource(R.string.title_install_plugin)
                            )
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
                bottom = bottom + 16.dp
            )
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding
            ) {
                pluginItems(
                    installed = installed,
                    onSelect = { navigator.navigate(Route.PluginSettings(it)) }
                )
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

private fun LazyListScope.pluginItems(
    installed: List<InstalledPlugin>,
    onSelect: (String) -> Unit,
) {
    if (installed.isEmpty()) {
        item(key = "plugin_empty") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                BasicComponent(
                    title = stringResource(R.string.summary_plugin_empty)
                )
            }
        }
        return
    }

    installed.forEach { plugin ->
        item(key = "plugin_${plugin.manifest.id}") {
            val context = LocalContext.current
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = plugin.manifest.localizedName(context),
                    summary = plugin.manifest.localizedSummary(context),
                    endActions = {
                        Text(
                            text = stringResource(
                                if (plugin.enabled) {
                                    R.string.plugin_status_enabled
                                } else {
                                    R.string.plugin_status_disabled
                                }
                            ),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = { onSelect(plugin.manifest.id) }
                )
            }
        }
    }
}

@Composable
fun PluginSettingsPage(pluginId: String) {
    val context = LocalContext.current
    val repository = remember { PluginRepository(context) }
    val plugin = remember(pluginId) {
        repository.listInstalled().firstOrNull { it.manifest.id == pluginId }
    }

    if (plugin == null) {
        PluginUnavailablePage(pluginId)
        return
    }

    PluginSettingsPageContent(
        plugin = plugin,
        repository = repository
    )
}

@Composable
private fun PluginUnavailablePage(pluginId: String) {
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
                    title = pluginId,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            BasicComponent(
                title = stringResource(R.string.summary_plugin_empty)
            )
        }
    }
}

@Composable
private fun PluginSettingsPageContent(
    plugin: InstalledPlugin,
    repository: PluginRepository,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val activationSetting = remember(plugin.manifest.id) {
        plugin.manifest.activationSettingKey?.let { key ->
            plugin.manifest.settings.settings.firstOrNull { it.key == key }
        }
    }
    val activationEnabled = remember(plugin.manifest.id) {
        activationSetting?.let { setting ->
            repository.configPreferences(plugin.manifest.id).getBoolean(
                setting.key,
                setting.defaultValue?.toBoolean() ?: false
            )
        } ?: plugin.enabled
    }
    var enabled by remember(plugin.manifest.id) { mutableStateOf(plugin.enabled && activationEnabled) }
    var showUninstallDialog by remember { mutableStateOf(false) }

    fun showError(error: Throwable) {
        scope.launch {
            snackbarHostState.showSnackbar(
                message = context.getString(
                    R.string.toast_plugin_operation_failed,
                    error.message ?: error.javaClass.simpleName
                ),
                duration = SnackbarDuration.Custom(3000L)
            )
        }
    }

    LaunchedEffect(plugin.manifest.id) {
        withContext(Dispatchers.IO) {
            repository.ensureDefaults(plugin.manifest)
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val settingsByGroup = plugin.manifest.settings.settings
        .filter { it.key != activationSetting?.key }
        .groupBy { it.group }
    val settingGroupDefinitions = remember(plugin.manifest.id) {
        plugin.manifest.settings.groups.associateBy { it.id }
    }

    if (showUninstallDialog) {
        WindowDialog(
            title = stringResource(R.string.dialog_uninstall_plugin_title),
            show = true,
            onDismissRequest = { showUninstallDialog = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = plugin.manifest.localizedName(context))
                Text(
                    text = stringResource(R.string.dialog_uninstall_plugin_summary),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            showUninstallDialog = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { repository.uninstall(plugin.manifest.id) }
                                }
                                if (result.isSuccess) {
                                    navigator.pop()
                                } else {
                                    result.exceptionOrNull()?.let(::showError)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        )
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showUninstallDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = plugin.manifest.localizedName(context),
                    subtitle = plugin.manifest.version,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showUninstallDialog = true }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = stringResource(R.string.title_uninstall_plugin)
                            )
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
                bottom = bottom + 16.dp
            )
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding
            ) {
                item(key = "plugin_enable") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.title_enable),
                            checked = enabled,
                            onCheckedChange = { requestedEnabled ->
                                val previousEnabled = enabled
                                enabled = requestedEnabled
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            repository.setEnabled(
                                                plugin.manifest.id,
                                                requestedEnabled
                                            )
                                        }
                                    }
                                    if (result.isFailure) {
                                        enabled = previousEnabled
                                        result.exceptionOrNull()?.let(::showError)
                                    }
                                }
                            }
                        )
                    }
                }
                if (settingsByGroup.isEmpty()) {
                    item(key = "plugin_settings_empty") {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.summary_plugin_no_settings),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    settingsByGroup.entries.forEachIndexed { index, (group, settings) ->
                        val groupId = group ?: DEFAULT_PLUGIN_SETTING_GROUP_ID
                        val groupTitle = settingGroupDefinitions[groupId]
                            ?.localizedTitle(context)
                            ?.takeIf { it.isNotBlank() }
                        groupTitle?.let { title ->
                            item(key = "plugin_settings_title_${groupId}_$index") {
                                SmallTitle(text = title)
                            }
                        }
                        item(key = "plugin_settings_${groupId}_$index") {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                PluginSettingsContent(
                                    manifest = plugin.manifest,
                                    repository = repository,
                                    pluginEnabled = enabled,
                                    settings = settings
                                )
                            }
                        }
                    }
                }
                if (plugin.manifest.cacheScopes.isNotEmpty()) {
                    item(key = "plugin_cache_scopes") {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                        ) {
                            plugin.manifest.cacheScopes.forEach { cacheScope ->
                                ArrowPreference(
                                    title = cacheScope.localizedTitle(context),
                                    onClick = {
                                        navigator.navigate(
                                            Route.PluginCache(plugin.manifest.id, cacheScope.id)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun PluginSettingsContent(
    manifest: com.lidesheng.hyperlyric.plugin.core.PluginManifest,
    repository: PluginRepository,
    pluginEnabled: Boolean,
    settings: List<PluginSettingSpec>,
) {
    val context = LocalContext.current
    val preferences = remember(manifest.id) { repository.configPreferences(manifest.id) }
    var revision by remember(manifest.id) { mutableIntStateOf(0) }
    var editing by remember(manifest.id) { mutableStateOf<PluginSettingSpec?>(null) }
    var editingMulti by remember(manifest.id) { mutableStateOf<PluginSettingSpec?>(null) }
    val _revision = revision

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(pluginEnabled) {
        if (!pluginEnabled) {
            editing = null
            editingMulti = null
        }
    }

    fun saveSwitch(setting: PluginSettingSpec, checked: Boolean) {
        repository.setSettingValue(manifest, setting, checked.toString())
        if (checked && setting.conflictsWith.isNotEmpty()) {
            manifest.settings.settings
                .filter { it.key in setting.conflictsWith && it.type == PluginSettingType.SWITCH }
                .forEach { conflicting ->
                    repository.setSettingValue(manifest, conflicting, false.toString())
                }
        }
        revision++
    }

    @Composable
    fun renderSetting(setting: PluginSettingSpec) {
        val title = setting.localizedTitle(context)
        when (setting.type) {
            PluginSettingType.SWITCH -> {
                SwitchPreference(
                    title = title,
                    summary = setting.localizedSummary(context),
                    checked = preferences.getBoolean(
                        setting.key,
                        setting.defaultValue?.toBoolean() ?: false
                    ),
                    enabled = pluginEnabled,
                    onCheckedChange = { saveSwitch(setting, it) }
                )
            }

            PluginSettingType.SELECT -> {
                val labels = setting.options.map { it.localizedLabel(context) }
                val current = preferences.getString(setting.key, setting.defaultValue)
                val selected = setting.options.indexOfFirst { it.value == current }.coerceAtLeast(0)
                WindowDropdownPreference(
                    title = title,
                    summary = setting.localizedSummary(context),
                    items = labels,
                    selectedIndex = selected,
                    enabled = pluginEnabled,
                    onSelectedIndexChange = { index ->
                        setting.options.getOrNull(index)?.let {
                            repository.setSettingValue(manifest, setting, it.value)
                            revision++
                        }
                    }
                )
            }

            PluginSettingType.MULTI_SELECT -> {
                val selected = preferences.getStringSet(
                    setting.key,
                    setting.defaultValue.orEmpty().split(',').filter(String::isNotBlank).toSet()
                ).orEmpty()
                val selectedSummary = setting.options
                    .filter { it.value in selected }
                    .joinToString(", ") { it.localizedLabel(context) }
                    .ifBlank { setting.localizedEmptyValueSummary(context).orEmpty() }
                ArrowPreference(
                    title = title,
                    summary = setting.localizedSummary(context) ?: selectedSummary,
                    enabled = pluginEnabled,
                    holdDownState = editingMulti?.key == setting.key,
                    onClick = { editingMulti = setting }
                )
            }

            PluginSettingType.SLIDER -> {
                val min = setting.min ?: 0f
                val upperBound = (setting.max ?: 1f).coerceAtLeast(min)
                val value = preferences.getFloat(
                    setting.key,
                    setting.defaultValue?.toFloatOrNull() ?: min
                ).coerceIn(min, upperBound)
                val step = setting.step?.takeIf { it > 0f }
                val steps = step?.let { max(0, ((upperBound - min) / it).roundToInt() - 1) } ?: 0
                SliderPreference(
                    value = value,
                    onValueChange = {
                        repository.setSettingValue(manifest, setting, it.toString())
                        revision++
                    },
                    title = title,
                    summary = setting.localizedSummary(context),
                    enabled = pluginEnabled,
                    valueText = value.toString(),
                    valueRange = min..upperBound,
                    steps = steps
                )
            }

            PluginSettingType.TEXT,
            PluginSettingType.PASSWORD,
            PluginSettingType.NUMBER -> {
                val current = readSettingValue(preferences, setting)
                val presentation = setting.valuePresentation
                val valueText = displaySettingValue(context, setting, current)
                val summary = when (presentation) {
                    PluginSettingValuePresentation.END_ACTION -> setting.localizedSummary(context)
                    PluginSettingValuePresentation.SUMMARY -> valueText
                    PluginSettingValuePresentation.SUMMARY_PREVIEW -> previewSettingValue(
                        current,
                        setting.previewLineCount
                    )

                    PluginSettingValuePresentation.DEFAULT -> setting.localizedSummary(context)
                        ?: valueText
                }
                ArrowPreference(
                    title = title,
                    summary = summary,
                    endActions = {
                        if (presentation == PluginSettingValuePresentation.END_ACTION) {
                            Text(
                                text = valueText,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (pluginEnabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        }
                    },
                    enabled = pluginEnabled,
                    onClick = { editing = setting }
                )
            }

            PluginSettingType.ACTION -> {
                ArrowPreference(
                    title = title,
                    summary = setting.localizedSummary(context),
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }

    settings.forEach { renderSetting(it) }

    val editingSetting = editing
    if (pluginEnabled && editingSetting != null) {
        TextInputDialog(
            show = true,
            title = editingSetting.localizedTitle(context),
            initialValue = readSettingValue(preferences, editingSetting),
            keyboardOptions = when (editingSetting.inputType) {
                PluginSettingInputType.URI -> KeyboardOptions(keyboardType = KeyboardType.Uri)
                PluginSettingInputType.NUMBER -> KeyboardOptions(keyboardType = KeyboardType.Number)
                PluginSettingInputType.DEFAULT -> if (
                    editingSetting.type == PluginSettingType.NUMBER
                ) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                }
            },
            onDismiss = { editing = null },
            onConfirm = { value ->
                repository.setSettingValue(manifest, editingSetting, value)
                revision++
                editing = null
            }
        )
    }

    val multiSetting = editingMulti
    if (pluginEnabled && multiSetting != null) {
        val selected = preferences.getStringSet(
            multiSetting.key,
            multiSetting.defaultValue.orEmpty().split(',').filter(String::isNotBlank).toSet()
        ).orEmpty()
        MultiSelectDialog(
            show = true,
            title = multiSetting.localizedTitle(context),
            summary = multiSetting.localizedDialogSummary(context).orEmpty(),
            options = multiSetting.options.map {
                MultiSelectDialogOption(it.value, it.localizedLabel(context))
            },
            selectedKeys = selected,
            onDismiss = { editingMulti = null },
            onConfirm = { values ->
                repository.setSettingValue(manifest, multiSetting, values.joinToString(","))
                revision++
                editingMulti = null
            }
        )
    }
}

private fun readSettingValue(
    preferences: SharedPreferences,
    setting: PluginSettingSpec
): String = when (setting.type) {
    PluginSettingType.NUMBER -> preferences.getLong(
        setting.key,
        setting.defaultValue?.toLongOrNull() ?: 0L
    ).toString()

    else -> preferences.getString(setting.key, setting.defaultValue).orEmpty()
}

private fun displaySettingValue(
    context: Context,
    setting: PluginSettingSpec,
    current: String
): String {
    if (setting.type == PluginSettingType.PASSWORD && current.isNotEmpty()) {
        return "***************"
    }
    if (current.isNotEmpty()) return current
    return setting.localizedEmptyValueSummary(context).orEmpty()
}

private fun previewSettingValue(value: String, lineCount: Int): String {
    val lines = value.lines()
    return if (lines.size > lineCount + 1) {
        lines.take(lineCount).joinToString("\n") + "..."
    } else {
        value
    }
}

internal fun com.lidesheng.hyperlyric.plugin.core.PluginManifest.localizedName(
    context: Context
): String = resolveLocalizedText(
    context = context,
    fallback = name,
    values = nameByLocale
)

internal fun com.lidesheng.hyperlyric.plugin.core.PluginManifest.localizedSummary(
    context: Context
): String? = resolveOptionalLocalizedText(
    context = context,
    fallback = summary.takeIf { it.isNotBlank() },
    values = summaryByLocale
)

internal fun PluginSettingSpec.localizedTitle(context: Context): String = resolveLocalizedText(
    context = context,
    fallback = title,
    values = titleByLocale
)

internal fun PluginSettingGroup.localizedTitle(context: Context): String = resolveLocalizedText(
    context = context,
    fallback = title,
    values = titleByLocale
)

internal fun PluginSettingSpec.localizedSummary(context: Context): String? =
    resolveOptionalLocalizedText(
        context = context,
        fallback = summary,
        values = summaryByLocale
    )

internal fun PluginSettingSpec.localizedDialogSummary(context: Context): String? =
    resolveOptionalLocalizedText(
        context = context,
        fallback = dialogSummary,
        values = dialogSummaryByLocale
    )

internal fun PluginSettingSpec.localizedEmptyValueSummary(context: Context): String? =
    resolveOptionalLocalizedText(
        context = context,
        fallback = emptyValueSummary,
        values = emptyValueSummaryByLocale
    )

internal fun com.lidesheng.hyperlyric.plugin.api.PluginSettingOption.localizedLabel(
    context: Context
): String = resolveLocalizedText(
    context = context,
    fallback = label,
    values = labelByLocale
)

internal fun com.lidesheng.hyperlyric.plugin.core.PluginCacheScope.localizedTitle(
    context: Context
): String = resolveLocalizedText(context, title, titleByLocale)

internal fun com.lidesheng.hyperlyric.plugin.core.PluginCacheScope.localizedSummary(
    context: Context
): String? = resolveOptionalLocalizedText(context, summary, summaryByLocale)

internal fun resolveOptionalLocalizedText(
    context: Context,
    fallback: String?,
    values: Map<String, String>
): String? {
    if (fallback == null && values.isEmpty()) return null
    return resolveLocalizedText(context, fallback.orEmpty(), values)
}

internal fun resolveLocalizedText(
    context: Context,
    fallback: String,
    values: Map<String, String>
): String {
    val locales = context.resources.configuration.locales
    for (index in 0 until locales.size()) {
        val locale = locales[index]
        values[locale.toLanguageTag()]?.let { return it }
        values[locale.language]?.let { return it }
    }
    return fallback
}
