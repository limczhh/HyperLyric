package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Canvas
import android.view.View

/** Preserve native perspective relative to artwork size, and its allocated bounds. */
internal class ControlCenterMediaCoverFlipGeometry(
    private val cover: View,
    private val nativeSize: Int,
) {
    private val nativeCameraDistance = cover.cameraDistance

    fun prepare() {
        if (cover.width <= 0 || nativeSize <= 0) return
        cover.cameraDistance = nativeCameraDistance * cover.width / nativeSize
    }

    fun restore() {
        cover.cameraDistance = nativeCameraDistance
    }

    fun draw(canvas: Canvas, original: () -> Any?): Any? {
        // The native Y rotation projects the near edge above/below its layout
        // rectangle. Clip in parent coordinates, before drawChild transforms it.
        // Keep this clip recorded even at rest: rotation is a RenderNode
        // property animation and need not re-record the parent's display list.
        val save = canvas.save()
        return try {
            canvas.clipRect(cover.left, cover.top, cover.right, cover.bottom)
            original()
        } finally {
            canvas.restoreToCount(save)
        }
    }
}
