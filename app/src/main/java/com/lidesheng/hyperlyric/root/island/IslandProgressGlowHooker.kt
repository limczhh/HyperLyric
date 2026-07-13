package com.lidesheng.hyperlyric.root.island

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal object IslandProgressGlowHooker {
    private const val TAG = "IslandProgressGlowHooker"
    private const val BACKGROUND_VIEW_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"

    private val stateByBackgroundView = WeakHashMap<View, ProgressState>()
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var loggedFirstDraw = false

    fun initialize(module: XposedModule) {
        this.module = module
    }

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        initialize(module)
        if (!hookedClassLoaders.add(classLoader)) return

        try {
            val backgroundClass = classLoader.loadClass(BACKGROUND_VIEW_CLASS)
            val onDrawMethod = backgroundClass.declaredMethods.firstOrNull {
                it.name == "onDraw" &&
                    it.parameterTypes.contentEquals(arrayOf(Canvas::class.java))
            } ?: throw NoSuchMethodException("$BACKGROUND_VIEW_CLASS.onDraw")
            onDrawMethod.isAccessible = true
            module.deoptimize(onDrawMethod)
            module.hook(onDrawMethod).intercept(BackgroundDrawHook())
            HookLogger.i(TAG, "Native island background progress hook initialized")
        } catch (e: Throwable) {
            hookedClassLoaders.remove(classLoader)
            throw e
        }
    }

    fun adoptBackgroundHook(backgroundClass: Class<*>) {
        backgroundClass.classLoader?.let { hookedClassLoaders.add(it) }
    }

    fun setMediaProgress(
        backgroundView: View,
        fraction: Float,
        progressColor: Int,
        trackColor: Int,
        progressStyle: Int
    ) {
        val state = synchronized(stateByBackgroundView) {
            stateByBackgroundView[backgroundView] ?: ProgressState(backgroundView).also {
                stateByBackgroundView[backgroundView] = it
            }
        }
        state.fraction = fraction.coerceIn(0f, 1f)
        state.progressColor = progressColor
        state.trackColor = trackColor
        state.progressStyle = progressStyle
        backgroundView.invalidate()
    }

    fun clearMediaProgress(backgroundView: View) {
        val removed = synchronized(stateByBackgroundView) {
            stateByBackgroundView.remove(backgroundView) != null
        }
        if (removed) backgroundView.invalidate()
    }

    class BackgroundDrawHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val backgroundView = chain.thisObject as? View ?: return result
            val canvas = chain.args.firstOrNull() as? Canvas ?: return result
            val state = synchronized(stateByBackgroundView) {
                stateByBackgroundView[backgroundView]
            } ?: return result

            state.draw(canvas)
            if (!loggedFirstDraw) {
                synchronized(IslandProgressGlowHooker) {
                    if (!loggedFirstDraw) {
                        loggedFirstDraw = true
                        HookLogger.i(
                            TAG,
                            "Native background progress drawn: progress=${state.fraction} " +
                                "view=${backgroundView.javaClass.name}"
                        )
                    }
                }
            }
            return result
        }
    }

    private class ProgressState(private val backgroundView: View) {
        private val actualLeft = findGetter(backgroundView, "getActualLeft")
        private val actualTop = findGetter(backgroundView, "getActualTop")
        private val actualRight = findGetter(backgroundView, "getActualWidth")
        private val actualBottom = findGetter(backgroundView, "getActualHeight")
        private val strokeWidth = findGetter(backgroundView, "getStokeWidth")

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val trackPath = Path()
        private val progressPath = Path()
        private val topRightArc = RectF()
        private val bottomRightArc = RectF()
        private val bottomLeftArc = RectF()
        private val topLeftArc = RectF()
        private val pathMeasure = PathMeasure()
        private val islandRadius = resolveIslandRadius(backgroundView)

        @Volatile
        var fraction: Float = 0f

        @Volatile
        var progressColor: Int = DEFAULT_PROGRESS_COLOR

        @Volatile
        var trackColor: Int = DEFAULT_TRACK_COLOR

        @Volatile
        var progressStyle: Int = RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_STYLE

        fun draw(canvas: Canvas) {
            val nativeStroke = (strokeWidth.invokeInt() ?: return).toFloat()
            val left = (actualLeft.invokeInt() ?: return).toFloat()
            val top = (actualTop.invokeInt() ?: return).toFloat()
            val right = (actualRight.invokeInt() ?: return).toFloat()
            val bottom = (actualBottom.invokeInt() ?: return).toFloat()
            if (right <= left || bottom <= top || nativeStroke <= 0f) return

            val drawStroke = (nativeStroke * STROKE_WIDTH_RATIO).coerceAtLeast(1f)
            val halfStroke = drawStroke / 2f
            val pathLeft = left - halfStroke + EDGE_OVERLAP_PX
            val pathTop = top - halfStroke + EDGE_OVERLAP_PX
            val pathRight = right + halfStroke - EDGE_OVERLAP_PX
            val pathBottom = bottom + halfStroke - EDGE_OVERLAP_PX
            val radius = (islandRadius + halfStroke - EDGE_OVERLAP_PX).coerceAtMost(
                minOf(pathRight - pathLeft, pathBottom - pathTop) / 2f
            )
            val centerX = (pathLeft + pathRight) / 2f

            trackPath.reset()
            trackPath.moveTo(centerX, pathTop)
            trackPath.lineTo(pathRight - radius, pathTop)
            topRightArc.set(
                pathRight - radius * 2f,
                pathTop,
                pathRight,
                pathTop + radius * 2f
            )
            trackPath.arcTo(topRightArc, -90f, 90f, false)
            trackPath.lineTo(pathRight, pathBottom - radius)
            bottomRightArc.set(
                pathRight - radius * 2f,
                pathBottom - radius * 2f,
                pathRight,
                pathBottom
            )
            trackPath.arcTo(bottomRightArc, 0f, 90f, false)
            trackPath.lineTo(pathLeft + radius, pathBottom)
            bottomLeftArc.set(
                pathLeft,
                pathBottom - radius * 2f,
                pathLeft + radius * 2f,
                pathBottom
            )
            trackPath.arcTo(bottomLeftArc, 90f, 90f, false)
            trackPath.lineTo(pathLeft, pathTop + radius)
            topLeftArc.set(
                pathLeft,
                pathTop,
                pathLeft + radius * 2f,
                pathTop + radius * 2f
            )
            trackPath.arcTo(topLeftArc, 180f, 90f, false)
            trackPath.close()

            trackPaint.strokeWidth = drawStroke
            trackPaint.color = trackColor
            progressPaint.strokeWidth = drawStroke
            progressPaint.color = Color.rgb(
                Color.red(progressColor),
                Color.green(progressColor),
                Color.blue(progressColor)
            )
            progressPaint.alpha = PROGRESS_ALPHA

            canvas.drawPath(trackPath, trackPaint)
            if (fraction <= 0f) return
            if (fraction >= 1f) {
                canvas.drawPath(trackPath, progressPaint)
                return
            }

            pathMeasure.setPath(trackPath, false)
            progressPath.reset()
            val pathLength = pathMeasure.length
            if (progressStyle == RootConstants.ISLAND_PROGRESS_STYLE_LEFT_BIDIRECTIONAL) {
                val branchLength = pathLength * 0.5f * fraction
                appendWrappedSegment(
                    startDistance = pathLength * LEFT_START_FRACTION - branchLength,
                    segmentLength = branchLength * 2f
                )
            } else {
                appendWrappedSegment(
                    startDistance = pathLength * startFraction(progressStyle),
                    segmentLength = pathLength * fraction
                )
            }
            canvas.drawPath(progressPath, progressPaint)
        }

        private fun Method.invokeInt(): Int? {
            return runCatching { invoke(backgroundView) as? Int }.getOrNull()
        }

        private fun findGetter(view: View, name: String): Method {
            return view.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.isEmpty()
            } ?: throw NoSuchMethodException("${view.javaClass.name}.$name")
        }

        private fun resolveIslandRadius(view: View): Float {
            val resourceId = runCatching {
                view.javaClass.classLoader
                    ?.loadClass(ISLAND_DIMEN_CLASS)
                    ?.getDeclaredField(ISLAND_RADIUS_FIELD)
                    ?.getInt(null)
            }.getOrNull()
            return resourceId
                ?.takeIf { it != 0 }
                ?.let { view.resources.getDimension(it) }
                ?: DEFAULT_ISLAND_RADIUS_DP * view.resources.displayMetrics.density
        }

        private fun appendWrappedSegment(startDistance: Float, segmentLength: Float) {
            val pathLength = pathMeasure.length
            if (pathLength <= 0f || segmentLength <= 0f) return

            val normalizedStart = ((startDistance % pathLength) + pathLength) % pathLength
            val clampedLength = segmentLength.coerceAtMost(pathLength)
            val endDistance = normalizedStart + clampedLength
            if (endDistance <= pathLength) {
                pathMeasure.getSegment(
                    normalizedStart,
                    endDistance,
                    progressPath,
                    true
                )
                return
            }

            val hasFirstSegment = pathMeasure.getSegment(
                normalizedStart,
                pathLength,
                progressPath,
                true
            )
            pathMeasure.getSegment(
                0f,
                endDistance - pathLength,
                progressPath,
                !hasFirstSegment
            )
        }

        private fun startFraction(style: Int): Float {
            return when (style) {
                RootConstants.ISLAND_PROGRESS_STYLE_RIGHT_CLOCKWISE -> 0.25f
                RootConstants.ISLAND_PROGRESS_STYLE_BOTTOM_CLOCKWISE -> 0.5f
                RootConstants.ISLAND_PROGRESS_STYLE_LEFT_CLOCKWISE -> LEFT_START_FRACTION
                else -> 0f
            }
        }
    }

    private const val DEFAULT_PROGRESS_COLOR = 0xFF5B8CFF.toInt()
    private const val DEFAULT_TRACK_COLOR = 0x66757575
    private const val PROGRESS_ALPHA = 255
    private const val STROKE_WIDTH_RATIO = 1f
    private const val LEFT_START_FRACTION = 0.75f
    private const val EDGE_OVERLAP_PX = 0.5f
    private const val ISLAND_DIMEN_CLASS = "miui.systemui.dynamicisland.R\$dimen"
    private const val ISLAND_RADIUS_FIELD = "island_radius"
    private const val DEFAULT_ISLAND_RADIUS_DP = 30f
}
