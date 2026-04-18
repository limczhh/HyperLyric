package com.lidesheng.hyperlyric.online.utils

import android.annotation.SuppressLint
import android.util.Log
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object NeCryptoUtils {
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val DIGEST_TEXT = "nobody%suse%smd5forencrypt"

    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @SuppressLint("GetInstance")
    private fun aesEncrypt(text: String): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val secretKey = SecretKeySpec(EAPI_KEY.toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(text.toByteArray())
    }

    @SuppressLint("GetInstance")
    fun aesDecrypt(data: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val secretKey = SecretKeySpec(EAPI_KEY.toByteArray(), "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            String(cipher.doFinal(data))
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * EAPI 参数加密
     * @param url API 路径 (例如 /api/v3/song/detail)
     * @param jsonParams JSON 字符串参?
     */
    fun encryptParams(url: String, jsonParams: String): ByteArray {
        val message = String.format(DIGEST_TEXT, url, jsonParams)
        val digest = md5(message)
        val data = "$url-36cd479b6b5-$jsonParams-36cd479b6b5-$digest"
        return aesEncrypt(data)
    }
}

object QmCryptoUtils {
    private const val TAG = "QmCryptoUtils"
    private const val QRC_KEY_STR = "!@#)(*$%123ZXC!@!@#)(NHL"

    fun decryptQrc(rawHexString: String): String {
        try {
            val hexString = rawHexString.replace(Regex("[^0-9A-Fa-f]"), "")
            if (hexString.isEmpty()) return ""

            val encryptedBytes = hexStringToByteArray(hexString)
            if (encryptedBytes.size % 8 != 0) {
                Log.e(TAG, "Encrypted bytes size not multiple of 8")
                return ""
            }

            val keyBytes = QRC_KEY_STR.toByteArray(Charsets.UTF_8)

            val schedules = TripleDesCustom.tripleDesKeySetup(keyBytes, TripleDesCustom.DECRYPT)

            val decryptedBytes = TripleDesCustom.tripleDesCrypt(encryptedBytes, schedules)

            if (decryptedBytes.isNotEmpty()) {
                Log.d(TAG, "Decrypted Header: %02X %02X".format(decryptedBytes[0], decryptedBytes[1]))
            }

            return decompress(decryptedBytes)

        } catch (e: Exception) {
            Log.e(TAG, "Decrypt Error", e)
            return ""
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val h = Character.digit(s[i], 16)
            val l = Character.digit(s[i + 1], 16)
            if (h == -1 || l == -1) { i += 2; continue }
            data[i / 2] = ((h shl 4) + l).toByte()
            i += 2
        }
        return data
    }

    private fun decompress(data: ByteArray): String {
        val inflater = Inflater(false)
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size * 2)
        try {
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) {
                    if (inflater.needsInput()) break
                    if (inflater.needsDictionary()) break
                }
                outputStream.write(buffer, 0, count)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Zlib Decompress failed: ${e.message}")
            return ""
        } finally {
            outputStream.close()
            inflater.end()
        }
        return outputStream.toString("UTF-8")
    }
}
