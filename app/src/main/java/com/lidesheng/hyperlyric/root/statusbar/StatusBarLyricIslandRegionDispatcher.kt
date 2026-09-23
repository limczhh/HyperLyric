package com.lidesheng.hyperlyric.root.statusbar

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.lidesheng.hyperlyric.root.utils.HookLogger

/** Coalesces the native SystemUI island-region stream into width-only lyric host updates. */
internal object StatusBarLyricIslandRegionDispatcher {
    data class IslandBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var islandBounds: IslandBounds? = null

    @Volatile
    private var adjustmentEnabled = false

    @Volatile
    private var lyricsVisible = false

    private var widthRefreshPending = false
    private var clockRefreshPending = false

    private val widthRefreshFrame = Choreographer.FrameCallback {
        widthRefreshPending = false
        StatusBarLyricRenderer.onIslandRegionChanged()
    }

    private val clockRefreshFrame = Choreographer.FrameCallback {
        clockRefreshPending = false
        StatusBarLyricRenderer.onIslandPresenceChanged()
    }

    fun updateAdjustmentEnabled(enabled: Boolean) = runOnMain {
        if (adjustmentEnabled == enabled) return@runOnMain
        adjustmentEnabled = enabled
        HookLogger.d(TAG, "原生超级岛宽度避让开关: enabled=$enabled")
        if (lyricsVisible) scheduleWidthRefresh()
    }

    fun updateLyricsVisible(visible: Boolean) = runOnMain {
        if (lyricsVisible == visible) return@runOnMain
        lyricsVisible = visible
        if (visible) scheduleWidthRefresh()
    }

    /** Receives the committed bounds from SystemUI's StatusBarIslandControllerImpl. */
    fun onNativeIslandRegionChanged(rect: Rect?) {
        val snapshot = rect
            ?.takeUnless(Rect::isEmpty)
            ?.let { IslandBounds(it.left, it.top, it.right, it.bottom) }
        runOnMain {
            val previous = islandBounds
            // Visibility may change while SystemUI keeps the same Rect, so refresh the Clock
            // policy for every committed region update, not only geometry transitions.
            scheduleClockVisibilityRefresh()
            if (previous == snapshot) return@runOnMain
            islandBounds = snapshot
            if ((previous == null) != (snapshot == null)) {
                HookLogger.d(TAG, "SystemUI 原生超级岛状态变更: visible=${snapshot != null}")
            }
            if (adjustmentEnabled && lyricsVisible) scheduleWidthRefresh()
        }
    }

    fun visibleIslandBounds(): IslandBounds? =
        if (adjustmentEnabled && lyricsVisible) islandBounds else null

    fun hasIsland(): Boolean = islandBounds != null

    private fun scheduleWidthRefresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::scheduleWidthRefresh)
            return
        }
        if (widthRefreshPending) return
        widthRefreshPending = true
        Choreographer.getInstance().postFrameCallback(widthRefreshFrame)
    }

    private fun scheduleClockVisibilityRefresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::scheduleClockVisibilityRefresh)
            return
        }
        if (clockRefreshPending) return
        clockRefreshPending = true
        Choreographer.getInstance().postFrameCallback(clockRefreshFrame)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private const val TAG = "StatusBarLyricIslandRegion"
}
