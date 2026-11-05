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
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import kotlin.math.max
import kotlin.math.min

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
    private val fallbackColors =
        listOf(0xffe60012.toInt(), 0xffff7a3d.toInt(), 0xff4d68ff.toInt(), 0xffd64d9d.toInt())

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

internal data class MediaFlowAppearance(
    val surface: Int,
    val surfaceBlend: Float,
    val saturation: Float
)

internal object MediaSoftArtworkFactory {
    fun appearance(tone: MediaFlowTone): MediaFlowAppearance = when (tone) {
        MediaFlowTone.DARK -> MediaFlowAppearance(
            surface = 0xff121316.toInt(),
            surfaceBlend = 0.39f,
            saturation = 2.5f
        )
        MediaFlowTone.LIGHT -> MediaFlowAppearance(
            surface = 0xfff3f3f5.toInt(),
            surfaceBlend = 0.65f,
            saturation = 2.5f
        )
    }

    /** 浅色模式叠加色 (1:1 Salt Music AbstractC0479mu.f12380) */
    val LIGHT_ADDITIONAL_COLORS = intArrayOf(
        0x95ffffff.toInt(),
        0x2affffff
    )

    /** 深色模式叠加色 (1:1 Salt Music AbstractC0479mu.f12381) */
    val DARK_ADDITIONAL_COLORS = intArrayOf(
        0x52000000,
        0x1a000000
    )

    /**
     * 静态渲染（供通知中心/超级岛卡片的"柔光封面背景"离线生成 Bitmap）
     * 针对后台工作线程的纯软件 Canvas 优化，严格兼容 BaseCanvas，绝不调用不受支持的 RuntimeShader
     */
    fun renderStatic(
        source: Bitmap,
        tone: MediaFlowTone,
        width: Int,
        height: Int,
        densityDpi: Int = 440
    ): Bitmap {
        if (width <= 0 || height <= 0 || source.isRecycled) return createBitmap(1, 1)
        val scaleFactor = if (densityDpi >= 420) 32f else 20f
        val smallW = width / scaleFactor
        val smallH = height / scaleFactor
        val bufW = Math.floor(smallW + 58.0).toInt().coerceAtLeast(1)
        val bufH = Math.floor(smallH + 58.0).toInt().coerceAtLeast(1)

        val buffer = Bitmap.createBitmap(bufW, bufH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(buffer)
        val avgColor = calculateAverageColor(source)
        buffer.eraseColor(avgColor)

        val fM6509 = Math.floor(max(bufW.toFloat(), bufH.toFloat()) * 1.3).toFloat()
        val coverScale = fM6509 / source.height.toFloat().coerceAtLeast(1f)
        val f5 = (-(fM6509 - bufW)) / 2.0f
        val f7 = (-(fM6509 - bufH)) / 2.0f
        val f8 = fM6509 / 2.0f

        val saturationPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
        }
        val matrix = Matrix()

        // 横屏长轴自适应 90 度三层微型平铺
        // Layer 0: 居中 (+90°)
        matrix.reset()
        matrix.setScale(coverScale, coverScale)
        matrix.postRotate(90f, f8, f8)
        matrix.postTranslate(f5, f7)
        canvas.drawBitmap(source, matrix, saturationPaint)

        // Layer 1: 左偏 (-0.7*W, -0.95*H)
        matrix.reset()
        matrix.setScale(coverScale, coverScale)
        matrix.postRotate(90f, f8, f8)
        matrix.postTranslate(f5, f7)
        matrix.postTranslate(bufW * (-0.7f), bufH * (-0.95f))
        canvas.drawBitmap(source, matrix, saturationPaint)

        // Layer 2: 右偏 (+0.7*W, -0.5*H) + 围绕中心自转
        matrix.reset()
        matrix.setScale(coverScale, coverScale)
        matrix.postRotate(90f, f8, f8)
        matrix.postTranslate(f5, f7)
        matrix.postTranslate(bufW * 0.7f, bufH * (-0.5f))
        matrix.postRotate(90f, bufW / 2.0f, bufH / 2.0f)
        canvas.drawBitmap(source, matrix, saturationPaint)

        // 双层深浅遮罩覆盖 (1:1 Salt Music)
        val colors = if (tone == MediaFlowTone.LIGHT) LIGHT_ADDITIONAL_COLORS else DARK_ADDITIONAL_COLORS
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (color in colors) {
            overlayPaint.color = color
            canvas.drawPaint(overlayPaint)
        }

        // 半径 25 的微型平滑高斯晕染
        blurBitmap(buffer, radius = 25)

        // 放大至最终目标宽高（使用纯软件 Canvas 完美支持的 BitmapShader 线性过滤）
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val resultCanvas = Canvas(result)

        val shaderMatrix = Matrix().apply {
            setScale(scaleFactor, scaleFactor)
            preTranslate((-(bufW - smallW)) / 2.0f, (-(bufH - smallH)) / 2.0f)
        }
        val shader = BitmapShader(buffer, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(shaderMatrix)
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        resultCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), drawPaint)

