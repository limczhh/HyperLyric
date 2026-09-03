package com.lidesheng.hyperlyric.root.island.sizing

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.host.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.view.MaxWidthFrameLayout
import java.util.WeakHashMap

/**
 * Owns the root-scoped state and UI scheduling for dynamic slot width updates.
 *
 * The actual width math stays in [IslandLyricWidthCalculator]. This coordinator only reads the
 * current lyric View state, retains preflight candidates until the line is committed, and asks
 * the SystemUI host to relayout after a wrapper width changes.
 */
internal object IslandDynamicWidthCoordinator {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshPending = WeakHashMap<ViewGroup, Boolean>()
    private val relayoutPending = WeakHashMap<ViewGroup, Boolean>()
    private val preflightTargets = WeakHashMap<ViewGroup, MutableMap<String, Float>>()
    private val metadataContentWidths = WeakHashMap<ViewGroup, MutableMap<String, Float>>()

    fun requestRefresh(rootView: ViewGroup) {
        val hostToken = IslandViewRegistry.tokenFor(rootView) ?: return
        val shouldPost = synchronized(refreshPending) {
            if (refreshPending[rootView] == true) {
                false
            } else {
                refreshPending[rootView] = true
                true
            }
        }
        if (!shouldPost) return

        mainHandler.post {
            synchronized(refreshPending) {
                refreshPending.remove(rootView)
            }
            if (!rootView.isAttachedToWindow ||
                !IslandPresentationCoordinator.isCurrentHost(hostToken)
            ) return@post
            if (!IslandPresentationCoordinator.isPlaybackActive()) return@post
            val prefs = HookEntry.instance?.prefs ?: return@post
            val config = IslandSlotRuntimeConfig.from(prefs)
            if (!config.geometry.isDynamicWidth) return@post
            if (refreshDynamicSlotWidths(rootView, config)) {
                scheduleSystemRelayout(rootView, hostToken)
            }
        }
    }

    /**
     * Applies the candidate line width before the candidate line is committed to the lyric View.
     * Candidates remain available until the corresponding line-application callback clears them.
     */
    fun prepareLyricWidth(
        rootView: ViewGroup,
        viewTag: String,
        contentWidthPx: Float
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)
        if (!config.geometry.isDynamicWidth ||
            config.modeForTag(viewTag) != RootConstants.ISLAND_CONTENT_MODE_LYRIC
        ) return false
        if (!IslandPresentationCoordinator.isPlaybackActive()) return false
        val hostToken = IslandViewRegistry.tokenFor(rootView) ?: return false
        if (!rootView.isAttachedToWindow ||
            !IslandPresentationCoordinator.isCurrentHost(hostToken)
        ) return false

