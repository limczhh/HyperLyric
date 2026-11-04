package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Rect
import android.view.View

/** Native frameCallback gives the card and its background the same animated size. */
internal class ControlCenterMediaTransitionClip(private val card: View) {
    private val original = card.clipBounds?.let(::Rect)
    private val bounds = Rect()

    fun apply() {
        bounds.set(0, 0, card.width.coerceAtLeast(0), card.height.coerceAtLeast(0))
        if (original != null && !bounds.intersect(original)) bounds.setEmpty()
        // Clip once in card-local coordinates. Leave native footer positions, scales
        // and alphaProgress untouched, including when the transition reverses.
        card.clipBounds = bounds
    }

    fun restore() {
        card.clipBounds = original
    }
}
