package com.lidesheng.hyperlyric.root.island.presentation

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * Re-enters Xiaomi's own media-island update path.
 *
 * This coordinator deliberately knows nothing about lyric views or settings. It only resolves the
 * current real media island, invokes DynamicIslandWindowView.updateDynamicIslandView(...) with the
 * data already owned by that island, and reports completion to the caller.
 */
internal object IslandNativeRefreshCoordinator {
    private const val TAG = "IslandNativeRefreshCoordinator"
    private const val REQUEST_DEBOUNCE_MS = 16L
    private const val NATIVE_SETTLE_TIMEOUT_MS = 160L

    private val mainHandler = Handler(Looper.getMainLooper())
    private enum class RequestKind { SETTINGS, WIDTH }

    // Coalesce only the same purpose for the same host generation. Settings and width callbacks
    // are independent; work arriving during a native update belongs to the next update.
    private val pendingRequests = linkedMapOf<IslandViewRegistry.HostToken,
            MutableMap<RequestKind, RefreshRequest>>()
    private val activeRefreshes = WeakHashMap<ViewGroup, ActiveRefresh>()
    private val nativeUpdateMethods = mutableMapOf<Class<*>, List<Method>>()

    private val requestRunnable = Runnable {
        performPendingRequest()
    }

    fun request(
        onComplete: (ViewGroup) -> Unit,
        targetToken: IslandViewRegistry.HostToken? = null,
        onUnavailable: (() -> Unit)? = null
    ) {
        runOnMain {
            val kind = if (targetToken == null) RequestKind.SETTINGS else RequestKind.WIDTH
            val request = RefreshRequest(onComplete, onUnavailable)
            val tokens = targetToken?.let(::listOf)
                ?: IslandPresentationCoordinator.snapshotAttachedRealHosts()
            if (tokens.isEmpty()) {
                notifyUnavailable(request)
                return@runOnMain
            }
            tokens.forEach { token ->
                if (isEligibleHost(token)) {
                    pendingRequests.getOrPut(token) { linkedMapOf() }[kind] = request
                }
            }
            schedulePendingRequests()
        }
    }

    private fun schedulePendingRequests() {
        if (pendingRequests.isNotEmpty() && !mainHandler.hasCallbacks(requestRunnable)) {
            mainHandler.postDelayed(requestRunnable, REQUEST_DEBOUNCE_MS)
        }
    }

    /**
     * Called by the real-island hook after Xiaomi has completed one update. A delayed completion
     * remains as a fallback because newer suspend signatures do not always expose a Boolean result
     * through the hooked method.
     */
    fun onSystemUpdateComplete(root: ViewGroup) {
        runOnMain {
            activeRefreshes[root]?.let { complete(root, it, "system_callback") }
        }
    }

    fun clear() {
        runOnMain {
            mainHandler.removeCallbacksAndMessages(null)
            pendingRequests.clear()
            nativeUpdateMethods.clear()
            activeRefreshes.values.forEach { it.settleRunnable?.let(mainHandler::removeCallbacks) }
            activeRefreshes.clear()
        }
    }

    private fun performPendingRequest() {
        // Snapshot keys only: a synchronous native callback can enqueue newer work. Do not remove
        // that work while draining this batch, nor treat an in-flight measurement as its refresh.
        pendingRequests.keys.toList().forEach { token ->
            if (!isEligibleHost(token)) {
                pendingRequests.remove(token)
                return@forEach
            }
            val existing = activeRefreshes[token.root]
            if (existing != null && existing.token == token) return@forEach
            if (existing != null) {
                existing.settleRunnable?.let(mainHandler::removeCallbacks)
                activeRefreshes.remove(token.root)
            }
            val requests = pendingRequests.remove(token)?.filterKeys { kind ->
                kind != RequestKind.WIDTH || IslandPresentationCoordinator.isPlaybackActive()
            } ?: return@forEach
            if (requests.isEmpty()) return@forEach
            val target = resolveTarget(token)
            if (target == null) {
                requests.values.forEach(::notifyUnavailable)
                return@forEach
            }
            val active = ActiveRefresh(token, requests.values.toList())
            activeRefreshes[token.root] = active
            if (!invokeNativeUpdate(target, active)) {
                if (activeRefreshes[token.root] === active) activeRefreshes.remove(token.root)
                if (isEligibleHost(token)) requests.values.forEach(::notifyUnavailable)
                schedulePendingRequests()
            }
        }
    }

    private fun notifyUnavailable(request: RefreshRequest) {
        runCatching { request.onUnavailable?.invoke() }
            .onFailure { error ->
                HookLogger.e(TAG, "小米原生超级岛刷新回退失败", error)
            }
    }

    private fun isEligibleHost(token: IslandViewRegistry.HostToken): Boolean {
        if (token.kind != IslandViewRegistry.HostKind.REAL || !token.root.isAttachedToWindow ||
            !IslandPresentationCoordinator.isCurrentHost(token)
        ) {
            return false
        }
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(
            IslandProbeUtils.getCurrentIslandData(token.root)
        ) ?: return false
        return mediaInfo.packageName == token.packageName
    }