        val overrides = synchronized(preflightTargets) {
            val rootTargets = preflightTargets.getOrPut(rootView) { hashMapOf() }
            rootTargets[viewTag] = contentWidthPx
            rootTargets.toMap()
        }
        val changed = refreshDynamicSlotWidths(rootView, config, overrides)
        if (changed) {
            scheduleSystemRelayout(rootView, hostToken)
        }
        return changed
    }

    /**
     * Stores the currently rendered music-info width for later lyric refreshes.
     * Music metadata normally changes only when the media item changes, while lyric lines can
     * change frequently; keeping this value per host avoids re-reading the metadata view on every
     * lyric update.
     */
    fun cacheMetadataWidth(rootView: ViewGroup, viewTag: String): Boolean {
        val view = rootView.findViewWithTag<View>(viewTag) ?: return false
        val contentWidthPx = metadataContentWidthPx(view) ?: return false
        return synchronized(metadataContentWidths) {
            val rootWidths = metadataContentWidths.getOrPut(rootView) { hashMapOf() }
            if (rootWidths[viewTag] == contentWidthPx) {
                false
            } else {
                rootWidths[viewTag] = contentWidthPx
                true
            }
        }
    }

    fun clearPreflight(rootView: ViewGroup, viewTag: String) {
        synchronized(preflightTargets) {
            val rootTargets = preflightTargets[rootView] ?: return
            rootTargets.remove(viewTag)
            if (rootTargets.isEmpty()) {
                preflightTargets.remove(rootView)
            }
        }
    }

    private fun refreshDynamicSlotWidths(
        rootView: ViewGroup,
        config: IslandSlotRuntimeConfig,
        contentWidthOverrides: Map<String, Float> = emptyMap()
    ): Boolean {
        if (!config.geometry.isDynamicWidth) return false

        val lyricOnly = config.dynamicWidthBasis ==
                RootConstants.ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY
        val slotBaseWidthDp = listOf(
            dynamicSlotBaseWidthDp(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config,
                contentWidthOverrides[IslandProbeUtils.LEFT_TEST_VIEW_TAG],
                lyricOnly
            ),
            dynamicSlotBaseWidthDp(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config,
                contentWidthOverrides[IslandProbeUtils.RIGHT_TEST_VIEW_TAG],
                lyricOnly
            )
        ).filterNotNull().maxOrNull() ?: return false
        val baseWidthDp = slotBaseWidthDp.coerceIn(
            config.geometry.rightMinWidthDp.toFloat(),
            config.geometry.rightMaxWidthDp.toFloat()
        )

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = updateDynamicSlotWidth(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config,
                baseWidthDp
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = updateDynamicSlotWidth(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config,
                baseWidthDp
            ) || changed
        }
        return changed
    }

    private fun dynamicSlotBaseWidthDp(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String,
        config: IslandSlotRuntimeConfig,
        contentWidthOverridePx: Float? = null,
        lyricOnly: Boolean = false
    ): Float? {
        val contentMode = config.modeForTag(viewTag)
        if (lyricOnly && contentMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO) {
            return null
        }
        val contentWidthPx = when (contentMode) {
            RootConstants.ISLAND_CONTENT_MODE_LYRIC -> {
                val lyricView = rootView.findViewWithTag<View>(viewTag) ?: return null
                when (lyricView) {
                    is RichLyricLineView -> contentWidthOverridePx ?: lyricView.main.lineWidth
                    is SpaceGateRichLyricLineView -> contentWidthOverridePx ?: maxOf(
                        lyricView.main.lineWidth,
                        lyricView.secondary.lineWidth
                    )
                    else -> return null
                }
            }

            RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO -> {
                metadataContentWidthPx(rootView, viewTag) ?: return null
            }

            else -> return null
        }
        return IslandLyricWidthCalculator.baseWidthDp(
            contentWidthPx = contentWidthPx,
            spec = dynamicLyricWidthSpec(rootView, parentName, config)
        )
    }

    private fun metadataContentWidthPx(rootView: ViewGroup, viewTag: String): Float? {
        val cachedWidth = synchronized(metadataContentWidths) {
            metadataContentWidths[rootView]?.get(viewTag)
        }
        if (cachedWidth != null) return cachedWidth

        val view = rootView.findViewWithTag<View>(viewTag) ?: return null
        val measuredWidth = metadataContentWidthPx(view) ?: return null
        synchronized(metadataContentWidths) {
            metadataContentWidths.getOrPut(rootView) { hashMapOf() }[viewTag] = measuredWidth
        }
        return measuredWidth
    }

    private fun metadataContentWidthPx(view: View): Float? {
        return when (view) {
            is RichLyricLineView -> maxOf(view.main.lineWidth, view.secondary.lineWidth)
            is SpaceGateRichLyricLineView -> maxOf(
                view.main.lineWidth,
                view.secondary.lineWidth
            )
            else -> null
        }
    }

    private fun updateDynamicSlotWidth(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String,
        config: IslandSlotRuntimeConfig,
        baseWidthDp: Float
    ): Boolean {
        val targetWidthPx = IslandLyricWidthCalculator.targetWidthPx(
            baseWidthDp = baseWidthDp,
            spec = dynamicLyricWidthSpec(rootView, parentName, config)
        ) ?: return false
        val wrapper = rootView.findViewWithTag<View>("${viewTag}_WRAPPER")
            as? MaxWidthFrameLayout ?: return false
        if (wrapper.maxWidthPx == targetWidthPx) return false

        wrapper.maxWidthPx = targetWidthPx
        wrapper.requestLayout()
        return true
    }

    private fun scheduleSystemRelayout(
        rootView: ViewGroup,
        hostToken: IslandViewRegistry.HostToken
    ) {
        val shouldPost = synchronized(relayoutPending) {
            if (relayoutPending[rootView] == true) {
                false
            } else {
                relayoutPending[rootView] = true
                true
            }
        }
        if (!shouldPost) return

        mainHandler.post {
            try {
                if (!rootView.isAttachedToWindow ||
                    !IslandPresentationCoordinator.isCurrentHost(hostToken)
                ) return@post
                when (hostToken.kind) {
                    IslandViewRegistry.HostKind.REAL -> {
                        if (!IslandPresentationCoordinator.isPlaybackActive()) return@post
                        IslandHostFacade.triggerSystemRelayout(rootView)
                    }

                    IslandViewRegistry.HostKind.FAKE -> {
                        // Fake is owned by Xiaomi's animation tree. Its local wrapper must be
                        // measured, but it must not trigger the real window relayout protocol.
                        rootView.requestLayout()
                    }
                }
            } finally {
                synchronized(relayoutPending) {
                    relayoutPending.remove(rootView)
                }
            }
        }
    }

    private fun dynamicLyricWidthSpec(
        rootView: ViewGroup,
        parentName: String,
        config: IslandSlotRuntimeConfig
    ): IslandLyricWidthSpec {
        return IslandLyricWidthSpec(
            density = rootView.resources.displayMetrics.density,
            paddingLeftPx = config.geometry.paddingLeftPx(rootView, parentName),
            paddingRightPx = config.geometry.paddingRightPx(rootView, parentName),
            minWidthDp = config.geometry.minWidthDp(parentName),
            maxWidthDp = config.geometry.maxWidthDp(parentName),
            isLeft = config.geometry.isLeftParent(parentName),
            showAlbum = config.geometry.showAlbum,
            showRhythm = config.geometry.showRhythm
        )
    }
}
