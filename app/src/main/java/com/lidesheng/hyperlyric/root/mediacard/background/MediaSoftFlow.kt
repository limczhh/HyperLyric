package com.lidesheng.hyperlyric.root.mediacard.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Rect
import android.graphics.Shader
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal enum class MediaFlowTone { LIGHT, DARK }

internal data class MediaSoftPalette(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val tertiary: Int,
    val dominance: List<Float>
) {
    fun asList(): List<Int> = listOf(primary, secondary, accent, tertiary)
}

internal object MediaSoftPaletteExtractor {
    private val fallbackColors = listOf(0xffe60012.toInt(), 0xffff7a3d.toInt(), 0xff4d68ff.toInt(), 0xffd64d9d.toInt())

    fun extract(bitmap: Bitmap): MediaSoftPalette? {
        if (bitmap.isRecycled) return null
        val colors = expandedNormalizedColors(
            ColorExtractor.extractThemePalette(bitmap, 4).rawColors
        )
        val dominance = estimateDominance(bitmap, colors)
        val order = colors.indices.sortedByDescending { dominance[it] }
        return createPalette(
            colors = order.map(colors::get),
            dominance = order.map(dominance::get)
        )
    }

    fun fromColors(rawColors: List<Int>): MediaSoftPalette {
        return createPalette(
            colors = expandedNormalizedColors(rawColors),
            dominance = DEFAULT_DOMINANCE
        )
    }

    private fun expandedNormalizedColors(rawColors: List<Int>): List<Int> =
        expandColors(rawColors.ifEmpty { fallbackColors }.take(4)).map(::normalizeForArtwork)

    private fun createPalette(colors: List<Int>, dominance: List<Float>): MediaSoftPalette {
        val total = dominance.sum().takeIf { it > 0f } ?: 1f
        return MediaSoftPalette(
            primary = colors[0],
            secondary = colors[1],
            accent = colors[2],
            tertiary = colors[3],
            dominance = dominance.map { it / total }
        )
    }

    private fun estimateDominance(bitmap: Bitmap, colors: List<Int>): List<Float> {
        val maxSide = max(bitmap.width, bitmap.height).coerceAtLeast(1)
        val scale = (DOMINANCE_SAMPLE_SIZE / maxSide.toFloat()).coerceAtMost(1f)
        val sample = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        if (sample !== bitmap) sample.recycle()

        val colorLabs = colors.map { color ->
            DoubleArray(3).also { ColorUtils.colorToLAB(color, it) }
        }
        val counts = FloatArray(colors.size)
        val pixelLab = DoubleArray(3)
        pixels.forEach { pixel ->
            if (Color.alpha(pixel) < 0x40) return@forEach
            ColorUtils.colorToLAB(pixel, pixelLab)
            var nearest = 0
            var nearestDistance = Double.MAX_VALUE
            colorLabs.forEachIndexed { index, lab ->
                val dl = pixelLab[0] - lab[0]
                val da = pixelLab[1] - lab[1]
                val db = pixelLab[2] - lab[2]
                val distance = dl * dl + da * da + db * db
                if (distance < nearestDistance) {
                    nearest = index
                    nearestDistance = distance
                }
            }
            counts[nearest] += 1f
        }
        val total = counts.sum()
        if (total <= 0f) return DEFAULT_DOMINANCE
        val smoothed = counts.map { count -> (count / total).coerceAtLeast(MIN_DOMINANCE) }
        val smoothedTotal = smoothed.sum()
        return smoothed.map { it / smoothedTotal }
    }

