package com.lidesheng.hyperlyric.root.statusbar

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.isCountdownLine
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.content.IslandSlotContentFacade
import com.lidesheng.hyperlyric.root.island.content.IslandSlotStyleAssembler
import com.lidesheng.hyperlyric.root.island.effects.color.StatusBarTextColorHooker
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController
import com.lidesheng.hyperlyric.root.island.view.MaxWidthFrameLayout
import java.lang.ref.WeakReference

/** One Clock-anchored lyric projection for a HyperOS status-bar root. */
internal class StatusBarLyricHost(
    root: ViewGroup,
    clock: View,
    parent: ViewGroup,
    private val statusBarId: Int,
    private val clockId: Int,
) {
    private val rootReference = WeakReference(root)
    private var clockReference = WeakReference(clock)
    private var parentReference = WeakReference(parent)
    private var container: MaxWidthFrameLayout? = null
    private var contentRow: LinearLayout? = null
    private var iconView: ImageView? = null
    private var iconController: StatusBarLyricIconController? = null
    private var lyricView: RichLyricLineView? = null
    private var gestureController: StatusBarLyricGestureController? = null
    private var clockGestureController: StatusBarLyricGestureController? = null
    private var clockGestureReference: WeakReference<View>? = null
    private var managedClockReference: WeakReference<View>? = null
    private var managedClockOriginalVisibility: Int? = null
    private var shouldKeepClockHidden = false
    private var layoutConfig: StatusBarLyricLayoutConfig? = null
    private var latestContentWidthPx = 0f
    private var iconWidthPx = 0
    private val wrapperLocationInWindow = IntArray(2)
    private var islandSpacingResources: Resources? = null
    private var islandSpacingDensityDpi = 0
    private var islandSpacingOrientation = -1
    private var cachedIslandSpacingPx = 0
    private val rootLayoutListener = View.OnLayoutChangeListener {
            _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if ((right - left) != (oldRight - oldLeft) ||
            (bottom - top) != (oldBottom - oldTop)
        ) {
            StatusBarLyricRenderer.onHostSizeChanged()
        }
    }
    private val lyricContainerLayoutListener = View.OnLayoutChangeListener {
        _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            StatusBarLyricRenderer.onHostGeometryChanged()
        }
    }

    init {
        root.addOnLayoutChangeListener(rootLayoutListener)
        attachClockGestureController(clock)
    }

    fun rootView(): ViewGroup? = rootReference.get()

    fun matches(root: ViewGroup, clock: View, parent: ViewGroup): Boolean =
        rootReference.get() === root && clockReference.get() === clock &&
                parentReference.get() === parent

    fun isShowingLyric(): Boolean {
        val wrapper = container ?: return false
        val lyrics = lyricView ?: return false
        return wrapper.visibility == View.VISIBLE &&
                (isRenderable(lyrics.rawLine) || isRenderable(lyrics.rawSecondaryLine))
    }

    fun managesClockVisibility(view: View): Boolean =
        clockReference.get() === view && shouldKeepClockHidden

    /** Reasserts only this host's Clock after SystemUI recalculates its native visibility. */
    fun enforceManagedClockVisibility(view: View, systemVisibility: Int? = null) {
        if (clockReference.get() !== view || !shouldKeepClockHidden) return
        if (managedClockReference?.get() !== view) {
            managedClockReference = WeakReference(view)
            managedClockOriginalVisibility = systemVisibility ?: view.visibility
        } else if (systemVisibility != null) {
            managedClockOriginalVisibility = systemVisibility
        } else if (view.visibility != View.GONE) {
            // Capture the latest visibility chosen by SystemUI so it can be restored afterwards.
            managedClockOriginalVisibility = view.visibility
        }
        if (view.visibility != View.GONE) view.visibility = View.GONE
    }

    fun render(
        prefs: SharedPreferences,
        force: Boolean = false,
        suppressAnimation: Boolean = false,
    ) {
        val root = rootReference.get() ?: return
        val lyricPrefs = StatusBarLyricPreferences.scoped(prefs)
        if (root.id != statusBarId || root.findViewById<View>(clockId) == null) {
            setClockHidden(false)
            clearLyrics()
            return
        }

        val clock = root.findViewById<View>(clockId) ?: run {
            setClockHidden(false)
            clearLyrics()
            return
        }
        val parent = clock.parent as? ViewGroup ?: run {
            setClockHidden(false)
            clearLyrics()
            return
        }
        updateClockReference(clock)
        parentReference = WeakReference(parent)
        val layoutConfig = StatusBarLyricLayoutConfig.from(lyricPrefs, root)
        this.layoutConfig = layoutConfig

        val config = IslandSlotRuntimeConfig.from(lyricPrefs).copy(
            activeMode = RootConstants.DEFAULT_HOOK_LYRIC_MODE,
            leftMode = RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            rightMode = RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            lyricAlignment = RootConstants.CONTENT_ALIGNMENT_LEFT,
            textColorStyle = StatusBarLyricPreferences.effectiveStatusBarTextColorStyle(lyricPrefs),
        )
        val (wrapper, lyrics) = ensureViews(root, parent, clock, layoutConfig.insertionOrder)
        wrapper.setPadding(
            layoutConfig.paddingLeftPx,
            0,
            layoutConfig.paddingRightPx,
            0,
        )
        val effectiveWidthLimitPx = effectiveWidthLimitPx(
            wrapper = wrapper,
            configuredLimitPx = layoutConfig.widthLimitPx,
            adjustForSuperIsland = layoutConfig.adjustWidthForSuperIsland,
        )
        val widthLimitChanged = wrapper.maxWidthPx != effectiveWidthLimitPx
        wrapper.maxWidthPx = effectiveWidthLimitPx
        if (widthLimitChanged) wrapper.requestLayout()
        iconWidthPx = updateIcon(lyrics, layoutConfig, effectiveWidthLimitPx)
        wrapper.visibility = View.VISIBLE

        IslandSlotContentFacade.applySlotContent(
            view = lyrics,
            prefs = lyricPrefs,
            config = config,
            mode = RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            force = force,
            playbackActive = LyriconDataBridge.isPlaybackActive(),
            playbackClock = LyriconDataBridge.currentPlaybackClock(),
            suppressAnimation = suppressAnimation,
            forceUnsplit = true,
            forceNoLyricsPlaceholder = true,
            onLineWillApply = { candidateContentWidth ->
                latestContentWidthPx = candidateContentWidth
                val desiredWidth = layoutConfig.desiredWidthPx(
                    contentWidthPx = candidateContentWidth,
                    iconWidthPx = iconWidthPx,
                )
                    .coerceAtMost(effectiveWidthLimitPx)
                val changed = wrapper.desiredWidthPx != desiredWidth
                wrapper.desiredWidthPx = desiredWidth
                if (changed && !widthLimitChanged) wrapper.requestLayout()
                changed || widthLimitChanged
            },
            onLineApplied = {
                updateVisibility(wrapper, lyrics)
            },
        )
        updateIconTint(lyrics)
        updateVisibility(wrapper, lyrics)
    }

    fun refreshTextStyle(prefs: SharedPreferences) {
        val root = rootReference.get() ?: return
        val lyrics = lyricView ?: return
        if (lyrics.parent == null) return
        val lyricPrefs = StatusBarLyricPreferences.scoped(prefs)
        val config = IslandSlotRuntimeConfig.from(lyricPrefs).copy(
            activeMode = RootConstants.DEFAULT_HOOK_LYRIC_MODE,
            leftMode = RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            rightMode = RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            lyricAlignment = RootConstants.CONTENT_ALIGNMENT_LEFT,
            textColorStyle = StatusBarLyricPreferences.effectiveStatusBarTextColorStyle(lyricPrefs),
        )
        IslandSlotContentFacade.configureView(
            view = lyrics,
            prefs = lyricPrefs,
            config = config,
            mode = RootConstants.ISLAND_CONTENT_MODE_LYRIC,
            force = true,
        )
        updateIconTint(lyrics)
        root.requestLayout()
    }

    fun updatePlaybackPosition(
        position: Long? = null,
        playbackSpeed: Float? = null,
    ) {
        val lyrics = lyricView ?: return
        if (lyrics.parent == null) return
        val clock = LyriconDataBridge.currentPlaybackClock()
        IslandLyricViewController.applyPlaybackSnapshot(
            view = lyrics,
            position = position ?: clock.positionMs,
            playbackSpeed = playbackSpeed ?: clock.playbackSpeed,
            activeTimeMs = clock.activeTimeMs,
            active = LyriconDataBridge.isPlaybackActive(),
        )
    }

    /** Reapplies the viewport width after native island or host geometry changes. */
    fun refreshIslandWidth() {
        val wrapper = container ?: return
        if (wrapper.parent == null) return
        val config = layoutConfig ?: return
        val effectiveWidthLimitPx = effectiveWidthLimitPx(
            wrapper = wrapper,
            configuredLimitPx = config.widthLimitPx,
            adjustForSuperIsland = config.adjustWidthForSuperIsland,
        )
        var changed = wrapper.maxWidthPx != effectiveWidthLimitPx
        wrapper.maxWidthPx = effectiveWidthLimitPx

        val requestedWidth = latestContentWidthPx.takeIf { it > 0f }
            ?.let { contentWidthPx ->
                config.desiredWidthPx(
                    contentWidthPx = contentWidthPx,
                    iconWidthPx = iconWidthPx,
                )
            }
            ?.coerceAtMost(effectiveWidthLimitPx)
        if (requestedWidth != null && wrapper.desiredWidthPx != requestedWidth) {
            wrapper.desiredWidthPx = requestedWidth
            changed = true
        }
        if (changed) wrapper.requestLayout()
    }

    fun clearLyrics() {
        val oldView = lyricView
        val oldContainer = container
        iconController?.clear()
        gestureController?.release()
        oldView?.setOnTouchListener(null)
        gestureController = null
        oldView?.let(IslandSlotContentFacade::invalidate)
        oldContainer?.removeOnLayoutChangeListener(lyricContainerLayoutListener)
        (oldContainer?.parent as? ViewGroup)?.removeView(oldContainer)
        oldContainer?.removeAllViews()
        lyricView = null
        contentRow = null
        iconView = null
        iconController = null
        container = null
        layoutConfig = null
        latestContentWidthPx = 0f
        iconWidthPx = 0
    }

    fun releaseForHotReload() {
        setClockHidden(false)
        clearLyrics()
        releaseClockGestureController()
        rootReference.get()?.let { root ->
            root.removeOnLayoutChangeListener(rootLayoutListener)
            root.setTag(R.id.hyperlyric_status_bar_lyric_host, null)
        }
    }

    private fun ensureViews(
        root: ViewGroup,
        parent: ViewGroup,
        clock: View,
        insertionOrder: Int,
    ): Pair<MaxWidthFrameLayout, RichLyricLineView> {
        var wrapper = container
        var row = contentRow
        var icon = iconView
        var lyrics = lyricView
        if (wrapper == null || row == null || icon == null || lyrics == null) {
            wrapper = MaxWidthFrameLayout(root.context).apply {
                // This wrapper is the status-bar lyric viewport. Keep the scrolling text and
                // child animations inside its measured content area and breathing padding.
                allowZeroWidth = true
                clipChildren = true
                clipToPadding = true
                setBackgroundColor(Color.TRANSPARENT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                addOnLayoutChangeListener(lyricContainerLayoutListener)
            }
            row = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                clipChildren = true
                clipToPadding = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            icon = ImageView(root.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            lyrics = RichLyricLineView(root.context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                IslandLyricViewController.configureProjection(this)
                gestureController = StatusBarLyricGestureController(this)
                setOnTouchListener(gestureController)
            }
            row.addView(icon, LinearLayout.LayoutParams(0, 0))
            row.addView(
                lyrics,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f,
                ),
            )
            wrapper.addView(
                row,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER_VERTICAL,
                )
            )
            container = wrapper
            contentRow = row
            iconView = icon
            iconController = StatusBarLyricIconController(row, icon)
            lyricView = lyrics
        }

        val oldParent = wrapper.parent as? ViewGroup
        if (oldParent !== parent) oldParent?.removeView(wrapper)
        val clockIndex = parent.indexOfChild(clock)
        val wrapperIndex = parent.indexOfChild(wrapper)
        val shouldBeBeforeClock = insertionOrder ==
                RootConstants.STATUS_BAR_LYRIC_INSERTION_BEFORE_CLOCK
        val alreadyInPosition = wrapperIndex >= 0 && clockIndex >= 0 &&
                if (shouldBeBeforeClock) wrapperIndex < clockIndex else wrapperIndex > clockIndex
        if (!alreadyInPosition && clockIndex >= 0) {
            if (wrapperIndex >= 0) parent.removeView(wrapper)
            val updatedClockIndex = parent.indexOfChild(clock)
            val targetIndex = if (shouldBeBeforeClock) {
                updatedClockIndex
            } else {
                updatedClockIndex + 1
            }
            parent.addView(
                wrapper,
                targetIndex.coerceIn(0, parent.childCount),
                layoutParams(parent),
            )
        }
        return wrapper to lyrics
    }

    private fun updateIcon(
        lyrics: RichLyricLineView,
        config: StatusBarLyricLayoutConfig,
        effectiveWidthLimitPx: Int,
    ): Int {
        val controller = iconController ?: return 0
        val mediaInfo = LyriconDataBridge.currentResolvedMediaInfo
        val mediaPackage = LyriconDataBridge.currentLyricPackageName
            ?: mediaInfo?.identity?.packageName
        val artwork = resolveCurrentArtwork(mediaInfo)
        val lyricColor = IslandSlotStyleAssembler.primaryTextColor(lyrics)
            ?: StatusBarTextColorHooker.currentTextColor()
        val contentWidthLimit = (
                effectiveWidthLimitPx - config.paddingLeftPx - config.paddingRightPx
                ).coerceAtLeast(0)
        return controller.update(
            config = config,
            mediaPackage = mediaPackage,
            artwork = artwork,
            lyricColor = lyricColor,
            isPlaying = LyriconDataBridge.isPlaybackActive(),
            contentWidthLimitPx = contentWidthLimit,
        )
    }

    private fun resolveCurrentArtwork(mediaInfo: MediaMetadataHelper.MediaInfo?): Bitmap? {
        val info = mediaInfo ?: return null
        val artwork = info.albumArt?.takeUnless { it.isRecycled } ?: return null
        val identity = info.identity
        val expectedPackage = LyriconDataBridge.currentLyricPackageName
            ?.takeIf { it.isNotBlank() }
        if (expectedPackage != null && identity.packageName != expectedPackage) return null

        val source = LyriconDataBridge.currentLyricMediaMetadata
        source?.let { metadata ->
            if (!metadata.packageName.isNullOrBlank() &&
                metadata.packageName != identity.packageName
            ) return null
            metadata.sessionToken?.let { token ->
                if (identity.sessionToken != token) return null
            }
            metadata.mediaId?.let { mediaId ->
                if (identity.mediaId != mediaId) return null
            }
        }
        return artwork
    }

    private fun effectiveWidthLimitPx(
        wrapper: MaxWidthFrameLayout,
        configuredLimitPx: Int,
        adjustForSuperIsland: Boolean,
    ): Int {
        if (!adjustForSuperIsland) return configuredLimitPx
        if (!wrapper.isLaidOut || wrapper.height <= 0) return configuredLimitPx
        val islandBounds = StatusBarLyricIslandRegionDispatcher
            .visibleIslandBounds() ?: return configuredLimitPx

        // Match IslandMonitor, which compares the committed Region against window-relative
        // status-bar container coordinates.
        wrapper.getLocationInWindow(wrapperLocationInWindow)
        val lyricLeft = wrapperLocationInWindow[0]
        val lyricTop = wrapperLocationInWindow[1]
        val lyricBottom = lyricTop + wrapper.height
        if (islandBounds.bottom <= lyricTop || islandBounds.top >= lyricBottom) {
            return configuredLimitPx
        }
        if (islandBounds.right <= lyricLeft) return configuredLimitPx

        val islandGapPx = islandSpacingPx(wrapper)
        val availableWidthPx = (islandBounds.left - lyricLeft - islandGapPx).coerceAtLeast(1)
        return minOf(configuredLimitPx, availableWidthPx)
    }

    private fun islandSpacingPx(wrapper: View): Int {
        val resources = wrapper.resources
        val densityDpi = resources.displayMetrics.densityDpi
        val orientation = resources.configuration.orientation
        if (islandSpacingResources === resources &&
            islandSpacingDensityDpi == densityDpi &&
            islandSpacingOrientation == orientation
        ) return cachedIslandSpacingPx

        val fallback = (4f * resources.displayMetrics.density).toInt()
        val spacing = runCatching {
            val resourceId = resources.getIdentifier(
            "status_bar_island_padding",
            "dimen",
            "com.android.systemui",
            )
            if (resourceId != 0) resources.getDimensionPixelSize(resourceId) else fallback
        }.getOrDefault(fallback)
        islandSpacingResources = resources
        islandSpacingDensityDpi = densityDpi
        islandSpacingOrientation = orientation
        cachedIslandSpacingPx = spacing
        return spacing
    }

    private fun layoutParams(parent: ViewGroup): ViewGroup.LayoutParams = when (parent) {
        is LinearLayout -> LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { gravity = Gravity.CENTER_VERTICAL }

        is FrameLayout -> FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_VERTICAL,
        )

        else -> ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun updateClockReference(clock: View) {
        if (clockReference.get() === clock) return
        releaseClockGestureController()
        restoreClockVisibility()
        clockReference = WeakReference(clock)
        attachClockGestureController(clock)
    }

    private fun attachClockGestureController(clock: View) {
        if (clockGestureReference?.get() === clock && clockGestureController != null) return
        releaseClockGestureController()
        val controller = StatusBarLyricGestureController(
            touchView = clock,
            allowHorizontalSwipe = false,
        )
        clockGestureReference = WeakReference(clock)
        clockGestureController = controller
        clock.setOnTouchListener(controller)
    }

    private fun releaseClockGestureController() {
        clockGestureController?.release()
        clockGestureReference?.get()?.setOnTouchListener(null)
        clockGestureController = null
        clockGestureReference = null
    }

    private fun updateIconTint(lyrics: RichLyricLineView) {
        val color = IslandSlotStyleAssembler.primaryTextColor(lyrics)
            ?: StatusBarTextColorHooker.currentTextColor()
        iconController?.updateTint(color)
    }

    private fun updateVisibility(wrapper: MaxWidthFrameLayout, lyrics: RichLyricLineView) {
        val hasLine = isRenderable(lyrics.rawLine) || isRenderable(lyrics.rawSecondaryLine)
        wrapper.visibility = if (hasLine) View.VISIBLE else View.GONE
        iconController?.setHostVisible(hasLine)
    }

    fun setClockHidden(shouldHide: Boolean) {
        shouldKeepClockHidden = shouldHide
        val root = rootReference.get()
        val clock = clockReference.get() ?: run {
            restoreClockVisibility()
            return
        }
        if (root?.findViewById<View>(clockId) !== clock) {
            restoreClockVisibility()
            return
        }
        if (!shouldHide) {
            restoreClockVisibility()
            return
        }

        val managedClock = managedClockReference?.get()
        if (managedClock !== clock) {
            restoreClockVisibility()
            managedClockReference = WeakReference(clock)
            managedClockOriginalVisibility = clock.visibility
        } else if (clock.visibility != View.GONE) {
            // Preserve a visibility change made by SystemUI while this option owns the Clock.
            managedClockOriginalVisibility = clock.visibility
        }
        if (clock.visibility != View.GONE) clock.visibility = View.GONE
    }

    private fun restoreClockVisibility() {
        shouldKeepClockHidden = false
        val clock = managedClockReference?.get()
        val originalVisibility = managedClockOriginalVisibility
        if (clock?.visibility == View.GONE && originalVisibility != null) {
            clock.visibility = originalVisibility
        }
        managedClockReference = null
        managedClockOriginalVisibility = null
    }

    private fun isRenderable(line: IRichLyricLine?): Boolean =
        line != null && (
                !line.text.isNullOrBlank() ||
                        !line.words.isNullOrEmpty() ||
                        line.isCountdownLine()
                )
}
