package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.lidesheng.hyperlyric.root.mediacard.style.MediaCardForegroundColors
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * Hosts the SystemUI-native page indicator for the notification media card.
 *
 * HyperOS 3 still contains [com.android.systemui.qs.PageIndicator] for the
 * regular QS media carousel, but the notification media path no longer adds
 * one to [MiuiMediaHeaderView]. Reusing that class keeps the native dot
 * drawable and tint consistent with SystemUI.
 */
internal class NotificationMediaPageIndicator {
    private companion object {
        const val TAG = "NotificationMediaPageIndicator"
        const val PAGE_INDICATOR = "com.android.systemui.qs.PageIndicator"
        const val BOTTOM_MARGIN_DP = 4f
    }

    private var indicator: View? = null
    private var setNumPages: Method? = null
    private var setLocation: Method? = null
    private var setTintListMethod: Method? = null
    private var configuredPageCount = -1
    private var foregroundColors: MediaCardForegroundColors? = null
    private var primaryTintList: ColorStateList? = null
    private var secondaryTintList: ColorStateList? = null
    private var currentIndex = -1

    fun attach(player: View) {
        val parent = player.parent as? ViewGroup ?: run {
            // MiuiMediaNotificationControllerImpl adds player to the header
            // immediately before MiuiMediaViewControllerImpl.attach(). A
            // single posted retry also covers variant-specific attach order.
            player.post {
                if (player.parent is ViewGroup) attach(player)
            }
            return
        }
        attachTo(parent)
    }

    fun attachTo(parent: ViewGroup) {
        if (indicator?.parent === parent) return

        val frameParent = parent as? FrameLayout ?: run {
            warnOnce("媒体卡片父容器不是 FrameLayout: ${parent.javaClass.name}")
            return
        }
        detach()
        val nativeIndicator = createNativeIndicator(frameParent) ?: return
        val density = frameParent.resources.displayMetrics.density
        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (BOTTOM_MARGIN_DP * density).roundToInt()
        }

