package com.lidesheng.hyperlyric.root.utils

import android.content.SharedPreferences
import android.graphics.Typeface
import java.io.File

object FontHelper {

    fun loadTypeface(prefs: SharedPreferences): Typeface {
        val fontWeight = prefs.getInt(Constants.KEY_HOOK_FONT_WEIGHT, Constants.DEFAULT_HOOK_FONT_WEIGHT)
        val fontItalic = prefs.getBoolean(Constants.KEY_HOOK_FONT_ITALIC, Constants.DEFAULT_HOOK_FONT_ITALIC)

        val customFontPath = prefs.getString(Constants.KEY_HOOK_CUSTOM_FONT_PATH, null)
        var baseTf: Typeface? = null

        if (!customFontPath.isNullOrBlank()) {
            try {
                val file = File(customFontPath)
                if (file.exists() && file.canRead()) {
                    baseTf = Typeface.createFromFile(file)
                    xLog("自定义字体加载成功：$customFontPath")
                } else {
                    xLog("自定义字体文件不存在或无法读取：$customFontPath (存在: ${file.exists()}, 可读: ${file.canRead()})")
                }
            } catch (e: Exception) {
                xLog("无法从文件创建字体：$customFontPath，原因: ${e.message}")
            }
        }

        val finalBaseTf = baseTf ?: Typeface.DEFAULT

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // API 28+ supports weight and italic separately
            Typeface.create(finalBaseTf, fontWeight.coerceIn(1, 1000), fontItalic)
        } else {
            // Fallback for older APIs
            val style = when {
                fontWeight >= 600 && fontItalic -> Typeface.BOLD_ITALIC
                fontWeight >= 600 -> Typeface.BOLD
                fontItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(finalBaseTf, style)
        }
    }
}
