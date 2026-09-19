package com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros

import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import java.util.Collections
import java.util.WeakHashMap

/**
 * ColorOS keeps the application identity in the top-right slot and reuses the
 * fifth action slot for the native media-output switch.  SystemUI still owns
 * both source views, so this controller only mirrors their current drawables
 * and forwards the action click to the native source.
 */
internal object IslandExpandedMediaColorOsAccessoryController {
    private const val DEVICE_SWITCH_ICON_INSET = 0.05f

    private val states = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, State>()
    )

    fun apply(
        views: IslandExpandedMediaColorOsAccessoryViews,
        hideDeviceSwitch: Boolean,
        hideCustomActions: Boolean,
        foregroundColor: Int?
    ) {
        val state = synchronized(states) {
            val existing = states[views.player]
            if (
                existing != null &&
                existing.views.container === views.container &&
                existing.views.sourceIcon === views.sourceIcon &&
                existing.views.action4 === views.action4
            ) {
                existing
            } else {
                existing?.restore()
                State.capture(views).also { states[views.player] = it }
            }
        }
        state.applyAppIcon()
        if (hideDeviceSwitch || hideCustomActions) {
            state.restoreAction4()
        } else {
            state.applyDeviceSwitchToAction4(foregroundColor)
        }
    }

    fun restore(views: IslandExpandedMediaColorOsAccessoryViews) {
        states.remove(views.player)?.restore()
    }

    fun applyToFakeView(
        fakeExpandedView: View,
        reference: IslandExpandedMediaColorOsAccessoryViews,
        hideDeviceSwitch: Boolean,
        hideCustomActions: Boolean,
        foregroundColor: Int?
    ) {
        val fakeRoot = fakeExpandedView as? ViewGroup ?: return
        val container = fakeRoot.findViewById<View>(reference.container.id) as? ViewGroup
            ?: return
        val sourceIcon = fakeRoot.findViewById<View>(reference.sourceIcon.id) as? ImageView
            ?: return
        val appIcon = fakeRoot.findViewById<View>(reference.appIcon.id) as? ImageView
            ?: return
        val action4 = fakeRoot.findViewById<View>(reference.action4.id) as? ImageView
            ?: return
        val sourceButton = reference.sourceButton?.id?.takeIf { it != 0 }?.let { id ->
            fakeRoot.findViewById<View>(id)
        }
        apply(
            views = IslandExpandedMediaColorOsAccessoryViews(
                player = fakeRoot,
                container = container,
                sourceIcon = sourceIcon,
                sourceButton = sourceButton,
                appIcon = appIcon,
                action4 = action4
            ),
            hideDeviceSwitch = hideDeviceSwitch,
            hideCustomActions = hideCustomActions,
            foregroundColor = foregroundColor
        )
    }

    fun clear() {
        states.values.toList().forEach(State::restore)
        states.clear()
    }

    private data class State(
        val views: IslandExpandedMediaColorOsAccessoryViews,
        val originalSourceVisibility: Int,
        val originalSourceButtonVisibility: Int?,
        val originalContainerVisibility: Int,
        val originalContainerBackground: Drawable?,
        val originalContainerClickable: Boolean,
        val originalContainerFocusable: Boolean,
        val originalActionVisibility: Int,
        val originalActionDrawable: Drawable?,
        val originalActionContentDescription: CharSequence?,
        val originalActionEnabled: Boolean,
        val originalActionTint: ColorStateList?,
        val originalActionTintBlendMode: BlendMode?,
        var appIconView: ImageView? = null,
        var action4Replaced: Boolean = false
    ) {
        fun applyAppIcon() {
            val drawable = views.appIcon.drawable ?: return
            val iconView = appIconView ?: ImageView(views.container.context).also { view ->
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                view.scaleType = ImageView.ScaleType.FIT_CENTER
                view.isClickable = false
                view.isFocusable = false
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                views.container.addView(view)
                appIconView = view
            }
            iconView.setImageDrawable(copyDrawable(drawable, iconView))
            iconView.imageTintList = null
            iconView.alpha = 1f
            iconView.visibility = View.VISIBLE
            views.container.background = null
            views.sourceIcon.visibility = View.GONE
            views.sourceButton?.visibility = View.GONE
            views.container.visibility = View.VISIBLE
            views.container.isClickable = false
            views.container.isFocusable = false
        }

        fun applyDeviceSwitchToAction4(foregroundColor: Int?) {
            val drawable = views.sourceIcon.drawable ?: run {
                restoreAction4()
                return
            }
            val action4 = views.action4
            action4.setImageDrawable(
                InsetDrawable(copyDrawable(drawable, action4), DEVICE_SWITCH_ICON_INSET)
            )
            action4.contentDescription = views.sourceIcon.contentDescription
            action4.isEnabled = true
            action4.imageTintList = foregroundColor?.let(ColorStateList::valueOf)
                ?: views.sourceIcon.imageTintList
            action4.imageTintBlendMode = if (foregroundColor != null) {
                BlendMode.SRC_IN
            } else {
                views.sourceIcon.imageTintBlendMode
            }
            action4.visibility = View.VISIBLE
            action4.setOnClickListener {
                if (views.sourceButton?.performClick() == true) return@setOnClickListener
                if (views.sourceIcon.performClick()) return@setOnClickListener
                val wasClickable = views.container.isClickable
                if (!wasClickable && originalContainerClickable) {
                    views.container.isClickable = true
                }
                views.container.performClick()
                if (!wasClickable) views.container.isClickable = false
            }
            action4Replaced = true
        }

        fun restoreAction4() {
            if (!action4Replaced) return
            val action4 = views.action4
            action4.setImageDrawable(originalActionDrawable)
            action4.contentDescription = originalActionContentDescription
            action4.isEnabled = originalActionEnabled
            action4.imageTintList = originalActionTint
            action4.imageTintBlendMode = originalActionTintBlendMode
            action4.visibility = originalActionVisibility
            action4.setOnClickListener(null)
            action4Replaced = false
        }

        fun restore() {
            restoreAction4()
            appIconView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
            }
            appIconView = null
            views.container.background = originalContainerBackground
            views.sourceIcon.visibility = originalSourceVisibility
            views.sourceButton?.visibility = originalSourceButtonVisibility ?: View.VISIBLE
            views.container.visibility = originalContainerVisibility
            views.container.isClickable = originalContainerClickable
            views.container.isFocusable = originalContainerFocusable
        }

        private fun copyDrawable(drawable: Drawable, target: ImageView): Drawable {
            return runCatching {
                drawable.constantState
                    ?.newDrawable(target.resources, target.context.theme)
                    ?.mutate()
            }.getOrNull() ?: drawable
        }

        companion object {
            fun capture(views: IslandExpandedMediaColorOsAccessoryViews): State {
                return State(
                    views = views,
                    originalSourceVisibility = views.sourceIcon.visibility,
                    originalSourceButtonVisibility = views.sourceButton?.visibility,
                    originalContainerVisibility = views.container.visibility,
                    originalContainerBackground = views.container.background,
                    originalContainerClickable = views.container.isClickable,
                    originalContainerFocusable = views.container.isFocusable,
                    originalActionVisibility = views.action4.visibility,
                    originalActionDrawable = views.action4.drawable,
                    originalActionContentDescription = views.action4.contentDescription,
                    originalActionEnabled = views.action4.isEnabled,
                    originalActionTint = views.action4.imageTintList,
                    originalActionTintBlendMode = views.action4.imageTintBlendMode
                )
            }
        }
    }
}

internal data class IslandExpandedMediaColorOsAccessoryViews(
    val player: ViewGroup,
    val container: ViewGroup,
    val sourceIcon: ImageView,
    val sourceButton: View?,
    val appIcon: ImageView,
    val action4: ImageView
)