        buffer.recycle()
        return result
    }

    fun calculateAverageColor(bitmap: Bitmap): Int {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return Color.BLACK
        var jRed = 0L
        var jGreen = 0L
        var jBlue = 0L
        var count = 0
        val bw = bitmap.width
        val bh = bitmap.height
        for (row in 0 until 5) {
            val y = (((row + 0.5f) * bh) / 5f).toInt().coerceIn(0, bh - 1)
            for (col in 0 until 5) {
                val x = (((col + 0.5f) * bw) / 5f).toInt().coerceIn(0, bw - 1)
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                jRed += (Color.red(pixel) * alpha) / 255
                jGreen += (Color.green(pixel) * alpha) / 255
                jBlue += (Color.blue(pixel) * alpha) / 255
                count++
            }
        }
        return if (count > 0) {
            Color.rgb((jRed / count).toInt(), (jGreen / count).toInt(), (jBlue / count).toInt())
        } else {
            Color.BLACK
        }
    }

    fun blurBitmap(bitmap: Bitmap, radius: Int) {
        if (radius <= 0 || bitmap.isRecycled) return
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val boxRadius = (radius * 0.577f).toInt().coerceAtLeast(1)
        val temp = IntArray(w * h)
        boxBlurPass(pixels, temp, w, h, boxRadius)
        boxBlurPass(pixels, temp, w, h, boxRadius)
        boxBlurPass(pixels, temp, w, h, boxRadius)

        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun boxBlurPass(src: IntArray, temp: IntArray, w: Int, h: Int, r: Int) {
        val window = r * 2 + 1
        val invWindow = 1.0f / window

        for (y in 0 until h) {
            val rowOffset = y * w
            var a = 0L
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L

            for (i in -r..r) {
                val p = src[rowOffset + i.coerceIn(0, w - 1)]
                a += Color.alpha(p)
                rSum += Color.red(p)
                gSum += Color.green(p)
                bSum += Color.blue(p)
            }

            for (x in 0 until w) {
                temp[rowOffset + x] = Color.argb(
                    (a * invWindow).toInt(),
                    (rSum * invWindow).toInt(),
                    (gSum * invWindow).toInt(),
                    (bSum * invWindow).toInt()
                )
                val outP = src[rowOffset + (x - r).coerceIn(0, w - 1)]
                val inP = src[rowOffset + (x + r + 1).coerceIn(0, w - 1)]
                a += Color.alpha(inP) - Color.alpha(outP)
                rSum += Color.red(inP) - Color.red(outP)
                gSum += Color.green(inP) - Color.green(outP)
                bSum += Color.blue(inP) - Color.blue(outP)
            }
        }

        for (x in 0 until w) {
            var a = 0L
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L

            for (i in -r..r) {
                val p = temp[i.coerceIn(0, h - 1) * w + x]
                a += Color.alpha(p)
                rSum += Color.red(p)
                gSum += Color.green(p)
                bSum += Color.blue(p)
            }

            for (y in 0 until h) {
                val colOffset = y * w + x
                src[colOffset] = Color.argb(
                    (a * invWindow).toInt(),
                    (rSum * invWindow).toInt(),
                    (gSum * invWindow).toInt(),
                    (bSum * invWindow).toInt()
                )
                val outP = temp[(y - r).coerceIn(0, h - 1) * w + x]
                val inP = temp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += Color.alpha(inP) - Color.alpha(outP)
                rSum += Color.red(inP) - Color.red(outP)
                gSum += Color.green(inP) - Color.green(outP)
                bSum += Color.blue(inP) - Color.blue(outP)
            }
        }
    }

    /** 兼容旧代码签名的重载 */
    fun renderStatic(
        palette: MediaSoftPalette,
        tone: MediaFlowTone,
        width: Int,
        height: Int,
        densityDpi: Int = 440
    ): Bitmap {
        val fallback = createFallbackBitmap(palette)
        val result = renderStatic(fallback, tone, width, height, densityDpi)
        fallback.recycle()
        return result
    }

    private fun createFallbackBitmap(palette: MediaSoftPalette): Bitmap {
        val colors = palette.asList()
        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        colors.forEachIndexed { i, color ->
            paint.color = color
            val x = (i % 2) * 16f
            val y = (i / 2) * 16f
            canvas.drawRect(x, y, x + 16f, y + 16f, paint)
        }
        return bmp
    }

    /**
     * 极纯净、完全去除噪点与畸变条纹的 GPU AGSL 流水线
     * 1:1 对齐 Salt Music 三层时间轴 (100s, 70s, 40s) 与饱和度 2.5、深浅双层遮罩
     */
    const val FLOW_SHADER = """
        uniform shader uArtworkFrom;
        uniform shader uArtworkTo;
        uniform float2 uResolution;
        uniform float uTextureSize;
        uniform float uTime;
        uniform float uTransition;
        uniform float uTone; // 0.0 = LIGHT, 1.0 = DARK

        float2 rotatePoint(float2 p, float angle) {
            float s = sin(angle);
            float c = cos(angle);
            return float2(p.x * c - p.y * s, p.x * s + p.y * c);
        }

        float3 sampleArtwork(float2 pScreen, float angle, float zoom, float2 offset) {
            float2 p = pScreen - offset;
            p = rotatePoint(p, -angle);
            p = p / zoom;
            float2 uv = p + 0.5;
            float2 pt = clamp(uv, float2(0.002), float2(0.998)) * (uTextureSize - 1.0);
            float3 fromCol = uArtworkFrom.eval(pt).rgb;
            float3 toCol = uArtworkTo.eval(pt).rgb;
            return mix(fromCol, toCol, uTransition);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / uResolution;
            float aspect = uResolution.x / max(uResolution.y, 1.0);
            float2 p = uv - 0.5;
            p.y = p.y / aspect;

            float TWO_PI = 6.2831853;
            float baseAngle = 1.5707963; // 横屏长轴旋转 90 度映射

            // Layer 0: 周期 100s 顺时针，居中放大 1.3
            float a0 = (uTime / 100.0) * TWO_PI + baseAngle;
            float3 layer0 = sampleArtwork(p, a0, 1.30, float2(0.0, 0.0));

            // Layer 1: 周期 70s 逆时针，横向左偏 (-0.35)
            float a1 = -(uTime / 70.0) * TWO_PI + baseAngle;
            float3 layer1 = sampleArtwork(p, a1, 1.35, float2(-0.35, -0.15 / aspect));

            // Layer 2: 周期 40s 逆时针，横向右偏 (+0.35)，叠加自转
            float a2 = -(uTime / 40.0) * TWO_PI + baseAngle;
            float3 layer2 = sampleArtwork(p, a2 * 1.5, 1.40, float2(0.35, 0.15 / aspect));

            // 三层自然交融混合
            float3 color = mix(layer0, layer1, 0.50);
            color = mix(color, layer2, 0.40);

            // 1:1 饱和度增强 2.5 倍 (Salt Music ColorMatrix 标准)
            float lum = dot(color, float3(0.2126, 0.7152, 0.0722));
            color = mix(float3(lum), color, 2.5);

            // 1:1 Salt Music 浅色/深色纯净遮罩 (浅色白 0.652, 深色黑 0.391)
            if (uTone < 0.5) {
                color = mix(color, float3(1.0), 0.652);
            } else {
                color = mix(color, float3(0.0), 0.391);
            }

            return half4(clamp(color, 0.0, 1.0), 1.0);
        }
    """
}