    private fun expandColors(colors: List<Int>): List<Int> = when (colors.size) {
        0 -> fallbackColors
        1 -> {
            val base = colors[0]
            val hsl = base.toHsl()
            if (hsl[1] < 0.12f) {
                listOf(
                    base.adjustHsl(lightnessDelta = -0.16f),
                    base.adjustHsl(lightnessDelta = -0.05f),
                    base.adjustHsl(lightnessDelta = 0.08f),
                    base.adjustHsl(lightnessDelta = 0.18f)
                )
            } else {
                listOf(
                    base.adjustHsl(hueDelta = -24f, lightnessDelta = -0.07f),
                    base.adjustHsl(hueDelta = -7f, lightnessDelta = 0.03f),
                    base.adjustHsl(hueDelta = 13f, lightnessDelta = -0.02f),
                    base.adjustHsl(hueDelta = 32f, lightnessDelta = 0.08f)
                )
            }
        }
        2 -> listOf(
            colors[0],
            colors[0].adjustHsl(hueDelta = 12f, lightnessDelta = 0.05f),
            colors[1].adjustHsl(hueDelta = -12f, lightnessDelta = -0.04f),
            colors[1]
        )
        3 -> listOf(
            colors[0],
            colors[1],
            colors[2],
            colors[0].adjustHsl(hueDelta = 18f, lightnessDelta = 0.07f)
        )
        else -> colors.take(4)
    }

    private fun normalizeForArtwork(color: Int): Int {
        val hsl = color.toHsl()
        if (hsl[1] >= 0.12f) hsl[1] = (hsl[1] * 1.12f).coerceIn(0.46f, 0.92f)
        hsl[2] = hsl[2].coerceIn(0.22f, 0.78f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun Int.toHsl(): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(this, it) }

    private fun Int.adjustHsl(
        hueDelta: Float = 0f,
        lightnessDelta: Float = 0f
    ): Int {
        val hsl = toHsl()
        hsl[0] = ((hsl[0] + hueDelta) % 360f + 360f) % 360f
        hsl[2] = (hsl[2] + lightnessDelta).coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }

    private const val DOMINANCE_SAMPLE_SIZE = 48
    private const val MIN_DOMINANCE = 0.06f
    private val DEFAULT_DOMINANCE = listOf(0.46f, 0.24f, 0.18f, 0.12f)
}

internal object MediaSoftArtworkFactory {
    const val TEXTURE_SIZE = 72

    private val anchorX = floatArrayOf(0.12f, 0.88f, 0.20f, 0.82f)
    private val anchorY = floatArrayOf(0.14f, 0.22f, 0.86f, 0.78f)

    fun createTexture(palette: MediaSoftPalette): Bitmap {
        val colors = palette.asList().map(::toOklab)
        val dominance = palette.dominance
        val pixels = IntArray(TEXTURE_SIZE * TEXTURE_SIZE)
        val denominator = (TEXTURE_SIZE - 1).toFloat()
        for (y in 0 until TEXTURE_SIZE) {
            val normalizedY = y / denominator
            for (x in 0 until TEXTURE_SIZE) {
                val normalizedX = x / denominator
                val warpedX = normalizedX + sin(normalizedY * PI * 2.0).toFloat() * 0.075f +
                    sin((normalizedX + normalizedY) * PI).toFloat() * 0.035f
                val warpedY = normalizedY + cos(normalizedX * PI * 2.0).toFloat() * 0.065f -
                    sin((normalizedX - normalizedY) * PI).toFloat() * 0.030f
                var totalWeight = 0f
                var lightness = 0f
                var greenRed = 0f
                var blueYellow = 0f
                colors.indices.forEach { index ->
                    val dx = warpedX - anchorX[index]
                    val dy = warpedY - anchorY[index]
                    val areaWeight = dominance[index]
                    val weight =
                        exp((-(dx * dx + dy * dy) * 5.2f).toDouble()).toFloat() *
                            (0.55f + areaWeight * 2.6f) +
                            0.006f + areaWeight * 0.11f
                    totalWeight += weight
                    lightness += colors[index].l * weight
                    greenRed += colors[index].a * weight
                    blueYellow += colors[index].b * weight
                }
                pixels[y * TEXTURE_SIZE + x] = fromOklab(
                    Oklab(lightness / totalWeight, greenRed / totalWeight, blueYellow / totalWeight)
                )
            }
        }
        return Bitmap.createBitmap(
            pixels,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            Bitmap.Config.ARGB_8888
        )
    }