    private fun resolveTarget(token: IslandViewRegistry.HostToken): NativeTarget? {
        val data = IslandProbeUtils.getCurrentIslandData(token.root) ?: return null
        val key = IslandTextHookerSupport.callNoArgMethodResult(data, "getKey")
            as? String
            ?: return null
        if (key.isEmpty()) return null

        val eventCoordinator = IslandTextHookerSupport.callNoArgMethodResult(
            token.root,
            "getDynamicIslandEventCoordinator"
        ) ?: return null
        val windowView = IslandTextHookerSupport.callNoArgMethodResult(
            eventCoordinator,
            "getWindowView"
        ) ?: return null
        val controller = IslandTextHookerSupport.callNoArgMethodResult(
            windowView,
            "getWindowViewController"
        ) ?: return null
        val maxWidth = (controller.let {
            IslandTextHookerSupport.callNoArgMethodResult(it, "getIslandMaxWidth")
        } as? Number)?.toFloat() ?: return null
        val methods = nativeUpdateMethods.getOrPut(windowView.javaClass) {
            val allMethods = windowView.javaClass.methods
            val matches = allMethods.filter(::isNativeUpdateMethod)
                .sortedByDescending { it.parameterCount }
            if (matches.isEmpty()) {
                val candidates = allMethods
                    .filter { it.name.contains("DynamicIsland", ignoreCase = true) }
                    .joinToString(separator = ";") { method ->
                        "${method.name}(${method.parameterTypes.joinToString(",") { it.name }})"
                    }
                HookLogger.w(
                    TAG,
                    "小米超级岛原生刷新接口不可用: target=updateDynamicIslandView, " +
                            "window=${windowView.javaClass.name}, candidates=$candidates"
                )
            }
            matches
        }
        val updateMethod = methods.firstOrNull { it.parameterTypes[0].isInstance(data) }
            ?: return null

        return NativeTarget(
            root = token.root,
            data = data,
            key = key,
            windowView = windowView,
            updateMethod = updateMethod,
            maxWidth = maxWidth
        )
    }

    private fun invokeNativeUpdate(target: NativeTarget, active: ActiveRefresh): Boolean {
        return runCatching {
            val arguments = when (target.updateMethod.parameterTypes.size) {
                // Plugin 16.5.3.43.0 / 17.0.2.11.1.
                3 -> arrayOf(target.data, false, target.maxWidth)
                // Plugin 17.1.4.26.0 adds the reInflated flag.
                4 -> arrayOf(target.data, false, target.maxWidth, false)
                else -> error("unexpected updateDynamicIslandView signature")
            }
            target.updateMethod.invoke(target.windowView, *arguments)
            active.settleRunnable = Runnable {
                activeRefreshes[target.root]?.let { current ->
                    if (current === active) complete(target.root, current, "settle_timeout")
                }
            }
            if (activeRefreshes[target.root] === active) {
                active.settleRunnable?.let {
                    mainHandler.postDelayed(it, NATIVE_SETTLE_TIMEOUT_MS)
                }
            }
            HookLogger.d(
                TAG,
                "已请求小米原生超级岛刷新: root=${System.identityHashCode(target.root)}, " +
                        "keyHash=${target.key.hashCode()}, maxWidth=${target.maxWidth}, " +
                        "parameterCount=${target.updateMethod.parameterTypes.size}"
            )
            true
        }.getOrElse { error ->
            HookLogger.w(
                TAG,
                "调用小米原生超级岛刷新失败: root=${System.identityHashCode(target.root)}",
                error
            )
            false
        }
    }

    private fun complete(root: ViewGroup, active: ActiveRefresh, reason: String) {
        if (activeRefreshes[root] !== active) return
        activeRefreshes.remove(root)
        active.settleRunnable?.let(mainHandler::removeCallbacks)
        schedulePendingRequests()

        if (!isEligibleHost(active.token)) {
            HookLogger.d(
                TAG,
                "忽略过期的原生超级岛刷新完成: root=${System.identityHashCode(root)}, reason=$reason"
            )
            return
        }

        active.requests.forEach { request ->
            if (!isEligibleHost(active.token)) return@forEach
            runCatching { request.onComplete(root) }
                .onFailure { error ->
                    HookLogger.e(TAG, "原生超级岛刷新后的配置对账失败", error)
                }
        }
    }

    private fun isNativeUpdateMethod(method: Method): Boolean {
        val types = method.parameterTypes
        if (method.name != "updateDynamicIslandView" ||
            method.returnType != Void.TYPE ||
            (types.size != 3 && types.size != 4) ||
            types[0].name != "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData" ||
            types[1] != Boolean::class.javaPrimitiveType ||
            types[2] != Float::class.javaPrimitiveType
        ) {
            return false
        }
        return types.size == 3 || types[3] == Boolean::class.javaPrimitiveType
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class RefreshRequest(
        val onComplete: (ViewGroup) -> Unit,
        val onUnavailable: (() -> Unit)?
    )

    private class ActiveRefresh(
        val token: IslandViewRegistry.HostToken,
        val requests: List<RefreshRequest>,
        var settleRunnable: Runnable? = null
    )

    private data class NativeTarget(
        val root: ViewGroup,
        val data: Any,
        val key: String,
        val windowView: Any,
        val updateMethod: Method,
        val maxWidth: Float
    )
}
