package com.lidesheng.hyperlyric.utils

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.LyricOutputTargetPreferencePolicy
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.root.RootApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.lidesheng.hyperlyric.common.ServiceConstants
import com.lidesheng.hyperlyric.common.SyllablePreferencePolicy
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.common.WordMotionPreferencePolicy

object BackupRestoreManager {
    private const val BACKUP_VERSION = 1

    data class RestoreResult(
        val success: Boolean,
        val empty: Boolean = false,
    )

    suspend fun buildBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        buildBackupDocument(context).toString(2)
    }

    suspend fun restoreFromUri(context: Context, uri: Uri): RestoreResult =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (json.isBlank()) {
                        RestoreResult(success = false, empty = true)
                    } else {
                        RestoreResult(restoreJsonDocument(context, JSONObject(json)))
                    }
                } ?: RestoreResult(false)
            }.getOrDefault(RestoreResult(false))
        }

    private fun buildBackupDocument(context: Context): JSONObject = JSONObject().apply {
        put("version", BACKUP_VERSION)
        put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        put("config", buildHostConfig(context))
    }

    private fun buildHostConfig(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        return JSONObject().apply {
            prefs.all.forEach { (key, value) ->
                if (isSensitivePreferenceKey(key)) return@forEach
                if (WordMotionPreferencePolicy.isBoundedFloatKey(key)) {
                    WordMotionPreferencePolicy.normalizeStoredFloat(key, value)?.let {
                        put(key, it.toDouble())
                    }
                    return@forEach
                }
                when (value) {
                    is Boolean -> put(key, value)
                    is Int -> put(key, value)
                    is Float -> put(key, value.toDouble())
                    is Long -> put(key, value)
                    is String -> put(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        put(key, (value as Set<String>).joinToString(","))
                    }
                }
            }
        }
    }

    suspend fun restoreFromJson(context: Context, json: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { restoreJsonDocument(context, JSONObject(json)) }.getOrDefault(false)
    }

    private fun restoreJsonDocument(context: Context, root: JSONObject): Boolean {
        if (root.optInt("version", -1) < 1) return false
        val config = root.optJSONObject("config") ?: return false
        restoreHostConfig(context, config)
        return true
    }

    private fun restoreHostConfig(context: Context, config: JSONObject) {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            val keys = config.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = config.get(key)
                if (key == "key_send_normal_notification" ||
                    key == "key_send_focus_notification" ||
                    key == "key_persistent_foreground" ||
                    isSensitivePreferenceKey(key)
                ) continue
                if (key == ServiceConstants.KEY_NOTIFICATION_WHITELIST) {
                    val raw = value.toString()
                    val set = if (raw.isBlank()) {
                        emptySet()
                    } else {
                        raw.split(",").map { it.trim() }.filter(String::isNotEmpty).toSet()
                    }
                    putStringSet(key, set)
                    continue
                }
                if (key == RootConstants.KEY_HOOK_AI_TRANS_SKIP_LANGUAGES) {
                    val raw = value as? String ?: ""
                    val set = if (raw.isBlank()) {
                        emptySet()
                    } else {
                        raw.split(",").map { it.trim() }.filter(String::isNotEmpty).toSet()
                    }
                    putStringSet(key, set)
                    continue
                }
                if (key == RootConstants.KEY_HOOK_AI_TRANS_MAX_TOKENS) {
                    val maxTokens = (value as? Number)?.toLong()
                        ?: value.toString().toLongOrNull()
                    if (maxTokens != null) putLong(key, maxTokens)
                    continue
                }
                if (WordMotionPreferencePolicy.isBoundedFloatKey(key)) {
                    WordMotionPreferencePolicy.normalizeStoredFloat(key, value)?.let {
                        putFloat(key, it)
                    }
                    continue
                }
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Double, is Float -> putFloat(key, (value as Number).toFloat())
                    is Long -> putLong(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        val restoredIslandEnabled = config.optBoolean(
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
            false,
        )
        val restoredStatusBarEnabled = config.optBoolean(
            StatusBarLyricPreferences.KEY_ENABLED,
            false,
        )
        val preferredEnabledTarget = when {
            restoredIslandEnabled && !restoredStatusBarEnabled ->
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND

            restoredStatusBarEnabled && !restoredIslandEnabled ->
                StatusBarLyricPreferences.KEY_ENABLED

            else -> null
        }
        LyricOutputTargetPreferencePolicy.normalizeInPlace(prefs, preferredEnabledTarget)
        val syllableSettings = SyllablePreferencePolicy.read(prefs)
        val syllableEditor = prefs.edit()
        SyllablePreferencePolicy.write(syllableEditor, syllableSettings)
        syllableEditor.apply()
    }

    private fun isSensitivePreferenceKey(key: String): Boolean =
        key.equals("api_key", ignoreCase = true) ||
                key.endsWith("_api_key", ignoreCase = true)
}

class BackupRestoreHelper(
    private val backupLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    private val restoreLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    fun launchBackup() {
        val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        backupLauncher.launch("hyperlyric_backup_$dateTime.json")
    }

    fun launchRestore() {
        restoreLauncher.launch(arrayOf("application/json"))
    }
}

@Composable
fun rememberBackupRestoreHelper(snackbarHostState: SnackbarHostState): BackupRestoreHelper {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val msgBackupSuccess = stringResource(R.string.toast_backup_success)
    val fmtBackupFailed = stringResource(R.string.toast_backup_failed)
    val msgRestoreEmpty = stringResource(R.string.toast_restore_empty)
    val msgRestoreSuccess = stringResource(R.string.toast_restore_success)
    val msgRestoreInvalid = stringResource(R.string.toast_restore_invalid)
    val msgRestoreFailed = stringResource(R.string.toast_restore_failed)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                try {
                    val jsonBytes = BackupRestoreManager.buildBackupJson(context).toByteArray(Charsets.UTF_8)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(jsonBytes)
                            it.flush()
                        }
                    }
                    snackbarHostState.showSnackbar(
                        message = msgBackupSuccess,
                        duration = SnackbarDuration.Custom(2000L)
                    )
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        message = fmtBackupFailed.format(e.message),
                        duration = SnackbarDuration.Custom(2000L)
                    )
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                try {
                    val result = BackupRestoreManager.restoreFromUri(context, uri)
                    if (result.success) RootApplication.syncAllPreferences()
                    val message = if (result.empty) {
                        msgRestoreEmpty
                    } else if (!result.success) {
                        msgRestoreInvalid
                    } else {
                        msgRestoreSuccess
                    }
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Custom(2000L)
                    )
                } catch (_: Exception) {
                    snackbarHostState.showSnackbar(
                        message = msgRestoreFailed,
                        duration = SnackbarDuration.Custom(2000L)
                    )
                }
            }
        }
    )

    return remember(backupLauncher, restoreLauncher) {
        BackupRestoreHelper(backupLauncher, restoreLauncher)
    }
}
