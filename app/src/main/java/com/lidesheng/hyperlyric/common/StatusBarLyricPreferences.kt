package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences
import java.util.WeakHashMap

/** Preference keys and the scoped view used by the status-bar lyric projection. */
object StatusBarLyricPreferences {
    const val KEY_ENABLED = RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ENABLED
    const val DEFAULT_ENABLED = RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ENABLED
    const val KEY_CONFIG_INITIALIZED =
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CONFIG_INITIALIZED

    private const val CONFIG_PREFIX = "key_hook_status_bar_lyric_"

    // Marquee settings intentionally remain unmapped so both lyric targets share the same values.
    private val independentLyricKeys = setOf(
        RootConstants.KEY_HOOK_TEXT_SIZE,
        RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
        RootConstants.KEY_HOOK_FONT_WEIGHT,
        RootConstants.KEY_HOOK_FONT_ITALIC,
        RootConstants.KEY_HOOK_FADING_EDGE_LENGTH,
        RootConstants.KEY_HOOK_TEXT_COLOR_STYLE,
        RootConstants.KEY_HOOK_CUSTOM_FONT_PATH,
        RootConstants.KEY_HOOK_NARROW_LATIN_FONT
    )

    private val scopedKeys = independentLyricKeys.associateWith { key ->
        CONFIG_PREFIX + key.removePrefix("key_hook_")
    }
    private val sourceKeys = scopedKeys.entries.associate { (source, scoped) -> scoped to source }

    private val layoutKeys = setOf(
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN,
    )

    fun scoped(prefs: SharedPreferences): SharedPreferences =
        ScopedSharedPreferences(prefs, scopedKeys)

    fun isStatusBarPreferenceKey(key: String?): Boolean =
        key == KEY_ENABLED || key in sourceKeys || key in layoutKeys

    fun scopedKey(key: String): String = scopedKeys[key] ?: key

    fun resolveStoredKey(prefs: SharedPreferences, key: String): String =
        (prefs as? ScopedSharedPreferences)?.storedKey(key) ?: key

    fun effectiveStatusBarTextColorStyle(prefs: SharedPreferences): Int {
        val style = LyricTextColorStylePolicy.read(prefs)
        return if (style == RootConstants.TEXT_COLOR_STYLE_DEFAULT) {
            RootConstants.TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR
        } else {
            style
        }
    }

    /** Copies the current shared lyric values once, so both renderers start visually alike. */
    fun initializeFromShared(prefs: SharedPreferences): Boolean {
        if (prefs.getBoolean(KEY_CONFIG_INITIALIZED, false)) return false

        val current = prefs.all
        val editor = prefs.edit()
        scopedKeys.forEach { (sourceKey, scopedKey) ->
            if (!current.containsKey(scopedKey)) {
                copyPreferenceValue(editor, scopedKey, current[sourceKey])
            }
        }
        editor.putBoolean(KEY_CONFIG_INITIALIZED, true).apply()
        return true
    }

