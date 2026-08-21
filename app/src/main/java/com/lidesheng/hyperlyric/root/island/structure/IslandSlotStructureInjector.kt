package com.lidesheng.hyperlyric.root.island.structure

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.content.IslandSlotContentFacade
import com.lidesheng.hyperlyric.root.island.host.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandViewHelper
import com.lidesheng.hyperlyric.root.island.sizing.IslandDynamicWidthCoordinator
import com.lidesheng.hyperlyric.root.island.view.MaxWidthFrameLayout
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Owns the structure of lyric slots injected into a Super Island host.
 *
 * Content refresh and playback progress are handled by their dedicated controllers. This class
 * only creates, restores, replaces and validates the injected slot views, while preserving the
 * existing synchronous preparation and relayout timing.
 */
internal object IslandSlotStructureInjector {
    private const val TAG = "IslandSlotStructureInjector"

    fun injectSlots(
        rootView: ViewGroup,
        reconfigureExisting: Boolean = true,
        suppressAnimation: Boolean = false
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: run {
            HookLogger.dState(
                stateId = "IslandSlotStructureInjector:${System.identityHashCode(rootView)}:all",
                tag = TAG,
                state = "prefs_missing"
            ) {
                "歌词结构未注入: reason=remote_preferences_missing, root=${System.identityHashCode(rootView)}"
            }
            return false
        }
        val config = IslandSlotRuntimeConfig.from(prefs)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = injectSlot(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG,
                config.leftMode,
                reconfigureExisting,
                config,
                suppressAnimation
            ) || changed
        } else {
            changed = removeInjectedSlot(
                rootView,
                IslandProbeUtils.LEFT_TEST_WRAPPER_TAG
            ) || changed
        }

        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = injectSlot(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG,
                config.rightMode,
                reconfigureExisting,
                config,
                suppressAnimation
            ) || changed
        } else {
            changed = removeInjectedSlot(
                rootView,
                IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG
            ) || changed
        }

        if (config.isSplitMode) {
            linkViews(rootView)
        }

        IslandHostFacade.applyHostSettings(rootView, prefs)
        IslandDynamicWidthCoordinator.requestRefresh(rootView)
        return changed
    }

    private fun removeInjectedSlot(rootView: ViewGroup, wrapperTag: String): Boolean {
        val wrapper = rootView.findViewWithTag<View>(wrapperTag) ?: return false
        val parent = wrapper.parent as? ViewGroup ?: return false
        parent.removeView(wrapper)
        IslandSlotContentFacade.invalidate(wrapper)
        return true
    }

    fun restoreExistingSlotsLightweight(rootView: ViewGroup): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = restoreExistingSlotLightweight(
                rootView,
                IslandProbeUtils.LEFT_PARENT_NAME,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
            changed = restoreExistingSlotLightweight(
                rootView,
                IslandProbeUtils.RIGHT_PARENT_NAME,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG
            ) || changed
        }
        IslandHostFacade.applyHostSettings(rootView, prefs)
        return changed
    }

    fun restoreExistingModuleSlotLightweight(rootView: ViewGroup, moduleType: String?): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)

        var changed = false
        if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE &&
            (moduleType == null || moduleType.endsWith("_1"))
        ) {
            changed = restoreExistingSlotByTagLightweight(
                rootView,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG
            ) || changed
        }
        if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE &&
            (moduleType == null || moduleType.endsWith("_2"))
        ) {
            changed = restoreExistingSlotByTagLightweight(
                rootView,
                IslandProbeUtils.RIGHT_TEST_VIEW_TAG
            ) || changed
        }
        if (!changed && moduleType != null && !moduleType.endsWith("_1") && !moduleType.endsWith("_2")) {
            if (config.leftMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
                changed = restoreExistingSlotByTagLightweight(
                    rootView,
                    IslandProbeUtils.LEFT_TEST_VIEW_TAG
                ) || changed
            }
            if (config.rightMode != RootConstants.ISLAND_CONTENT_MODE_NONE) {
                changed = restoreExistingSlotByTagLightweight(
                    rootView,
                    IslandProbeUtils.RIGHT_TEST_VIEW_TAG
                ) || changed
            }
        }

        IslandHostFacade.applyHostSettings(rootView, prefs)
        return changed
    }

    fun hasInjectedLyricText(rootView: ViewGroup): Boolean {
        return rootView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_WRAPPER_TAG) != null ||
                rootView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG) != null ||
                rootView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG) != null ||
                rootView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG) != null
    }

    fun hasInjectedLyricSlot(rootView: ViewGroup, viewTag: String): Boolean {
        return rootView.findViewWithTag<View>("${viewTag}_WRAPPER") != null ||
                rootView.findViewWithTag<View>(viewTag) != null
    }

    fun hasAllConfiguredSlots(
        rootView: ViewGroup,
        moduleType: String? = null
    ): Boolean {
        val prefs = HookEntry.instance?.prefs ?: return false
        val config = IslandSlotRuntimeConfig.from(prefs)
        val checksLeft = moduleType?.endsWith("_2") != true
        val checksRight = moduleType?.endsWith("_1") != true
        val leftReady = !checksLeft || config.leftMode == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                hasInjectedLyricSlot(rootView, IslandProbeUtils.LEFT_TEST_VIEW_TAG)
        val rightReady = !checksRight || config.rightMode == RootConstants.ISLAND_CONTENT_MODE_NONE ||
                hasInjectedLyricSlot(rootView, IslandProbeUtils.RIGHT_TEST_VIEW_TAG)
        return leftReady && rightReady
    }

    private fun injectSlot(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String,
        mode: Int,
        reconfigureExisting: Boolean,
        config: IslandSlotRuntimeConfig,
        suppressAnimation: Boolean
    ): Boolean {
        val widthPx = config.geometry.widthPx(rootView, parentName) ?: run {
            logSlotSkip(rootView, viewTag, "width_missing")
            return false
        }

        val parent = IslandViewHelper.findViewByName(rootView, parentName) as? ViewGroup ?: run {
            logSlotSkip(rootView, viewTag, "parent_missing")
            return false
        }
        val container = IslandViewHelper.findViewByName(
            parent,
            IslandProbeUtils.TEXT_CONTAINER_NAME
        ) as? ViewGroup ?: run {
            logSlotSkip(rootView, viewTag, "text_container_missing")
            return false
        }

        val wrapperTag = "${viewTag}_WRAPPER"

        IslandViewHelper.showForInjection(container)

        HookEntry.instance?.prefs ?: run {
            logSlotSkip(rootView, viewTag, "remote_preferences_missing")
            return false
        }

        val taggedWrapper = container.findViewWithTag<View>(wrapperTag)
        val existingWrapper = taggedWrapper as? MaxWidthFrameLayout
        if (taggedWrapper != null && existingWrapper == null) {
            (taggedWrapper.parent as? ViewGroup)?.removeView(taggedWrapper)
            HookLogger.d(TAG, "已移除热重载遗留的旧歌词容器: tag=$wrapperTag")
        }
        if (existingWrapper != null) {
            existingWrapper.keepVisible = true
            var changed = updateWrapper(existingWrapper, widthPx, config, parentName)
            val targetView = existingWrapper.findViewWithTag<View>(viewTag)

            if (targetView == null) {
                existingWrapper.addView(
                    createLyricView(
                        rootView,
                        viewTag,
                        config,
                        mode,
                        suppressAnimation
                    ), createLyricTextLayoutParams()
                )
                changed = true
            } else if (!isViewTypeCorrect(targetView, config.activeMode)) {
                existingWrapper.removeView(targetView)
                IslandSlotContentFacade.invalidate(targetView)
                existingWrapper.addView(
                    createLyricView(
                        rootView,
                        viewTag,
                        config,
                        mode,
                        suppressAnimation
                    ), createLyricTextLayoutParams()
                )
                changed = true
            } else {
                changed = restoreTargetView(
                    targetView,
                    config,
                    mode,
                    reconfigureExisting,
                    suppressAnimation
                ) || changed
            }

            if (existingWrapper.visibility != View.VISIBLE) {
                existingWrapper.visibility = View.VISIBLE
                changed = true
            }
            changed = forceWrapperLayout(existingWrapper, container, widthPx) || changed
            IslandViewHelper.hideNativeChildren(container, existingWrapper)
            return changed
        }

        val wrapperView = MaxWidthFrameLayout(rootView.context).apply {
            tag = wrapperTag
            clipChildren = true
            maxWidthPx = widthPx
            keepVisible = true
        }
        updateWrapper(wrapperView, widthPx, config, parentName)
        wrapperView.addView(
            createLyricView(rootView, viewTag, config, mode, suppressAnimation),
            createLyricTextLayoutParams()
        )

        container.addView(
            wrapperView,
            FrameLayout.LayoutParams(
                wrapperLayoutWidth(config),
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
        IslandViewHelper.hideNativeChildren(container, wrapperView)

        forceWrapperLayout(wrapperView, container, widthPx)

        return true
    }

    private fun logSlotSkip(rootView: ViewGroup, viewTag: String, reason: String) {
        HookLogger.dState(
            stateId = "IslandSlotStructureInjector:${System.identityHashCode(rootView)}:$viewTag",
            tag = TAG,
            state = reason
        ) {
            "歌词结构未注入: root=${System.identityHashCode(rootView)}, tag=$viewTag, reason=$reason"
        }
    }

    private fun restoreExistingSlotLightweight(
        rootView: ViewGroup,
        parentName: String,
        viewTag: String
    ): Boolean {
        val parent =
            IslandViewHelper.findViewByName(rootView, parentName) as? ViewGroup ?: return false
        val container = IslandViewHelper.findViewByName(
            parent,
            IslandProbeUtils.TEXT_CONTAINER_NAME
        ) as? ViewGroup ?: return false
        val wrapper = container.findViewWithTag<View>("${viewTag}_WRAPPER") as? MaxWidthFrameLayout
            ?: return false
        val targetView = wrapper.findViewWithTag<View>(viewTag) ?: return false

        var changed = false
        wrapper.keepVisible = true
        if (container.visibility != View.VISIBLE) {
            IslandViewHelper.showForInjection(container)
            changed = true
        }
        if (wrapper.visibility != View.VISIBLE) {
            wrapper.visibility = View.VISIBLE
            changed = true
        }
        if (targetView.visibility != View.VISIBLE) {
            targetView.visibility = View.VISIBLE
            changed = true
        }
        IslandViewHelper.hideNativeChildren(container, wrapper)
        return changed
    }

    private fun restoreExistingSlotByTagLightweight(rootView: ViewGroup, viewTag: String): Boolean {
        val wrapper = rootView.findViewWithTag<View>("${viewTag}_WRAPPER") as? MaxWidthFrameLayout
            ?: return false
        val targetView = wrapper.findViewWithTag<View>(viewTag) ?: return false
        val container = wrapper.parent as? ViewGroup ?: return false

        var changed = false
        wrapper.keepVisible = true
        if (container.visibility != View.VISIBLE) {
            IslandViewHelper.showForInjection(container)
            changed = true
        }
        if (wrapper.visibility != View.VISIBLE) {
            wrapper.visibility = View.VISIBLE
            changed = true
        }
        if (targetView.visibility != View.VISIBLE) {
            targetView.visibility = View.VISIBLE
            changed = true
        }
        IslandViewHelper.hideNativeChildren(container, wrapper)
        return changed
    }

    private fun updateWrapper(
        wrapper: MaxWidthFrameLayout,
        widthPx: Int,
        config: IslandSlotRuntimeConfig,
        parentName: String
    ): Boolean {
        var changed = false
        val paddingLeft = config.geometry.paddingLeftPx(wrapper, parentName)
        val paddingRight = config.geometry.paddingRightPx(wrapper, parentName)
        if (wrapper.paddingLeft != paddingLeft || wrapper.paddingRight != paddingRight) {
            wrapper.setPadding(paddingLeft, wrapper.paddingTop, paddingRight, wrapper.paddingBottom)
            changed = true
        }
        if (wrapper.minimumWidth != 0) {
            wrapper.minimumWidth = 0
            changed = true
        }
        // Keep the configured cap separate from the current dynamic target. The target is
        // refreshed from the lyric line after the wrapper is attached and must survive lifecycle
        // re-ensure paths (especially pause/keep), where content refresh can be skipped.
        val targetMaxWidthPx = widthPx
        val targetDesiredWidthPx = if (config.geometry.isDynamicWidth) {
            wrapper.desiredWidthPx.takeIf { it > 0 }?.coerceAtMost(widthPx) ?: widthPx
        } else {
            -1
        }
        if (wrapper.maxWidthPx != targetMaxWidthPx) {
            wrapper.maxWidthPx = targetMaxWidthPx
            changed = true
        }
        if (wrapper.desiredWidthPx != targetDesiredWidthPx) {
            wrapper.desiredWidthPx = targetDesiredWidthPx
            changed = true
        }
        val layoutParams = wrapper.layoutParams
        val expectedWidth = if (config.geometry.isDynamicWidth && wrapper.desiredWidthPx > 0) {
            wrapper.desiredWidthPx
        } else {
            wrapperLayoutWidth(config)
        }
        if (layoutParams != null && (layoutParams.width != expectedWidth || layoutParams.height != FrameLayout.LayoutParams.MATCH_PARENT)) {
            layoutParams.width = expectedWidth
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            wrapper.layoutParams = layoutParams
            changed = true
        }
        if (changed) wrapper.requestLayout()
        return changed
    }

    private fun isViewTypeCorrect(view: View, activeMode: Int): Boolean {
        return if (activeMode == 1) {
            view is SpaceGateRichLyricLineView
        } else {
            view is RichLyricLineView
        }
    }

    private fun restoreTargetView(
        targetView: View,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        reconfigure: Boolean,
        suppressAnimation: Boolean = false
    ): Boolean {
        var changed = false
        val layoutParams = targetView.layoutParams
        if (layoutParams != null &&
            (layoutParams.width != FrameLayout.LayoutParams.MATCH_PARENT ||
                    layoutParams.height != FrameLayout.LayoutParams.MATCH_PARENT)
        ) {
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            targetView.layoutParams = layoutParams
            changed = true
        }
        if (targetView.visibility != View.VISIBLE) {
            targetView.visibility = View.VISIBLE
            changed = true
        }

        val prefs = HookEntry.instance?.prefs
        if (reconfigure && prefs != null) {
            changed = IslandSlotContentFacade.applySlotContent(
                targetView,
                prefs,
                config,
                mode,
                force = true,
                suppressAnimation = suppressAnimation
            ) || changed
        }
        return changed
    }

    private fun forceWrapperLayout(
        wrapper: MaxWidthFrameLayout,
        container: ViewGroup,
        widthPx: Int
    ): Boolean {
        val wasZeroWidth = wrapper.width == 0 || wrapper.measuredWidth == 0
        if (!wasZeroWidth) {
            return false
        }

        val heightPx = if (container.height > 0) container.height else container.measuredHeight
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.AT_MOST)
        val heightSpec = if (heightPx > 0) {
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        }
        wrapper.measure(widthSpec, heightSpec)
        val finalHeight = if (heightPx > 0) heightPx else wrapper.measuredHeight
        wrapper.layout(0, 0, wrapper.measuredWidth, finalHeight)
        return wasZeroWidth
    }

    private fun wrapperLayoutWidth(config: IslandSlotRuntimeConfig): Int {
        return if (config.geometry.isDynamicWidth || config.isSplitMode) {
            FrameLayout.LayoutParams.WRAP_CONTENT
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
    }

    private fun createLyricTextLayoutParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun createLyricView(
        rootView: ViewGroup,
        tagValue: String,
        config: IslandSlotRuntimeConfig,
        mode: Int,
        suppressAnimation: Boolean = false
    ): View {
        val prefs = HookEntry.instance?.prefs
        val view = if (config.isSplitMode) {
            SpaceGateRichLyricLineView(rootView.context)
        } else {
            RichLyricLineView(rootView.context)
        }
        view.tag = tagValue

        if (prefs != null) {
            IslandSlotContentFacade.applySlotContent(
                view,
                prefs,
                config,
                mode,
                force = true,
                suppressAnimation = true
            )
        }
        return view
    }

    fun linkViews(rootView: ViewGroup) {
        val leftView =
            rootView.findViewWithTag<View>(IslandProbeUtils.LEFT_TEST_VIEW_TAG) as? SpaceGateRichLyricLineView
        val rightView =
            rootView.findViewWithTag<View>(IslandProbeUtils.RIGHT_TEST_VIEW_TAG) as? SpaceGateRichLyricLineView

        leftView?.setSplitGradientConfig(isRightSide = false, sibling = rightView)
        rightView?.setSplitGradientConfig(isRightSide = true, sibling = leftView)

        leftView?.main?.spaceGateEnabled = false
        leftView?.secondary?.spaceGateEnabled = false
        rightView?.main?.spaceGateEnabled = false
        rightView?.secondary?.spaceGateEnabled = false

    }
}
