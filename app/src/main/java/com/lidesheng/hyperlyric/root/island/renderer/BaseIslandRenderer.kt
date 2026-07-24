package com.lidesheng.hyperlyric.root.island.renderer

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.IslandLyricTextInjector
import com.lidesheng.hyperlyric.root.island.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.IslandProgressGlowController
import com.lidesheng.hyperlyric.root.island.IslandSlotContentAssembler
import com.lidesheng.hyperlyric.root.island.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.IslandViewRegistry
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger

object BaseIslandRenderer : IslandRenderer {

    private const val REFRESH_DEBOUNCE_MS = 32L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { performRefreshActiveIsland() }

    @Volatile
    private var playbackActive = true

    @Volatile
    private var clearedByPause = false

    /**
     * Source lifecycle events are the authority for lyric rendering state.
     * Hook paths must not re-query MediaSession here: during a lyric refresh the source can
     * already be stopped while the player session still reports STATE_PLAYING.
     */
    fun shouldRenderInjectedIsland(): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
            return false
        }
        val behavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )
        return playbackActive || behavior != 0
    }

    override fun refreshActiveIsland() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    fun refreshAlbumColors(
        packageName: String,
        albumArt: Bitmap,
        expectedMediaColorKey: String? = null
    ) {
        if (albumArt.isRecycled) return
        mainHandler.post refresh@{
            if (albumArt.isRecycled ||
                LyriconDataBridge.currentLyricPackageName != packageName ||
                !shouldRenderInjectedIsland()
            ) {
                return@refresh
            }

            val prefs = HookEntry.instance?.prefs ?: return@refresh
            val config = IslandSlotRuntimeConfig.from(prefs)
            IslandViewRegistry.snapshotAttached(packageName).forEach { (rootView, _) ->
                rootView.post updateColors@{
                    if (albumArt.isRecycled ||
                        LyriconDataBridge.currentLyricPackageName != packageName ||
                        !shouldRenderInjectedIsland()
                    ) {
                        return@updateColors
                    }
                    val mediaInfo = MediaMetadataHelper
                        .getMediaInfo(rootView.context, packageName, HookLogger)
                        .copy(albumArt = albumArt)
                    val currentMediaColorKey = CoverColorHelper.resolveMediaKey(
                        packageName = packageName,
                        title = mediaInfo.title,
                        artist = mediaInfo.artist,
                        album = mediaInfo.album,
                        duration = mediaInfo.duration
                    )
                    if (expectedMediaColorKey != null &&
                        currentMediaColorKey != expectedMediaColorKey
                    ) {
                        HookLogger.d(
                            "BaseIslandRenderer",
                            "忽略已过期的原生封面颜色刷新"
                        )
                        return@updateColors
                    }
                    CoverColorHelper.updateMediaSession(
                        packageName = packageName,
                        title = mediaInfo.title,
                        artist = mediaInfo.artist,
                        album = mediaInfo.album,
                        duration = mediaInfo.duration
                    )
                    refreshSlotColors(
                        rootView,
                        IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                        config.leftMode,
                        prefs,
                        config,
                        mediaInfo
                    )
                    refreshSlotColors(
                        rootView,
                        IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                        config.rightMode,
                        prefs,
                        config,
                        mediaInfo
                    )
                    IslandHostFacade.updateProgressGlow(
                        rootView,
                        packageName,
                        mediaInfo,
                        prefs
                    )
                    IslandMusicWaveColorHooker.refresh()
                }
            }
        }
    }

    private fun performRefreshActiveIsland() {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
            clearAllViews()
            return
        }
        if (!shouldRenderInjectedIsland()) {
            clearActiveViewsForPause()
            return
        }

        val lyricPkg = LyriconDataBridge.currentLyricPackageName?.takeIf { it.isNotEmpty() } ?: return

        IslandSlotContentAssembler.invalidate()

        val activeViews = IslandViewRegistry.snapshotAttached(lyricPkg)
        val config = IslandSlotRuntimeConfig.from(prefs)
        activeViews.forEach { (cv, _) ->
            cv.post {
                if (IslandLyricTextInjector.injectSlots(cv)) {
                    IslandHostFacade.triggerSystemRelayout(cv)
                } else {
                    IslandHostFacade.applyHostSettings(cv, prefs)
                }
                updateContentForView(cv, lyricPkg, prefs, config)
            }
        }

        HookLogger.d("BaseIslandRenderer", "已刷新活动媒体岛: 数量=${activeViews.size}")
    }

    override fun updateLyricLine() {
        if ((HookEntry.instance?.prefs?.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) != true) return
        if (!shouldRenderInjectedIsland()) return
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        if (lyricPkg.isNullOrEmpty()) return

        val prefs = HookEntry.instance?.prefs ?: return
        val config = IslandSlotRuntimeConfig.from(prefs)

        IslandViewRegistry.snapshotAttached(lyricPkg)
            .forEach { (cv, _) ->
                cv.post {
                    updateLyricContentForView(cv, prefs, config)
                }
            }
    }

    override fun updatePosition(position: Long) {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) return
        if (!shouldRenderInjectedIsland()) return
        val lyricPkg = LyriconDataBridge.currentLyricPackageName ?: return

        IslandViewRegistry.snapshotAttachedInjectedViews(lyricPkg)
            .forEach { (cv, indexedViews) ->
                cv.post {
                    if (indexedViews.isEmpty()) {
                        setPosition(
                            cv.findViewWithTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG),
                            position
                        )
                        setPosition(
                            cv.findViewWithTag(IslandProbeUtils.RIGHT_TEST_VIEW_TAG),
                            position
                        )
                        IslandViewRegistry.refreshInjectedViews(cv)
                    } else {
                        indexedViews.forEach { view -> setPosition(view, position) }
                    }
                    IslandHostFacade.updateProgressGlow(cv, lyricPkg, prefs)
                }
            }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
            clearAllViews()
            return
        }
        val stateChanged = playbackActive != isPlaying
        playbackActive = isPlaying
        if (stateChanged) {
            IslandProgressGlowController.onPlaybackStateChanged(isPlaying)
        }
        HookLogger.d("BaseIslandRenderer", "播放状态变化: 正在播放=$isPlaying")
        val behavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )

        if (isPlaying) {
            if (clearedByPause) {
                clearedByPause = false
                restoreActiveViewsAfterPause(prefs)
            } else if (stateChanged) {
                applyPlaybackStateToActiveViews(true)
            }
            HookLogger.d("BaseIslandRenderer", "播放已继续，等待进度或歌词事件")
        } else if (behavior == 0) {
            if (!clearedByPause) {
                clearActiveViewsForPause()
                HookLogger.d("BaseIslandRenderer", "已暂停，恢复原生媒体岛")
            } else {
                HookLogger.d("BaseIslandRenderer", "忽略重复暂停状态，原生媒体岛已恢复")
            }
        } else if (stateChanged) {
            applyPlaybackStateToActiveViews(false)
            HookLogger.d("BaseIslandRenderer", "已暂停，保留当前歌词注入")
        }
    }

    private fun restoreActiveViewsAfterPause(prefs: android.content.SharedPreferences) {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val config = IslandSlotRuntimeConfig.from(prefs)
        val activeViews = IslandViewRegistry.snapshotAttached(lyricPkg)
        if (activeViews.isEmpty()) {
            refreshActiveIsland()
            return
        }

        activeViews.forEach { (cv, _) ->
            cv.post {
                if (!shouldRenderInjectedIsland() ||
                    LyriconDataBridge.currentLyricPackageName != lyricPkg
                ) {
                    return@post
                }

                val changed = if (IslandLyricTextInjector.hasInjectedLyricText(cv)) {
                    IslandLyricTextInjector.restoreExistingSlotsLightweight(cv)
                } else {
                    IslandLyricTextInjector.injectSlots(
                        cv,
                        reconfigureExisting = false,
                        suppressAnimation = true
                    )
                }
                val expectsInjectedView = config.leftMode != 0 || config.rightMode != 0
                if (expectsInjectedView && !IslandLyricTextInjector.hasInjectedLyricText(cv)) {
                    refreshActiveIsland()
                    return@post
                }

                setPlaybackActiveRecursively(cv, true)
                if (changed) {
                    IslandHostFacade.triggerSystemRelayout(cv)
                }
            }
        }
    }

    private fun clearActiveViewsForPause() {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        IslandViewRegistry.snapshotAttached()
            .filter { (_, pkgName) -> lyricPkg == null || pkgName == lyricPkg }
            .forEach { (cv, _) ->
                cv.post {
                    IslandHostFacade.clearAndRefresh(cv)
                }
            }
        clearedByPause = true
    }

    private fun applyPlaybackStateToActiveViews(isPlaying: Boolean) {
        val lyricPkg = LyriconDataBridge.currentLyricPackageName
        IslandViewRegistry.snapshotAttachedInjectedViews(lyricPkg)
            .forEach { (cv, indexedViews) ->
                cv.post {
                    if (indexedViews.isEmpty()) {
                        setPlaybackActiveRecursively(cv, isPlaying)
                        IslandViewRegistry.refreshInjectedViews(cv)
                    } else {
                        indexedViews.forEach { view ->
                            setPlaybackActive(view, isPlaying)
                        }
                    }
                }
            }
    }

    private fun setPlaybackActive(view: View, isPlaying: Boolean) {
        when (view) {
            is RichLyricLineView -> view.setPlaybackActive(isPlaying)
            is SpaceGateRichLyricLineView -> view.setPlaybackActive(isPlaying)
        }
    }

    private fun setPosition(view: View?, position: Long) {
        when (view) {
            is RichLyricLineView -> view.setPosition(position)
            is SpaceGateRichLyricLineView -> view.setPosition(position)
        }
    }

    private fun setPlaybackActiveRecursively(view: View, isPlaying: Boolean) {
        when (view) {
            is RichLyricLineView,
            is SpaceGateRichLyricLineView -> setPlaybackActive(view, isPlaying)
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    setPlaybackActiveRecursively(view.getChildAt(index), isPlaying)
                }
            }
        }
    }

    override fun clearAllViews() {
        mainHandler.removeCallbacks(refreshRunnable)
        playbackActive = false
        clearedByPause = true
        IslandViewRegistry.snapshotAttached()
            .forEach { (cv, _) ->
                cv.post {
                    IslandHostFacade.clearAndRefresh(cv)
                }
            }
    }

    private fun updateContentForView(
        cv: ViewGroup,
        packageName: String,
        prefs: android.content.SharedPreferences,
        config: IslandSlotRuntimeConfig
    ) {
        val mediaInfo = MediaMetadataHelper.getMediaInfo(cv.context, packageName, HookLogger)
        IslandHostFacade.updateHostGlow(cv, mediaInfo.albumArt, prefs)
        IslandHostFacade.updateProgressGlow(cv, packageName, mediaInfo, prefs)
        updateSlot(cv, IslandProbeUtils.LEFT_TEST_VIEW_TAG, config.leftMode, prefs, config, mediaInfo)
        updateSlot(cv, IslandProbeUtils.RIGHT_TEST_VIEW_TAG, config.rightMode, prefs, config, mediaInfo)
        IslandMusicWaveColorHooker.refresh()
    }

    private fun updateLyricContentForView(
        cv: ViewGroup,
        prefs: android.content.SharedPreferences,
        config: IslandSlotRuntimeConfig
    ) {
        updateLyricSlot(cv, IslandProbeUtils.LEFT_TEST_VIEW_TAG, config.leftMode, prefs, config)
        updateLyricSlot(cv, IslandProbeUtils.RIGHT_TEST_VIEW_TAG, config.rightMode, prefs, config)
    }

    private fun updateLyricSlot(
        cv: ViewGroup,
        tag: String,
        mode: Int,
        prefs: android.content.SharedPreferences,
        config: IslandSlotRuntimeConfig
    ) {
        if (mode != 7) return
        val view = cv.findViewWithTag<View>(tag) ?: return
        val line = IslandSlotContentAssembler.buildSlotLyricLine(
            view = view,
            prefs = prefs,
            config = config,
            isLeft = tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
        )
        IslandSlotContentAssembler.applyLyricLineContent(
            view = view,
            prefs = prefs,
            config = config,
            lineOverride = line,
            playbackActive = playbackActive
        )
    }

    private fun updateSlot(
        cv: ViewGroup,
        tag: String,
        mode: Int,
        prefs: android.content.SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ) {
        if (mode == 0) return
        val view = cv.findViewWithTag<View>(tag) ?: return
        val lineOverride = if (mode == 7) {
            IslandSlotContentAssembler.buildSlotLyricLine(
                view = view,
                prefs = prefs,
                config = config,
                isLeft = tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
            )
        } else {
            null
        }
        IslandSlotContentAssembler.applySlotContent(
            view = view,
            prefs = prefs,
            config = config,
            mode = mode,
            lineOverride = lineOverride,
            playbackActive = playbackActive,
            mediaInfo = mediaInfo
        )
    }

    private fun refreshSlotColors(
        rootView: ViewGroup,
        tag: String,
        mode: Int,
        prefs: android.content.SharedPreferences,
        config: IslandSlotRuntimeConfig,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ) {
        if (mode == 0) return
        val view = rootView.findViewWithTag<View>(tag) ?: return
        IslandSlotContentAssembler.configureView(
            view = view,
            prefs = prefs,
            config = config,
            mode = mode,
            mediaInfo = mediaInfo,
            force = true
        )
    }

}
