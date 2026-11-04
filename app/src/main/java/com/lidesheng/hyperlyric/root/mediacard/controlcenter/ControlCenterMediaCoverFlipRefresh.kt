package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Bitmap
import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import kotlin.math.abs

/** Keep late artwork through native queued-update drops and skipped midpoint callbacks. */
internal class ControlCenterMediaCoverFlipRefresh(
    header: View,
    private val isFlipping: (View) -> Boolean,
    private val canApply: (View) -> Boolean,
    private val apply: (View, Bitmap?) -> Unit,
) : ViewTreeObserver.OnPreDrawListener {
    private val header = WeakReference(header)
    private var cover = WeakReference<View>(null)
    private var observer: ViewTreeObserver? = null
    private var receivedArtwork = false
    private var latest: Bitmap? = null
    private var generation = 0
    private var revision = 0L
    private var appliedRevision = -1L
    private var startRotation = 0f
    private var passedMidpoint = false

    init {
        header.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                stop()
                latest = null
                receivedArtwork = false
            }
        })
    }

    fun update(bitmap: Bitmap?) {
        val nextGeneration = bitmap?.generationId ?: 0
        if (!receivedArtwork || bitmap !== latest || nextGeneration != generation) revision++
        latest = bitmap
        generation = nextGeneration
        receivedArtwork = true
    }

    fun begin(cover: View, fallback: Bitmap?) {
        stop()
        // A delayed native performFlip may still carry an earlier bitmap.
        if (!receivedArtwork) update(fallback)
        this.cover = WeakReference(cover)
        startRotation = cover.rotationY
        passedMidpoint = false
        appliedRevision = -1L
        val view = header.get() ?: return
        observer = view.viewTreeObserver.takeIf { it.isAlive }?.also {
            it.addOnPreDrawListener(this)
        }
    }

    fun afterStart() {
        val view = header.get() ?: return stop()
        val target = cover.get() ?: return stop()
        // Disabled system animations may finish synchronously, before another draw.
        if (!isFlipping(target)) {
            if (view.isAttachedToWindow && canApply(view)) flush(view)
            stop()
        }
    }

    override fun onPreDraw(): Boolean {
        val view = header.get()
        val target = cover.get()
        if (view == null || target == null || !view.isAttachedToWindow || !canApply(view)) {
            stop()
            return true
        }
        val flipping = isFlipping(target)
        if (abs(target.rotationY - startRotation) >= 90f) passedMidpoint = true
        if (passedMidpoint || !flipping) flush(view)
        if (!flipping) stop()
        return true
    }

    private fun flush(view: View) {
        if (!receivedArtwork || appliedRevision == revision) return
        val applied = revision
        apply(view, latest)
        appliedRevision = applied
    }

    fun stop() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
    }
}
