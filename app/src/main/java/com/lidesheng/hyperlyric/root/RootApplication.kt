package com.lidesheng.hyperlyric.root

import android.app.Application
import android.content.Context
import com.lidesheng.hyperlyric.common.PrefsBridge
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
        appContext = this

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                reconcileRemotePreferences(this@RootApplication, service)
            }
            override fun onServiceDied(service: XposedService) {
                xposedService = null
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
        private fun syncAllPreferences(
            context: Context,
            service: XposedService? = xposedService,
            replaceRemote: Boolean = false
        ): Boolean {
            val remotePrefs = try {
                service?.getRemotePreferences(UIConstants.PREF_NAME)
            } catch (e: Exception) {
                LogManager.w(TAG, "获取 Xposed 远程配置失败", e)
                null
            } ?: return false

            val localPrefs = context.getSharedPreferences(UIConstants.PREF_NAME, MODE_PRIVATE)
            return try {
                val editor = remotePrefs.edit()
                if (replaceRemote) editor.clear()
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
                editor.commit()
            } catch (e: Exception) {
                LogManager.w(TAG, "同步 Xposed 远程配置失败", e)
                false
            }
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
                    LogManager.i(TAG, "已按当前安装数据重置 Xposed 远程配置")
                }.onFailure {
                    LogManager.w(TAG, "保存 Xposed 远程配置对账状态失败", it)
                }
            }
        }

        private var appContext: Context? = null
    }
}
