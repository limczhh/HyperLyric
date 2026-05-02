package com.lidesheng.hyperlyric.root.utils

import android.content.SharedPreferences
import android.graphics.Typeface
import java.io.File

object FontHelper {

    fun loadTypeface(prefs: SharedPreferences): Typeface {
        val fontWeight = prefs.getInt(Constants.KEY_HOOK_FONT_WEIGHT, Constants.DEFAULT_HOOK_FONT_WEIGHT)
        val fontItalic = prefs.getBoolean(Constants.KEY_HOOK_FONT_ITALIC, Constants.DEFAULT_HOOK_FONT_ITALIC)

        val customFontPath = prefs.getString(Constants.KEY_HOOK_CUSTOM_FONT_PATH, null)
        if (!customFontPath.isNullOrBlank()) {
            try {
                val file = File(customFontPath)
                if (file.exists()) {
                    val baseTf = Typeface.createFromFile(file)
                    return Typeface.create(baseTf, fontWeight, fontItalic)
                }
            } catch (_: Exception) {
                // fall through to default
            }
        }

        return Typeface.create(Typeface.DEFAULT, fontWeight, fontItalic)
    }
}