/**
 * 预晕染光晕模型：在切歌后台做一次高质量高斯平滑，供 GPU 纯硬件并行采样
 */
internal class MediaFlowArtwork private constructor(
    private val textureBitmap: Bitmap,
    private val signature: Int
) {
    fun createBitmap(): Bitmap = textureBitmap

    fun recycle() {
        if (!textureBitmap.isRecycled) {
            textureBitmap.recycle()
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MediaFlowArtwork && signature == other.signature

    override fun hashCode(): Int = signature

    companion object {
        const val TEXTURE_SIZE = 128
        private const val BLUR_RADIUS = 20

        fun prepare(bitmap: Bitmap): MediaFlowArtwork? {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null

            val side = min(bitmap.width, bitmap.height)
            val sourceRect = Rect(
                (bitmap.width - side) / 2,
                (bitmap.height - side) / 2,
                (bitmap.width + side) / 2,
                (bitmap.height + side) / 2
            )
            val texture = createBitmap(TEXTURE_SIZE, TEXTURE_SIZE)
            return try {
                Canvas(texture).drawBitmap(
                    bitmap,
                    sourceRect,
                    Rect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                val pixels = IntArray(TEXTURE_SIZE * TEXTURE_SIZE)
                texture.getPixels(pixels, 0, TEXTURE_SIZE, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE)

                // 3-pass 高质量平滑高斯晕染
                val blurred = fastBlur3Pass(pixels, TEXTURE_SIZE, TEXTURE_SIZE, BLUR_RADIUS)
                val blurredBmp = Bitmap.createBitmap(blurred, TEXTURE_SIZE, TEXTURE_SIZE, Bitmap.Config.ARGB_8888)
                MediaFlowArtwork(blurredBmp, blurred.contentHashCode())
            } finally {
                texture.recycle()
            }
        }

        private fun fastBlur3Pass(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
            if (radius <= 0) return src
            val boxR = (radius * 0.577f).toInt().coerceAtLeast(1)
            var current = src
            repeat(3) {
                current = singleBoxBlur(current, w, h, boxR)
            }
            return current
        }

        private fun singleBoxBlur(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
            val horizontal = IntArray(source.size)
            val result = IntArray(source.size)
            val window = radius * 2 + 1
            val invWindow = 1.0f / window

            for (y in 0 until height) {
                var alpha = 0L
                var red = 0L
                var green = 0L
                var blue = 0L
                val rowOffset = y * width
                for (offset in -radius..radius) {
                    val color = source[rowOffset + offset.coerceIn(0, width - 1)]
                    alpha += Color.alpha(color)
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                }
                for (x in 0 until width) {
                    horizontal[rowOffset + x] = Color.argb(
                        (alpha * invWindow).toInt(),
                        (red * invWindow).toInt(),
                        (green * invWindow).toInt(),
                        (blue * invWindow).toInt()
                    )
                    val removed = source[rowOffset + (x - radius).coerceIn(0, width - 1)]
                    val added = source[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]
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
                        (alpha * invWindow).toInt(),
                        (red * invWindow).toInt(),
                        (green * invWindow).toInt(),
                        (blue * invWindow).toInt()
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

/**
 * 纯 GPU 硬件管线动态流光背景 View：CPU 占用为 0%，硬件插值满帧如丝般顺滑
 */
internal class MediaFlowBackgroundView(
    context: Context,
    private val timeline: MediaFlowTimeline = MediaFlowTimeline()
) : View(context), Choreographer.FrameCallback {

    private val runtimeShader = RuntimeShader(MediaSoftArtworkFactory.FLOW_SHADER)
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
        if (artwork != null && artwork != targetArtwork) {
            updateArtwork(artwork)
        }
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
        Choreographer.getInstance().postFrameCallback(this)
        frameScheduled = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val viewport = transitionViewport
        val shaderWidth = viewport?.width()?.coerceAtLeast(1) ?: width
        val shaderHeight = viewport?.height()?.coerceAtLeast(1) ?: height

        // 仅向 GPU 发送几个基础浮点参数，CPU 计算耗时为 0
        runtimeShader.setFloatUniform("uResolution", shaderWidth.toFloat(), shaderHeight.toFloat())
        runtimeShader.setFloatUniform("uTextureSize", MediaFlowArtwork.TEXTURE_SIZE.toFloat())
        runtimeShader.setFloatUniform("uTime", animationTime)
        runtimeShader.setFloatUniform("uTransition", transitionFraction())
        runtimeShader.setFloatUniform("uTone", if (tone == MediaFlowTone.LIGHT) 0f else 1f)

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
        ).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
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
        private const val ARTWORK_TRANSITION_SECONDS = 0.5f
        private val DEFAULT_ARTWORK = createDefaultArtwork()

        private fun createDefaultArtwork(): MediaFlowArtwork {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(0xff2d3142.toInt())
            return requireNotNull(MediaFlowArtwork.prepare(bmp))
        }
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
                .newInstance(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                    as ViewGroup.LayoutParams
        }.getOrNull()
    }
}
