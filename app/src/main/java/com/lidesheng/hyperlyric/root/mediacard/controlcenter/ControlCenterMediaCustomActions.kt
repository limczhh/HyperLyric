package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/** Keeps native actions in the middle three slots and app actions in the outer two. */
internal class ControlCenterMediaCustomActions(
    private val container: ViewGroup?,
    private val title: TextView,
    private val enabled: Boolean,
    private val requestRefresh: () -> Unit = {},
) {
    private data class ActionSource(
        val controller: MediaController,
        val actions: List<PlaybackState.CustomAction>,
        val isPlaying: Boolean,
    )

    private val nativeButtons = container?.let { viewGroup ->
        (0 until viewGroup.childCount)
            .map { viewGroup.getChildAt(it) }
            .filterIsInstance<ImageView>()
            .take(3)
    }.orEmpty()
    private val nativeParams = nativeButtons.mapNotNull { button ->
        button.layoutParams?.let { params ->
            runCatching { button to MediaLayoutParams.copy(params) }.getOrNull()
        }
    }.toMap()
    private val customButtons = arrayOfNulls<ImageView>(2)
    private val available = BooleanArray(2)
    private var visible = true
    private var lastChain = emptyList<View>()
    private var nativeGap: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { title.context.getSystemService(MediaSessionManager::class.java) }
    private var sessions = emptyList<MediaController>()
    private var observing = false
    private var sourceDirty = true
    private var cachedSource: ActionSource? = null
    private var refreshPosted = false
    private val refreshTask = Runnable { refreshPosted = false; requestRefresh() }
    private val iconKeys = arrayOfNulls<Pair<String, Int>>(2)
    private val sessionCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = scheduleRefresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = scheduleRefresh()
        override fun onSessionDestroyed() = scheduleRefresh()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        observeSessions(controllers.orEmpty())
        scheduleRefresh()
    }

    init {
        if (enabled && container != null) {
            title.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) { startObserving(); scheduleRefresh() }
                override fun onViewDetachedFromWindow(view: View) {
                    sessions.forEach { runCatching { it.unregisterCallback(sessionCallback) } }
                    sessions = emptyList()
                    runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
                    observing = false
                    sourceDirty = true
                    cachedSource = null
                    handler.removeCallbacks(refreshTask)
                    refreshPosted = false
                }
            })
        }
    }

    private fun scheduleRefresh() {
        sourceDirty = true
        if (refreshPosted || !title.isAttachedToWindow) return
        refreshPosted = true
        handler.post(refreshTask)
    }

    private fun startObserving() {
        if (observing) return
        observing = true
        runCatching { manager?.addOnActiveSessionsChangedListener(sessionsListener, null, handler) }
        observeSessions(runCatching { manager?.getActiveSessions(null) }.getOrNull().orEmpty())
    }

    private fun observeSessions(controllers: List<MediaController>) {
        sessions.forEach { runCatching { it.unregisterCallback(sessionCallback) } }
        sessions = controllers
        sessions.forEach { runCatching { it.registerCallback(sessionCallback, handler) } }
        sourceDirty = true
    }

    /** Called after measuring the original three-button hierarchy, before insertion. */
    fun captureNativeGeometry() {
        if (!enabled || lastChain.isNotEmpty() || nativeButtons.size != 3) return
        val gaps = nativeButtons.zipWithNext { left, right -> right.left - left.right }
        if (nativeButtons.all { it.width > 0 } && gaps.all { it >= 0 }) {
            nativeGap = gaps.sum() / gaps.size
        }
    }

    fun refresh(): Boolean {
        val previous = lastChain
        refreshButtons()
        return previous != lastChain
    }

    /** Extend the three-button row by one native button pitch at either end. */
    fun expandedWidth(row: View, nativeWidth: Int): Int {
        if (row !== container || lastChain.isEmpty() || nativeWidth <= 0) return nativeWidth
        val widths = nativeButtons.map { button ->
            nativeParams[button]?.width?.takeIf { it > 0 } ?: button.measuredWidth
        }
        if (widths.any { it <= 0 }) return nativeWidth
        val gap = nativeGap ?: return nativeWidth
        return row.paddingLeft + row.paddingRight + widths.sum() +
            2 * widths.first() + 4 * gap
    }

    private fun refreshButtons() {
        if (!enabled || container == null || nativeButtons.size != 3) return
        // Never insert into an unmeasured row: it would destroy the native gap
        // before the expanded layout has had a chance to capture it.
        if (nativeGap == null) return
        for (index in customButtons.indices) {
            if (customButtons[index] == null) {
                val button = newButton() ?: return
                container.addView(button)
                customButtons[index] = button
            }
        }
        val source = resolveSource()
        val first = source?.actions?.firstOrNull()
            ?.let { createButton(0, source.controller, it) }
        val last = source?.actions?.lastOrNull()
            ?.takeUnless { source.actions.size == 1 }
            ?.let { createButton(1, source.controller, it) }
        updateAvailability(0, first != null)
        updateAvailability(1, last != null)
        connectChain()
    }

    /** INVISIBLE keeps both outer slots in the chain, including during collapse. */
    fun setVisible(show: Boolean) {
        visible = show
        customButtons.forEachIndexed { index, button ->
            button?.visibility = if (show && available[index]) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun updateAvailability(index: Int, hasAction: Boolean) {
        available[index] = hasAction
        val button = customButtons[index] ?: return
        if (!hasAction) {
            button.setImageDrawable(null)
            button.setOnClickListener(null)
            button.contentDescription = null
            button.tooltipText = null
            iconKeys[index] = null
        }
        button.isEnabled = hasAction
        button.isClickable = hasAction
        button.isFocusable = hasAction
        button.importantForAccessibility = if (hasAction) nativeButtons[0].importantForAccessibility
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO
        button.visibility = if (visible && hasAction) View.VISIBLE else View.INVISIBLE
    }

    fun restore() {
        val host = container ?: return
        if (customButtons.all { it == null } && lastChain.isEmpty()) return
        customButtons.forEach { button ->
            button?.let { if (it.parent === host) host.removeView(it) }
        }
        customButtons.fill(null)
        available.fill(false)
        iconKeys.fill(null)
        lastChain = emptyList()
        nativeParams.forEach { (button, params) ->
            button.layoutParams = MediaLayoutParams.copy(params)
        }
        host.requestLayout()
    }

    private fun createButton(
        index: Int,
        controller: MediaController,
        action: PlaybackState.CustomAction,
    ): ImageView? {
        val button = customButtons[index] ?: return null
        val iconKey = controller.packageName to action.icon
        if (iconKeys[index] != iconKey || button.drawable == null) {
            button.setImageDrawable(loadDrawable(controller, action) ?: return null)
            iconKeys[index] = iconKey
        }
        button.contentDescription = action.name
        button.tooltipText = action.name
        button.setOnClickListener {
            runCatching {
                controller.transportControls.sendCustomAction(action.action, action.extras)
            }
        }
        return button
    }

    private fun newButton(): ImageView? {
        val reference = nativeButtons.getOrNull(0) ?: return null
        return ImageView(container?.context ?: title.context).also {
            it.layoutParams = runCatching { MediaLayoutParams.fresh(reference.layoutParams) }.getOrNull()
                ?: return null
            it.scaleType = reference.scaleType
            it.layoutDirection = reference.layoutDirection
            it.imageTintList = reference.imageTintList
            it.imageTintMode = reference.imageTintMode
            it.imageTintBlendMode = reference.imageTintBlendMode
            it.setPadding(
                reference.paddingLeft,
                reference.paddingTop,
                reference.paddingRight,
                reference.paddingBottom
            )
            it.alpha = reference.alpha
            it.visibility = View.INVISIBLE
            it.isEnabled = false
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            it.id = View.generateViewId()
            it.isClickable = false
            it.isFocusable = false
        }
    }

    private fun connectChain() {
        val host = container ?: return
        val chain = buildList {
            customButtons[0]?.let(::add)
            addAll(nativeButtons)
            customButtons[1]?.let(::add)
        }
        if (chain == lastChain) return
        lastChain = chain
        chain.forEachIndexed { index, view ->
            val params = MediaLayoutParams.fresh(view.layoutParams)
            setInt(params, "topToTop", 0)
            setInt(params, "bottomToBottom", 0)
            (params as ViewGroup.MarginLayoutParams).marginStart = if (index == 0) 0 else nativeGap ?: 0
            if (index == 0) {
                setInt(params, "startToStart", 0)
            } else {
                setInt(params, "startToEnd", chain[index - 1].id)
            }
            if (index == chain.lastIndex) {
                setInt(params, "endToEnd", 0)
            } else {
                setInt(params, "endToStart", chain[index + 1].id)
            }
            // Explicit native gaps, rather than redistributing whatever width
            // the solver happens to give the row during a transition.
            setInt(params, "horizontalChainStyle", 2)
            view.layoutParams = params
        }
        host.requestLayout()
    }

    private fun setInt(params: ViewGroup.LayoutParams, name: String, value: Int) {
        runCatching { params.javaClass.getField(name).setInt(params, value) }
    }

    private fun resolveSource(): ActionSource? {
        startObserving()
        if (!sourceDirty) return cachedSource
        sourceDirty = false
        val candidates = sessions.mapNotNull { controller ->
            val state = controller.playbackState ?: return@mapNotNull null
            val actions = state.customActions.orEmpty()
                .filter { it.action.isNotBlank() && it.icon != 0 }
            actions.takeIf { it.isNotEmpty() }?.let {
                ActionSource(controller, it, state.state == PlaybackState.STATE_PLAYING)
            }
        }
        val currentTitle = title.text?.toString()?.trim().orEmpty()
        return candidates.maxByOrNull { source ->
            val sessionTitle = source.controller.metadata
                ?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.trim()
                .orEmpty()
            (if (currentTitle.isNotEmpty() && currentTitle == sessionTitle) 4 else 0) +
                (if (source.isPlaying) 2 else 0)
        }.also { cachedSource = it }
    }

    private fun loadDrawable(controller: MediaController, action: PlaybackState.CustomAction): Drawable? {
        return runCatching {
            val resources = title.context.packageManager
                .getResourcesForApplication(controller.packageName)
            resources.getDrawable(action.icon, title.context.theme)
        }.getOrNull()
    }
}
