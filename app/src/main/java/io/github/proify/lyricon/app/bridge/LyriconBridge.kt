/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.app.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * LyriconBridge: 声明式、协程优先的跨进程异步通信桥接器。
 *
 * 修复点：
 * 1. 支持 suspend 处理函数，消灭异步回调地狱。
 * 2. 优化 onQuery 逻辑，通过 CompletableDeferred 实现真正的异步转同步回传。
 * 3. 增强代码注释与规范。
 */
object LyriconBridge {

    private const val ACTION_IPC = "io.github.proify.lyricon.app.ACTION_IPC_ROUTER"
    private const val EXTRA_KEY = "key"
    private const val EXTRA_PAYLOAD = "payload"
    private const val EXTRA_CALLBACK = "callback"

    /** 处理器存储：支持挂起函数的 Lambda 容器 */
    private val handlers = ConcurrentHashMap<String, suspend (Bundle) -> Bundle?>()

    /** 桥接器全局协程作用域 */
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 广播接收器：处理来自其他进程的请求 */
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_IPC) return
            val key = intent.getStringExtra(EXTRA_KEY) ?: return
            val handler = handlers[key] ?: return

            val extras = intent.extras ?: return
            val data = extras.getBundle(EXTRA_PAYLOAD) ?: Bundle.EMPTY
            val binder = extras.getBinder(EXTRA_CALLBACK) ?: return

            // 启动协程处理请求，避免阻塞主线程
            bridgeScope.launch {
                val callback = IBridgeCallback.Stub.asInterface(binder)
                try {
                    // 执行挂起的处理器并等待其返回 Bundle
                    val result = handler(data) ?: Bundle.EMPTY
                    callback.onReply(result)
                } catch (e: Exception) {
                    // 发生异常时回传空 Bundle 确保客户端 await 能够结束
                    callback.onReply(Bundle.EMPTY)
                    e.printStackTrace()
                }
            }
        }
    }

    @Volatile
    private var isInitialized = false

    /**
     * 初始化并注册路由。
     * @param context 上下文
     * @param block 路由配置块
     */
    fun routing(context: Context, block: BridgeRoutingScope.() -> Unit) {
        if (!isInitialized) synchronized(this) {
            if (!isInitialized) {
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    receiver,
                    IntentFilter(ACTION_IPC),
                    ContextCompat.RECEIVER_EXPORTED
                )
                isInitialized = true
            }
        }
        BridgeRoutingScope().apply(block)
    }

    /**
     * 启动一个请求构造链。
     * @param context 发起请求的上下文
     */
    fun with(context: Context) = RequestTask(context.applicationContext)

    // --- 内部作用域类 ---

    /**
     * 路由注册 DSL 作用域
     */
    class BridgeRoutingScope {
        /**
         * 注册单向指令处理器。
         * @param key 路由键
         * @param action 处理逻辑（支持挂起）
         */
        fun onCommand(key: String, action: suspend (Bundle) -> Unit) {
            handlers[key] = { data ->
                action(data)
                Bundle.EMPTY
            }
        }

        /**
         * 注册双向查询处理器。
         * @param key 路由键
         * @param action 处理逻辑，在 [QueryScope] 中需显式调用 reply
         */
        fun onQuery(key: String, action: suspend QueryScope.() -> Unit) {
            handlers[key] = { data ->
                val deferred = CompletableDeferred<Bundle>()
                val scope = QueryScope(data, deferred)
                // 在当前协程执行业务逻辑
                scope.action()
                // 挂起直到业务方调用了 scope.reply(...)
                deferred.await()
            }
        }
    }

    /**
     * 查询上下文作用域：用于持有数据并回传结果。
     */
    class QueryScope(
        val data: Bundle,
        private val deferred: CompletableDeferred<Bundle>
    ) {
        /**
         * 显式回传结果并恢复挂起的处理器。
         * @param bundle 回传给客户端的数据
         */
        fun reply(bundle: Bundle) {
            if (deferred.isActive) {
                deferred.complete(bundle)
            }
        }
    }

    /**
     * RequestTask: 客户端任务构造器，用于封装请求参数并发送。
     */
    class RequestTask(private val context: Context) {
        private var key: String? = null
        private var data: Bundle = Bundle.EMPTY
        private var targetPkg: String = context.packageName

        /** 设置目标进程包名 */
        fun to(pkg: String) = apply { this.targetPkg = pkg }

        /** 设置业务唯一标识 */
        fun key(key: String) = apply { this.key = key }

        /** 设置数据载体 */
        fun payload(bundle: Bundle) = apply {
            this.data = bundle
        }

        /** 发送单向指令（不关心返回） */
        fun send() {
            executeInternal(null)
        }

        /**
         * 异步获取结果（支持协程挂起）。
         * @param timeout 超时时间，默认 3000ms
         * @return 服务端回传的 [Bundle]
         */
        suspend fun await(timeout: Long = 3000): Bundle {
            return try {
                withTimeout(timeout) {
                    val deferred = CompletableDeferred<Bundle>()
                    executeInternal { deferred.complete(it) }
                    deferred.await()
                }
            } catch (e: TimeoutCancellationException) {
                Bundle.EMPTY
            }
        }

        /**
         * 传统回调方式执行任务。
         * @param onReply 处理服务端回传的数据回调
         */
        fun execute(onReply: ((Bundle) -> Unit)? = null) {
            executeInternal(onReply)
        }

        private fun executeInternal(onReplyAction: ((Bundle) -> Unit)?) {
            val targetKey = key ?: throw IllegalArgumentException("LyriconBridge: key missing")
            val intent = Intent(ACTION_IPC).apply {
                `package` = targetPkg
                putExtra(EXTRA_KEY, targetKey)
            }

            val bundle = Bundle().apply {
                putBundle(EXTRA_PAYLOAD, data)
                // 注入匿名 Binder 用于跨进程回传结果
                putBinder(EXTRA_CALLBACK, object : IBridgeCallback.Stub() {
                    override fun onReply(res: Bundle) {
                        onReplyAction?.invoke(res)
                    }
                })
            }
            context.sendBroadcast(intent.putExtras(bundle))
        }
    }
}