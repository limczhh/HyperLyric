package com.lidesheng.hyperlyric.root.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShellUtils {

    suspend fun restartSystemUI(): Boolean {
        return execRootCmdSilent("pkill -9 com.android.systemui || killall -9 com.android.systemui")
    }

    suspend fun execRootCmdSilent(cmd: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val exitCode = process.waitFor()
                return@withContext exitCode == 0
            } catch (_: Exception) {
                return@withContext false
            }
        }
    }

}