    fun renderStatic(
        palette: MediaSoftPalette,
        tone: MediaFlowTone,
        width: Int,
        height: Int
    ): Bitmap {
        val texture = createTexture(palette)
        val flow = createBitmap(width, height)
        val canvas = Canvas(flow)
        drawLayer(canvas, texture, width, height, angle = 0f, zoom = 1.34f, alpha = 255)
        drawLayer(canvas, texture, width, height, angle = 97f, zoom = 1.48f, offsetX = -0.08f, offsetY = -0.06f, alpha = 82)
        drawLayer(canvas, texture, width, height, angle = 218f, zoom = 1.58f, offsetX = 0.07f, offsetY = 0.08f, alpha = 62)

        val appearance = appearance(tone)
        val result = createBitmap(width, height)
        val saturationMatrix = ColorMatrix().apply { setSaturation(appearance.saturation) }
        Canvas(result).apply {
            drawBitmap(flow, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                colorFilter = ColorMatrixColorFilter(saturationMatrix)
            })
            drawColor(appearance.surface.withAlpha((appearance.surfaceBlend * 255f).toInt()))
        }
        texture.recycle()
        flow.recycle()
        return result
    }

    fun appearance(tone: MediaFlowTone): MediaFlowAppearance = when (tone) {
        MediaFlowTone.DARK -> MediaFlowAppearance(
            surface = 0xff121316.toInt(),
            surfaceBlend = 0.39f,
            saturation = 1.18f
        )
        MediaFlowTone.LIGHT -> MediaFlowAppearance(
            surface = 0xfff3f3f5.toInt(),
            surfaceBlend = 0.65f,
            saturation = 1.30f
        )
    }

    private fun drawLayer(
        canvas: Canvas,
        texture: Bitmap,
        width: Int,
        height: Int,
        angle: Float,
        zoom: Float,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        alpha: Int
    ) {
        val shader = BitmapShader(texture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        val scale = max(width, height) / TEXTURE_SIZE.toFloat() * zoom
        val matrix = Matrix().apply {
            setTranslate(-TEXTURE_SIZE / 2f, -TEXTURE_SIZE / 2f)
            postRotate(angle)
            postScale(scale, scale)
            postTranslate(width / 2f + width * offsetX, height / 2f + height * offsetY)
        }
        shader.setLocalMatrix(matrix)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            this.alpha = alpha
        })
    }

    private data class Oklab(val l: Float, val a: Float, val b: Float)

    private fun toOklab(color: Int): Oklab {
        val r = srgbToLinear(Color.red(color) / 255f)
        val g = srgbToLinear(Color.green(color) / 255f)
        val b = srgbToLinear(Color.blue(color) / 255f)
        val l = cubeRoot(0.41222147f * r + 0.53633255f * g + 0.05144599f * b)
        val m = cubeRoot(0.2119035f * r + 0.6806995f * g + 0.107397f * b)
        val s = cubeRoot(0.08830246f * r + 0.28171884f * g + 0.6299787f * b)
        return Oklab(
            0.21045426f * l + 0.7936178f * m - 0.00407205f * s,
            1.9779985f * l - 2.4285922f * m + 0.4505937f * s,
            0.02590404f * l + 0.78277177f * m - 0.80867577f * s
        )
    }

    private fun fromOklab(color: Oklab): Int {
        val l = color.l + 0.39633778f * color.a + 0.21580376f * color.b
        val m = color.l - 0.10556135f * color.a - 0.06385417f * color.b
        val s = color.l - 0.08948418f * color.a - 1.2914855f * color.b
        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s
        val r = linearToSrgb(4.0767417f * l3 - 3.3077116f * m3 + 0.23096994f * s3)
        val g = linearToSrgb(-1.268438f * l3 + 2.6097574f * m3 - 0.3413194f * s3)
        val b = linearToSrgb(-0.00419609f * l3 - 0.7034186f * m3 + 1.7076147f * s3)
        return Color.rgb((r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt())
    }

    private fun srgbToLinear(value: Float): Float = if (value <= 0.04045f) {
        value / 12.92f
    } else {
        ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    }

    private fun linearToSrgb(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= 0.0031308f) {
            clamped * 12.92f
        } else {
            (1.055 * clamped.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()
        }.coerceIn(0f, 1f)
    }

    private fun cubeRoot(value: Float): Float = Math.cbrt(value.toDouble()).toFloat()

    private fun Int.withAlpha(alpha: Int): Int =
        this and 0x00ffffff or (alpha.coerceIn(0, 255) shl 24)
}

