package com.lidesheng.hyperlyric.root.statusbar

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.LyricTypePreference
import com.lidesheng.hyperlyric.common.LyricTextColorStylePolicy
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.hooks.StatusBarLyricMediaIslandCoordinator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.concurrent.atomic.AtomicLong

/** Renders the shared rich lyric presentation into Clock-anchored status-bar roots. */
internal object StatusBarLyricRenderer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderGeneration = AtomicLong(0L)
    private val preferenceRefresh = Runnable { renderLyricLine(force = true) }
    private var clockTemporarilyRevealed = false
    private var lyricTemporarilyHidden = false
    private val playbackStateLock = Any()
    private var lastPlaybackStateIsPlaying = false
    private val restoreClockHideRunnable = Runnable {
        clockTemporarilyRevealed = false
        refreshClockVisibility(HookEntry.instance?.prefs)
    }
    private val restoreTemporaryLyricHideRunnable = Runnable {
        lyricTemporarilyHidden = false
        refreshTemporaryLyricVisibility()
    }
    @Volatile
    private var renderingEnabled = false
    @Volatile
    private var keyguardLocked = false
    private var keyguardManager: KeyguardManager? = null
    private var keyguardStateListener: KeyguardManager.KeyguardLockedStateListener? = null

    fun updateLyricLine() {
        renderLyricLine(force = false)
    }

    private fun renderLyricLine(force: Boolean) {
        dispatchLatest {
            val prefs = HookEntry.instance?.prefs
            if (prefs == null) {
                renderingEnabled = false
                refreshClockVisibility(null)
                refreshNotificationIconVisibility(null)
                refreshIslandSuppressionPolicy(null)
                clearOnMain()
                return@dispatchLatest
            }
            StatusBarLyricIslandRegionDispatcher.updateAdjustmentEnabled(
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                )
            )
            if (!shouldRender(prefs)) {
                renderingEnabled = false
                // 本轮没有任何自有内容可展示，必须交还时钟/通知图标/灵动岛，
                // 否则"播放时隐藏"会把系统元素一起藏掉，留下整条空白的状态栏。
                refreshClockVisibility(null)
                refreshNotificationIconVisibility(null)
                refreshIslandSuppressionPolicy(null)
                clearOnMain()
                return@dispatchLatest
            }
            renderingEnabled = true
            refreshClockVisibility(prefs)
            refreshNotificationIconVisibility(prefs)
            refreshIslandSuppressionPolicy(prefs)
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.setLyricTemporarilyHidden(lyricTemporarilyHidden)
                host.render(prefs, force = force)
            }
            refreshClockVisibility(prefs)
            publishRenderedLyricState()
        }
    }

    /**
     * 只有模块确实在渲染自有内容时，才有资格隐藏时钟、通知图标与灵动岛。
     * 单看设置项或数据桥的内容判定都不够：本方法还要跟 [shouldRender] 的结论保持一致，
     * 否则锁屏隐藏、暂停策略等分支会让系统元素被藏掉而自有视图是空的。
     */
    private fun isOwningStatusBarContent(): Boolean =
        renderingEnabled && LyriconDataBridge.isPresentingManagedContent()

    fun ensureKeyguardStateListener(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { ensureKeyguardStateListener(context) }
            return
        }
        if (keyguardStateListener != null) return

        val manager = runCatching {
            context.getSystemService(KeyguardManager::class.java)
        }.getOrNull() ?: run {
            HookLogger.w("StatusBarLyricRenderer", "锁屏状态监听未注册: reason=keyguard_service_unavailable")
            return
        }
        keyguardManager = manager
        keyguardLocked = runCatching { manager.isKeyguardLocked }.getOrDefault(false)
        val listener = KeyguardManager.KeyguardLockedStateListener { locked ->
            if (keyguardLocked != locked) {
                keyguardLocked = locked
                updateLyricLine()
            }
        }
        runCatching {
            manager.addKeyguardLockedStateListener(context.mainExecutor, listener)
        }.onSuccess {
            keyguardStateListener = listener
        }.onFailure { error ->
            HookLogger.w(
                "StatusBarLyricRenderer",
                "锁屏状态监听未注册: reason=${error.message}"
            )
        }
    }

    fun onHostSizeChanged() {
        renderLyricLine(force = true)
    }

    fun onIslandRegionChanged() {
        refreshHostWidths()
    }

    fun onIslandPresenceChanged() {
        refreshClockVisibility(HookEntry.instance?.prefs)
    }

    /** Re-evaluates the Clock policy after SystemUI finishes its native visibility calculation. */
    fun onClockVisibilityUpdated(clock: View, systemVisibility: Int?) = runOnMain {
        if (StatusBarLyricHostRegistry.managesClockVisibility(clock)) {
            // Preserve the visibility requested by SystemUI before refreshClockVisibility may
            // release our Clock ownership and restore that value.
            StatusBarLyricHostRegistry.enforceManagedClockVisibility(clock, systemVisibility)
        }
        refreshClockVisibility(HookEntry.instance?.prefs)
    }

    fun onHostRegistered() {
        renderLyricLine(force = true)
    }

    fun onHostGeometryChanged() {
        refreshHostWidths()
    }

    fun updateMetadata() = updateLyricLine()

    fun onPreferenceChanged() {
        mainHandler.removeCallbacks(preferenceRefresh)
        mainHandler.post(preferenceRefresh)
    }

    /** Toggles a five-second reveal while keeping the configured hide policy intact. */
    fun toggleTemporaryClockReveal() {
        runOnMain {
            mainHandler.removeCallbacks(restoreClockHideRunnable)
            clockTemporarilyRevealed = !clockTemporarilyRevealed
            if (clockTemporarilyRevealed) {
                mainHandler.postDelayed(restoreClockHideRunnable, TEMPORARY_GESTURE_VISIBILITY_MS)
            }
            refreshClockVisibility(HookEntry.instance?.prefs)
        }
    }

    /** Toggles a five-second lyric hide; triggering it again restores the lyric immediately. */
    fun toggleTemporaryLyricHide() {
        runOnMain {
            mainHandler.removeCallbacks(restoreTemporaryLyricHideRunnable)
            lyricTemporarilyHidden = !lyricTemporarilyHidden
            if (lyricTemporarilyHidden) {
                mainHandler.postDelayed(
                    restoreTemporaryLyricHideRunnable,
                    TEMPORARY_GESTURE_VISIBILITY_MS,
                )
            }
            refreshTemporaryLyricVisibility()
        }
    }

    fun updateTextColors() {
        runOnMain {
            val prefs = HookEntry.instance?.prefs ?: return@runOnMain
            if (!LyricTypePreference.isEnabled(prefs, RootConstants.LYRIC_TYPE_STATUS_BAR)) {
                return@runOnMain
            }
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.refreshTextStyle(prefs)
            }
        }
    }

    /** Refreshes cover palettes only after SystemUI settles on the opposite status-bar tint mode. */
    fun updateCoverPaletteForBackgroundChange() {
        runOnMain {
            val prefs = HookEntry.instance?.prefs ?: return@runOnMain
            if (!LyricTypePreference.isEnabled(prefs, RootConstants.LYRIC_TYPE_STATUS_BAR)) {
                return@runOnMain
            }
            val lyricPrefs = StatusBarLyricPreferences.scoped(prefs)
            val style = StatusBarLyricPreferences.effectiveStatusBarTextColorStyle(lyricPrefs)
            if (!LyricTextColorStylePolicy.usesCoverColor(style)) return@runOnMain

            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.refreshTextStyle(prefs, forceStyle = false)
            }
        }
    }

    fun updatePosition(position: Long, playbackSpeed: Float) {
        if (!renderingEnabled) return
        runOnMain {
            if (!renderingEnabled) return@runOnMain
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.updatePlaybackPosition(position, playbackSpeed)
            }
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        val resumed = synchronized(playbackStateLock) {
            val wasPlaying = lastPlaybackStateIsPlaying
            lastPlaybackStateIsPlaying = isPlaying
            isPlaying && !wasPlaying
        }
        if (resumed) resetTemporaryLyricHide()

        val prefs = HookEntry.instance?.prefs
        refreshClockVisibility(prefs)
        refreshNotificationIconVisibility(prefs)
        refreshIslandSuppressionPolicy(prefs)
        if (prefs == null ||
            !LyricTypePreference.isEnabled(prefs, RootConstants.LYRIC_TYPE_STATUS_BAR)
        ) {
            clearAllViews()
            return
        }
        val pauseBehavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
        )
        if (!isPlaying && pauseBehavior == 0) {
            clearAllViews()
        } else {
            updateLyricLine()
        }
    }

    fun clearAllViews() {
        resetPlaybackState()
        resetTemporaryLyricHide()
        renderingEnabled = false
        refreshIslandSuppressionPolicy(HookEntry.instance?.prefs)
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        val generation = renderGeneration.incrementAndGet()
        runOnMain {
            if (generation != renderGeneration.get()) return@runOnMain
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.setClockHidden(false)
                host.setNotificationIconContainerHidden(false)
                host.setNotificationIconContainerMovedRight(false)
            }
            clearOnMain()
        }
    }

    fun prepareForHotReload(): List<ViewGroup> {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricRenderer.prepareForHotReload must run on the main thread"
        }
        renderGeneration.incrementAndGet()
        renderingEnabled = false
        clearTemporaryGestureOverrides()
        StatusBarLyricMediaIslandCoordinator.updateSuppressionPolicy(
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
            null,
        )
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        StatusBarLyricHostRegistry.liveHosts().forEach { host ->
            host.setClockHidden(false)
            host.setNotificationIconContainerHidden(false)
            host.setNotificationIconContainerMovedRight(false)
        }
        removeKeyguardStateListener()
        return StatusBarLyricHostRegistry.prepareForHotReload()
    }

    fun adoptHotReloadRoots(roots: List<ViewGroup>): Int {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricRenderer.adoptHotReloadRoots must run on the main thread"
        }
        val adopted = StatusBarLyricHostRegistry.adoptHotReloadRoots(roots)
        updateLyricLine()
        return adopted
    }

    fun cleanupForHotReload() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricRenderer.cleanupForHotReload must run on the main thread"
        }
        renderGeneration.incrementAndGet()
        renderingEnabled = false
        clearTemporaryGestureOverrides()
        StatusBarLyricMediaIslandCoordinator.updateSuppressionPolicy(
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
            null,
        )
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        StatusBarLyricHostRegistry.liveHosts().forEach { host ->
            host.setClockHidden(false)
            host.setNotificationIconContainerHidden(false)
            host.setNotificationIconContainerMovedRight(false)
        }
        removeKeyguardStateListener()
        StatusBarLyricHostRegistry.cleanupForHotReload()
    }

    private fun shouldRender(prefs: android.content.SharedPreferences): Boolean {
        val hasLyrics = LyriconDataBridge.hasLyricsForPresentation()
        val hasPlaceholder = !hasLyrics &&
                LyriconDataBridge.hasNoLyricsPlaceholderForPresentation()
        if (!LyricTypePreference.isEnabled(prefs, RootConstants.LYRIC_TYPE_STATUS_BAR) ||
            (!hasLyrics && !hasPlaceholder)
        ) return false
        if (prefs.getBoolean(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN,
            ) && isKeyguardLocked()
        ) return false
        val isPlaying = LyriconDataBridge.isPlaybackActive()
        val pauseBehavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
        )
        return isPlaying || pauseBehavior != 0
    }

    private fun isKeyguardLocked(): Boolean =
        runCatching { keyguardManager?.isKeyguardLocked ?: keyguardLocked }
            .getOrDefault(keyguardLocked)

    private fun removeKeyguardStateListener() {
        val manager = keyguardManager
        val listener = keyguardStateListener
        if (manager != null && listener != null) {
            runCatching { manager.removeKeyguardLockedStateListener(listener) }
        }
        keyguardStateListener = null
        keyguardManager = null
        keyguardLocked = false
    }

    private fun clearOnMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::clearOnMain)
            return
        }
        StatusBarLyricHostRegistry.liveHosts().forEach(StatusBarLyricHost::clearLyrics)
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
    }

    private fun refreshClockVisibility(prefs: android.content.SharedPreferences?) {
        runOnMain {
            val enabled = prefs != null && LyricTypePreference.isEnabled(
                prefs,
                RootConstants.LYRIC_TYPE_STATUS_BAR,
            )
            val behavior = prefs?.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
            )?.coerceIn(
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_NONE,
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT,
            ) ?: RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_NONE
            val shouldHide = !clockTemporarilyRevealed && enabled &&
                    LyriconDataBridge.isPlaybackActive() && when (behavior) {
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING ->
                    isOwningStatusBarContent() && !lyricTemporarilyHidden

                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT ->
                    StatusBarLyricIslandRegionDispatcher.hasIsland()

                else -> false
            }
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.setClockHidden(shouldHide)
            }
        }
    }

    private fun refreshNotificationIconVisibility(prefs: android.content.SharedPreferences?) {
        runOnMain {
            val enabled = prefs != null && LyricTypePreference.isEnabled(
                prefs,
                RootConstants.LYRIC_TYPE_STATUS_BAR,
            )
            val behavior = prefs?.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_BEHAVIOR,
            )?.coerceIn(
                RootConstants.STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_NONE,
                RootConstants.STATUS_BAR_LYRIC_NOTIFICATION_ICON_MOVE_RIGHT_WHILE_PLAYING,
            ) ?: RootConstants.STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_NONE
            val active = enabled &&
                    LyriconDataBridge.isPlaybackActive() &&
                    isOwningStatusBarContent() &&
                    !lyricTemporarilyHidden
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.setNotificationIconContainerHidden(active &&
                    behavior == RootConstants.STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_WHILE_PLAYING)
                host.setNotificationIconContainerMovedRight(active &&
                    behavior == RootConstants.STATUS_BAR_LYRIC_NOTIFICATION_ICON_MOVE_RIGHT_WHILE_PLAYING)
            }
        }
    }

    private fun refreshIslandSuppressionPolicy(prefs: android.content.SharedPreferences?) {
        val enabled = prefs != null && LyricTypePreference.isEnabled(
            prefs,
            RootConstants.LYRIC_TYPE_STATUS_BAR,
        )
        val behavior = if (enabled) {
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
                RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS,
            )
        } else {
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE
        }
        val hasLyrics = enabled && isOwningStatusBarContent()
        val shouldOwnIsland = hasLyrics && when (behavior) {
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_WHILE_PLAYING ->
                LyriconDataBridge.isPlaybackActive()

            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS -> true
            else -> false
        }
        val ownerPackageName = if (shouldOwnIsland) {
            LyriconDataBridge.currentLyricPackageName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        } else {
            null
        }
        StatusBarLyricMediaIslandCoordinator.updateSuppressionPolicy(
            behavior = behavior,
            ownerPackageName = ownerPackageName,
        )
    }

    private fun publishRenderedLyricState() {
        val lyricsVisible = renderingEnabled &&
                StatusBarLyricHostRegistry.liveHosts().any(StatusBarLyricHost::isShowingLyric)
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(lyricsVisible)
    }

    private fun refreshHostWidths() {
        runOnMain {
            if (!renderingEnabled) return@runOnMain
            StatusBarLyricHostRegistry.liveHosts().forEach(StatusBarLyricHost::refreshIslandWidth)
        }
    }

    private fun dispatchLatest(block: () -> Unit) {
        val generation = renderGeneration.incrementAndGet()
        runOnMain {
            if (generation == renderGeneration.get()) block()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun refreshTemporaryLyricVisibility() {
        val hidden = lyricTemporarilyHidden
        StatusBarLyricHostRegistry.liveHosts().forEach { host ->
            host.setLyricTemporarilyHidden(hidden)
        }
        publishRenderedLyricState()
        refreshClockVisibility(HookEntry.instance?.prefs)
        refreshNotificationIconVisibility(HookEntry.instance?.prefs)
    }

    private fun clearTemporaryGestureOverrides() {
        mainHandler.removeCallbacks(restoreClockHideRunnable)
        mainHandler.removeCallbacks(restoreTemporaryLyricHideRunnable)
        clockTemporarilyRevealed = false
        lyricTemporarilyHidden = false
        resetPlaybackState()
        StatusBarLyricHostRegistry.liveHosts().forEach { host ->
            host.setLyricTemporarilyHidden(false)
        }
    }

    private fun resetTemporaryLyricHide() {
        runOnMain {
            mainHandler.removeCallbacks(restoreTemporaryLyricHideRunnable)
            lyricTemporarilyHidden = false
        }
    }

    private fun resetPlaybackState() {
        synchronized(playbackStateLock) {
            lastPlaybackStateIsPlaying = false
        }
    }

    private const val TEMPORARY_GESTURE_VISIBILITY_MS = 5_000L
}
