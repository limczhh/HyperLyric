package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.view.View
import android.view.ViewParent

/**
 * Owns only the parent-intercept contract for the notification media card.
 *
 * HyperOS' [MiuiNotificationSwipeHelper] lives above the media header. The
 * selected media-card mode must block it for a horizontal switch gesture,
 * while a seek bar must block the card container itself and a vertical gesture
 * must be released back to the notification stack.
 */
internal class NotificationMediaNativeGestureBlocker(
    private val isCarouselActive: () -> Boolean,
    private val isNativeEdgeMenuShowing: () -> Boolean,
    private val headerParent: () -> ViewParent?
) {
    private var parentInterceptDisallowed = false

    fun onDown(view: View, seekBarTouch: Boolean) {
        if (seekBarTouch || (isCarouselActive() && !isNativeEdgeMenuShowing())) {
            disallow(view, seekBarTouch)
        }
    }

    fun onHorizontal(view: View, seekBarTouch: Boolean) {
        if (!seekBarTouch) {
            disallow(view, seekBarTouch = false)
        }
    }

    fun onVertical(view: View) {
        release(view)
    }

    /**
     * A child receives ACTION_CANCEL when PageScrollView intercepts. Keep the
     * outer lock in that case; PageScrollView releases its own parent lock at
     * the end of the gesture.
     */
    fun reset(view: View, releaseParent: Boolean = true) {
        if (releaseParent) release(view)
    }

    fun release(view: View) {
        if (!parentInterceptDisallowed) return
        runCatching {
            view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        parentInterceptDisallowed = false
    }

    /**
     * PageScrollView has already classified this sequence as an outward edge
     * pull. Release the same parent that was locked on ACTION_DOWN so the
     * native notification SwipeHelper can intercept the next MOVE.
     */
    fun releaseForNativeGesture() {
        if (!parentInterceptDisallowed) return
        runCatching {
            (headerParent() ?: return@runCatching)
                .requestDisallowInterceptTouchEvent(false)
        }
        parentInterceptDisallowed = false
    }

    private fun disallow(view: View, seekBarTouch: Boolean) {
        if (parentInterceptDisallowed) return
        val parent = if (seekBarTouch) {
            // SeekBar: block the page container as well as its outer parents.
            view.parent
        } else {
            // Carousel: do not start at the player/page container, otherwise
            // HorizontalScrollView cannot intercept its child on the MOVE.
            headerParent() ?: view.parent
        } ?: return
        runCatching {
            parent.requestDisallowInterceptTouchEvent(true)
            parentInterceptDisallowed = true
        }
    }
}
