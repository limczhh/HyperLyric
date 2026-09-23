package com.lidesheng.hyperlyric.root

import android.app.Application
import android.content.Context
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.LyricOutputTargetPreferencePolicy
import com.lidesheng.hyperlyric.common.SyllablePreferencePolicy
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.ui.utils.AppUtils
import com.lidesheng.hyperlyric.ui.utils.LocaleUtils
import com.lidesheng.hyperlyric.utils.LogManager
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.io.File

class RootApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LocaleUtils.clearLegacyPlatformLocale(this)
        AppUtils.initPredictiveBackGesture(this)
        LogManager.init(this)
        PrefsBridge.init(this)
        SyllablePreferencePolicy.normalizeInPlace(PrefsBridge.getPrefs())
        LyricOutputTargetPreferencePolicy.normalizeInPlace(PrefsBridge.getPrefs())
        appContext = this

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                reconcileRemotePreferences(this@RootApplication, service)
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedService === service) xposedService = null
            }
        })
    }

    companion object {
        private const val TAG = "RootApplication"
        private const val REMOTE_PREFS_RECONCILIATION_VERSION = 1

        @JvmStatic
        var xposedService: XposedService? = null
            private set

        @JvmStatic
        fun syncPreference(group: String, key: String, value: Any?) {
            val remotePrefs = try {
                xposedService?.getRemotePreferences(group)
            } catch (_: Exception) {
                null
            } ?: return

            remotePrefs.edit().apply {
                when (value) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                }
                apply()
            }
        }

        @JvmStatic
        fun syncBooleanPreferences(group: String, values: Map<String, Boolean>) {
            val remotePrefs = try {
                xposedService?.getRemotePreferences(group)
            } catch (_: Exception) {
                null
            } ?: return

            runCatching {
                remotePrefs.edit().apply {
                    values.forEach { (key, value) -> putBoolean(key, value) }
                    apply()
                }
            }.onFailure { error ->
                LogManager.w(TAG, "同步互斥歌词输出开关失败", error)
            }
        }

        @JvmStatic
        private fun syncAllPreferences(
            context: Context,
            service: XposedService? = xposedService,
            replaceRemote: Boolean = false
        ): Boolean {
            val connectedService = service ?: return false
            val remotePrefs = try {
                connectedService.getRemotePreferences(UIConstants.PREF_NAME)
            } catch (e: Exception) {
                LogManager.w(TAG, "获取 Xposed 远程配置失败", e)
                null
            } ?: return false

            val localPrefs = context.getSharedPreferences(UIConstants.PREF_NAME, MODE_PRIVATE)
            SyllablePreferencePolicy.normalizeInPlace(localPrefs)
            LyricOutputTargetPreferencePolicy.normalizeInPlace(localPrefs)
            val hostSynced = try {
                val editor = remotePrefs.edit()
                // LSPosed's RemotePreferences service persists the explicit delete set; its
                // wrapper-only clear flag is ignored by older service implementations. Rewrite
                // the group with explicit removals when replacing the remote snapshot.
                if (replaceRemote) remotePrefs.all.keys.forEach(editor::remove)
                localPrefs.all.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is String -> editor.putString(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(
                            key,
                            value as Set<String>
                        )
                    }
                }
                LyricOutputTargetPreferencePolicy.read(localPrefs).forEach { (key, value) ->
                    editor.putBoolean(key, value)
                }
                editor.commit()
            } catch (e: Exception) {
                LogManager.w(TAG, "同步 Xposed 远程配置失败", e)
                false
            }
            if (!hostSynced) return false
            return true
        }

        @JvmStatic
        fun syncAllPreferences() {
            val context = appContext ?: return
            syncAllPreferences(context)
        }

        private fun reconcileRemotePreferences(context: Context, service: XposedService) {
            val marker = File(
                context.noBackupFilesDir,
                "remote_preferences_v$REMOTE_PREFS_RECONCILIATION_VERSION"
            )
            val replaceRemote = !marker.exists()
            if (!syncAllPreferences(context, service, replaceRemote)) return

            if (replaceRemote) {
                runCatching {
                    marker.parentFile?.mkdirs()
                    if (!marker.exists() && !marker.createNewFile()) {
                        error("无法创建远程配置对账标记")
                    }
                }.onSuccess {
                    LogManager.d(TAG, "已按当前安装数据重置 Xposed 远程配置")
                }.onFailure {
                    LogManager.w(TAG, "保存 Xposed 远程配置对账状态失败", it)
                }
            }
        }

        private var appContext: Context? = null
    }

}
