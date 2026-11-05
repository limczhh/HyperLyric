package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shared expanded endpoint for static layout and the panel transition. */
internal class LandscapeMediaLayout(
    private val host: LinearLayout,
    private val header: LinearLayout,
    private val meta: ViewGroup,
    private val artworkParent: ViewGroup,
    private val artwork: ImageView,
    private val artist: TextView,
    private val title: TextView,
    private val noPlayback: TextView,
    private val audio: View,
    private val video: View,
    private val progress: View,
    private val volume: View,
    private val devices: View,
    private val divider: View?,
    private val expandedActionWidth: (View, Int) -> Int = { _, width -> width },
    private val prepareActions: () -> Unit = {},
) {
    private val coverIndex = meta.indexOfChild(artworkParent)
    private val orientation = host.orientation
    private val views = listOf(header, meta, artworkParent, artwork, artist, title,
        noPlayback, audio, video, progress, volume, devices) + listOfNotNull(divider)
    private val params = views.associateWith { copy(it.layoutParams) }
    private val dividerVisibility = divider?.visibility
    private val coverScale = artwork.scaleType
    // Match the native title-container top inset (currently 16dp), not the
    // visible glyph's top. Shared by the panel and notification MiPlay hosts.
    private val artworkInset = header.paddingTop.coerceAtLeast(0)
    private var actionSpacing: NativeMediaActionSpacing? = null
    // The original landscape left column deliberately reserves vertical space.
    // A stacked right column needs the system's portrait spacing instead.
    private val portraitResources = host.context.createConfigurationContext(
        Configuration(host.resources.configuration).apply { orientation = Configuration.ORIENTATION_PORTRAIT }
    ).resources
    private val textGap = portraitDimension("miplay_detail_header_subtitle_margin_top",
        (artist.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    private val progressGap = portraitDimension("miplay_detail_header_seek_bar_margin_top",
        (progress.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    private val volumeGap = portraitDimension("miplay_detail_header_volume_bar_margin_top_land",
        (volume.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
    private val listGap = portraitDimension("miplay_detail_recycler_margin_top",
        (devices.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0)
    private var actionWidths = emptyMap<View, Int>()
    private var flowVisibility: List<Int>? = null
    private var flowActionWidths: List<Int>? = null
    private val devicePadding = intArrayOf(devices.paddingLeft, devices.paddingTop, devices.paddingRight, devices.paddingBottom)
    private var deviceHeight = 0
    var active = false
        private set
    val availableDeviceHeight: Int get() = deviceHeight

    fun apply(): Boolean {
        if (active) return true
        val width = host.width - host.paddingLeft - host.paddingRight
        val height = host.height - host.paddingTop - host.paddingBottom
        val rightWidth = width - height
        // Refuse a layout that cannot preserve the native control sizes.
        if (height <= 0 || rightWidth <= header.paddingLeft + header.paddingRight) {
            return false
        }
        val innerWidth = rightWidth - header.paddingLeft - header.paddingRight
        // MiPlay has a full-width vertical Header even in landscape. Measuring
        // that widened Header first stretches its MATCH_CONSTRAINT action row,
        // which is NOT the native row width and would reject the right column.
        // Capture controls at the native single-column width BEFORE reparenting.
        val widthId = host.resources.getIdentifier("miplay_detail_header_width", "dimen", "miui.systemui.plugin")
        val nativeWidth = (if (widthId != 0) host.resources.getDimensionPixelSize(widthId) else 0)
            .takeIf { it > 0 } ?: portraitDimension("miplay_detail_content_width", rightWidth)
        actionSpacing = NativeMediaActionSpacing.capture(header, nativeWidth,
            listOf(title, artist, noPlayback), listOf(audio, video))
        actionWidths = listOf(audio, video).associateWith { it.measuredWidth }
        prepareActions()
        val actions = listOf(audio, video).filter { it.visibility != View.GONE }
        if (actions.any { actionWidths.getValue(it) > innerWidth }) {
            return false
        }

        active = true
        try {
            host.orientation = LinearLayout.HORIZONTAL
            meta.removeView(artworkParent)
            divider?.visibility = View.GONE
            header.layoutParams = copy(params.getValue(header)).apply {
                this.width = rightWidth
                this.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            meta.layoutParams = copy(params.getValue(meta)).apply { this.width = -1; this.height = -2 }

            configureMetadata()
            header.measure(exact(rightWidth), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            // Do not reject an otherwise valid layout just because the entire
            // first device row plus Recycler padding is not initially visible.
            if (header.measuredHeight + listGap >= height) {
                restore()
                return false
            }

            val inset = coverInset(height)
            val coverSize = height - inset * 2
            artworkParent.layoutParams = LinearLayout.LayoutParams(coverSize, coverSize).apply {
                // Keep the original H x H column's measurement reservation.
                // Without these margins a WRAP_CONTENT panel could shrink along
                // with the cover, and move/resize the otherwise unchanged right column.
                setMargins(inset, inset, inset, inset)
            }
            artwork.layoutParams = copy(params.getValue(artwork)).apply { this.width = 0; this.height = 0 }
            artwork.scaleType = ImageView.ScaleType.CENTER_CROP
            host.addView(artworkParent, 0)
            // Keep Header attached: its native onDetachedFromWindow clears the
            // current cover and observer state. Devices stay in their host too.
            // The root's static onLayout places the list below Header. Reserve
            // no extra horizontal slot for it during LinearLayout measurement.
            devices.layoutParams = copy(params.getValue(devices)).apply {
                this.width = 0
                this.height = 0
                (this as? LinearLayout.LayoutParams)?.weight = 0f
                (this as? ViewGroup.MarginLayoutParams)?.let { it.topMargin = 0; it.bottomMargin = 0 }
            }
            devices.setPadding(devicePadding[0],
                portraitDimension("miplay_detail_recycler_padding_top", devicePadding[1]),
                devicePadding[2], portraitDimension("miplay_detail_recycler_padding_bottom", devicePadding[3]))
            settle()
            return true
        } catch (error: Throwable) {
            restore()
            throw error
        }
    }

    /** Resolve the final static geometry after the native animator's last frame. */
    fun settle() {
        if (!active) return
        listOf(progress, volume, devices, video).forEach { it.scaleX = 1f; it.scaleY = 1f }
        val width = host.width - host.paddingLeft - host.paddingRight
        val height = host.height - host.paddingTop - host.paddingBottom
        val rightWidth = width - height
        configureMetadata()
        header.forceLayout()
        artworkParent.forceLayout()
        val inset = coverInset(height)
        val coverSize = height - inset * 2
        artworkParent.measure(exact(coverSize), exact(coverSize))
        val coverLeft = host.paddingLeft + inset
        val coverTop = host.paddingTop + inset
        artworkParent.layout(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize)
        header.measure(exact(rightWidth), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val x = host.paddingLeft + height
        header.layout(x, host.paddingTop, x + rightWidth, host.paddingTop + header.measuredHeight)
        deviceHeight = (height - header.measuredHeight - listGap).coerceAtLeast(0)
        devices.measure(exact(rightWidth), exact(deviceHeight))
        val y = host.paddingTop + header.measuredHeight + listGap
        devices.layout(x, y, x + rightWidth, y + deviceHeight)
    }

    private fun configureMetadata() {
        val visibility = listOf(artist, title, noPlayback, audio, video, progress).map { it.visibility }
        val widths = listOf(audio, video).map { row ->
            expandedActionWidth(row, actionWidths[row]?.takeIf { it > 0 } ?: params.getValue(row).width)
        }
        if (visibility == flowVisibility && widths == flowActionWidths) return
        val actions = listOf(audio, video).filter { it.visibility != View.GONE }
        // Keep the native TextViews and action rows; only replace constraints.
        constrain(title, -2, params.getValue(title).height,
            "startToStart" to 0, "endToEnd" to 0, "topToTop" to 0, "constrainedWidth" to true)
        constrain(artist, -2, params.getValue(artist).height,
            "startToStart" to 0, "endToEnd" to 0, "topToBottom" to title.id,
            "topMargin" to if (title.visibility != View.GONE) textGap else 0,
            "constrainedWidth" to true)
        constrain(noPlayback, -2, params.getValue(noPlayback).height,
            "startToStart" to 0, "endToEnd" to 0, "topToTop" to 0, "constrainedWidth" to true)
        val label = listOf(artist, title, noPlayback).firstOrNull { it.visibility != View.GONE }
        listOf(audio, video).forEachIndexed { index, row ->
            constrain(row, widths[index],
                params.getValue(row).height, "startToStart" to 0, "endToEnd" to 0,
                "topToBottom" to (label?.id ?: 0), "topMargin" to (actionSpacing?.before(row) ?: 0))
        }
        constrain(progress, 0, params.getValue(progress).height,
            "startToStart" to 0, "endToEnd" to 0,
            "topToBottom" to (actions.firstOrNull()?.id ?: label?.id ?: 0), "topMargin" to progressGap)
        volume.layoutParams = copy(params.getValue(volume)).apply {
            this.width = -1
            (this as ViewGroup.MarginLayoutParams).topMargin = volumeGap
        }
        flowVisibility = visibility
        flowActionWidths = widths
    }

    fun restore() {
        if (!active) return
        listOf(progress, volume, devices, video).forEach { it.scaleX = 1f; it.scaleY = 1f }
        (artworkParent.parent as? ViewGroup)?.removeView(artworkParent)
        params.forEach { (view, original) -> MediaLayoutParams.assign(view, copy(original)) }
        meta.addView(artworkParent, coverIndex)
        dividerVisibility?.let { divider?.visibility = it }
        artwork.scaleType = coverScale
        host.orientation = orientation
        devices.setPadding(devicePadding[0], devicePadding[1], devicePadding[2], devicePadding[3])
        deviceHeight = 0
        active = false
    }

    private fun constrain(view: View, width: Int, height: Int, vararg values: Pair<String, Any>) {
        MediaLayoutParams.constrain(view, width, height, *values)
    }
    private fun portraitDimension(name: String, fallback: Int): Int {
        val id = portraitResources.getIdentifier(name, "dimen", "miui.systemui.plugin")
        return if (id != 0) portraitResources.getDimensionPixelSize(id) else fallback
    }
    private fun coverInset(height: Int) = artworkInset.coerceAtMost(((height - 1) / 2).coerceAtLeast(0))
    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
    private fun copy(original: ViewGroup.LayoutParams): ViewGroup.LayoutParams =
        MediaLayoutParams.copy(original)
}
