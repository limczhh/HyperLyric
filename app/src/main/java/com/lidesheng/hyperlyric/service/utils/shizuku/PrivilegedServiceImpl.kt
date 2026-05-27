package com.lidesheng.hyperlyric.service.utils.shizuku

import androidx.annotation.Keep
import com.lidesheng.hyperlyric.IPrivilegedService
import com.lidesheng.hyperlyric.IPrivilegedLogCallback
import com.lidesheng.hyperlyric.utils.LogManager
import android.os.IBinder
import java.lang.reflect.InvocationTargetException
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Keep
class PrivilegedServiceImpl : IPrivilegedService.Stub() {

    companion object {
        private const val TAG = "PrivilegedServiceImpl"
        private const val OP_TIMEOUT_MS = 3000L
        private val workerThread: HandlerThread by lazy {
            HandlerThread("PrivilegedServiceWorker").apply { start() }
        }
        private val workerHandler: Handler by lazy { Handler(workerThread.looper) }
        
        init {
            try {
                LogManager.d(TAG, "PrivilegedServiceImpl 类已加载")
            } catch (ignored: Exception) {}
        }
    }
    
    init {
        try {
            logD("PrivilegedServiceImpl 实例已创建")
        } catch (ignored: Exception) {}
    }

    @Volatile private var logCallback: IPrivilegedLogCallback? = null

    override fun setLogCallback(callback: IPrivilegedLogCallback?) {
        logCallback = callback
        logD("Log callback set: ${callback != null}")
    }

    override fun setPackageNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        logD("setPackageNetworkingEnabled 开始执行: UID=$uid, enabled=$enabled")
        
        try {
            val resultRef = AtomicReference<Result<Boolean>?>(null)
            val latch = CountDownLatch(1)

            workerHandler.post {
                val result = runCatching {
                    logD("步骤 1: 正在获取 ConnectivityManager...")
                    val realCm = getConnectivityManagerInstance()
                    logD("步骤 2: 成功获取 ConnectivityManager: ${realCm.javaClass.name}")
                    
                    val chain = 9
                    
                    logD("步骤 3: 正在调用 setFirewallChainEnabled($chain, true)...")
                    callMethodResilient(realCm, "setFirewallChainEnabled", chain, true)
                    logD("步骤 4: 调用 setFirewallChainEnabled 成功")
                    
                    val rule = if (enabled) 0 else 2
                    logD("步骤 5: 正在调用 setUidFirewallRule($chain, $uid, $rule)...")
                    callMethodResilient(realCm, "setUidFirewallRule", chain, uid, rule)
                    
                    logD("调用成功: 防火墙规则已更新, UID=$uid")
                    true
                }

                resultRef.set(result)
                latch.countDown()
            }

            val completed = latch.await(OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                logE("调用失败: setPackageNetworkingEnabled 执行超时, 超时时间为 ${OP_TIMEOUT_MS}ms")
                return false
            }

            val result = resultRef.get() ?: return false
            
            if (result.isFailure) {
                val e = result.exceptionOrNull()
                logE("调用失败: setPackageNetworkingEnabled 抛出异常: ${e?.javaClass?.name}: ${e?.message}")
                e?.cause?.let { cause ->
                    logE("异常根源: ${cause.javaClass.name}: ${cause.message}")
                }
                return false
            }
            
            return result.getOrDefault(false)
            
        } catch (e: Throwable) {
            logE("严重错误: PrivilegedServiceImpl 发生异常: ${e.message}")
            return false
        } finally {
            logD("setPackageNetworkingEnabled 执行结束")
        }
    }
    
    private fun getConnectivityManagerInstance(): Any {
        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "connectivity") as? IBinder
            ?: throw RuntimeException("connectivity service not found")
            
        val stubClass = Class.forName("android.net.IConnectivityManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, binder) ?: throw RuntimeException("asInterface returned null")
    }
    
    private fun callMethodResilient(obj: Any, methodName: String, vararg args: Any) {
        val clazz = obj.javaClass
        val methods = clazz.methods
        
        val targetMethod = methods.find { it.name == methodName && it.parameterCount == args.size }
            ?: throw NoSuchMethodException("Could not find method $methodName with ${args.size} params on ${clazz.name}")
            
        targetMethod.isAccessible = true
        
        val finalArgs = Array(args.size) { i ->
            val paramType = targetMethod.parameterTypes[i]
            val arg = args[i]
            
            when {
                paramType == Int::class.javaPrimitiveType && arg is Int -> arg
                paramType == Boolean::class.javaPrimitiveType && arg is Boolean -> arg
                paramType == Boolean::class.javaPrimitiveType && arg is Number -> arg.toInt() != 0
                paramType == Int::class.javaPrimitiveType && arg is Boolean -> if (arg) 1 else 0
                else -> arg
            }
        }
        
        try {
            targetMethod.invoke(obj, *finalArgs)
        } catch (e: InvocationTargetException) {
            val cause = e.targetException ?: e.cause
            if (cause != null) {
                logW("InvocationTargetException cause: ${cause.javaClass.name}: ${cause.message}")
            } else {
                logW("InvocationTargetException with null cause")
            }
            throw e
        }
    }

    private fun logD(message: String) {
        LogManager.d(TAG, message)
        logCallback?.let { runCatching { it.log(0, TAG, message) } }
    }

    private fun logW(message: String) {
        LogManager.w(TAG, message)
        logCallback?.let { runCatching { it.log(2, TAG, message) } }
    }

    private fun logE(message: String) {
        LogManager.e(TAG, message)
        logCallback?.let { runCatching { it.log(3, TAG, message) } }
    }
}