    fun shouldFollowStatusBarTextColor(prefs: SharedPreferences): Boolean {
        val sharedFollow = LyricTextColorStylePolicy.followsStatusBar(
            LyricTextColorStylePolicy.read(prefs)
        )
        if (sharedFollow) return true
        if (!prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)) return false
        val statusBarStyle = LyricTextColorStylePolicy.read(scoped(prefs))
        return statusBarStyle == RootConstants.TEXT_COLOR_STYLE_DEFAULT ||
                LyricTextColorStylePolicy.followsStatusBar(statusBarStyle)
    }

    private fun copyPreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        value: Any?
    ) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toMutableSet())
        }
    }

    private class ScopedSharedPreferences(
        private val delegate: SharedPreferences,
        private val keyMap: Map<String, String>
    ) : SharedPreferences {
        private val listeners = WeakHashMap<
            SharedPreferences.OnSharedPreferenceChangeListener,
            SharedPreferences.OnSharedPreferenceChangeListener
            >()
        private val reverseMap = keyMap.entries.associate { (source, scoped) -> scoped to source }

        fun storedKey(key: String): String = keyMap[key] ?: key

        private fun readKey(key: String): String {
            val scopedKey = keyMap[key] ?: return key
            if (delegate.contains(scopedKey)) return scopedKey
            val initialized = delegate.getBoolean(KEY_CONFIG_INITIALIZED, false)
            return if (!initialized && delegate.contains(key)) key else scopedKey
        }

        override fun getAll(): MutableMap<String, *> {
            val values = delegate.all.entries.associateTo(mutableMapOf()) { it.key to it.value }
            val initialized = delegate.getBoolean(KEY_CONFIG_INITIALIZED, false)
            keyMap.forEach { (sourceKey, scopedKey) ->
                when {
                    delegate.contains(scopedKey) -> values[sourceKey] = delegate.all[scopedKey]
                    !initialized && delegate.contains(sourceKey) -> Unit
                    else -> values.remove(sourceKey)
                }
                values.remove(scopedKey)
            }
            return values
        }

        override fun getString(key: String?, defValue: String?): String? =
            delegate.getString(readKey(requireNotNull(key)), defValue)

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            delegate.getStringSet(readKey(requireNotNull(key)), defValues)?.toMutableSet()

        override fun getInt(key: String?, defValue: Int): Int {
            val sourceKey = requireNotNull(key)
            val scopedDefault = if (sourceKey == RootConstants.KEY_HOOK_TEXT_SIZE) {
                RootConstants.DEFAULT_STATUS_BAR_LYRIC_TEXT_SIZE_SP
            } else {
                defValue
            }
            return delegate.getInt(readKey(sourceKey), scopedDefault)
        }

        override fun getLong(key: String?, defValue: Long): Long =
            delegate.getLong(readKey(requireNotNull(key)), defValue)

        override fun getFloat(key: String?, defValue: Float): Float =
            delegate.getFloat(readKey(requireNotNull(key)), defValue)

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            delegate.getBoolean(readKey(requireNotNull(key)), defValue)

        override fun contains(key: String?): Boolean {
            val sourceKey = requireNotNull(key)
            val scopedKey = keyMap[sourceKey] ?: return delegate.contains(sourceKey)
            return delegate.contains(scopedKey) ||
                    (!delegate.getBoolean(KEY_CONFIG_INITIALIZED, false) &&
                            delegate.contains(sourceKey))
        }

        override fun edit(): SharedPreferences.Editor = ScopedEditor(delegate.edit(), keyMap)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener == null) return
            synchronized(listeners) {
                if (listeners.containsKey(listener)) return
                val mappedListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    listener.onSharedPreferenceChanged(this, reverseMap[key] ?: key)
                }
                listeners[listener] = mappedListener
                delegate.registerOnSharedPreferenceChangeListener(mappedListener)
            }
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener == null) return
            val mappedListener = synchronized(listeners) { listeners.remove(listener) } ?: return
            delegate.unregisterOnSharedPreferenceChangeListener(mappedListener)
        }
    }

    private class ScopedEditor(
        private val delegate: SharedPreferences.Editor,
        private val keyMap: Map<String, String>
    ) : SharedPreferences.Editor {
        private fun mapped(key: String): String = keyMap[key] ?: key

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            apply { delegate.putString(mapped(requireNotNull(key)), value) }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?
        ): SharedPreferences.Editor = apply {
            delegate.putStringSet(mapped(requireNotNull(key)), values)
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            apply { delegate.putInt(mapped(requireNotNull(key)), value) }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            apply { delegate.putLong(mapped(requireNotNull(key)), value) }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            apply { delegate.putFloat(mapped(requireNotNull(key)), value) }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            apply { delegate.putBoolean(mapped(requireNotNull(key)), value) }

        override fun remove(key: String?): SharedPreferences.Editor =
            apply { delegate.remove(mapped(requireNotNull(key))) }

        override fun clear(): SharedPreferences.Editor =
            apply { keyMap.values.forEach(delegate::remove) }

        override fun commit(): Boolean = delegate.commit()

        override fun apply() = delegate.apply()
    }
}