        runCatching {
            nativeIndicator.isClickable = false
            nativeIndicator.isFocusable = false
            nativeIndicator.isFocusableInTouchMode = false
            nativeIndicator.importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_NO
            frameParent.addView(nativeIndicator, layoutParams)
            indicator = nativeIndicator
            applyColors(nativeIndicator)
        }.onFailure { error ->
            warnOnce("挂载媒体圆点指示器失败", error)
        }
    }

    /**
     * Uses the shared media foreground roles for the native PageIndicator.
     * SystemUI stores one base tint and copies it to every child ImageView;
     * update each child after the native position update so the selected dot
     * uses primary and all other dots use secondary. Native alpha and drawable
     * transitions remain owned by PageIndicator.
     */
    fun updateColors(colors: MediaCardForegroundColors?) {
        if (foregroundColors == colors) {
            indicator?.let(::applyColors)
            return
        }
        foregroundColors = colors
        primaryTintList = colors?.primary?.let(ColorStateList::valueOf)
        secondaryTintList = colors?.secondary?.let(ColorStateList::valueOf)
        indicator?.let(::applyColors)
    }

    fun update(pageCount: Int, selectedIndex: Int, enabled: Boolean) {
        updateLocation(
            pageCount = pageCount,
            location = selectedIndex.toFloat(),
            enabled = enabled
        )
    }

    /**
     * Recreates the native indicator after the page key order changes. A
     * PageIndicator owns an internal AnimatedVectorDrawable queue; reusing it
     * across a semantic reorder lets an animation for the old page mapping
     * finish against the new mapping. A fresh instance keeps reorder atomic,
     * while ordinary finger movement is still updated as a direct page state.
     */
    fun resetForPageOrder(pageCount: Int, selectedIndex: Int, enabled: Boolean) {
        val parent = indicator?.parent as? ViewGroup ?: return
        val translation = indicator?.translationX ?: 0f
        detach()
        attachTo(parent)
        indicator?.translationX = translation
        update(pageCount, selectedIndex, enabled)
    }

    /**
     * Updates the native location while the custom carousel is being dragged.
     * The renderer supplies a fractional logical location, but the indicator
     * deliberately rounds it to a page before calling setLocation(). AOSP's
     * PageIndicator encodes fractional positions as adjacent transition states;
     * integer positions are two steps apart and therefore switch the selected
     * dot directly without the two-dot fill/morph animation.
     */
    fun updateLocation(pageCount: Int, location: Float, enabled: Boolean) {
        val view = indicator ?: return
        val count = pageCount.coerceAtLeast(0)
        if (!enabled || count <= 1) {
            hide(view)
            view.visibility = View.GONE
            return
        }

        if (!ensurePageCount(view, count)) return
        view.visibility = View.VISIBLE
        val targetIndex = location
            .coerceIn(0f, (count - 1).toFloat())
            .roundToInt()
        val method = setLocation ?: return
        val previousIndex = currentIndex
        runCatching {
            method.invoke(view, targetIndex.toFloat())
            currentIndex = targetIndex
            if (previousIndex != targetIndex) {
                applyChildColors(view)
            }
        }.onFailure { error ->
            warnOnce("更新媒体圆点位置失败", error)
        }
    }

    fun setTranslationX(translation: Float) {
        indicator?.translationX = translation
    }

    fun detach() {
        val view = indicator ?: return
        runCatching {
            (view.parent as? ViewGroup)?.removeView(view)
        }.onFailure { error ->
            warnOnce("移除媒体圆点指示器失败", error)
        }
        indicator = null
        setNumPages = null
        setLocation = null
        setTintListMethod = null
        configuredPageCount = -1
        currentIndex = -1
    }

    private fun createNativeIndicator(player: View): View? {
        val classLoader = player.javaClass.classLoader ?: run {
            warnOnce("无法取得 SystemUI classLoader")
            return null
        }
        return runCatching {
            val indicatorClass = classLoader.loadClass(PAGE_INDICATOR)
            if (!View::class.java.isAssignableFrom(indicatorClass)) {
                error("$PAGE_INDICATOR 不是 View")
            }
            val constructor = indicatorClass.getConstructor(
                Context::class.java,
                AttributeSet::class.java
            )
            val pageIndicator = constructor.newInstance(
                player.context,
                null as AttributeSet?
            ) as View
            setNumPages = indicatorClass.getMethod(
                "setNumPages",
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            setLocation = indicatorClass.getMethod(
                "setLocation",
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }
            setTintListMethod = findMethod(indicatorClass, "setTintList") {
                it.parameterCount == 1 &&
                    it.parameterTypes[0] == ColorStateList::class.java
            }
            pageIndicator
        }.onFailure { error ->
            warnOnce("SystemUI 原生 PageIndicator 不可用", error)
        }.getOrNull()
    }

    private fun applyColors(view: View) {
        if (foregroundColors == null) return
        primaryTintList?.let { tint ->
            setTintListMethod?.let { method ->
                runCatching {
                    method.invoke(view, tint)
                }.onFailure { error ->
                    warnOnce("更新媒体圆点基础颜色失败", error)
                }
            }
        }

        applyChildColors(view)
    }

    private fun applyChildColors(view: View) {
        val parent = view as? ViewGroup ?: return
        val selectedTint = primaryTintList ?: return
        val unselectedTint = secondaryTintList ?: return
        for (index in 0 until parent.childCount) {
            (parent.getChildAt(index) as? ImageView)?.imageTintList =
                if (index == currentIndex) selectedTint else unselectedTint
        }
        view.invalidate()
    }

    private fun invokeNumPages(view: View, count: Int): Boolean {
        return runCatching {
            setNumPages?.invoke(view, count)
        }.onFailure { error ->
            warnOnce("更新媒体圆点数量失败", error)
        }.isSuccess
    }

    private fun ensurePageCount(view: View, count: Int): Boolean {
        if (configuredPageCount == count) return true
        if (!invokeNumPages(view, count)) return false
        configuredPageCount = count
        applyChildColors(view)
        return true
    }

    private fun hide(view: View) {
        if (configuredPageCount != 0) {
            invokeNumPages(view, 0)
            configuredPageCount = 0
        }
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        predicate: (Method) -> Boolean
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && predicate(it) }
                ?.apply { isAccessible = true }
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private var warned = false

    private fun warnOnce(message: String, error: Throwable? = null) {
        if (warned) return
        warned = true
        HookLogger.w(TAG, message, error)
    }
}
