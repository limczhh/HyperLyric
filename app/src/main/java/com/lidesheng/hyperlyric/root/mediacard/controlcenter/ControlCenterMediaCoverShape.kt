package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser

/** Removes the two inner rounded edges; the host still clips the outer card. */
internal class ControlCenterMediaCoverShape(
    private val header: View,
    private val content: View?,
    private val coverParent: View,
    private val cover: View,
) {
    private val contentClip = content?.clipToOutline
    private val coverClip = cover.clipToOutline
    private val parentClip = coverParent.clipToOutline
    private val setRadius = header.javaClass.methods.firstOrNull {
        it.name == "setCoverRadius" && it.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType))
    }
    private val nativeRadius = runCatching {
        header.javaClass.getMethod("getCoverRadius").invoke(header) as Float
    }.getOrNull()
    private var original: Drawable? = null
    private val placeholderId = cover.resources.getIdentifier(
        "miplay_cover_default", "drawable", "miui.systemui.plugin")
    private val placeholderState = runCatching {
        if (placeholderId != 0)
            cover.resources.getDrawable(placeholderId, cover.context.theme).constantState else null
    }.getOrNull()
    private val square by lazy {
        if (placeholderId != 0) squarePlaceholder(cover.resources, placeholderId) else null
    }

    fun apply(outerHostClips: Boolean, transitioning: Boolean) {
        // The notification modal already clips mDetailContainer. Panel can use
        // the detail content itself as its outer clipping boundary.
        content?.clipToOutline = if (outerHostClips) false else contentClip ?: false
        // Static artwork is rectangular. During Panel morphing, restore the
        // native outlines so its own spring interpolates compactRadius <-> 0.
        cover.clipToOutline = transitioning && coverClip
        coverParent.clipToOutline = transitioning && parentClip
        val background = cover.background ?: return
        // The stock miplay_cover_default is a vector with a rounded first path.
        // Removing View outline clipping cannot remove corners baked into it.
        if (background.constantState == null || background.constantState != placeholderState) return
        val replacement = square ?: return
        original = background
        cover.background = replacement
    }

    /** Called before the native animator captures its expanded endpoint. */
    fun prepareTransition() {
        setRadius?.invoke(header, 0f)
    }

    fun restore() {
        contentClip?.let { content?.clipToOutline = it }
        cover.clipToOutline = coverClip
        coverParent.clipToOutline = parentClip
        nativeRadius?.let { setRadius?.invoke(header, it) }
        original?.let { if (cover.background === square) cover.background = it }
    }

    private fun squarePlaceholder(resources: Resources, id: Int): Drawable? = runCatching {
        resources.getXml(id).use { input ->
            var width = 0f
            var height = 0f
            val paths = mutableListOf<Pair<Path, Paint>>()
            while (input.eventType != XmlPullParser.END_DOCUMENT) {
                if (input.eventType == XmlPullParser.START_TAG) {
                    when (input.name) {
                        "vector" -> {
                            width = input.getAttributeFloatValue(ANDROID, "viewportWidth", 0f)
                            height = input.getAttributeFloatValue(ANDROID, "viewportHeight", 0f)
                        }
                        "path" -> {
                            check(width > 0f && height > 0f)
                            val attrs = cover.context.theme.obtainStyledAttributes(input,
                                intArrayOf(android.R.attr.fillColor, android.R.attr.pathData,
                                    android.R.attr.fillAlpha, android.R.attr.fillType), 0, 0)
                            try {
                                val path = if (paths.isEmpty()) Path().apply {
                                    addRect(0f, 0f, width, height, Path.Direction.CW)
                                } else PathParser.createPathFromPathData(requireNotNull(attrs.getString(1)))
                                if (attrs.getInt(3, 0) == 1) path.fillType = Path.FillType.EVEN_ODD
                                val color = attrs.getColor(0, 0)
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    this.color = color
                                    alpha = (alpha * attrs.getFloat(2, 1f)).toInt()
                                }
                                paths += path to paint
                            } finally {
                                attrs.recycle()
                            }
                        }
                        else -> error("Unsupported placeholder vector element: ${input.name}")
                    }
                }
                input.next()
            }
            check(paths.isNotEmpty())
            SquarePlaceholder(width, height, paths)
        }
    }.getOrNull()

    private class SquarePlaceholder(
        private val width: Float,
        private val height: Float,
        private val paths: List<Pair<Path, Paint>>,
    ) : Drawable() {
        private val alphas = paths.map { it.second.alpha }
        override fun draw(canvas: Canvas) {
            val save = canvas.save()
            try {
                canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
                canvas.scale(bounds.width() / width, bounds.height() / height)
                paths.forEach { (path, paint) -> canvas.drawPath(path, paint) }
            } finally { canvas.restoreToCount(save) }
        }
        override fun setAlpha(alpha: Int) {
            paths.forEachIndexed { index, (_, paint) -> paint.alpha = alphas[index] * alpha / 255 }
            invalidateSelf()
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paths.forEach { it.second.colorFilter = colorFilter }
            invalidateSelf()
        }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
