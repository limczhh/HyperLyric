package com.lidesheng.hyperlyric.root.mediacard.notification.background

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.root.mediacard.background.MediaFlowTone
import com.lidesheng.hyperlyric.root.mediacard.background.MediaSoftArtworkFactory
import com.lidesheng.hyperlyric.root.mediacard.background.MediaSoftPaletteExtractor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

internal data class NotificationMediaColorConfig(
    val textPrimary: Int,
    val textSecondary: Int,
    val backgroundStart: Int,
    val backgroundEnd: Int
)

internal data class RenderedNotificationMediaBackground(
    val bitmap: Bitmap,
    val colors: NotificationMediaColorConfig,
    val artworkFingerprint: Long
)

internal class NotificationMediaBackgroundRenderer(
    private val classLoader: ClassLoader
) {
    private val monet = MonetApi.create(classLoader)
    private val cacheLock = Any()
    @Volatile
    private var closed = false
    private val profileCache = LruCache<Long, ArtworkProfile>(12)
    private val renderCache = object : LruCache<RenderCacheKey, CachedBackground>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: RenderCacheKey,
            oldValue: CachedBackground,
            newValue: CachedBackground?
        ) {
            if (oldValue.bitmap !== newValue?.bitmap && !oldValue.bitmap.isRecycled) {
                oldValue.bitmap.recycle()
            }
        }
    }

    fun close() {
        synchronized(cacheLock) {
            closed = true
            renderCache.evictAll()
            profileCache.evictAll()
        }
    }

    fun render(
        context: Context,
        artworkIcon: Icon?,
        packageName: String,
        style: Int,
        blurAmount: Int,
        autoInvert: Boolean,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): RenderedNotificationMediaBackground? {
        val artwork = runCatching {
            artworkIcon?.loadDrawable(context)
                ?: context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        return renderArtwork(
            context,
            artwork,
            style,
            blurAmount,
            autoInvert,
            softCoverTone,
            width,
            height
        )
    }

    fun renderDrawable(
        context: Context,
        artworkDrawable: Drawable?,
        packageName: String,
        style: Int,
        blurAmount: Int,
        autoInvert: Boolean,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): RenderedNotificationMediaBackground? {
        val artwork = runCatching {
            artworkDrawable?.constantState
                ?.newDrawable(context.resources)
                ?.mutate()
                ?: artworkDrawable
                ?: context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        return renderArtwork(
            context,
            artwork,
            style,
            blurAmount,
            autoInvert,
            softCoverTone,
            width,
            height
        )
    }

    private fun renderArtwork(
        context: Context,
        artwork: Drawable,
        style: Int,
        blurAmount: Int,
        autoInvert: Boolean,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): RenderedNotificationMediaBackground? {
        if (closed || width <= 0 || height <= 0) return null
        val source = artwork.toBitmapSafe() ?: return null
        val fingerprint = source.fingerprint()
        val darkMode = context.resources.configuration.uiMode and 0x30 == 0x20
        val cacheKey = RenderCacheKey(
            fingerprint,
            style,
            blurAmount,
            autoInvert,
            softCoverTone,
            darkMode,
            width,
            height
        )
        cachedBackground(cacheKey)?.let { cached ->
            source.recycle()
            return cached
        }
        val profile = artworkProfile(source, fingerprint) ?: run {
            source.recycle()
            return null
        }
        val colors = colorConfig(style, profile, autoInvert, softCoverTone)
        val result = when (style) {
            1 -> renderCoverArt(source, colors, darkMode, fingerprint, width, height)
            2 -> renderBlurredCover(source, colors, blurAmount, width, height)
            3 -> renderRadialGradient(source, colors, width, height)
            4 -> renderLinearGradient(source, colors, width, height)
            5 -> renderSoftCover(profile.rawColors, softCoverTone, width, height)
            else -> null
        }
        source.recycle()
        result ?: return null
        cacheBackground(cacheKey, result, colors)
        return RenderedNotificationMediaBackground(result, colors, fingerprint)
    }

    private fun colorConfig(
        style: Int,
        profile: ArtworkProfile,
        autoInvert: Boolean,
        softCoverTone: Int
    ): NotificationMediaColorConfig {
        val palette = profile.palette
        return when (style) {
            1 -> NotificationMediaColorConfig(
                palette.accent1[2], palette.accent1[2],
                palette.accent1[8], palette.accent1[8]
            )
            2, 3 -> NotificationMediaColorConfig(
                palette.neutral1[1], palette.neutral2[3],
                palette.accent2[9], palette.accent1[9]
            )
            4 -> {
                val reverse = autoInvert && profile.brightness >= 192f
                val text = palette.accent1[if (reverse) 8 else 2]
                val background = palette.accent1[if (reverse) 3 else 8]
                NotificationMediaColorConfig(text, text, background, background)
            }
            5 -> {
                val tone = softCoverTone.toFlowTone()
                val surface = MediaSoftArtworkFactory.appearance(tone).surface
                if (tone == MediaFlowTone.LIGHT) {
                    NotificationMediaColorConfig(
                        0xff1d1d1f.toInt(),
                        0xa61d1d1f.toInt(),
                        surface,
                        surface
                    )
                } else {
                    NotificationMediaColorConfig(
                        Color.WHITE,
                        0xccffffff.toInt(),
                        surface,
                        surface
                    )
                }
            }
            else -> NotificationMediaColorConfig(Color.WHITE, Color.WHITE, Color.BLACK, Color.BLACK)
        }
    }

    private fun renderCoverArt(
        artwork: Bitmap,
        colors: NotificationMediaColorConfig,
        darkMode: Boolean,
        fingerprint: Long,
        width: Int,
        height: Int
    ): Bitmap {
        val tile = artwork.scaleOwned(132, 132)
        val smallTile = tile.scaleOwned(66, 66)
        val mosaic = createBitmap(264, 264)
        val canvas = Canvas(mosaic)
        val random = Random((fingerprint xor (fingerprint ushr 32)).toInt())
        val positions = arrayOf(0f to 0f, 132f to 0f, 0f to 132f, 132f to 132f, 99f to 99f)
        positions.forEachIndexed { index, position ->
            val source = if (index < 4) tile else smallTile
            val matrix = Matrix().apply {
                postRotate(random.nextInt(4) * 90f, source.width / 2f, source.height / 2f)
                postScale(
                    if (random.nextBoolean()) -1f else 1f,
                    if (random.nextBoolean()) -1f else 1f,
                    source.width / 2f,
                    source.height / 2f
                )
            }
            val transformed = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
            canvas.drawBitmap(transformed, position.first, position.second, null)
            if (transformed !== source) transformed.recycle()
        }
        tile.recycle()
        smallTile.recycle()

        val correction = when (mosaic.brightness()) {
            in 0f..<50f -> 40f
            in 50f..<100f -> 20f
            in 100f..<200f -> -20f
            else -> -40f
        }
        val colorMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, correction,
            0f, 1f, 0f, 0f, correction,
            0f, 0f, 1f, 0f, correction,
            0f, 0f, 0f, 1f, 0f
        ))
        val corrected = createBitmap(mosaic.width, mosaic.height)
        val correctedCanvas = Canvas(corrected)
        correctedCanvas.drawBitmap(mosaic, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        })
        mosaic.recycle()
        correctedCanvas.drawColor(colors.backgroundStart.withAlpha(111))
        val overlay = if (darkMode) 0 else 248
        correctedCanvas.drawColor(Color.argb(20, overlay, overlay, overlay))

        val blurred = corrected.hardwareBlur(40f)
        corrected.recycle()
        val result = blurred.centerCrop(width, height)
        blurred.recycle()
        return result
    }

    private fun renderBlurredCover(
        artwork: Bitmap,
        colors: NotificationMediaColorConfig,
        blurAmount: Int,
        width: Int,
        height: Int
    ): Bitmap {
        val base = artwork.centerCrop(max(width, height), max(width, height))
        val canvas = Canvas(base)
        canvas.drawCircleGradient(
            colors.backgroundStart,
            colors.backgroundEnd,
            centerXFraction = 0.42f,
            startAlpha = 48,
            endAlpha = 225,
            radiusScale = 0.9f
        )
        val blurred = base.hardwareBlur(height * blurAmount.coerceIn(1, 20) / 100f)
        base.recycle()
        val result = blurred.centerCrop(width, height)
        blurred.recycle()
        return result
    }

    private fun renderRadialGradient(
        artwork: Bitmap,
        colors: NotificationMediaColorConfig,
        width: Int,
        height: Int
    ): Bitmap {
        val result = artwork.centerCrop(width, height)
        Canvas(result).drawCircleGradient(
            colors.backgroundStart,
            colors.backgroundEnd,
            startAlpha = 32,
            endAlpha = 235,
            radiusScale = 0.8f
        )
        return result
    }

    private fun renderLinearGradient(
        artwork: Bitmap,
        colors: NotificationMediaColorConfig,
        width: Int,
        height: Int
    ): Bitmap {
        val result = createBitmap(width, height)
        val canvas = Canvas(result)
        canvas.drawColor(colors.backgroundStart)
        val coverWidth = min(width, (height * 1.25f).roundToInt())
        val coverLeft = width - coverWidth
        val cover = artwork.centerCrop(coverWidth, height)
        canvas.drawBitmap(cover, coverLeft.toFloat(), 0f, null)
        cover.recycle()
        val shader = LinearGradient(
            coverLeft.toFloat(), 0f, width.toFloat(), 0f,
            intArrayOf(
                colors.backgroundStart,
                colors.backgroundStart.withAlpha(144),
                colors.backgroundStart.withAlpha(24)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(coverLeft.toFloat(), 0f, width.toFloat(), height.toFloat(), Paint().apply {
            this.shader = shader
        })
        return result
    }

    private fun renderSoftCover(
        rawColors: List<Int>,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): Bitmap = MediaSoftArtworkFactory.renderStatic(
        palette = MediaSoftPaletteExtractor.fromColors(rawColors),
        tone = softCoverTone.toFlowTone(),
        width = width,
        height = height
    )

    private fun Canvas.drawCircleGradient(
        start: Int,
        end: Int,
        centerXFraction: Float = 0.5f,
        centerYFraction: Float = 0.5f,
        startAlpha: Int = 48,
        endAlpha: Int = 235,
        radiusScale: Float = 0.85f
    ) {
        val shader = RadialGradient(
            width * centerXFraction,
            height * centerYFraction,
            max(width, height) * radiusScale,
            intArrayOf(
                start.withAlpha(startAlpha),
                end.withAlpha(endAlpha)
            ), null, Shader.TileMode.CLAMP
        )
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
    }

    private fun artworkProfile(bitmap: Bitmap, fingerprint: Long): ArtworkProfile? {
        synchronized(cacheLock) {
            profileCache.get(fingerprint)?.let { return it }
        }
        val extracted = ColorExtractor.extractThemePalette(bitmap, 3).rawColors
        if (extracted.isEmpty()) return null
        val wallpaperColors = WallpaperColors(
            Color.valueOf(extracted[0]),
            extracted.getOrNull(1)?.let { Color.valueOf(it) },
            extracted.getOrNull(2)?.let { Color.valueOf(it) }
        )
        val palette = monet.palette(wallpaperColors) ?: return null
        val profile = ArtworkProfile(palette, bitmap.brightness(), extracted.toList())
        synchronized(cacheLock) {
            if (!closed) profileCache.put(fingerprint, profile)
        }
        return profile
    }

    private fun cachedBackground(key: RenderCacheKey): RenderedNotificationMediaBackground? {
        return synchronized(cacheLock) {
            if (closed) return@synchronized null
            renderCache.get(key)?.let { cached ->
                RenderedNotificationMediaBackground(
                    cached.bitmap.copy(Bitmap.Config.ARGB_8888, true),
                    cached.colors,
                    key.fingerprint
                )
            }
        }
    }

    private fun cacheBackground(
        key: RenderCacheKey,
        bitmap: Bitmap,
        colors: NotificationMediaColorConfig
    ) {
        synchronized(cacheLock) {
            if (closed) return
            val cacheBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            renderCache.put(key, CachedBackground(cacheBitmap, colors))
        }
    }

    private fun Drawable.toBitmapSafe(): Bitmap? {
        val width = intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = intrinsicHeight.takeIf { it > 0 } ?: return null
        return runCatching {
            val bitmap = createBitmap(width, height)
            val originalBounds = Rect(bounds)
            try {
                setBounds(0, 0, width, height)
                draw(Canvas(bitmap))
            } finally {
                setBounds(originalBounds)
            }
            bitmap
        }.getOrNull()
    }

    private fun Bitmap.centerCrop(targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = max(targetWidth / width.toFloat(), targetHeight / height.toFloat())
        val scaledWidth = (width * scale).toInt().coerceAtLeast(targetWidth)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(targetHeight)
        val scaled = scale(scaledWidth, scaledHeight)
        val result = Bitmap.createBitmap(
            scaled,
            (scaled.width - targetWidth) / 2,
            (scaled.height - targetHeight) / 2,
            targetWidth,
            targetHeight
        ).copy(Bitmap.Config.ARGB_8888, true)
        if (scaled !== this) scaled.recycle()
        return result
    }

    private fun Bitmap.scaleOwned(targetWidth: Int, targetHeight: Int): Bitmap {
        val scaled = scale(targetWidth, targetHeight)
        return if (scaled === this) copy(Bitmap.Config.ARGB_8888, true) else scaled
    }

    private fun Bitmap.brightness(): Float {
        val step = 5
        var total = 0f
        var count = 0
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = getPixel(x, y)
                total += Color.red(pixel) * 0.299f +
                    Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                count++
            }
        }
        return if (count == 0) 0f else total / count
    }

    private fun Bitmap.fingerprint(): Long {
        var hash = 1125899906842597L
        hash = hash * 31 + width
        hash = hash * 31 + height
        val stepX = max(1, width / 16)
        val stepY = max(1, height / 16)
        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                hash = hash * 31 + getPixel(x, y).toLong()
            }
        }
        return hash
    }

    private fun Bitmap.hardwareBlur(radius: Float): Bitmap {
        val reader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )
        val node = RenderNode("HyperLyricMediaBlur")
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            node.setPosition(0, 0, width, height)
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR))
            node.beginRecording().also { canvas ->
                canvas.drawBitmap(this, 0f, 0f, null)
                node.endRecording()
            }
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                val buffer = image?.hardwareBuffer ?: error("No blur output buffer")
                buffer.use {
                    return Bitmap.wrapHardwareBuffer(buffer, null)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                        ?: error("Unable to copy blur output")
                }
            }
        } finally {
            node.discardDisplayList()
            renderer.destroy()
            reader.close()
        }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return this and 0x00ffffff or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun Int.toFlowTone(): MediaFlowTone =
        if (this == RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT) {
            MediaFlowTone.LIGHT
        } else {
            MediaFlowTone.DARK
        }

    private data class ArtworkProfile(
        val palette: MonetPalette,
        val brightness: Float,
        val rawColors: List<Int>
    )

    private data class CachedBackground(
        val bitmap: Bitmap,
        val colors: NotificationMediaColorConfig
    )

    private data class RenderCacheKey(
        val fingerprint: Long,
        val style: Int,
        val blurAmount: Int,
        val autoInvert: Boolean,
        val softCoverTone: Int,
        val darkMode: Boolean,
        val width: Int,
        val height: Int
    )

    private data class MonetPalette(
        val neutral1: List<Int>,
        val neutral2: List<Int>,
        val accent1: List<Int>,
        val accent2: List<Int>
    )

    private class MonetApi private constructor(
        private val constructor: java.lang.reflect.Constructor<*>,
        private val styleContent: Any,
        private val allShadesField: java.lang.reflect.Field,
        private val neutral1Field: java.lang.reflect.Field,
        private val neutral2Field: java.lang.reflect.Field,
        private val accent1Field: java.lang.reflect.Field,
        private val accent2Field: java.lang.reflect.Field
    ) {
        @Suppress("UNCHECKED_CAST")
        fun palette(colors: WallpaperColors): MonetPalette? = runCatching {
            val scheme = if (constructor.parameterCount == 3) {
                constructor.newInstance(colors, true, styleContent)
            } else {
                constructor.newInstance(colors, styleContent)
            }
            MonetPalette(
                allShadesField.get(neutral1Field.get(scheme)) as List<Int>,
                allShadesField.get(neutral2Field.get(scheme)) as List<Int>,
                allShadesField.get(accent1Field.get(scheme)) as List<Int>,
                allShadesField.get(accent2Field.get(scheme)) as List<Int>
            )
        }.getOrNull()

        companion object {
            fun create(classLoader: ClassLoader): MonetApi {
                val schemeClass = classLoader.loadClass("com.android.systemui.monet.ColorScheme")
                val paletteClass = classLoader.loadClass("com.android.systemui.monet.TonalPalette")
                val styleClass = classLoader.loadClass("com.android.systemui.monet.Style")
                val constructor = schemeClass.declaredConstructors.singleOrNull { it.parameterCount == 3 }
                    ?: schemeClass.declaredConstructors.single { it.parameterCount == 2 }
                constructor.isAccessible = true
                val valueOf = styleClass.getDeclaredMethod("valueOf", String::class.java).apply {
                    isAccessible = true
                }
                return MonetApi(
                    constructor,
                    valueOf.invoke(null, "CONTENT") ?: error("Monet CONTENT style is unavailable"),
                    paletteClass.requiredField("allShades"),
                    schemeClass.requiredField("mNeutral1", "neutral1"),
                    schemeClass.requiredField("mNeutral2", "neutral2"),
                    schemeClass.requiredField("mAccent1", "accent1"),
                    schemeClass.requiredField("mAccent2", "accent2")
                )
            }

            private fun Class<*>.requiredField(vararg names: String): java.lang.reflect.Field {
                names.forEach { name ->
                    runCatching { getDeclaredField(name) }.getOrNull()?.let {
                        it.isAccessible = true
                        return it
                    }
                }
                error("Missing field ${names.joinToString()} in $name")
            }
        }
    }
}
