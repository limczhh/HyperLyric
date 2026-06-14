package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * MIUI 灵动岛视图管理助手
 * 负责处理灵动岛内部组件的查找、显隐切换及布局刷新
 */
object IslandViewHelper {

    private val SYSTEMUI_PKG_NAMES = arrayOf("miui.systemui.plugin", "com.android.systemui")

    /**
     * 切换灵动岛内部容器（如图标、文本容器）的可见性
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
                        parent.findViewById<View>(id)?.visibility = if (show) View.VISIBLE else View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            HookLogger.e("IslandViewHelper", "切换容器可见性失败 ($containerName)", e)
        }
    }

    /**
     * 清除灵动岛文本容器的边距
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
                                if (clearStart) lp.marginStart = 0
                                if (clearEnd) lp.marginEnd = 0
                                textContainer.layoutParams = lp
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            HookLogger.e("IslandViewHelper", "清除边距失败 ($parentName)", e)
        }
    }

    /**
     * 清理所有注入的视图并恢复系统原生组件
     */
    fun clearInjectedViews(rootView: ViewGroup) {
        rootView.findViewWithTag<View>("HYPERLYRIC_LEFT_VIEW")?.visibility = View.GONE
        rootView.findViewWithTag<View>("HYPERLYRIC_LEFT_VIEW_WRAPPER")?.visibility = View.GONE
        rootView.findViewWithTag<View>("HYPERLYRIC_RIGHT_VIEW")?.visibility = View.GONE
        rootView.findViewWithTag<View>("HYPERLYRIC_RIGHT_VIEW_WRAPPER")?.visibility = View.GONE
        
        // 恢复系统原有组件的可见性
        toggleContainer(rootView, "island_container_module_image_text_1", "island_container_module_icon", true)
        toggleContainer(rootView, "island_container_module_image_text_2", "island_container_module_icon", true)
        
        showOriginalTexts(rootView, "island_container_module_image_text_1")
        showOriginalTexts(rootView, "island_container_module_image_text_2")
    }

    /**
     * 显示原本被隐藏的原生文本视图
     */
    @SuppressLint("DiscouragedApi")
    fun showOriginalTexts(rootView: ViewGroup, parentName: String) {
        try {
            val res = rootView.resources
            val slotId = res.getIdentifier(parentName, "id", "miui.systemui.plugin")
            if (slotId == 0) return
            val parent = rootView.findViewById<ViewGroup>(slotId) ?: return
            
            val textSlotId = res.getIdentifier("island_container_module_text", "id", "miui.systemui.plugin")
            val container = if (textSlotId != 0) parent.findViewById(textSlotId) ?: parent else parent

            if (container is ViewGroup) {
                for (i in 0 until container.childCount) {
                    val child = container.getChildAt(i)
                    val tag = child.tag as? String ?: ""
                    if (!tag.startsWith("HYPERLYRIC")) {
                        child.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            HookLogger.e("IslandViewHelper", "恢复原生文本失败 ($parentName)", e)
        }
    }

    /**
     * 触发灵动岛系统的布局刷新
     */
    fun triggerSystemRelayout(islandView: ViewGroup) {
        HookLogger.d("IslandViewHelper","正在触发布局刷新")
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
}
