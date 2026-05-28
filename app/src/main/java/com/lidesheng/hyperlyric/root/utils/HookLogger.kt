package com.lidesheng.hyperlyric.root.utils

import android.util.Log
import com.lidesheng.hyperlyric.common.HyperLogger
import io.github.libxposed.api.XposedModule

private const val TAG = "HyperLyric"

object HookLogger : HyperLogger {
    var module: XposedModule? = null

    override fun d(tag: String, msg: String) {
        if (readLogLevel() < 1) return
        Log.d(TAG, "[$tag] $msg")
        module?.log(Log.DEBUG, TAG, "[$tag] $msg")
    }

    override fun i(tag: String, msg: String) {
        Log.i(TAG, "[$tag] $msg")
        module?.log(Log.INFO, TAG, "[$tag] $msg")
    }

    override fun w(tag: String, msg: String, e: Throwable?) {
        val finalMsg = if (e != null) "[$tag] $msg: ${e.message}" else "[$tag] $msg"
        Log.w(TAG, finalMsg, e)
        module?.log(Log.WARN, TAG, finalMsg, e)
    }

    override fun e(tag: String, msg: String, e: Throwable?) {
        val finalMsg = if (e != null) "[$tag] $msg: ${e.message}" else "[$tag] $msg"
        Log.e(TAG, finalMsg, e)
        module?.log(Log.ERROR, TAG, finalMsg, e)
    }

    private fun readLogLevel(): Int {
        val prefs = try { module?.getRemotePreferences("com.lidesheng.hyperlyric_preferences") } catch (_: Exception) { null }
        return prefs?.getInt("key_log_level", 0) ?: 0
    }
}
