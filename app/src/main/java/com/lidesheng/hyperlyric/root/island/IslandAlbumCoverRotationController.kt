package com.lidesheng.hyperlyric.root.island

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import java.util.WeakHashMap

internal object IslandAlbumCoverRotationController {
    private const val ROTATION_DURATION_MS = 20_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val states = WeakHashMap<ImageView, RotationState>()

    @Volatile
    private var playbackActive = true

    private val attachStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            val imageView = view as? ImageView ?: return
            states[imageView]?.let { startIfNeeded(imageView, it) }
        }

        override fun onViewDetachedFromWindow(view: View) {
            val imageView = view as? ImageView ?: return
            states[imageView]?.let { stopAnimator(imageView, it, resetRotation = true) }
        }
    }

    fun attach(view: ImageView) {
        runOnMain {
            val state = states.getOrPut(view) {
                view.addOnAttachStateChangeListener(attachStateListener)
                RotationState()
            }
            startIfNeeded(view, state)
        }
    }

    fun detach(view: ImageView) {
        runOnMain {
            val state = states.remove(view) ?: return@runOnMain
            view.removeOnAttachStateChangeListener(attachStateListener)
            stopAnimator(view, state, resetRotation = true)
        }
    }

    fun setPlaybackActive(active: Boolean) {
        if (playbackActive == active) return
        playbackActive = active
        runOnMain {
            states.toList().forEach { (view, state) ->
                if (active) {
                    startIfNeeded(view, state)
                } else {
                    state.animator?.takeIf { it.isStarted && !it.isPaused }?.pause()
                }
            }
        }
    }

    fun cleanup() {
        runOnMain {
            states.toList().forEach { (view, state) ->
                view.removeOnAttachStateChangeListener(attachStateListener)
                stopAnimator(view, state, resetRotation = true)
            }
            states.clear()
        }
    }

    private fun startIfNeeded(view: ImageView, state: RotationState) {
        if (!playbackActive || !view.isAttachedToWindow) return

        val existing = state.animator
        if (existing != null) {
            if (existing.isPaused) existing.resume()
            return
        }

        state.animator = ObjectAnimator.ofFloat(
            view,
            View.ROTATION,
            view.rotation,
            view.rotation + 360f
        ).apply {
            duration = ROTATION_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopAnimator(
        view: ImageView,
        state: RotationState,
        resetRotation: Boolean
    ) {
        state.animator?.cancel()
        state.animator = null
        if (resetRotation) view.rotation = 0f
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class RotationState(
        var animator: ObjectAnimator? = null
    )
}
