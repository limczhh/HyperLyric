package com.lidesheng.hyperlyric.service.utils.shizuku

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.lidesheng.hyperlyric.utils.LogManager
import com.lidesheng.hyperlyric.BuildConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import rikka.sui.Sui
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.edit

class ShizukuContext(base: Context) : ContextWrapper(base) {
    override fun getOpPackageName(): String = "com.android.shell"

    override fun getAttributionSource(): AttributionSource {
        val shellUid = try {
            Shizuku.getUid()
        } catch (_: Throwable) {
            2000
        }
        val builder = AttributionSource.Builder(shellUid)
            .setPackageName("com.android.shell")
        if (Build.VERSION.SDK_INT >= 34) {
            Api34Impl.setPid(builder)
        }
        return builder.build()
    }

    private object Api34Impl {
        @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        fun setPid(builder: AttributionSource.Builder) {
            builder.setPid(-1)
        }
    }
}

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    private const val OEM_DENY_CHAIN = "oem_deny"

    private val hookedServiceCache = ConcurrentHashMap<String, Any>()

    private data class ServiceBackend(
        val label: String,
        val stubClassName: String,
        val systemServiceName: String
    )

    private val serviceBackends = listOf(
        ServiceBackend("Connectivity", $$"android.net.IConnectivityManager$Stub", Context.CONNECTIVITY_SERVICE),
        ServiceBackend("NetworkManagement", $$"android.os.INetworkManagementService$Stub", "network_management")
    )

    suspend fun checkShizukuPermission(): Boolean {
        return try {
            if (!isShizukuServiceRunning()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true

            callbackFlow {
                val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                    trySend(grantResult == PackageManager.PERMISSION_GRANTED)
                }
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(1001)
                awaitClose { Shizuku.removeRequestPermissionResultListener(listener) }
            }.catch { emit(false) }.first()
        } catch (e: Throwable) {
            LogManager.e(TAG, "Shizuku 权限检查异常: ${e.message}", e)
            false
        }
    }

    fun isShizukuServiceRunning(): Boolean {
        return try {
            Sui.init(BuildConfig.APPLICATION_ID)
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    private fun disableBypassFocusLimit(context: Context) {
        try {
            val prefs = context.getSharedPreferences("com.lidesheng.hyperlyric_preferences", Context.MODE_PRIVATE)
            if (prefs.getBoolean("key_bypass_focus_notification_limit", false)) {
                LogManager.i(TAG, "检测到 Shizuku 服务离线，已自动回退关闭 [key_bypass_focus_notification_limit] 选项")
                prefs.edit { putBoolean("key_bypass_focus_notification_limit", false) }
            }
        } catch (e: Throwable) {
            LogManager.w(TAG, "自动关闭绕过选项失败: ${e.message}", e)
        }
    }

    suspend fun setXmsfNetworkingEnabled(context: Context, enabled: Boolean): Boolean {
        try {
            if (!isShizukuServiceRunning()) {
                disableBypassFocusLimit(context)
                return false
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                disableBypassFocusLimit(context)
                return false
            }
        } catch (e: Throwable) {
            LogManager.e(TAG, "Shizuku 检查异常 (服务可能未启动或 binder 未接收): ${e.message}", e)
            disableBypassFocusLimit(context)
            return false
        }

        val pm = context.packageManager
        val uid = try {
            pm.getPackageUid(XMSF_PACKAGE, 0)
        } catch (_: Exception) {
            LogManager.w(TAG, "未找到 XMSF 包名 (UID 查询失败)")
            return false
        }

        LogManager.d(TAG, "setXmsfNetworkingEnabled 被调用: enabled=$enabled, UID=$uid")

        // 1. 优先尝试使用具有高权限的 Shizuku User Service 进行跨进程调用以完美绕过系统对于 callingUid 的强校验
        try {
            LogManager.d(TAG, "正在尝试使用 Shizuku UserService...")
            val service = ShizukuServiceConnection.getPrivilegedService()
            LogManager.d(TAG, "成功获取特权 Service, 正在调用 setPackageNetworkingEnabled...")
            val success = service.setPackageNetworkingEnabled(uid, enabled)
            if (success) {
                LogManager.d(TAG, "通过特权 Service 成功设置 XMSF 网络状态为 $enabled")
                return true
            } else {
                LogManager.w(TAG, "特权 Service 返回失败, UID=$uid, 正在退回到本地 Hook 模式")
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "特权 Service 调用失败: ${e.message}, 正在退回到本地 Hook 模式", e)
        }

        // 2. 本地 Hook 备用方案（当 User Service 无法正常工作或尚未就绪时的降级路径）
        val rule = if (enabled) 0 else 2 // 0 = ALLOW, 2 = DENY
        val failures = mutableListOf<String>()

        for (backend in serviceBackends) {
            try {
                val service = getHookedService(backend)
                LogManager.d(TAG, "正在尝试 ${backend.label} 后端: UID=$uid, enabled=$enabled (本地 Hook)")

                if (!enabled) {
                    callMethodResilient(
                        service,
                        listOf("setFirewallChainEnabled"),
                        OEM_DENY_CHAIN,
                        true
                    )
                    LogManager.d(TAG, "在拦截 UID=$uid 之前, 已通过 ${backend.label} 启用防火墙链 $OEM_DENY_CHAIN (本地 Hook)")
                }

                val methodUsed = callMethodResilient(
                    service,
                    listOf("setUidFirewallRule", "setFirewallUidRule"),
                    OEM_DENY_CHAIN,
                    uid,
                    rule
                )
                LogManager.d(
                    TAG,
                    "已通过 ${backend.label}.$methodUsed 成功${if (enabled) "恢复" else "拦截"} UID=$uid 的网络连接 (本地 Hook)"
                )
                return true
            } catch (t: Throwable) {
                val detail = "${backend.label} 失败: ${t.message}"
                failures += detail
                LogManager.w(TAG, detail, t)
            }
        }

        LogManager.e(TAG, "所有防火墙后端均失败: ${failures.joinToString(" || ")}")
        return false
    }

    private fun getHookedService(backend: ServiceBackend): Any {
        hookedServiceCache[backend.stubClassName]?.let { return it }

        return synchronized(this) {
            hookedServiceCache[backend.stubClassName]?.let { return@synchronized it }

            val originalBinder = SystemServiceHelper.getSystemService(backend.systemServiceName)
            val wrapper = ShizukuBinderWrapper(originalBinder)

            val stubClass = Class.forName(backend.stubClassName)
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val service = asInterfaceMethod.invoke(null, wrapper) 
                ?: throw RuntimeException("无法通过 asInterface 转换服务 Binder: ${backend.stubClassName}")

            hookedServiceCache[backend.stubClassName] = service
            service
        }
    }

    private fun callMethodResilient(obj: Any, methodNames: List<String>, vararg args: Any): String {
        val clazz = obj.javaClass
        val methods = clazz.methods

        for (methodName in methodNames) {
            val targetMethod = methods.find { it.name == methodName && it.parameterCount == args.size }
            if (targetMethod != null) {
                targetMethod.isAccessible = true
                val finalArgs = Array(args.size) { i ->
                    val paramType = targetMethod.parameterTypes[i]
                    val arg = args[i]
                    when (paramType) {
                        Int::class.javaPrimitiveType if arg is Int -> arg
                        Boolean::class.javaPrimitiveType if arg is Boolean -> arg
                        Boolean::class.javaPrimitiveType if arg is Number -> arg.toInt() != 0
                        Int::class.javaPrimitiveType if arg is Boolean -> if (arg) 1 else 0
                        else -> arg
                    }
                }
                try {
                    targetMethod.invoke(obj, *finalArgs)
                    return methodName
                } catch (e: InvocationTargetException) {
                    throw e.targetException ?: e
                }
            }
        }
        throw NoSuchMethodException("Could not find any matching method in $methodNames with ${args.size} params on ${clazz.name}")
    }
}
