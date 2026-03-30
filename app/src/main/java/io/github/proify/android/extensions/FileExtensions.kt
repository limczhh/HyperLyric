/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
@file:Suppress("unused")

package io.github.proify.android.extensions

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

fun File.md5(): String {
    if (!exists() || !canRead()) return ""
    try {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(this).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        e.printStackTrace()
        return ""
    }
}