package com.lidesheng.hyperlyric.root.island.renderer

import android.content.Context
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
import com.lidesheng.hyperlyric.root.island.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.IslandLyricTextInjector
import com.lidesheng.hyperlyric.root.island.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.IslandSlotContentAssembler
import com.lidesheng.hyperlyric.root.island.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.IslandViewRegistry
import com.lidesheng.hyperlyric.root.utils.HookLogger

object BaseIslandRenderer : IslandRenderer {

    private const val REFRESH_DEBOUNCE_MS = 32L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { performRefreshActiveIsland() }

    @Volatile
    private var playbackActive = true

    @Volatile
    private var clearedByPause = false

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

    fun shouldRenderInjectedIsland(context: Context, packageName: String): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
            return false
        }
        val behavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )
        if (behavior != 0) {
            return true
        }
        return MediaMetadataHelper.isPackagePlaying(context, packageName)
    }

    override fun refreshActiveIsland() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_DEBOUNCE_MS)
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

        IslandViewRegistry.snapshotAttached(lyricPkg)
            .forEach { (cv, _) ->
                cv.post {
                    val leftView = cv.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG)
                    val rightView = cv.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
                    (leftView as? RichLyricLineView)?.setPosition(position)
                        ?: (leftView as? SpaceGateRichLyricLineView)?.setPosition(position)
                    (rightView as? RichLyricLineView)?.setPosition(position)
                        ?: (rightView as? SpaceGateRichLyricLineView)?.setPosition(position)
                    IslandHostFacade.updateProgressGlow(cv, lyricPkg, prefs)
                }
            }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        IslandAlbumCoverStyleHooker.onPlaybackStateChanged(isPlaying)
        val prefs = HookEntry.instance?.prefs ?: return
        if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) {
            clearAllViews()
            return
        }
        playbackActive = isPlaying
        HookLogger.d("BaseIslandRenderer", "播放状态变化: 正在播放=$isPlaying")
        val behavior = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )

        if (isPlaying) {
            if (clearedByPause) {
                clearedByPause = false
                refreshActiveIsland()
            } else {
                applyPlaybackStateToActiveViews(true)
            }
                HookLogger.d("BaseIslandRenderer", "播放已继续，等待进度或歌词事件")
        } else if (behavior == 0) {
            clearActiveViewsForPause()
                HookLogger.d("BaseIslandRenderer", "已暂停，恢复原生媒体岛")
        } else {
            applyPlaybackStateToActiveViews(false)
                HookLogger.d("BaseIslandRenderer", "已暂停，保留当前歌词注入")
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
        IslandViewRegistry.snapshotAttached()
            .forEach { (cv, pkgName) ->
                if (lyricPkg == null || pkgName == lyricPkg) {
                    cv.post {
                        setPlaybackActive(cv.findViewWithTag(IslandProbeUtils.LEFT_TEST_VIEW_TAG), isPlaying)
                        setPlaybackActive(cv.findViewWithTag(IslandProbeUtils.RIGHT_TEST_VIEW_TAG), isPlaying)
                    }
                }
            }
    }

    private fun setPlaybackActive(view: View?, isPlaying: Boolean) {
        when (view) {
            is RichLyricLineView -> view.setPlaybackActive(isPlaying)
            is SpaceGateRichLyricLineView -> view.setPlaybackActive(isPlaying)
        }
    }

    override fun clearAllViews() {
        mainHandler.removeCallbacks(refreshRunnable)
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
        IslandHostFacade.updateMusicWaveColors(cv, packageName, mediaInfo, prefs)
        IslandHostFacade.updateHostGlow(cv, mediaInfo.albumArt, prefs)
        IslandHostFacade.updateProgressGlow(cv, packageName, mediaInfo, prefs)
        updateSlot(cv, IslandProbeUtils.LEFT_TEST_VIEW_TAG, config.leftMode, prefs, config, mediaInfo)
        updateSlot(cv, IslandProbeUtils.RIGHT_TEST_VIEW_TAG, config.rightMode, prefs, config, mediaInfo)
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
}
