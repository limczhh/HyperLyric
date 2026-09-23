package com.lidesheng.hyperlyric.root.statusbar

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
    private val restoreClockHideRunnable = Runnable {
        clockTemporarilyRevealed = false
        refreshClockVisibility(HookEntry.instance?.prefs)
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
                refreshIslandSuppressionPolicy(null)
                clearOnMain()
                return@dispatchLatest
            }
            refreshClockVisibility(prefs)
            refreshIslandSuppressionPolicy(prefs)
            StatusBarLyricIslandRegionDispatcher.updateAdjustmentEnabled(
                prefs.getBoolean(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                )
            )
            if (!shouldRender(prefs)) {
                renderingEnabled = false
                clearOnMain()
                return@dispatchLatest
            }
            renderingEnabled = true
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.render(prefs, force = force)
            }
            refreshClockVisibility(prefs)
            publishRenderedLyricState()
        }
    }

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
                mainHandler.postDelayed(restoreClockHideRunnable, TEMPORARY_CLOCK_REVEAL_MS)
            }
            refreshClockVisibility(HookEntry.instance?.prefs)
        }
    }

    fun updateTextColors() {
        runOnMain {
            val prefs = HookEntry.instance?.prefs ?: return@runOnMain
            if (!prefs.getBoolean(
                    StatusBarLyricPreferences.KEY_ENABLED,
                    StatusBarLyricPreferences.DEFAULT_ENABLED,
                )
            ) return@runOnMain
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.refreshTextStyle(prefs)
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
        val prefs = HookEntry.instance?.prefs
        refreshClockVisibility(prefs)
        refreshIslandSuppressionPolicy(prefs)
        if (prefs == null || !prefs.getBoolean(
                StatusBarLyricPreferences.KEY_ENABLED,
                StatusBarLyricPreferences.DEFAULT_ENABLED,
            )
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
        renderingEnabled = false
        refreshIslandSuppressionPolicy(HookEntry.instance?.prefs)
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        val generation = renderGeneration.incrementAndGet()
        runOnMain {
            if (generation != renderGeneration.get()) return@runOnMain
            StatusBarLyricHostRegistry.liveHosts().forEach { it.setClockHidden(false) }
            clearOnMain()
        }
    }

    fun prepareForHotReload(): List<ViewGroup> {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "StatusBarLyricRenderer.prepareForHotReload must run on the main thread"
        }
        renderGeneration.incrementAndGet()
        renderingEnabled = false
        clearTemporaryClockReveal()
        StatusBarLyricMediaIslandCoordinator.updateSuppressionPolicy(
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
            null,
        )
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        StatusBarLyricHostRegistry.liveHosts().forEach { it.setClockHidden(false) }
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
        clearTemporaryClockReveal()
        StatusBarLyricMediaIslandCoordinator.updateSuppressionPolicy(
            RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
            null,
        )
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        StatusBarLyricHostRegistry.liveHosts().forEach { it.setClockHidden(false) }
        removeKeyguardStateListener()
        StatusBarLyricHostRegistry.cleanupForHotReload()
    }

    private fun shouldRender(prefs: android.content.SharedPreferences): Boolean {
        val hasLyrics = LyriconDataBridge.hasLyricsForPresentation()
        val hasPlaceholder = !hasLyrics &&
                LyriconDataBridge.hasNoLyricsPlaceholderForPresentation()
        if (!prefs.getBoolean(
                StatusBarLyricPreferences.KEY_ENABLED,
                StatusBarLyricPreferences.DEFAULT_ENABLED,
            ) ||
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
            val enabled = prefs?.getBoolean(
                StatusBarLyricPreferences.KEY_ENABLED,
                StatusBarLyricPreferences.DEFAULT_ENABLED,
            ) == true
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
                    LyriconDataBridge.hasLyricsForPresentation()

                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT ->
                    StatusBarLyricIslandRegionDispatcher.hasIsland()

                else -> false
            }
            StatusBarLyricHostRegistry.liveHosts().forEach { host ->
                host.setClockHidden(shouldHide)
            }
        }
    }

    private fun refreshIslandSuppressionPolicy(prefs: android.content.SharedPreferences?) {
        val enabled = prefs?.getBoolean(
            StatusBarLyricPreferences.KEY_ENABLED,
            StatusBarLyricPreferences.DEFAULT_ENABLED,
        ) == true
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
        val hasLyrics = enabled && LyriconDataBridge.hasLyricsForPresentation()
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

    private fun clearTemporaryClockReveal() {
        mainHandler.removeCallbacks(restoreClockHideRunnable)
        clockTemporarilyRevealed = false
    }

    private const val TEMPORARY_CLOCK_REVEAL_MS = 5_000L
}
