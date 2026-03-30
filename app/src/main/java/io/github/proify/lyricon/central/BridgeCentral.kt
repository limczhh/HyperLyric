/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.proify.lyricon.central.util.ScreenStateMonitor

/**
 * 中央桥接管理对象。
 *
 * 负责初始化全局 Context，并管理核心广播通信：
 * - 注册 [CentralReceiver] 接收提供者注册请求；
 * - 向系统或其他组件发送启动完成广播。
 */
@SuppressLint("StaticFieldLeak")
object BridgeCentral {

    /** 全局应用 Context，用于广播和注册接收器 */
    private lateinit var context: Context

    /** 用于接收中央控制广播的接收器实例 */
    private val receiver = CentralReceiver

    /**
     * 初始化中央桥接。
     *
     * 仅在第一次调用时生效，后续调用将被忽略。
     *
     * @param appContext 应用级 Context
     */
    fun initialize(appContext: Context) {
        if (::context.isInitialized) return
        context = appContext.applicationContext
        ScreenStateMonitor.initialize(appContext)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Constants.ACTION_REGISTER_PROVIDER),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /**
     * 发送中央启动完成广播。
     *
     * 通知系统或其他组件中央模块已完成初始化。
     */
    fun sendBootCompleted() {
        if (!::context.isInitialized) return
        Log.d("BridgeCentral", "Sending Central Boot Completed broadcast: ${Constants.ACTION_CENTRAL_BOOT_COMPLETED}")
        context.sendBroadcast(Intent(Constants.ACTION_CENTRAL_BOOT_COMPLETED))
    }
}