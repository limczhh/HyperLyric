package com.lidesheng.hyperlyric.ui.utils

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.lidesheng.hyperlyric.common.UIConstants

object LocaleUtils {
    const val LANGUAGE_SYSTEM = 0
    const val LANGUAGE_SIMPLIFIED_CHINESE = 1
    const val LANGUAGE_ENGLISH = 2

    fun applyStoredLanguage(context: Context) {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        applyLanguage(context, prefs.getInt(UIConstants.KEY_APP_LANGUAGE, UIConstants.DEFAULT_APP_LANGUAGE))
    }

    fun applyLanguage(context: Context, languageMode: Int) {
        val localeList = when (languageMode) {
            LANGUAGE_SIMPLIFIED_CHINESE -> LocaleList.forLanguageTags("zh-Hans")
            LANGUAGE_ENGLISH -> LocaleList.forLanguageTags("en")
            else -> LocaleList.getEmptyLocaleList()
        }
        context.getSystemService(LocaleManager::class.java)?.applicationLocales = localeList
    }
}
