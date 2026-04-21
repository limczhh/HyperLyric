package com.lidesheng.hyperlyric.online.utils

import android.content.Context

/**
 * 极轻量级繁简转换工具（仅支持 繁体 -> 简体）
 * 基于正向最大匹配算法，避免了引入完整 opencc4j 导致的体积暴增
 */
object ChineseUtils {
    @Volatile
    private var initialized = false
    
    private val phraseMap = HashMap<String, String>()
    private val charMap = HashMap<Char, Char>()
    private var maxPhraseLength = 1

    private fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            
            try {
                // 加载词组映射 (TSPhrases.txt)
                context.assets.open("dictionary/TSPhrases.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val key = parts[0]
                            val value = parts[1]
                            phraseMap[key] = value
                            if (key.length > maxPhraseLength) {
                                maxPhraseLength = key.length
                            }
                        }
                    }
                }
                
                // 加载单字映射 (TSCharacters.txt)
                context.assets.open("dictionary/TSCharacters.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val key = parts[0]
                            val value = parts[1]
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                charMap[key[0]] = value[0]
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            initialized = true
        }
    }

    /**
     * 将繁体中文转换为简体中文 (附带基于上下文词组的转换纠正)
     */
    fun toSimplified(context: Context, text: String): String {
        if (text.isEmpty()) return text
        ensureLoaded(context)
        
        val result = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var matched = false
            // 基于长度的词组最长正向匹配
            for (len in maxPhraseLength downTo 2) {
                if (i + len <= text.length) {
                    val sub = text.substring(i, i + len)
                    val mapped = phraseMap[sub]
                    if (mapped != null) {
                        result.append(mapped)
                        i += len
                        matched = true
                        break
                    }
                }
            }
            // 未命中词组，查单字映射，再查不到保留原字
            if (!matched) {
                val c = text[i]
                result.append(charMap[c] ?: c)
                i++
            }
        }
        return result.toString()
    }
}
