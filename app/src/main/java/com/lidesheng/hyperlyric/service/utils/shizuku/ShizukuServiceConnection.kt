package com.lidesheng.hyperlyric.service.utils.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.lidesheng.hyperlyric.BuildConfig
import com.lidesheng.hyperlyric.IPrivilegedService
import com.lidesheng.hyperlyric.IPrivilegedLogCallback
import rikka.shizuku.Shizuku
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

object ShizukuServiceConnection {

    private val serviceMutex = Mutex()
    private var cachedService: IPrivilegedService? = null
    private var serviceConnection: ServiceConnection? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var lastPingAttempt = 0L
    private const val TAG = "ShizukuServiceConnection"
    private const val PING_INTERVAL_MS = 5000L  // Only ping every 5 seconds to avoid overhead
    private const val BIND_TIMEOUT_MS = 10000L  // 10 second timeout for service binding

    @Volatile private var logCallbackEnabled = true
    private val logCallback = object : IPrivilegedLogCallback.Stub() {
        override fun log(level: Int, tag: String?, message: String?) {
            val safeTag = tag ?: "PrivilegedService"
            val safeMsg = message ?: ""
            when (level) {
                0 -> Log.d(safeTag, safeMsg)
                1 -> Log.i(safeTag, safeMsg)
                2 -> Log.w(safeTag, safeMsg)
                3 -> Log.e(safeTag, safeMsg)
                else -> Log.d(safeTag, safeMsg)
            }
        }
    }

    /**
     * Gets or creates a persistent connection to the privileged service.
     * Uses caching to avoid repeated bind/unbind cycles.
     */
    suspend fun getPrivilegedService(): IPrivilegedService {
        serviceMutex.withLock {
            cachedService?.let { cached ->
                val now = System.currentTimeMillis()
                if (now - lastPingAttempt > PING_INTERVAL_MS) {
                    try {
                        if (cached.asBinder().pingBinder()) {
                            lastPingAttempt = now
                            Log.d(TAG, "Service 缓存有效 (ping 成功)")
                            return cached
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Service ping 失败, 正在重新绑定: ${e.message}")
                    }
                    lastPingAttempt = now
                    cachedService = null
                } else {
                    Log.v(TAG, "正在使用缓存 of Service (距离上次 ping 已过 ${now - lastPingAttempt}ms)")
                    return cached
                }
            }

            Log.d(TAG, "正在建立新的 Service 连接...")
            return establishServiceConnection().also {
                lastPingAttempt = System.currentTimeMillis()
                Log.d(TAG, "Service 连接建立成功")
            }
        }
    }

    fun setLogCallbackEnabled(enabled: Boolean) {
        logCallbackEnabled = enabled
        val service = cachedService ?: return
        try {
            if (enabled) {
                service.setLogCallback(logCallback)
                Log.d(TAG, "Log 回调已注册到特权 Service (开关切换)")
            } else {
                service.setLogCallback(null)
                Log.d(TAG, "Log 回调已从特权 Service 取消注册 (开关切换)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "切换 Log 回调失败: ${e.message}")
        }
    }

    private suspend fun establishServiceConnection(): IPrivilegedService {
        return withTimeoutOrNull(BIND_TIMEOUT_MS.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.d(TAG, "onServiceConnected 被调用, service=$service")
                        if (service != null) {
                            val privileged = IPrivilegedService.Stub.asInterface(service)
                            if (logCallbackEnabled) {
                                try {
                                    privileged.setLogCallback(logCallback)
                                    Log.d(TAG, "Log 回调已注册到特权 Service")
                                } catch (e: Exception) {
                                    Log.w(TAG, "注册 Log 回调失败: ${e.message}")
                                }
                            }
                            cachedService = privileged
                            serviceConnection = this
                            continuation.resume(privileged)
                        } else {
                            Log.e(TAG, "onServiceConnected 但 binder 为 null!")
                            continuation.resumeWithException(Exception("Shizuku UserService 已绑定但返回了空的 binder"))
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.w(TAG, "Service 异常断开连接")
                        cachedService = null
                    }
                }

                val args = Shizuku.UserServiceArgs(
                    ComponentName(BuildConfig.APPLICATION_ID, PrivilegedServiceImpl::class.java.name)
                )
                    .daemon(true)
                    .processNameSuffix("privileged")
                    .debuggable(BuildConfig.DEBUG)
                    .version(2)

                serviceArgs = args

                try {
                    Log.d(TAG, "正在调用 Shizuku.bindUserService()...")
                    Shizuku.bindUserService(args, connection)
                } catch (e: Throwable) {
                    Log.e(TAG, "bindUserService 抛出异常: ${e.message}", e)
                    continuation.resumeWithException(e)
                }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "Service 绑定已取消, 正在执行 unbind...")
                    try {
                        Shizuku.unbindUserService(args, connection, true)
                    } catch (ignored: Throwable) {
                        Log.w(TAG, "执行 unbind 期间发生错误: ${ignored.message}")
                    }
                }
            }
        } ?: throw Exception("Service 绑定超时, 超时时间为 ${BIND_TIMEOUT_MS}ms")
    }

    suspend fun <T> executeWithService(action: suspend (IPrivilegedService) -> T): T {
        Log.d(TAG, "executeWithService 被调用")
        return try {
            action(getPrivilegedService()).also {
                Log.d(TAG, "executeWithService 执行成功")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "executeWithService 执行失败: ${e.message}", e)
            throw e
        }
    }
}
