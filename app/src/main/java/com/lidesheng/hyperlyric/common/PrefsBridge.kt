package com.lidesheng.hyperlyric.common

import android.content.Context
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.root.RootApplication

object PrefsBridge {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PreferenceKeys.PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getPrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("PrefsBridge not initialized. Call init() first.")
    }

    fun getBoolean(key: String, default: Boolean): Boolean = getPrefs().getBoolean(key, default)
    fun getInt(key: String, default: Int): Int = getPrefs().getInt(key, default)
    fun getString(key: String, default: String? = null): String? = getPrefs().getString(key, default)
    fun getLong(key: String, default: Long): Long = getPrefs().getLong(key, default)
    fun getFloat(key: String, default: Float): Float = getPrefs().getFloat(key, default)
    fun getStringSet(key: String, default: Set<String>? = null): Set<String>? = getPrefs().getStringSet(key, default)

    fun putBoolean(key: String, value: Boolean) {
        val preferences = getPrefs()
        val targetUpdates = LyricOutputTargetPreferencePolicy.updatesFor(key, value)
        if (targetUpdates != null) {
            preferences.edit().apply {
                targetUpdates.forEach { (targetKey, targetEnabled) ->
                    putBoolean(targetKey, targetEnabled)
                }
            }.apply()
            RootApplication.syncBooleanPreferences(PreferenceKeys.PREF_NAME, targetUpdates)
        } else {
            preferences.edit().putBoolean(key, value).apply()
            RootApplication.syncPreference(PreferenceKeys.PREF_NAME, key, value)
        }
    }

    fun putInt(key: String, value: Int) {
        getPrefs().edit().putInt(key, value).apply()
        RootApplication.syncPreference(PreferenceKeys.PREF_NAME, key, value)
    }

    fun putString(key: String, value: String?) {
        getPrefs().edit().putString(key, value).apply()
        RootApplication.syncPreference(PreferenceKeys.PREF_NAME, key, value)
    }

    fun putLong(key: String, value: Long) {
        getPrefs().edit().putLong(key, value).apply()
        RootApplication.syncPreference(PreferenceKeys.PREF_NAME, key, value)
    }

    fun putFloat(key: String, value: Float) {
        getPrefs().edit().putFloat(key, value).apply()
        RootApplication.syncPreference(PreferenceKeys.PREF_NAME, key, value)
    }

    fun putStringSet(key: String, value: Set<String>?) {
        getPrefs().edit().putStringSet(key, value).apply()
        RootApplication.syncPreference(PreferenceKeys.PREF_NAME, key, value)
    }

    fun syncAllToRemote() {
        RootApplication.syncAllPreferences()
    }
}
