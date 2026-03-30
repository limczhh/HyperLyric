/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

val json: Json = Json {
    coerceInputValues = true     // 尝试转换类型
    ignoreUnknownKeys = true     // 忽略未知字段
    isLenient = true             // 宽松的 JSON 语法
    explicitNulls = false        // 不序列化 null
    encodeDefaults = false       // 不序列化默认值
}

/**
 * 使用 Deflate 算法解压当前字节数组。
 *
 * @return 解压后的字节数组
 * @throws DataFormatException 当压缩数据格式非法时抛出
 */
@Throws(DataFormatException::class)
internal fun ByteArray.inflate(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    val inflater = Inflater().apply {
        setInput(this@inflate)
    }

    return ByteArrayOutputStream().also { out ->
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count > 0) {
                out.write(buffer, 0, count)
            } else if (inflater.needsInput()) {
                break
            }
        }
        inflater.end()
    }.toByteArray()
}