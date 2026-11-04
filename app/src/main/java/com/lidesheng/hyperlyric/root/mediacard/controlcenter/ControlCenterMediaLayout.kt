package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.res.Configuration
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal data class ControlCenterMediaLayoutOptions(
    val immersiveCoverLayout: Boolean = false,
    val progressHeadGlow: Boolean = false,
    val customActions: Boolean = false,
)

/** Changes native component bounds only. Text styling and transition drawing stay native. */
internal class ControlCenterMediaLayout private constructor(
    val header: LinearLayout,
    private val meta: ViewGroup,
    private val coverParent: ViewGroup,
    private val cover: ImageView,
    private val title: TextView,
    private val artist: TextView,
    private val noPlayback: TextView,
    private val audio: View,
    private val video: View,
    private val progress: View,
    private val volume: View,
    val contentView: ViewGroup? = null,
    private val options: ControlCenterMediaLayoutOptions = ControlCenterMediaLayoutOptions(),
) {
    private val padding = intArrayOf(header.paddingLeft, header.paddingTop, header.paddingRight, header.paddingBottom)
    private var savedParams = listOf(header, meta, coverParent, cover, title, artist, noPlayback,
        audio, video, progress, volume).associateWith { it.layoutParams }
    private var titleArtistGap = margin(artist).topMargin
    private var progressGap = margin(progress).topMargin
    private var actionSpacing: NativeMediaActionSpacing? = null
    private val metadataLabels = listOf(title, artist, noPlayback)
    private var transitionClip: ControlCenterMediaTransitionClip? = null
    private var artworkBlend: AlbumBlurTransition? = null
    private var artworkProgress = 1f
    private var nativeWidth = 0
    private var nativeAudioWidth = 0
    private var nativeVideoWidth = 0
    private var nativeProgressHeight = 0
    var isApplied = false
        private set
    var isTransitioning = false
    var panelSizing: ControlCenterMediaPanelSizing? = null
    private var landscape: LandscapeMediaLayout? = null
    private var modalLandscapeSizing: MiPlayLandscapeSizing? = null
    private var landscapeSize: Pair<Int, Int>? = null
    private var failedLandscapeSize: Pair<Int, Int>? = null
    private var nativeUpdateDepth = 0
    private val progressGlow = ControlCenterMediaProgressGlow(header, progress, options.progressHeadGlow)
    private val customActions = ControlCenterMediaCustomActions(
        audio as? ViewGroup,
        title,
        options.customActions && options.immersiveCoverLayout,
        ::refreshCustomActions
    )
    var isPanelHost = false
    private var panelExpanded = false
    private var panelAnimating = false
    var transitionGeneration = 0
        private set
    val isLandscapeApplied: Boolean get() = landscape?.active == true
    val landscapeDeviceHeight: Int? get() = landscape?.takeIf { it.active }?.availableDeviceHeight

    fun layoutLandscapeBounds(): Boolean {
        val current = landscape?.takeIf { it.active } ?: return false
        current.settle()
        return true
    }

    init {
        contentView?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (header.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                applyLandscapeIfReady()
            }
        }
    }

    fun beginNativeUpdate() {
        nativeUpdateDepth++
        if (nativeUpdateDepth == 1) {
            transitionGeneration++
            restore()
            modalLandscapeSizing?.restore()
        }
    }

    fun endNativeUpdate(): Boolean {
        nativeUpdateDepth--
        if (nativeUpdateDepth != 0) return false
        savedParams = savedParams.keys.associateWith { it.layoutParams }
        padding[0] = header.paddingLeft; padding[1] = header.paddingTop
        padding[2] = header.paddingRight; padding[3] = header.paddingBottom
        titleArtistGap = margin(artist).topMargin
        progressGap = margin(progress).topMargin
        actionSpacing = null
        nativeWidth = 0
        failedLandscapeSize = null
        contentView?.requestLayout()
        return true
    }

    fun preparePanelTransition() {
        transitionGeneration++
        applyLandscapeIfReady(forTransition = true)
        panelAnimating = true
        panelExpanded = false
    }

    fun prepareModalLandscape() {
        if (isPanelHost || isTransitioning) return
        val content = contentView ?: return
        val sizing = modalLandscapeSizing ?: MiPlayLandscapeSizing(content).also { modalLandscapeSizing = it }
        sizing.prepare()
    }

    fun markPanelShown() {
        panelAnimating = false
        panelExpanded = true
        customActions.setVisible(true)
        applyLandscapeIfReady()
        landscape?.settle()
    }

    fun endPanelAnimation() { panelAnimating = false }

    fun setCustomActionsVisible(visible: Boolean) { customActions.setVisible(visible) }

    private fun applyLandscapeIfReady(forTransition: Boolean = false) {
        if (header.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        if (!options.immersiveCoverLayout) {
            if (nativeUpdateDepth == 0 && !isTransitioning) progressGlow.apply()
            return
        }
        if (nativeUpdateDepth > 0 || isTransitioning || (!forTransition &&
                (panelAnimating || (isPanelHost && !panelExpanded)))) {
            return
        }
        progressGlow.apply()
        val host = contentView as? LinearLayout ?: return
        if ((isPanelHost && host.orientation != LinearLayout.HORIZONTAL) || host.width <= 0 || host.height <= 0) {
            return
        }
        val size = host.width to host.height
        if (isLandscapeApplied && landscapeSize == size) return
        if (failedLandscapeSize == size) return
        if (isApplied) restore()
        val listId = host.resources.getIdentifier("list", "id", "miui.systemui.plugin")
        val list = host.findViewById<View>(listId) ?: return
        if (header.parent !== host || list.parent !== host) {
            return
        }
        val dividerId = host.resources.getIdentifier("spilt_screen_divider", "id", "miui.systemui.plugin")
        val next = LandscapeMediaLayout(host, header, meta, coverParent, cover, artist, title,
            noPlayback, audio, video, progress, volume, list, host.findViewById(dividerId),
            customActions::expandedWidth,
            { customActions.captureNativeGeometry(); customActions.refresh() })
        if (next.apply()) {
            landscape = next
            landscapeSize = size
            isApplied = true
            artworkBlend?.setActive(false)
        } else {
            customActions.restore()
            failedLandscapeSize = size
        }
    }

    fun ensureApplied() {
        if (!options.immersiveCoverLayout) {
            applyNativeFeatures()
        } else if (!isApplied) {
            apply()
        }
    }

    fun apply() {
        if (!options.immersiveCoverLayout) {
            applyNativeFeatures()
            return
        }
        if (header.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyLandscapeIfReady()
            return
        }
        applyPortrait()
    }

    private fun applyPortrait() {
        progressGlow.apply()
        if (nativeWidth == 0) {
            val id = header.resources.getIdentifier("miplay_detail_content_width", "dimen", "miui.systemui.plugin")
            val width = if (id != 0) header.resources.getDimensionPixelSize(id) else header.measuredWidth
            if (width > 0) captureNativeGeometry(width)
        }
        customActions.refresh()
        isApplied = true
        artworkBlend?.setActive(true)
        header.setPadding(0, 0, 0, padding[3])
        setSize(header, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setSize(meta, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setConstraints(coverParent, 0, 0, "startToStart" to 0, "endToEnd" to 0,
            "topToTop" to 0, "dimensionRatio" to "1:1")
        setConstraints(cover, 0, 0, "startToStart" to 0, "endToEnd" to 0,
            "topToTop" to 0, "bottomToBottom" to 0)

        // Center the native text VIEW rather than changing text gravity, colors, alpha,
        // font metrics or spacing. Native morphing can interpolate these exact bounds.
        setConstraints(artist, ViewGroup.LayoutParams.WRAP_CONTENT, savedParams.getValue(artist).height,
            "startToStart" to 0, "endToEnd" to 0, "bottomToBottom" to coverParent.id,
            "bottomMargin" to padding[3], "leftMargin" to padding[0], "rightMargin" to padding[2],
            "constrainedWidth" to true)
        setConstraints(title, ViewGroup.LayoutParams.WRAP_CONTENT, savedParams.getValue(title).height,
            "startToStart" to 0, "endToEnd" to 0, "bottomToTop" to artist.id,
            "bottomMargin" to titleArtistGap, "leftMargin" to padding[0], "rightMargin" to padding[2],
            "constrainedWidth" to true)
        setConstraints(noPlayback, ViewGroup.LayoutParams.WRAP_CONTENT, savedParams.getValue(noPlayback).height,
            "startToStart" to 0, "endToEnd" to 0, "bottomToBottom" to coverParent.id,
            "bottomMargin" to padding[3], "leftMargin" to padding[0], "rightMargin" to padding[2],
            "constrainedWidth" to true)
        configureControls()
        val nativeVolume = savedParams.getValue(volume)
        val volumeParams = margin(volume)
        if (volumeParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            volumeParams.height != nativeVolume.height ||
            volumeParams.leftMargin != padding[0] || volumeParams.rightMargin != padding[2]) {
            volume.layoutParams = copySize(nativeVolume, ViewGroup.LayoutParams.MATCH_PARENT, nativeVolume.height).also {
                (it as ViewGroup.MarginLayoutParams).apply { leftMargin = padding[0]; rightMargin = padding[2] }
            }
        }
    }

    private fun captureNativeGeometry(width: Int) {
        actionSpacing = NativeMediaActionSpacing.capture(header, width, metadataLabels, listOf(audio, video))
        customActions.captureNativeGeometry()
        nativeWidth = width
        nativeAudioWidth = audio.measuredWidth
        nativeProgressHeight = progress.measuredHeight
        nativeVideoWidth = video.measuredWidth
    }

    private fun configureControls() {
        listOf(audio to nativeAudioWidth, video to nativeVideoWidth).forEach { (view, measuredWidth) ->
            val native = savedParams.getValue(view)
            setConstraints(view, customActions.expandedWidth(view,
                measuredWidth.takeIf { it > 0 } ?: native.width), native.height,
                "startToStart" to 0, "endToEnd" to 0, "topToBottom" to coverParent.id,
                "topMargin" to actionGap(view))
        }
        val actions = listOf(audio, video).firstOrNull { it.visibility != View.GONE }
        setConstraints(progress, 0, savedParams.getValue(progress).height,
            "startToStart" to 0, "endToEnd" to 0, "topToBottom" to (actions?.id ?: coverParent.id),
            "topMargin" to progressGap, "leftMargin" to padding[0], "rightMargin" to padding[2])
    }

    private fun actionGap(view: View): Int = actionSpacing?.before(view)
        ?: (savedParams.getValue(view) as ViewGroup.MarginLayoutParams).topMargin

    private fun applyNativeFeatures() {
        progressGlow.apply()
        refreshCustomActions()
    }

    private fun refreshCustomActions() {
        if (!customActions.refresh() || !isApplied || isTransitioning || nativeUpdateDepth > 0) return
        if (isLandscapeApplied) landscape?.settle()
        else if (nativeWidth > 0) configureControls()
    }

    fun refreshMediaFeatures() { applyNativeFeatures() }

    fun beginTransition(card: View) {
        if (!isApplied) return
        if (transitionClip == null) transitionClip = ControlCenterMediaTransitionClip(card)
        isTransitioning = true
        clipTransition()
    }

    fun enableArtworkBlend(loader: ClassLoader) {
        if (artworkBlend != null) return
        artworkBlend = AlbumBlurTransition(cover, loader).also { it.setActive(isApplied) }
        cover.setTag(com.lidesheng.hyperlyric.R.id.control_center_media_layout_state, this)
        cover.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncArtworkBlend() }
        // Resolve labels after their parent has completed the current layout.
        meta.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncArtworkBlend() }
        cover.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { syncArtworkBlend() }
            override fun onViewDetachedFromWindow(view: View) { artworkBlend?.clear() }
        })
    }

    fun setArtworkBackground(background: View) { artworkBlend?.setBackground(background) }

    fun onArtworkChanged() {
        artworkBlend?.invalidateContent()
        syncArtworkBlend()
        refreshMediaFeatures()
    }

    fun syncArtworkBlend(progress: Float = artworkProgress) {
        artworkProgress = progress.coerceIn(0f, 1f)
        if (!isApplied || isLandscapeApplied) {
            artworkBlend?.clear()
            return
        }
        val labels = metadataLabels.filter { it.visibility != View.GONE && it.height > 0 }
        if (labels.isEmpty()) {
            artworkBlend?.clear()
            return
        }
        // Labels and coverParent share meta as their parent. Convert their actual
        // native container top into artwork-local coordinates. The other endpoint
        // is the artwork's bottom, even when the artist has bottom padding.
        val origin = coverParent.top + cover.top
        artworkBlend?.sync(labels.minOf { it.top } - origin, cover.height, artworkProgress)
    }

    fun drawArtwork(canvas: Canvas, original: () -> Any?): Any? {
        val blend = artworkBlend ?: return original()
        return blend.draw(canvas, original)
    }

    fun clipTransition() {
        if (isApplied && isTransitioning) transitionClip?.apply()
    }

    fun finishTransition() {
        transitionClip?.restore()
        transitionClip = null
        isTransitioning = false
    }

    fun forceHeaderHeight(width: Int): Int {
        if (!options.immersiveCoverLayout) {
            progressGlow.apply()
            return header.measuredHeight
        }
        if (isTransitioning) return header.measuredHeight
        if (header.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!isLandscapeApplied) restore()
            return header.measuredHeight
        }
        if (nativeWidth != width) {
            restore()
            captureNativeGeometry(width)
        }
        ensureApplied()
        if (!isApplied || width <= 0) return header.measuredHeight
        setSize(coverParent, width, width)
        setSize(cover, width, width)
        configureControls()
        var actionBlockHeight = 0
        listOf(audio, video).filter { it.visibility != View.GONE }.forEach { view ->
            measureNative(view, view.layoutParams.width.coerceAtLeast(0))
            actionBlockHeight = maxOf(actionBlockHeight, actionGap(view) + view.measuredHeight)
        }
        val progressHeight = if (progress.visibility == View.GONE) 0 else {
            measureNative(progress, (width - padding[0] - padding[2]).coerceAtLeast(1))
            progress.measuredHeight.takeIf { it > 0 } ?: nativeProgressHeight
        }
        val metaHeight = width + actionBlockHeight +
            (if (progress.visibility != View.GONE) progressGap + progressHeight else 0)
        setSize(meta, ViewGroup.LayoutParams.MATCH_PARENT, metaHeight)
        var height = header.paddingTop + header.paddingBottom
        for (index in 0 until header.childCount) {
            val child = header.getChildAt(index)
            if (child.visibility == View.GONE) continue
            val margins = margin(child)
            if (child === meta) height += metaHeight else {
                measureNative(child, (width - margins.leftMargin - margins.rightMargin).coerceAtLeast(1))
                height += child.measuredHeight
            }
            height += margins.topMargin + margins.bottomMargin
        }
        setSize(header, ViewGroup.LayoutParams.MATCH_PARENT, height)
        header.measure(exact(width), exact(height))
        return height
    }

    private fun measureNative(view: View, width: Int) {
        val height = savedParams[view]?.height ?: view.layoutParams.height
        view.measure(exact(width), View.MeasureSpec.makeMeasureSpec(height.coerceAtLeast(0),
            if (height >= 0) View.MeasureSpec.EXACTLY else View.MeasureSpec.UNSPECIFIED))
    }

    fun availableDeviceHeight(maxHeight: Int, listTopMargin: Int, widthHint: Int): Int {
        if (widthHint > 0 && !isTransitioning) forceHeaderHeight(widthHint)
        return (maxHeight - header.measuredHeight - listTopMargin).coerceAtLeast(0)
    }

    fun restore() {
        customActions.restore()
        if (!isApplied) return
        artworkBlend?.setActive(false)
        finishTransition()
        if (isLandscapeApplied) {
            landscape?.restore()
            landscape = null
            landscapeSize = null
            isApplied = false
            return
        }
        savedParams.forEach { (view, params) -> view.layoutParams = params }
        header.setPadding(padding[0], padding[1], padding[2], padding[3])
        isApplied = false
    }

    private fun margin(view: View) = view.layoutParams as ViewGroup.MarginLayoutParams
    private fun exact(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun setSize(view: View, width: Int, height: Int) {
        val current = view.layoutParams
        val weighted = current is LinearLayout.LayoutParams && current.weight != 0f
        if (current.width == width && current.height == height && !weighted) return
        view.layoutParams = copySize(current, width, height).also {
            if (it is LinearLayout.LayoutParams) it.weight = 0f
        }
    }

    private fun setConstraints(view: View, width: Int, height: Int, vararg values: Pair<String, Any>) {
        MediaLayoutParams.constrain(view, width, height, *values)
    }

    private fun copySize(params: ViewGroup.LayoutParams, width: Int, height: Int): ViewGroup.LayoutParams =
        MediaLayoutParams.copy(params)
            .also { it.width = width; it.height = height }

    companion object {
        fun create(header: LinearLayout): ControlCenterMediaLayout = create(header, contentView = null)

        fun create(
            header: LinearLayout,
            contentView: ViewGroup?,
            options: ControlCenterMediaLayoutOptions = ControlCenterMediaLayoutOptions(),
        ): ControlCenterMediaLayout {
            fun view(name: String): View? {
                val id = header.resources.getIdentifier(name, "id", "miui.systemui.plugin")
                return if (id == 0) null else header.findViewById(id)
            }
            return create(header, ::view, contentView, options)
        }

        internal fun create(header: LinearLayout, view: (String) -> View?,
            contentView: ViewGroup? = null,
            options: ControlCenterMediaLayoutOptions = ControlCenterMediaLayoutOptions(),
        ): ControlCenterMediaLayout {
            fun required(name: String) = requireNotNull(view(name)) { "Missing MiPlay view: $name" }
            val meta = required("meta_data_info") as ViewGroup
            val parent = required("coverParent") as ViewGroup
            val cover = required("cover") as ImageView
            val title = required("title") as TextView
            val artist = required("subtitle") as TextView
            val noPlayback = required("title_no_play_back_history") as TextView
            val audio = required("container_audio")
            val video = required("container_video")
            val progress = required("container_progress")
            require(parent.parent === meta && cover.parent === parent && meta.parent === header)
            require(listOf(title, artist, noPlayback, audio, video, progress).all { it.parent === meta })
            return ControlCenterMediaLayout(header, meta, parent, cover, title, artist, noPlayback,
                audio, video, progress, required("volume_bar_container"), contentView, options)
        }
    }
}