internal data class MediaFlowAppearance(
    val surface: Int,
    val surfaceBlend: Float,
    val saturation: Float
)

internal class MediaFlowArtwork private constructor(
    private val pixels: IntArray,
    private val signature: Int
) {
    fun createBitmap(): Bitmap = Bitmap.createBitmap(
        pixels,
        TEXTURE_SIZE,
        TEXTURE_SIZE,
        Bitmap.Config.ARGB_8888
    )

    override fun equals(other: Any?): Boolean =
        other is MediaFlowArtwork &&
            signature == other.signature &&
            pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = signature

    companion object {
        const val TEXTURE_SIZE = 96

        fun prepare(bitmap: Bitmap): MediaFlowArtwork? {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            val side = min(bitmap.width, bitmap.height)
            val source = Rect(
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                (bitmap.width + side) / 2,
                (bitmap.height + side) / 2
            )
            val texture = createBitmap(TEXTURE_SIZE, TEXTURE_SIZE)
            return try {
                Canvas(texture).drawBitmap(
                    bitmap,
                    source,
                    Rect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                val pixels = IntArray(TEXTURE_SIZE * TEXTURE_SIZE)
                texture.getPixels(
                    pixels,
                    0,
                    TEXTURE_SIZE,
                    0,
                    0,
                    TEXTURE_SIZE,
                    TEXTURE_SIZE
                )
                val blurred = boxBlur(pixels, TEXTURE_SIZE, TEXTURE_SIZE, BLUR_RADIUS)
                MediaFlowArtwork(blurred, blurred.contentHashCode())
            } finally {
                texture.recycle()
            }
        }

        private fun boxBlur(
            source: IntArray,
            width: Int,
            height: Int,
            radius: Int
        ): IntArray {
            if (radius <= 0) return source
            val horizontal = IntArray(source.size)
            val result = IntArray(source.size)
            val window = radius * 2 + 1

            for (y in 0 until height) {
                var alpha = 0L
                var red = 0L
                var green = 0L
                var blue = 0L
                for (offset in -radius..radius) {
                    val color = source[y * width + offset.coerceIn(0, width - 1)]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                for (x in 0 until width) {
                    horizontal[y * width + x] = Color.argb(
                        (alpha / window).toInt(),
                        (red / window).toInt(),
                        (green / window).toInt(),
                        (blue / window).toInt()
                    )
                    val removed = source[y * width + (x - radius).coerceIn(0, width - 1)]
                    val added = source[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                    alpha += Color.alpha(added) - Color.alpha(removed)
                    red += Color.red(added) - Color.red(removed)
                    green += Color.green(added) - Color.green(removed)
                    blue += Color.blue(added) - Color.blue(removed)
                }
            }

            for (x in 0 until width) {
                var alpha = 0L
                var red = 0L
                var green = 0L
                var blue = 0L
                for (offset in -radius..radius) {
                    val color = horizontal[offset.coerceIn(0, height - 1) * width + x]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                for (y in 0 until height) {
                    result[y * width + x] = Color.argb(
                        (alpha / window).toInt(),
                        (red / window).toInt(),
                        (green / window).toInt(),
                        (blue / window).toInt()
                    )
                    val removed = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                    val added = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                    alpha += Color.alpha(added) - Color.alpha(removed)
                    red += Color.red(added) - Color.red(removed)
                    green += Color.green(added) - Color.green(removed)
                    blue += Color.blue(added) - Color.blue(removed)
                }
            }
            return result
        }

        private const val BLUR_RADIUS = 5
    }
}

internal class MediaFlowTimeline {
    private var accumulatedSeconds = 0f
    private var startedAtNanos = 0L
    private var playing = false

    fun setPlaying(playing: Boolean) {
        if (this.playing == playing) return
        val now = SystemClock.elapsedRealtimeNanos()
        if (this.playing) {
            accumulatedSeconds += (now - startedAtNanos) / 1_000_000_000f
        }
        this.playing = playing
        startedAtNanos = if (playing) now else 0L
    }

    fun currentTimeSeconds(): Float {
        if (!playing) return accumulatedSeconds
        return accumulatedSeconds +
            (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000_000f
    }
}

internal class MediaFlowBackgroundView(
    context: Context,
    private val timeline: MediaFlowTimeline = MediaFlowTimeline()
) : View(context), Choreographer.FrameCallback {
    private val runtimeShader = RuntimeShader(FLOW_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = runtimeShader }
    private var targetArtwork = DEFAULT_ARTWORK
    private var fromTexture = createTexture(DEFAULT_ARTWORK)
    private var targetTexture = fromTexture
    private var transitionStartedAt: Float? = null
    private var animationTime = 0f
    private var frameScheduled = false
    private var playing = false
    private var tone = MediaFlowTone.DARK
    private var transitionViewport: Rect? = null
    private val visibleRect = Rect()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
        updateShaderArtwork()
    }

    fun update(
        artwork: MediaFlowArtwork? = null,
        tone: MediaFlowTone,
        playing: Boolean
    ) {
        animationTime = timeline.currentTimeSeconds()
        if (artwork != null && artwork != targetArtwork) updateArtwork(artwork)
        this.tone = tone
        if (this.playing != playing) {
            this.playing = playing
            timeline.setPlaying(playing)
            if (!playing) {
                finishArtworkTransition()
                stopFrames()
            } else {
                scheduleFrame()
            }
        }
        invalidate()
    }

    fun setTransitionViewport(viewport: Rect?) {
        val next = viewport?.let(::Rect)
        if (transitionViewport == next) return
        transitionViewport = next
        invalidate()
    }

    private fun updateArtwork(artwork: MediaFlowArtwork) {
        val nextTexture = createTexture(artwork)
        if (!playing) {
            targetArtwork = artwork
            fromTexture = nextTexture
            targetTexture = nextTexture
            transitionStartedAt = null
        } else {
            fromTexture = targetTexture
            targetArtwork = artwork
            targetTexture = nextTexture
            transitionStartedAt = animationTime
        }
        updateShaderArtwork()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleFrame()
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) scheduleFrame() else stopFrames()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) scheduleFrame() else stopFrames()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!shouldScheduleFrames()) return
        val visible = isEffectivelyVisible()
        if (visible) {
            animationTime = timeline.currentTimeSeconds()
            invalidate()
        }
        Choreographer.getInstance().postFrameCallbackDelayed(
            this,
            if (visible) FRAME_INTERVAL_MS else HIDDEN_PROBE_INTERVAL_MS
        )
        frameScheduled = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val viewport = transitionViewport
        val shaderWidth = viewport?.width()?.coerceAtLeast(1) ?: width
        val shaderHeight = viewport?.height()?.coerceAtLeast(1) ?: height
        val appearance = MediaSoftArtworkFactory.appearance(tone)
        runtimeShader.setFloatUniform(
            "uResolution",
            shaderWidth.toFloat(),
            shaderHeight.toFloat()
        )
        runtimeShader.setFloatUniform("uTextureSize", MediaFlowArtwork.TEXTURE_SIZE.toFloat())
        runtimeShader.setFloatUniform("uTime", animationTime)
        runtimeShader.setFloatUniform("uTransition", transitionFraction())
        runtimeShader.setFloatUniform("uSurfaceBlend", appearance.surfaceBlend)
        runtimeShader.setFloatUniform("uSaturation", FLOW_SATURATION)
        runtimeShader.setFloatUniform(
            "uSurface",
            Color.red(appearance.surface) / 255f,
            Color.green(appearance.surface) / 255f,
            Color.blue(appearance.surface) / 255f,
            1f
        )
        if (viewport == null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } else {
            val extensionHeight = (height - viewport.top).coerceAtLeast(shaderHeight)
            val checkpoint = canvas.save()
            canvas.translate(viewport.left.toFloat(), viewport.top.toFloat())
            canvas.clipRect(0f, 0f, shaderWidth.toFloat(), extensionHeight.toFloat())
            canvas.drawRect(0f, 0f, shaderWidth.toFloat(), extensionHeight.toFloat(), paint)
            canvas.restoreToCount(checkpoint)
        }
        finishTransitionIfNeeded()
    }

    private fun createTexture(artwork: MediaFlowArtwork): BitmapShader {
        return BitmapShader(
            artwork.createBitmap(),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        ).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
    }

    private fun updateShaderArtwork() {
        runtimeShader.setInputShader("uArtworkFrom", fromTexture)
        runtimeShader.setInputShader("uArtworkTo", targetTexture)
    }

    private fun transitionFraction(): Float {
        val start = transitionStartedAt ?: return 1f
        val fraction = ((animationTime - start) / ARTWORK_TRANSITION_SECONDS).coerceIn(0f, 1f)
        return fraction * fraction * (3f - 2f * fraction)
    }

    private fun finishTransitionIfNeeded() {
        val start = transitionStartedAt ?: return
        if (animationTime - start < ARTWORK_TRANSITION_SECONDS) return
        finishArtworkTransition()
    }

    private fun finishArtworkTransition() {
        if (transitionStartedAt == null) return
        fromTexture = targetTexture
        transitionStartedAt = null
        updateShaderArtwork()
    }

    private fun shouldScheduleFrames(): Boolean =
        playing && isAttachedToWindow && windowVisibility == VISIBLE

    private fun isEffectivelyVisible(): Boolean {
        if (!isShown || !getGlobalVisibleRect(visibleRect) || visibleRect.isEmpty) return false
        var current: View? = this
        while (current != null) {
            if (current.visibility != VISIBLE || current.alpha <= 0.01f) return false
            current = current.parent as? View
        }
        return true
    }

    private fun scheduleFrame() {
        if (!shouldScheduleFrames() || frameScheduled) return
        Choreographer.getInstance().postFrameCallback(this)
        frameScheduled = true
    }

    private fun stopFrames() {
        if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this)
        frameScheduled = false
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 42L
        private const val HIDDEN_PROBE_INTERVAL_MS = 250L
        private const val ARTWORK_TRANSITION_SECONDS = 0.85f
        private const val FLOW_SATURATION = 2.5f
        private val DEFAULT_ARTWORK = createDefaultArtwork()

        private fun createDefaultArtwork(): MediaFlowArtwork {
            val bitmap = MediaSoftArtworkFactory.createTexture(
                MediaSoftPaletteExtractor.fromColors(emptyList())
            )
            return try {
                requireNotNull(MediaFlowArtwork.prepare(bitmap))
            } finally {
                bitmap.recycle()
            }
        }

        private const val FLOW_SHADER = """
            uniform shader uArtworkFrom;
            uniform shader uArtworkTo;
            uniform float2 uResolution;
            uniform float uTextureSize;
            uniform float uTime;
            uniform float uTransition;
            uniform float uSurfaceBlend;
            uniform float uSaturation;
            uniform float4 uSurface;

            float hash(float2 point) {
                return fract(sin(dot(point, float2(127.1, 311.7))) * 43758.5453);
            }

            float2 rotatePoint(float2 point, float angle) {
                float sine = sin(angle);
                float cosine = cos(angle);
                return float2(point.x * cosine - point.y * sine, point.x * sine + point.y * cosine);
            }

            float3 sampleArtwork(float2 uv) {
                float2 point = clamp(uv, float2(0.002), float2(0.998)) * (uTextureSize - 1.0);
                return mix(float3(uArtworkFrom.eval(point).rgb), float3(uArtworkTo.eval(point).rgb), uTransition);
            }

            float2 layerUv(float2 uv, float angle, float zoom, float2 offset) {
                return rotatePoint(uv - 0.5, angle) / zoom + 0.5 + offset;
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / uResolution;
                float aspect = uResolution.x / max(uResolution.y, 1.0);
                float2 coverUv = uv;
                if (aspect > 1.0) coverUv.y = (coverUv.y - 0.5) / aspect + 0.5;
                else coverUv.x = (coverUv.x - 0.5) * aspect + 0.5;

                float fullTurn = 6.2831853;
                float3 layer0 = sampleArtwork(layerUv(coverUv, uTime * fullTurn / 100.0, 1.34, float2(0.0)));
                float3 layer1 = sampleArtwork(layerUv(coverUv, -uTime * fullTurn / 70.0 + 1.7, 1.48, float2(-0.08, -0.06)));
                float3 layer2 = sampleArtwork(layerUv(coverUv, -uTime * fullTurn / 40.0 + 3.8, 1.58, float2(0.07, 0.08)));
                float blend1 = 0.24 + 0.13 * (0.5 + 0.5 * sin(dot(uv, float2(2.1, 1.3)) * 3.14159 + uTime * 0.035));
                float blend2 = 0.16 + 0.11 * (0.5 + 0.5 * cos(dot(uv, float2(-1.2, 2.4)) * 3.14159 - uTime * 0.029));
                float3 color = mix(mix(layer0, layer1, blend1), layer2, blend2);
                float luminance = dot(color, float3(0.2126, 0.7152, 0.0722));
                color = mix(float3(luminance), color, uSaturation);
                color = mix(color, uSurface.rgb, uSurfaceBlend);
                color += sin(dot(uv, float2(1.5, 2.0)) * 3.14159 + uTime * 0.041) * 0.006;
                color += (hash(fragCoord) - 0.5) * 0.005;
                return half4(clamp(color, 0.0, 1.0), 1.0);
            }
        """
    }
}

