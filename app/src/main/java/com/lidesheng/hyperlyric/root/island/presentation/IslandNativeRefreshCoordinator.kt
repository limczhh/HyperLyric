package com.lidesheng.hyperlyric.root.island.presentation

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.LyriconDataBridge
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
    private var pendingRequest: RefreshRequest? = null
    private val activeRefreshes = WeakHashMap<ViewGroup, ActiveRefresh>()

    private val requestRunnable = Runnable {
        performPendingRequest()
    }

    fun request(
        onComplete: (ViewGroup) -> Unit
    ) {
        runOnMain {
            pendingRequest = RefreshRequest(onComplete)
            mainHandler.removeCallbacks(requestRunnable)
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
            mainHandler.removeCallbacks(requestRunnable)
            pendingRequest = null
            activeRefreshes.values.forEach { it.settleRunnable?.let(mainHandler::removeCallbacks) }
            activeRefreshes.clear()
        }
    }

    private fun performPendingRequest() {
        val request = pendingRequest ?: return
        pendingRequest = null

        val packageName = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return

        var accepted = false
        IslandPresentationCoordinator.snapshotAttachedRealHosts(packageName).forEach { token ->
            if (!isEligibleHost(token)) return@forEach

            val target = resolveTarget(token) ?: return@forEach
            val existing = activeRefreshes[token.root]
            if (existing != null) {
                existing.request = request
                accepted = true
                return@forEach
            }

            val active = ActiveRefresh(token, request)
            activeRefreshes[token.root] = active
            if (invokeNativeUpdate(target, active)) {
                accepted = true
            } else if (activeRefreshes[token.root] === active) {
                activeRefreshes.remove(token.root)
            }
        }

        if (!accepted && activeRefreshes.isEmpty()) {
            HookLogger.d(TAG, "未找到可执行小米原生超级岛刷新的当前媒体岛")
        }
    }

    private fun isEligibleHost(token: IslandViewRegistry.HostToken): Boolean {
        if (!IslandPresentationCoordinator.isCurrentHost(token)) {
            return false
        }
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(
            IslandProbeUtils.getCurrentIslandData(token.root)
        ) ?: return false
        return mediaInfo.packageName == token.packageName &&
                IslandPresentationCoordinator.isCurrentLyricOwner(mediaInfo)
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
        val updateMethod = windowView.javaClass.methods.firstOrNull(::isNativeUpdateMethod)
            ?: run {
                HookLogger.w(TAG, "小米超级岛原生刷新接口不可用: target=updateDynamicIslandView")
                return null
            }

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
            target.updateMethod.invoke(
                target.windowView,
                target.data,
                false,
                target.maxWidth,
                false
            )
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
                        "keyHash=${target.key.hashCode()}, maxWidth=${target.maxWidth}"
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

        if (!IslandPresentationCoordinator.isCurrentHost(active.token) ||
            LyriconDataBridge.currentLyricPackageName != active.token.packageName
        ) {
            HookLogger.d(
                TAG,
                "忽略过期的原生超级岛刷新完成: root=${System.identityHashCode(root)}, reason=$reason"
            )
            return
        }

        runCatching { active.request.onComplete(root) }
            .onFailure { error ->
                HookLogger.e(TAG, "原生超级岛刷新后的配置对账失败", error)
            }
    }

    private fun isNativeUpdateMethod(method: Method): Boolean {
        val types = method.parameterTypes
        return method.name == "updateDynamicIslandView" &&
                types.size == 4 &&
                types[1] == Boolean::class.javaPrimitiveType &&
                types[2] == Float::class.javaPrimitiveType &&
                types[3] == Boolean::class.javaPrimitiveType
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class RefreshRequest(
        val onComplete: (ViewGroup) -> Unit
    )

    private class ActiveRefresh(
        val token: IslandViewRegistry.HostToken,
        var request: RefreshRequest,
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
