package com.lidesheng.hyperlyric.root.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.root.utils.HookLogger

/** One source owns this arbiter. All snapshots and downstream writes are serialized on main. */
internal class LyricSessionArbiter(
    context: Context,
    private val downstream: LyricSink,
    private val sampleIntervalMs: Long,
    private val sourceTracksMediaChanges: Boolean = false,
    private val onOwnerRemoved: (Any) -> Unit = {}
) {
    private val appContext = context.applicationContext ?: context
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val handler = Handler(Looper.getMainLooper())
    // Exact tokens and unbound publisher keys share one chronology.
    private val order = LyricPlaybackOrder<Any>()
    private class Session(
        val controller: MediaController,
        val callback: MediaController.Callback,
        var cachedState: PlaybackState?,
        var lastSyncUptimeMs: Long
    )
    private val sessions = LinkedHashMap<MediaSession.Token, Session>()
    private val destroyedTokens = HashSet<MediaSession.Token>()
    private val slots = LinkedHashMap<Any, Slot>()
    private val removedPublishers = HashSet<String>()
    private var selected: Slot? = null
    private var sampling: Slot? = null
    @Volatile private var closed = false
    @Volatile private var screenInteractive: Boolean = powerManager?.isInteractive ?: true
    private var listenerRegistered = false
    private var screenReceiverRegistered = false
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { syncSessions(it.orEmpty()) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenInteractiveChanged(false)
                Intent.ACTION_SCREEN_ON -> onScreenInteractiveChanged(true)
            }
        }
    }

    private companion object {
        // 本地缓存 PlaybackState 的兜底校准间隔，避免每 33ms 跨进程 Binder 轮询
        private const val STATE_RESYNC_INTERVAL_MS = 2000L
    }

    fun start() {
        execute {
            screenInteractive = powerManager?.isInteractive ?: true
            registerScreenReceiver()
            runCatching {
                manager.addOnActiveSessionsChangedListener(listener, null, handler)
                listenerRegistered = true
                syncSessions(manager.getActiveSessions(null))
            }.onFailure { HookLogger.w("LyricSessionArbiter", "媒体会话监听失败", it) }
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            appContext.registerReceiver(screenReceiver, filter, null, handler)
            screenReceiverRegistered = true
        }.onFailure { HookLogger.w("LyricSessionArbiter", "屏幕亮灭广播注册失败", it) }
    }

    private fun onScreenInteractiveChanged(interactive: Boolean) = execute {
        if (screenInteractive == interactive) return@execute
        screenInteractive = interactive
        if (!interactive) {
            handler.removeCallbacks(sample)
            sampling = null
            return@execute
        }
        val winner = selected
        if (winner != null) {
            winner.token?.let { resolvePlaybackState(it, forceRefresh = true) }
            publishPosition(winner)
        }
        updateSampling(winner)
    }

    private fun resolvePlaybackState(token: MediaSession.Token, forceRefresh: Boolean = false): PlaybackState? {
        val session = sessions[token] ?: return null
        val now = SystemClock.uptimeMillis()
        if (!forceRefresh && session.cachedState != null && (now - session.lastSyncUptimeMs) < STATE_RESYNC_INTERVAL_MS) {
            return session.cachedState
        }
        val fresh = runCatching { session.controller.playbackState }.getOrNull()
        session.cachedState = fresh
        session.lastSyncUptimeMs = now
        return fresh
    }

    fun execute(block: () -> Unit) {
        val guarded = {
            if (!closed) {
                try { block() }
                catch (e: Exception) { HookLogger.w("LyricSessionArbiter", "歌词会话事件处理失败", e) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) guarded() else handler.post { guarded() }
    }

    fun update(
        key: Any,
        packageName: String,
        token: MediaSession.Token? = null,
        delayMs: Int = 0,
        block: LyricSink.() -> Unit
    ) = execute {
        if (key is String && key in removedPublishers) {
            if (sessions.values.none { it.controller.packageName == packageName }) return@execute
            removedPublishers.remove(key)
        }
        val slot = slots.getOrPut(key) {
            Slot(key, packageName, token, ::currentMediaId) { onOwnerRemoved(key) }.also {
                checkTrack(it, token?.let { sessionToken -> sessions[sessionToken]?.controller?.metadata })
            }
        }
        slot.delayMs = delayMs
        bind(slot)
        slot.detached = false
        slot.block()
        if (slot.token == null) order.update(key, slot.active)
        if (slot.requiresSelection) {
            reconcile()
        } else if (slot === selected && slot.positionDirty) {
            // Position cannot change ownership or display eligibility.
            publishPosition(slot)
        }
        // An unselected snapshot is replayed in full when it wins; it needs no pending flags.
        slot.clearChanges()
    }

    /** Current unadjusted position of this publisher. Call within execute/update on main. */
    fun currentPosition(key: Any): Long {
        val slot = slots[key] ?: return -1L
        val state = slot.token?.let { resolvePlaybackState(it) }
        return if (state != null) MediaMetadataHelper.estimatePlaybackPosition(state) else slot.position
    }

    fun remove(key: Any) = execute {
        slots.remove(key)?.let { if (it.token == null) order.remove(key) }
        reconcile()
    }

    /** Only a complete timeline can keep advancing while Lyricon hides this publisher. */
    fun detachStreamingOwner(key: Any) = execute {
        val slot = slots[key] ?: return@execute
        slot.detached = true
        if (slot.token == null || slot.song?.lyrics.isNullOrEmpty()) slot.onStop()
        reconcile()
        slot.clearChanges()
    }

    fun clear() = execute {
        slots.values.forEach { if (it.token == null) order.remove(it.key) }
        slots.clear()
        removedPublishers.clear()
        reconcile()
    }

    fun close() {
        closed = true // Reject already queued Binder callbacks before unregistering on main.
        val cleanup = {
            handler.removeCallbacks(sample)
            sampling = null
            if (screenReceiverRegistered) {
                runCatching { appContext.unregisterReceiver(screenReceiver) }
                screenReceiverRegistered = false
            }
            if (listenerRegistered) runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            sessions.values.forEach { runCatching { it.controller.unregisterCallback(it.callback) } }
            sessions.clear()
            destroyedTokens.clear()
            slots.clear()
            order.clear()
            removedPublishers.clear()
            selected = null
            downstream.onStop()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup() else handler.post { cleanup() }
    }

    private fun syncSessions(incomingControllers: List<MediaController>) {
        if (closed) return
        val controllers = incomingControllers.filter { it.sessionToken !in destroyedTokens }
        val tokens = controllers.map { it.sessionToken }.toSet()
        sessions.keys.filter { it !in tokens }.forEach { token ->
            removeSession(token)
        }
        order.seed(controllers.map { it.sessionToken to LyricPlaybackState.isActive(it.playbackState) })
        controllers.forEach { controller ->
            val token = controller.sessionToken
            if (token !in sessions) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        val session = sessions[token]
                        if (closed || session?.controller !== controller) return
                        session.cachedState = state
                        session.lastSyncUptimeMs = SystemClock.uptimeMillis()
                        order.update(token, LyricPlaybackState.isActive(state))
                        // A playback transition may make a previously ambiguous package bindable.
                        val packageSlots = slots.values.filter { it.packageName == controller.packageName }
                        packageSlots.forEach(::bind)
                        val affected = packageSlots.filter { it.token == token }
                        affected.forEach {
                            it.onPlaybackStateChanged(LyricPlaybackState.isAdvancing(state),
                                state?.playbackSpeed ?: Float.NaN, LyricPlaybackState.isActive(state))
                            it.onPositionChanged(MediaMetadataHelper.estimatePlaybackPosition(state),
                                state?.playbackSpeed ?: Float.NaN)
                        }
                        reconcile()
                        affected.forEach { it.clearChanges() }
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        if (closed || sessions[token]?.controller !== controller) return
                        val affected = slots.values.filter { it.token == token }
                        if (!sourceTracksMediaChanges) affected.forEach { checkTrack(it, metadata) }
                        reconcile()
                        affected.forEach { it.clearChanges() }
                    }
                    override fun onSessionDestroyed() {
                        if (closed || sessions[token]?.controller !== controller) return
                        destroyedTokens += token
                        removeSession(token)
                        reconcile()
                    }
                }
                runCatching {
                    controller.registerCallback(callback, handler)
                    sessions[token] = Session(controller, callback, controller.playbackState, SystemClock.uptimeMillis())
                }.onFailure { HookLogger.w("LyricSessionArbiter", "媒体会话回调注册失败", it) }
            }
        }
        slots.values.forEach(::bind)
        reconcile()
    }

    private fun removeSession(token: MediaSession.Token) {
        sessions.remove(token)?.let { runCatching { it.controller.unregisterCallback(it.callback) } }
        order.remove(token)
        slots.entries.filter { it.value.token == token }.map { it.key }.forEach {
            slots.remove(it)
            if (it is String) removedPublishers += it
            onOwnerRemoved(it)
        }
    }

    private fun bind(slot: Slot) {
        if (slot.token == null) {
            // A package is not a session identity. Do not guess when several sessions exist.
            val matching = sessions.values.filter { it.controller.packageName == slot.packageName }
            val playing = matching.filter { order.isActive(it.controller.sessionToken) }
            val controller = (playing.singleOrNull() ?: matching.singleOrNull())?.controller
            if (controller != null) {
                slot.token = controller.sessionToken
                order.remove(slot.key)
                slot.metadataDirty = true
                checkTrack(slot, controller.metadata)
            }
        }
    }

    private fun checkTrack(slot: Slot, metadata: MediaMetadata?) {
        if (metadata == null) return
        val id = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val previousId = slot.mediaId
        val previousTitle = slot.mediaTitle
        if ((id != null && previousId != null && id != previousId) ||
            (slot.detached && id == null && previousId == null &&
                title != null && previousTitle != null && title != previousTitle)) {
            slot.resetForMediaChange()
        }
        slot.mediaId = id ?: previousId
        slot.mediaTitle = title ?: previousTitle
    }

    private fun currentMediaId(token: MediaSession.Token): String? =
        sessions[token]?.controller?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?.takeIf { it.isNotBlank() }

    private fun isActive(slot: Slot): Boolean {
        val token = slot.token
        if (token != null) return order.isActive(token)
        // No controller yet: use the source signal only while MediaSession identity is unavailable.
        if (sessions.values.any { it.controller.packageName == slot.packageName }) return false
        return slot.active
    }

    private fun isPlaying(slot: Slot): Boolean = slot.token?.let {
        LyricPlaybackState.isAdvancing(resolvePlaybackState(it))
    } ?: slot.playing

    /** Choose whose source snapshot to publish. Renderers decide whether to display it. */
    private fun selectOwner(): Slot? {
        var playingLyrics: Slot? = null
        var playingInfo: Slot? = null
        var latestContent: Slot? = null
        fun newer(candidate: Slot, current: Slot?): Boolean = current == null ||
            order.rank(candidate.token ?: candidate.key) > order.rank(current.token ?: current.key)
        for (slot in slots.values) {
            if (!slot.sourceAvailable) continue
            if (newer(slot, latestContent)) latestContent = slot
            if (!isActive(slot)) continue
            if (newer(slot, playingInfo)) playingInfo = slot
            if (slot.hasLyrics && newer(slot, playingLyrics)) playingLyrics = slot
        }
        (playingLyrics ?: playingInfo)?.let { return it }
        // A different playing session without a source must not display a paused owner's lyrics.
        if (sessions.keys.any(order::isActive)) return null
        return selected?.takeIf { slots[it.key] === it && it.sourceAvailable }
            ?: latestContent
    }

    private fun reconcile() {
        val winner = selectOwner()
        val switched = selected !== winner
        if (switched) {
            downstream.onStop()
            selected = winner
        }
        if (winner != null) {
            downstream.onSourceStateChanged(true, winner.mediaIdentity)
            if (switched || winner.songDirty) downstream.onSongChanged(winner.song)
            if (switched || winner.metadataDirty) downstream.onMetadata(
                winner.metadata?.let {
                    it.copy(sessionToken = it.sessionToken ?: winner.token, mediaId = winner.mediaId ?: it.mediaId)
                }
            )
            val playingNow = isPlaying(winner)
            val activeNow = isActive(winner)
            if (switched || winner.lastPlaying != playingNow || winner.lastActive != activeNow || winner.playbackDirty) {
                downstream.onPlaybackStateChanged(playingNow, winner.speed, activeNow)
                winner.lastPlaying = playingNow
                winner.lastActive = activeNow
            }
            if (switched || winner.contentDirty) {
                winner.line?.let(downstream::onLyricLine)
                if (winner.textSet) downstream.onPlainText(winner.text)
            }
            if (switched || winner.positionDirty || winner.playbackDirty) publishPosition(winner)
            winner.clearChanges()
        }
        updateSampling(winner)
    }

    private fun updateSampling(winner: Slot?) {
        // 息屏时直接停掉 33ms 采样定时器，避免黑屏状态下唤醒 SystemUI 主线程
        val sampleOwner = if (screenInteractive) winner?.takeIf { isPlaying(it) && it.token != null } else null
        if (sampling !== sampleOwner) {
            handler.removeCallbacks(sample)
            sampling = sampleOwner
            if (sampleOwner != null) handler.postDelayed(sample, sampleIntervalMs)
        }
    }

    private fun publishPosition(slot: Slot, preloadedState: PlaybackState? = null) {
        // 息屏时不再把高频进度推给 UI 渲染管线，亮屏瞬间会自动同步最新进度
        if (!screenInteractive) return
        val state = preloadedState ?: slot.token?.let { resolvePlaybackState(it) }
        val position = if (state != null) MediaMetadataHelper.estimatePlaybackPosition(state) else slot.position
        if (position >= 0L) downstream.onPositionChanged(
            (position - slot.delayMs).coerceAtLeast(0L), state?.playbackSpeed ?: slot.speed
        )
    }

    private val sample = object : Runnable {
        override fun run() {
            if (closed || !screenInteractive) {
                sampling = null
                return
            }
            val slot = selected ?: return
            if (slot !== sampling) return
            val state = try {
                slot.token?.let { resolvePlaybackState(it) }
            } catch (e: Exception) {
                HookLogger.w("LyricSessionArbiter", "选中会话状态读取失败", e)
                sampling = null
                return
            }
            try {
                publishPosition(slot, preloadedState = state)
            } catch (e: Exception) {
                HookLogger.w("LyricSessionArbiter", "选中会话位置采样失败", e)
                sampling = null
                return
            }
            val stillPlaying = if (slot.token != null) LyricPlaybackState.isAdvancing(state) else slot.playing
            if (stillPlaying && screenInteractive) handler.postDelayed(this, sampleIntervalMs)
            else sampling = null
        }
    }

    private class Slot(
        val key: Any,
        val packageName: String,
        var token: MediaSession.Token?,
        private val currentMediaId: (MediaSession.Token) -> String?,
        private val onMediaChanged: () -> Unit
    ) : LyricSink {
        var song: Song? = null
        var sourceAvailable = false
            private set
        val mediaIdentity get() = MediaIdentity(
            packageName = packageName, sessionToken = token,
            mediaId = mediaId, songId = metadata?.songId
        )
        private var songHasLyrics = false
        var metadata: LyricMediaMetadata? = null
        var line: IRichLyricLine? = null
        var text: String? = null
        var textSet = false
        var detached = false
        var playing = false
        var active = false
        var speed = Float.NaN
        var position = -1L
        var delayMs = 0
        var lastPlaying: Boolean? = null
        var lastActive: Boolean? = null
        var mediaId: String? = null
        var mediaTitle: String? = null
        var songDirty = false
        var metadataDirty = false
        var playbackDirty = false
        var contentDirty = false
        var positionDirty = false
        val requiresSelection get() = songDirty || metadataDirty || playbackDirty || contentDirty
        fun clearChanges() {
            songDirty = false
            metadataDirty = false
            playbackDirty = false
            contentDirty = false
            positionDirty = false
        }
        val hasLyrics get() = songHasLyrics ||
            !line?.text.isNullOrBlank() || !text.isNullOrBlank()

        override fun onSourceStateChanged(sourceAvailable: Boolean, mediaIdentity: MediaIdentity?) {
            if (!sourceAvailable) {
                onStop()
                return
            }
            // Capture the player's item when the source responds. The metadata callback for
            // this item may still be queued; it must not revoke a response already bound to it.
            val responseMediaId = mediaIdentity?.mediaId
                ?: token?.let(currentMediaId)
            if (responseMediaId != null && mediaId != null && responseMediaId != mediaId) {
                resetForMediaChange()
            }
            if (this.sourceAvailable != sourceAvailable ||
                responseMediaId != null && responseMediaId != mediaId
            ) metadataDirty = true
            this.sourceAvailable = sourceAvailable
            mediaId = responseMediaId ?: mediaId
        }

        fun resetForMediaChange() {
            onStop()
            onMediaChanged()
        }

        override fun onSongChanged(song: Song?) {
            this.song = song
            songHasLyrics = song?.lyrics?.any {
                !it.text.isNullOrBlank() && it.begin >= 0L && it.end > it.begin
            } == true
            line = null
            text = null
            textSet = false
            songDirty = true
            contentDirty = true
            position = -1L
        }
        override fun onMetadata(metadata: LyricMediaMetadata?) {
            this.metadata = metadata
            metadataDirty = true
        }
        override fun onLyricLine(line: IRichLyricLine) {
            this.line = line
            textSet = false
            text = null
            contentDirty = true
        }
        override fun onPlainText(text: String?) {
            if (song != null) {
                song = null
                songHasLyrics = false
                songDirty = true
            }
            this.text = text
            textSet = true
            line = null
            contentDirty = true
        }
        override fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float, isActive: Boolean) {
            playing = isPlaying
            active = isActive
            speed = playbackSpeed
            playbackDirty = true
        }
        override fun onPositionChanged(position: Long, playbackSpeed: Float) {
            this.position = position
            speed = playbackSpeed
            positionDirty = true
        }
        override fun onStop() {
            onSongChanged(null)
            onMetadata(null)
            playing = false
            active = false
            sourceAvailable = false
        }
    }
}
