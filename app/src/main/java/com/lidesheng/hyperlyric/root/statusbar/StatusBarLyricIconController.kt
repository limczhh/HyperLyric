package com.lidesheng.hyperlyric.root.statusbar

import android.graphics.Bitmap
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.MonochromeAppIconAssets

/** Owns the optional status-bar lyric icon and its small playback animation. */
internal class StatusBarLyricIconController(
    private val row: LinearLayout,
    private val iconView: ImageView,
) {
    private var imageKind = ImageKind.NONE
    private var imageStyle = -1
    private var lastArtwork: Bitmap? = null
    private var lastAppPackage: String? = null
    private var renderedAsTintedIcon = false
    private var lastTintColor: Int? = null
    private var currentStyle = -1
    private var currentOrder = -1
    private var currentSizePx = -1
    private var shapeStyle = -1
    private var shapeSizePx = -1
    private var rotationAnimator: android.animation.ObjectAnimator? = null
    private var hostVisible = false
    private var shouldRotateWhenVisible = false

    /** Returns the width reserved inside the lyric viewport for this icon. */
    fun update(
        config: StatusBarLyricLayoutConfig,
        mediaPackage: String?,
        artwork: Bitmap?,
        lyricColor: Int,
        isPlaying: Boolean,
        contentWidthLimitPx: Int,
    ): Int {
        val requiredWidth = config.iconSizePx + config.iconSpacingPx + 1
        val fits = config.iconEnabled && contentWidthLimitPx >= requiredWidth
        val nextOrder = config.iconOrder
        if (currentOrder != nextOrder || row.indexOfChild(iconView) < 0) {
            (iconView.parent as? LinearLayout)?.removeView(iconView)
            val index = if (nextOrder == RootConstants.STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC) {
                0
            } else {
                row.childCount
            }
            row.addView(iconView, index.coerceIn(0, row.childCount), iconLayoutParams(config))
            currentOrder = nextOrder
        }

        if (currentStyle != config.iconStyle || currentSizePx != config.iconSizePx ||
            iconView.layoutParams == null
        ) {
            iconView.layoutParams = iconLayoutParams(config)
            currentStyle = config.iconStyle
            currentSizePx = config.iconSizePx
        } else {
            val params = iconView.layoutParams as? LinearLayout.LayoutParams
            if (params != null) {
                val before = nextOrder == RootConstants.STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC
                val start = if (before) 0 else config.iconSpacingPx
                val end = if (before) config.iconSpacingPx else 0
                if (params.marginStart != start || params.marginEnd != end) {
                    params.marginStart = start
                    params.marginEnd = end
                    iconView.layoutParams = params
                }
            }
        }

        if (!fits) {
            iconView.visibility = View.GONE
            shouldRotateWhenVisible = false
            stopRotation(reset = true)
            return 0
        }

        val coverStyle = config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_MUSIC_COVER ||
                config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_CIRCLE_COVER ||
                config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_ROTATING_COVER
        val circle = config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_CIRCLE_COVER ||
                config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_ROTATING_COVER
        applyShape(config.iconStyle, config.iconSizePx, coverStyle, circle)

        val usableArtwork = artwork?.takeUnless { it.isRecycled }
        val hasImage = when {
            coverStyle && usableArtwork != null -> {
                useArtwork(config.iconStyle, usableArtwork)
                true
            }

            config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_APP ->
                useAppIcon(mediaPackage)

            config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_MONOCHROME ->
                useMonochromeAppIcon(mediaPackage)

            else -> false
        }
        if (!hasImage) {
            iconView.visibility = View.GONE
            shouldRotateWhenVisible = false
            stopRotation(reset = true)
            return 0
        }

        iconView.visibility = View.VISIBLE
        shouldRotateWhenVisible =
            config.iconStyle == RootConstants.STATUS_BAR_LYRIC_ICON_ROTATING_COVER &&
                    usableArtwork != null && isPlaying
        updateTint(lyricColor)

        if (shouldRotateWhenVisible && hostVisible) {
            startRotation()
        } else {
            stopRotation(
                reset = config.iconStyle != RootConstants.STATUS_BAR_LYRIC_ICON_ROTATING_COVER ||
                        usableArtwork == null,
            )
        }
        return config.iconSizePx + config.iconSpacingPx
    }

    fun updateTint(color: Int) {
        if (renderedAsTintedIcon) {
            if (lastTintColor != color) {
                iconView.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
                lastTintColor = color
            }
        } else if (lastTintColor != null) {
            iconView.clearColorFilter()
            lastTintColor = null
        }
    }

    fun setHostVisible(visible: Boolean) {
        hostVisible = visible
        if (!visible) {
            stopRotation(reset = false)
        } else if (shouldRotateWhenVisible) {
            startRotation()
        }
    }

    fun clear() {
        stopRotation(reset = true)
        iconView.clearColorFilter()
        iconView.setImageDrawable(null)
        imageKind = ImageKind.NONE
        imageStyle = -1
        lastArtwork = null
        lastAppPackage = null
        renderedAsTintedIcon = false
        lastTintColor = null
        hostVisible = false
        shouldRotateWhenVisible = false
        currentStyle = -1
        currentOrder = -1
        currentSizePx = -1
        shapeStyle = -1
        shapeSizePx = -1
    }

    private fun iconLayoutParams(config: StatusBarLyricLayoutConfig) =
        LinearLayout.LayoutParams(config.iconSizePx, config.iconSizePx).apply {
            val before = config.iconOrder == RootConstants.STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC
            marginStart = if (before) 0 else config.iconSpacingPx
            marginEnd = if (before) config.iconSpacingPx else 0
        }

    private fun applyShape(style: Int, sizePx: Int, isCover: Boolean, circle: Boolean) {
        if (shapeStyle == style && shapeSizePx == sizePx) return
        shapeStyle = style
        shapeSizePx = sizePx
        iconView.scaleType = if (isCover) ImageView.ScaleType.CENTER_CROP else
            ImageView.ScaleType.FIT_CENTER
        iconView.clipToOutline = isCover
        val roundedCoverRadiusPx = if (isCover && !circle) {
            musicCoverCornerRadiusPx(sizePx)
        } else {
            0f
        }
        iconView.outlineProvider = if (!isCover) {
            null
        } else {
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    if (circle) {
                        outline.setOval(0, 0, view.width, view.height)
                    } else {
                        outline.setRoundRect(
                            0,
                            0,
                            view.width,
                            view.height,
                            roundedCoverRadiusPx,
                        )
                    }
                }
            }
        }
        iconView.invalidateOutline()
    }

    private fun musicCoverCornerRadiusPx(sizePx: Int): Float {
        val resources = iconView.resources
        val packageName = iconView.context.packageName
        fun systemDimension(name: String): Float? {
            val id = resources.getIdentifier(name, "dimen", packageName)
            if (id == 0) return null
            return runCatching { resources.getDimension(id) }
                .getOrNull()
                ?.takeIf { it > 0f }
        }

        val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val radiusPx = systemDimension("album_art_bg_radius") ?: 10f * density
        val coverWidthPx = systemDimension("album_art_width") ?: 52.5f * density
        return (sizePx * radiusPx / coverWidthPx).coerceAtMost(sizePx / 2f)
    }

    private fun useArtwork(style: Int, bitmap: Bitmap) {
        if (imageKind == ImageKind.ARTWORK && imageStyle == style && lastArtwork === bitmap) return
        clearTintCache()
        iconView.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.setImageBitmap(bitmap)
        imageKind = ImageKind.ARTWORK
        imageStyle = style
        lastArtwork = bitmap
        lastAppPackage = null
        renderedAsTintedIcon = false
    }

    private fun useAppIcon(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (imageKind == ImageKind.APP && lastAppPackage == packageName) {
            return iconView.drawable != null
        }
        val drawable = runCatching {
            iconView.context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return false
        clearTintCache()
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        iconView.setImageDrawable(drawable)
        imageKind = ImageKind.APP
        imageStyle = RootConstants.STATUS_BAR_LYRIC_ICON_APP
        lastArtwork = null
        lastAppPackage = packageName
        renderedAsTintedIcon = false
        return true
    }

    private fun useMonochromeAppIcon(packageName: String?): Boolean {
        val normalizedPackage = packageName?.takeIf(String::isNotBlank)
        if (imageKind == ImageKind.MONOCHROME_APP && lastAppPackage == normalizedPackage) {
            return iconView.drawable != null
        }

        val bitmap = MonochromeAppIconAssets.load(iconView.context, normalizedPackage)
            ?: return false
        showMonochromeIcon(normalizedPackage, bitmap)
        return true
    }

    private fun showMonochromeIcon(packageName: String?, bitmap: Bitmap) {
        clearTintCache()
        iconView.scaleType = ImageView.ScaleType.FIT_CENTER
        iconView.setImageBitmap(bitmap)
        imageKind = ImageKind.MONOCHROME_APP
        imageStyle = RootConstants.STATUS_BAR_LYRIC_ICON_MONOCHROME
        lastArtwork = null
        lastAppPackage = packageName
        renderedAsTintedIcon = true
    }

    private fun clearTintCache() {
        if (lastTintColor != null) iconView.clearColorFilter()
        lastTintColor = null
    }

    private fun startRotation() {
        if (rotationAnimator?.isStarted == true) return
        rotationAnimator = android.animation.ObjectAnimator.ofFloat(
            iconView,
            View.ROTATION,
            iconView.rotation,
            iconView.rotation + 360f,
        ).apply {
            duration = ROTATION_DURATION_MS
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopRotation(reset: Boolean) {
        rotationAnimator?.cancel()
        rotationAnimator = null
        if (reset) iconView.rotation = 0f
    }

    private enum class ImageKind { NONE, ARTWORK, APP, MONOCHROME_APP }

    private companion object {
        const val ROTATION_DURATION_MS = 8_000L
    }
}
