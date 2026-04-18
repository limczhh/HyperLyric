package com.lidesheng.hyperlyric.root.utils

import android.util.Log

private const val TAG = "HyperLyric"

/** INFO 级别日志 */
internal fun log(msg: String) {
    Log.i(TAG, msg)
}

/** ERROR 级别日志 */
internal fun logError(msg: String, e: Throwable? = null) {
    val finalMsg = if (e != null) "$msg: ${e.message}" else msg
    Log.e(TAG, finalMsg)
}