internal object MediaFlowOverlayLayout {
    fun createConstraintFill(source: ViewGroup.LayoutParams): ViewGroup.LayoutParams? {
        val sourceClass = source.javaClass
        return runCatching {
            val intType = Int::class.javaPrimitiveType ?: error("No primitive int type")
            val params = sourceClass.getDeclaredConstructor(intType, intType)
                .apply { isAccessible = true }
                .newInstance(0, 0) as ViewGroup.LayoutParams
            listOf("leftToLeft", "topToTop", "rightToRight", "bottomToBottom")
                .forEach { fieldName ->
                    sourceClass.getDeclaredField(fieldName).apply {
                        isAccessible = true
                        setInt(params, 0)
                    }
                }
            params
        }.getOrNull()
    }

    fun copyForOverlay(source: ViewGroup.LayoutParams): ViewGroup.LayoutParams? {
        val sourceClass = source.javaClass
        return runCatching {
            sourceClass.getDeclaredConstructor(sourceClass).apply { isAccessible = true }
                .newInstance(source) as ViewGroup.LayoutParams
        }.recoverCatching {
            sourceClass.getDeclaredConstructor(ViewGroup.LayoutParams::class.java)
                .apply { isAccessible = true }
                .newInstance(source) as ViewGroup.LayoutParams
        }.recoverCatching {
            val intType = Int::class.javaPrimitiveType ?: error("No primitive int type")
            sourceClass.getDeclaredConstructor(intType, intType).apply { isAccessible = true }
                .newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                as ViewGroup.LayoutParams
        }.getOrNull()
    }
}
