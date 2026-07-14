package com.lidesheng.hyperlyric.root.mediacard.island

import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.MediaCoverRotationController
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object IslandExpandedMediaElementController {
    private val states = Collections.synchronizedMap(
        WeakHashMap<View, ElementState>()
    )
    private val circleOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    fun apply(
        elements: IslandExpandedMediaElements,
        coverStyle: Int,
        hideCoverSource: Boolean,
        hideDeviceSwitch: Boolean,
        playbackActive: Boolean
    ) {
        if (
            coverStyle == RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT &&
            !hideCoverSource &&
            !hideDeviceSwitch
        ) {
            restore(elements)
            return
        }

        val state = states.getOrPut(elements.albumView) {
            ElementState.capture(elements)
        }

        when (coverStyle) {
            RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_CIRCLE -> {
                state.restoreHiddenCover()
                state.applyCircle()
                MediaCoverRotationController.detach(elements.albumImage)
            }

            RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_ROTATING_CIRCLE -> {
                state.restoreHiddenCover()
                state.applyCircle()
                MediaCoverRotationController.attach(elements.albumImage, playbackActive)
            }

            RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN -> {
                MediaCoverRotationController.detach(elements.albumImage)
                state.restoreOutlines()
                state.hideCover()
            }

            else -> {
                MediaCoverRotationController.detach(elements.albumImage)
                state.restoreCover()
            }
        }

        state.applyCoverSourceHidden(hideCoverSource)
        state.applyDeviceSwitchHidden(hideDeviceSwitch)
    }

    fun restore(elements: IslandExpandedMediaElements) {
        val state = states.remove(elements.albumView) ?: run {
            MediaCoverRotationController.detach(elements.albumImage)
            return
        }
        MediaCoverRotationController.detach(elements.albumImage)
        state.restoreAll()
    }

    fun cleanup() {
        states.values.toList().forEach { state ->
            MediaCoverRotationController.detach(state.elements.albumImage)
            state.restoreAll()
        }
        states.clear()
    }

    private data class ElementState(
        val elements: IslandExpandedMediaElements,
        var albumVisibility: Int,
        val albumOutlineProvider: ViewOutlineProvider?,
        val albumClipToOutline: Boolean,
        val imageOutlineProvider: ViewOutlineProvider?,
        val imageClipToOutline: Boolean,
        var coverSourceVisibility: Int,
        var deviceSwitchVisibility: Int,
        val titleGoneStartMargin: Int,
        val artistGoneStartMargin: Int,
        val actionsGoneTopMargin: Int,
        val firstActionGoneTopMargin: Int,
        var coverHidden: Boolean = false,
        var coverOutlined: Boolean = false,
        var coverSourceHidden: Boolean = false,
        var deviceSwitchHidden: Boolean = false
    ) {
        fun applyCircle() {
            if (
                coverOutlined &&
                elements.albumView.outlineProvider === circleOutlineProvider &&
                !elements.albumView.clipToOutline &&
                elements.albumImage.outlineProvider === circleOutlineProvider &&
                elements.albumImage.clipToOutline
            ) {
                return
            }
            elements.albumView.outlineProvider = circleOutlineProvider
            elements.albumView.clipToOutline = false
            elements.albumImage.outlineProvider = circleOutlineProvider
            elements.albumImage.clipToOutline = true
            elements.albumView.invalidateOutline()
            elements.albumImage.invalidateOutline()
            coverOutlined = true
        }

        fun hideCover() {
            if (
                coverHidden &&
                elements.albumView.visibility == View.GONE &&
                elements.albumImage.visibility == View.GONE
            ) {
                return
            }
            if (elements.albumView.visibility != View.GONE) {
                albumVisibility = elements.albumView.visibility
            }
            val albumHeight = elements.albumView.height.takeIf { it > 0 }
                ?: elements.albumView.layoutParams.height
            val textGoneStartMargin = (
                26f * elements.player.resources.displayMetrics.density
            ).roundToInt()
            elements.title.setGoneMargin(
                GONE_START_MARGIN_FIELD,
                textGoneStartMargin
            )
            elements.artist.setGoneMargin(
                GONE_START_MARGIN_FIELD,
                textGoneStartMargin
            )
            elements.actionsAnchor.setGoneMargin(GONE_TOP_MARGIN_FIELD, albumHeight)
            elements.firstAction.setGoneMargin(
                GONE_TOP_MARGIN_FIELD,
                albumHeight + elements.firstAction.topMargin
            )
            elements.albumView.visibility = View.GONE
            coverHidden = true
            elements.player.requestLayout()
        }

        fun restoreHiddenCover() {
            if (!coverHidden) return
            elements.title.setGoneMargin(GONE_START_MARGIN_FIELD, titleGoneStartMargin)
            elements.artist.setGoneMargin(GONE_START_MARGIN_FIELD, artistGoneStartMargin)
            elements.actionsAnchor.setGoneMargin(GONE_TOP_MARGIN_FIELD, actionsGoneTopMargin)
            elements.firstAction.setGoneMargin(
                GONE_TOP_MARGIN_FIELD,
                firstActionGoneTopMargin
            )
            elements.albumView.visibility = albumVisibility
            coverHidden = false
            elements.player.requestLayout()
        }

        fun restoreOutlines() {
            if (!coverOutlined) return
            elements.albumView.outlineProvider = albumOutlineProvider
            elements.albumView.clipToOutline = albumClipToOutline
            elements.albumImage.outlineProvider = imageOutlineProvider
            elements.albumImage.clipToOutline = imageClipToOutline
            elements.albumView.invalidateOutline()
            elements.albumImage.invalidateOutline()
            coverOutlined = false
        }

        fun restoreCover() {
            restoreHiddenCover()
            restoreOutlines()
        }

        fun applyCoverSourceHidden(hidden: Boolean) {
            if (hidden) {
                if (coverSourceHidden && elements.coverSource.visibility == View.GONE) return
                if (elements.coverSource.visibility != View.GONE) {
                    coverSourceVisibility = elements.coverSource.visibility
                }
                elements.coverSource.visibility = View.GONE
                coverSourceHidden = true
            } else if (coverSourceHidden) {
                elements.coverSource.visibility = coverSourceVisibility
                coverSourceHidden = false
            }
        }

        fun applyDeviceSwitchHidden(hidden: Boolean) {
            if (hidden) {
                if (deviceSwitchHidden && elements.deviceSwitch.visibility == View.GONE) return
                if (elements.deviceSwitch.visibility != View.GONE) {
                    deviceSwitchVisibility = elements.deviceSwitch.visibility
                }
                elements.deviceSwitch.visibility = View.GONE
                deviceSwitchHidden = true
            } else if (deviceSwitchHidden) {
                elements.deviceSwitch.visibility = deviceSwitchVisibility
                deviceSwitchHidden = false
            }
        }

        fun restoreAll() {
            restoreCover()
            applyCoverSourceHidden(false)
            applyDeviceSwitchHidden(false)
        }

        companion object {
            fun capture(elements: IslandExpandedMediaElements): ElementState {
                return ElementState(
                    elements = elements,
                    albumVisibility = elements.albumView.visibility,
                    albumOutlineProvider = elements.albumView.outlineProvider,
                    albumClipToOutline = elements.albumView.clipToOutline,
                    imageOutlineProvider = elements.albumImage.outlineProvider,
                    imageClipToOutline = elements.albumImage.clipToOutline,
                    coverSourceVisibility = elements.coverSource.visibility,
                    deviceSwitchVisibility = elements.deviceSwitch.visibility,
                    titleGoneStartMargin = elements.title.getGoneMargin(
                        GONE_START_MARGIN_FIELD
                    ),
                    artistGoneStartMargin = elements.artist.getGoneMargin(
                        GONE_START_MARGIN_FIELD
                    ),
                    actionsGoneTopMargin = elements.actionsAnchor.getGoneMargin(
                        GONE_TOP_MARGIN_FIELD
                    ),
                    firstActionGoneTopMargin = elements.firstAction.getGoneMargin(
                        GONE_TOP_MARGIN_FIELD
                    )
                )
            }
        }
    }

    private const val GONE_START_MARGIN_FIELD = "goneStartMargin"
    private const val GONE_TOP_MARGIN_FIELD = "goneTopMargin"

    private val View.topMargin: Int
        get() = (layoutParams as ViewGroup.MarginLayoutParams).topMargin

    private fun View.getGoneMargin(fieldName: String): Int {
        val params = layoutParams
        return params.javaClass.getField(fieldName).getInt(params)
    }

    private fun View.setGoneMargin(fieldName: String, value: Int) {
        val params = layoutParams
        params.javaClass.getField(fieldName).setInt(params, value)
        layoutParams = params
    }
}

internal data class IslandExpandedMediaElements(
    val albumView: View,
    val albumImage: ImageView,
    val coverSource: ImageView,
    val deviceSwitch: View,
    val title: View,
    val artist: View,
    val actionsAnchor: View,
    val firstAction: View,
    val player: View
)
