package com.lidesheng.hyperlyric.root.island

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.island.view.MaxWidthFrameLayout
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.WeakHashMap

/**
 * 小米超级岛视图管理
 * 负责处理超级岛内部组件的查找、显隐切换及布局刷新
 */
object IslandViewHelper {

    private val SYSTEMUI_PKG_NAMES = arrayOf("miui.systemui.plugin", "com.android.systemui")
    private val originalMargins = WeakHashMap<View, MarginSnapshot>()
    private val originalVisibilities = WeakHashMap<View, Int>()
    private val isRelayouting = ThreadLocal.withInitial { false }

    /**
     * 切换超级岛内部容器（如图标、文本容器）的可见性
     */
    @SuppressLint("DiscouragedApi")
    fun toggleContainer(root: ViewGroup, parentName: String, containerName: String, show: Boolean) {
        try {
            val res = root.resources
            val parent = findViewByName(root, parentName) as? ViewGroup
            
            if (parent != null) {
                for (pkg in SYSTEMUI_PKG_NAMES) {
                    val id = res.getIdentifier(containerName, "id", pkg)
                    if (id != 0) {
                        parent.findViewById<View>(id)?.let { container ->
                            setVisibilityForInjection(
                                container,
                                if (show) View.VISIBLE else View.GONE
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            HookLogger.e("IslandViewHelper", "切换容器可见性失败: container=$containerName", e)
        }
    }

    /**
     * 清除超级岛文本容器的边距
     */
    @SuppressLint("DiscouragedApi")
    fun clearTextContainerMargin(root: ViewGroup, parentName: String, clearStart: Boolean, clearEnd: Boolean) {
        try {
            val res = root.resources
            val parent = findViewByName(root, parentName) as? ViewGroup
            
            if (parent != null) {
                for (pkg in SYSTEMUI_PKG_NAMES) {
                    val id = res.getIdentifier("island_container_module_text", "id", pkg)
                    if (id != 0) {
                        val textContainer = parent.findViewById<View>(id)
                        if (textContainer != null) {
                            val lp = textContainer.layoutParams as? ViewGroup.MarginLayoutParams
                            if (lp != null) {
                                synchronized(originalMargins) {
                                    originalMargins.getOrPut(textContainer) {
                                        MarginSnapshot(lp.marginStart, lp.marginEnd)
                                    }
                                }
                                if (clearStart) lp.marginStart = 0
                                if (clearEnd) lp.marginEnd = 0
                                textContainer.layoutParams = lp
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            HookLogger.e("IslandViewHelper", "清除边距失败: parent=$parentName", e)
        }
    }

    /**
     * 清理所有注入的视图并恢复系统原生组件
     */
    fun clearInjectedViews(rootView: ViewGroup): Boolean {
        val hasNativeState = hasRememberedNativeState(rootView)
        val hiddenCount =
            hideInjectedSlot(
                rootView,
                IslandProbeUtils.LEFT_TEST_WRAPPER_TAG,
                IslandProbeUtils.LEFT_TEST_VIEW_TAG
            ) +
                hideInjectedSlot(
                    rootView,
                    IslandProbeUtils.RIGHT_TEST_WRAPPER_TAG,
                    IslandProbeUtils.RIGHT_TEST_VIEW_TAG
                ) +
                hideInjectedView(rootView, "HYPERLYRIC_TEST_VIEW_WRAPPER_LEFT") +
                hideInjectedView(rootView, "HYPERLYRIC_TEST_VIEW_WRAPPER_RIGHT")

        if (!hasNativeState && hiddenCount == 0) return false

        restoreContainerVisibility(rootView, IslandProbeUtils.LEFT_PARENT_NAME, "island_container_module_icon")
        restoreContainerVisibility(rootView, IslandProbeUtils.RIGHT_PARENT_NAME, "island_container_module_icon")
        restoreContainerVisibility(rootView, IslandProbeUtils.LEFT_PARENT_NAME, IslandProbeUtils.TEXT_CONTAINER_NAME)
        restoreContainerVisibility(rootView, IslandProbeUtils.RIGHT_PARENT_NAME, IslandProbeUtils.TEXT_CONTAINER_NAME)
        restoreTextContainerMargins(rootView, "island_container_module_image_text_1")
        restoreTextContainerMargins(rootView, "island_container_module_image_text_2")
        showOriginalTexts(rootView, "island_container_module_image_text_1")
        showOriginalTexts(rootView, "island_container_module_image_text_2")

        if (hiddenCount > 0) {
            HookLogger.d("IslandViewHelper", "已隐藏歌词注入视图并恢复原生媒体岛: 数量=$hiddenCount")
        }
        return true
    }

    fun showForInjection(view: View) {
        setVisibilityForInjection(view, View.VISIBLE)
    }

    fun hideNativeChildren(container: ViewGroup, keepView: View) {
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child === keepView) {
                child.visibility = View.VISIBLE
                continue
            }
            val tag = child.tag as? String
            if (tag?.startsWith("HYPERLYRIC") == true) continue
            rememberOriginalVisibility(child)
            child.visibility = View.GONE
        }
    }

    private fun hideInjectedSlot(rootView: ViewGroup, wrapperTag: String, targetTag: String): Int {
        if (rootView.findViewWithTag<View>(wrapperTag) != null) {
            return hideInjectedView(rootView, wrapperTag)
        }
        return hideInjectedView(rootView, targetTag)
    }

    private fun hideInjectedView(rootView: ViewGroup, tag: String): Int {
        val view = rootView.findViewWithTag<View>(tag) ?: return 0
        val wrapper = view as? MaxWidthFrameLayout
        if (wrapper == null && view.javaClass.name == MaxWidthFrameLayout::class.java.name) {
            (view.parent as? ViewGroup)?.removeView(view)
            return 1
        }
        val changed = view.visibility != View.GONE || wrapper?.keepVisible == true
        wrapper?.keepVisible = false
        view.visibility = View.GONE
        return if (changed) 1 else 0
    }

    /**
     * 显示原本被隐藏的原生文本视图
     */
    @SuppressLint("DiscouragedApi")
    fun showOriginalTexts(rootView: ViewGroup, parentName: String) {
        try {
            val parent = findViewByName(rootView, parentName) as? ViewGroup ?: return
            val container = findViewByName(parent, IslandProbeUtils.TEXT_CONTAINER_NAME) as? ViewGroup
                ?: parent

            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val tag = child.tag as? String ?: ""
                if (!tag.startsWith("HYPERLYRIC")) {
                    restoreVisibility(child, View.VISIBLE)
                }
            }
        } catch (e: Exception) {
            HookLogger.e("IslandViewHelper", "恢复原生文本失败: parent=$parentName", e)
        }
    }

    /**
     * 触发超级岛系统的布局刷新
     *
     * 使用 ThreadLocal 防止重入：triggerSystemRelayout 调用的系统方法可能被
     * Hook 拦截后再次触发 triggerSystemRelayout，导致无限递归。
     */
    fun triggerSystemRelayout(islandView: ViewGroup) {
        if (isRelayouting.get() == true) return
        HookLogger.d("IslandViewHelper","正在触发布局刷新")
        isRelayouting.set(true)
        try {
            runCatching {
                val viewClass = islandView.javaClass
                // 优先尝试 updateBigIslandViewWidth
                val updateWidthMethod = viewClass.methods.find { it.name == "updateBigIslandViewWidth" }
                if (updateWidthMethod != null) {
                    updateWidthMethod.invoke(islandView)
                } else {
                    // 兜底尝试 calculateBigIslandWidth
                    viewClass.methods.find { it.name == "calculateBigIslandWidth" }?.invoke(islandView)
                }
            }.onFailure { e ->
                HookLogger.e("IslandViewHelper", "超级岛布局刷新失败", e)
            }
        } finally {
            isRelayouting.set(false)
        }
    }

    /**
     * 根据名称寻找 View（支持多包名兜底）
     */
    @SuppressLint("DiscouragedApi")
    fun findViewByName(root: ViewGroup, name: String): View? {
        val res = root.resources
        for (pkg in SYSTEMUI_PKG_NAMES) {
            val id = res.getIdentifier(name, "id", pkg)
            if (id != 0) {
                val v = root.findViewById<View>(id)
                if (v != null) return v
            }
        }
        return null
    }

    private fun restoreTextContainerMargins(rootView: ViewGroup, parentName: String) {
        val parent = findViewByName(rootView, parentName) as? ViewGroup ?: return
        val container = findViewByName(parent, "island_container_module_text") ?: return
        val snapshot = synchronized(originalMargins) {
            originalMargins.remove(container)
        } ?: return
        val lp = container.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.marginStart != snapshot.marginStart || lp.marginEnd != snapshot.marginEnd) {
            lp.marginStart = snapshot.marginStart
            lp.marginEnd = snapshot.marginEnd
            container.layoutParams = lp
        }
    }

    private fun restoreContainerVisibility(rootView: ViewGroup, parentName: String, containerName: String) {
        val parent = findViewByName(rootView, parentName) as? ViewGroup ?: return
        val container = findViewByName(parent, containerName) ?: return
        restoreVisibility(container, View.VISIBLE)
    }

    private fun setVisibilityForInjection(view: View, visibility: Int) {
        rememberOriginalVisibility(view)
        view.visibility = visibility
    }

    private fun rememberOriginalVisibility(view: View) {
        synchronized(originalVisibilities) {
            originalVisibilities.getOrPut(view) { view.visibility }
        }
    }

    private fun restoreVisibility(view: View, fallback: Int) {
        val visibility = synchronized(originalVisibilities) {
            originalVisibilities.remove(view)
        } ?: fallback
        view.visibility = visibility
    }

    private fun hasRememberedNativeState(rootView: ViewGroup): Boolean {
        for (parentName in arrayOf(IslandProbeUtils.LEFT_PARENT_NAME, IslandProbeUtils.RIGHT_PARENT_NAME)) {
            val parent = findViewByName(rootView, parentName) as? ViewGroup ?: continue
            val icon = findViewByName(parent, "island_container_module_icon")
            val text = findViewByName(parent, IslandProbeUtils.TEXT_CONTAINER_NAME)
            if (icon != null && hasRememberedVisibility(icon)) return true
            if (text != null) {
                if (hasRememberedVisibility(text) || hasRememberedMargin(text)) return true
                if (text is ViewGroup) {
                    for (index in 0 until text.childCount) {
                        if (hasRememberedVisibility(text.getChildAt(index))) return true
                    }
                }
            }
        }
        return false
    }

    private fun hasRememberedVisibility(view: View): Boolean {
        return synchronized(originalVisibilities) {
            originalVisibilities.containsKey(view)
        }
    }

    private fun hasRememberedMargin(view: View): Boolean {
        return synchronized(originalMargins) {
            originalMargins.containsKey(view)
        }
    }

    private data class MarginSnapshot(
        val marginStart: Int,
        val marginEnd: Int
    )
}
