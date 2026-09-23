package com.lidesheng.hyperlyric.root.statusbar

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.ref.WeakReference

/** Tracks the current status-bar roots without extending their lifetime. */
internal object StatusBarLyricHostRegistry {
    private const val TAG = "StatusBarLyricHostRegistry"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val STATUS_BAR_ROOT_ID = "status_bar"
    private const val CLOCK_ID = "clock"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hosts = mutableListOf<WeakReference<StatusBarLyricHost>>()

    fun registerInflatedRoot(root: ViewGroup) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { registerInflatedRoot(root) }
            return
        }

        val resources = root.resources
        val statusBarId = resources.getIdentifier(STATUS_BAR_ROOT_ID, "id", SYSTEM_UI_PACKAGE)
        if (statusBarId == 0 || root.id != statusBarId) return

        val clockId = resources.getIdentifier(CLOCK_ID, "id", SYSTEM_UI_PACKAGE)
        if (clockId == 0) {
            HookLogger.w(TAG, "状态栏歌词宿主未注册: reason=clock_id_unavailable")
            return
        }
        val clock = root.findViewById<View>(clockId)
        val parent = clock?.parent as? ViewGroup
        if (clock == null || parent == null || parent.indexOfChild(clock) < 0) {
            HookLogger.w(TAG, "状态栏歌词宿主未注册: reason=clock_parent_unavailable")
            return
        }
        StatusBarLyricRenderer.ensureKeyguardStateListener(root.context)

        val existing = root.getTag(R.id.hyperlyric_status_bar_lyric_host)
                as? StatusBarLyricHost
        if (existing != null && existing.matches(root, clock, parent)) {
            addIfMissing(existing)
            StatusBarLyricRenderer.onHostRegistered()
            return
        }
        existing?.releaseForHotReload()

        val host = StatusBarLyricHost(root, clock, parent, statusBarId, clockId)
        root.setTag(R.id.hyperlyric_status_bar_lyric_host, host)
        addIfMissing(host)
        StatusBarLyricRenderer.onHostRegistered()
    }

    fun liveHosts(): List<StatusBarLyricHost> {
        val live = hosts.mapNotNull { it.get() }
        hosts.removeAll { it.get() == null }
        return live
    }

    fun enforceManagedClockVisibility(clock: View, systemVisibility: Int? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { enforceManagedClockVisibility(clock, systemVisibility) }
            return
        }
        liveHosts().forEach { host ->
            host.enforceManagedClockVisibility(clock, systemVisibility)
        }
    }

    fun managesClockVisibility(clock: View): Boolean =
        Looper.myLooper() == Looper.getMainLooper() &&
                liveHosts().any { host -> host.managesClockVisibility(clock) }

    fun prepareForHotReload(): List<ViewGroup> {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricHostRegistry.prepareForHotReload must run on the main thread"
        }
        val roots = liveHosts().mapNotNull { host ->
            host.rootView()?.also { host.releaseForHotReload() }
        }
        hosts.clear()
        return roots.distinct()
    }

    fun adoptHotReloadRoots(roots: List<ViewGroup>): Int {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricHostRegistry.adoptHotReloadRoots must run on the main thread"
        }
        roots.forEach(::registerInflatedRoot)
        return roots.count { root ->
            root.getTag(R.id.hyperlyric_status_bar_lyric_host) is StatusBarLyricHost
        }
    }

    fun cleanupForHotReload() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricHostRegistry.cleanupForHotReload must run on the main thread"
        }
        liveHosts().forEach(StatusBarLyricHost::releaseForHotReload)
        hosts.clear()
    }

    private fun addIfMissing(host: StatusBarLyricHost) {
        if (hosts.any { it.get() === host }) return
        hosts += WeakReference(host)
    }
}
