package com.lidesheng.hyperlyric.root.utils

import android.util.Log
import io.github.libxposed.api.XposedModule

private const val TAG = "HyperLyric"

var globalXposedModule: XposedModule? = null

/** DEBUG 级别日志，同时输出到 Logcat 和 LSPosed 管理器 */
fun xLogDebug(msg: String) {
    val module = globalXposedModule ?: return
    val prefs = try { module.getRemotePreferences("com.lidesheng.hyperlyric_preferences") } catch (_: Exception) { null }
    val level = prefs?.getInt("key_log_level", 0) ?: 0
    if (level < 1) return
    Log.d(TAG, msg)
    module.log(Log.DEBUG, TAG, msg)
}

/** INFO 级别日志，同时输出到 Logcat 和 LSPosed 管理器 */
fun xLog(msg: String) {
    Log.i(TAG, msg)
    globalXposedModule?.log(Log.INFO, TAG, msg)
}

/** WARN 级别日志，同时输出到 Logcat 和 LSPosed 管理器 */
fun xLogWarn(msg: String) {
    Log.w(TAG, msg)
    globalXposedModule?.log(Log.WARN, TAG, msg)
}

/** ERROR 级别日志，同时输出到 Logcat 和 LSPosed 管理器 */
fun xLogError(msg: String, e: Throwable? = null) {
    val finalMsg = if (e != null) "$msg: ${e.message}" else msg
    Log.e(TAG, finalMsg)
    globalXposedModule?.log(Log.ERROR, TAG, finalMsg, e)
}

