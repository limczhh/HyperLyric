/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.media.session.PlaybackState
import android.os.SharedMemory
import android.os.SystemClock
import android.system.OsConstants
import android.util.Log
import io.github.proify.lyricon.central.inflate
import io.github.proify.lyricon.central.json
import io.github.proify.lyricon.central.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.IRemotePlayer
import io.github.proify.lyricon.provider.ProviderConstants
import io.github.proify.lyricon.provider.ProviderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

internal class RemotePlayer(
    private val info: ProviderInfo,
    private val playerListener: PlayerListener = ActivePlayerDispatcher
) : IRemotePlayer.Stub(), ScreenStateMonitor.ScreenStateListener {

    companion object {
        private const val TAG = "RemotePlayer"
        private const val MIN_INTERVAL_MS = 16L
        private const val POSITION_OFFSET = 0
    }

    private val recorder = PlayerRecorder(info)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val released = AtomicBoolean(false)
    private val isState2Enabled = AtomicBoolean(false)
    private val taskMutex = Mutex()

    private var positionSharedMemory: SharedMemory? = null

    @Volatile
    private var positionReadBuffer: ByteBuffer? = null

    @Volatile
    private var positionProducerJob: Job? = null

    @Volatile
    private var positionUpdateInterval: Long = ProviderConstants.DEFAULT_POSITION_UPDATE_INTERVAL

    @Volatile
    private var lastPlaybackState: PlaybackState? = null

    init {
        initSharedMemory()
        ScreenStateMonitor.addListener(this)
    }

    fun destroy() {
        if (!released.compareAndSet(false, true)) return
        ScreenStateMonitor.removeListener(this)
        stopPositionUpdate()

        scope.launch {
            taskMutex.withLock {
                positionReadBuffer?.let { runCatching { SharedMemory.unmap(it) } }
                positionSharedMemory?.close()
                positionReadBuffer = null
                positionSharedMemory = null
                scope.cancel()
            }
        }
    }

    private fun initSharedMemory() {
        try {
            val hashHex = Integer.toHexString(
                "${info.providerPackageName}/${info.playerPackageName}/${info.processName}".hashCode()
            )
            positionSharedMemory =
                SharedMemory.create("lyricon_pos_$hashHex", Long.SIZE_BYTES).apply {
                    setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
                    positionReadBuffer = mapReadOnly()
                }
        } catch (t: Throwable) {
            Log.e(TAG, "SharedMemory init failed", t)
        }
    }

    private fun computeCurrentPosition(): Long {
        if (!isState2Enabled.get()) {
            return readSharedMemoryPosition()
        }

        val state = lastPlaybackState ?: return 0L
        val basePosition = state.position.coerceAtLeast(0L)

        if (state.state != PlaybackState.STATE_PLAYING) {
            return basePosition
        }

        val lastUpdate = state.lastPositionUpdateTime
        if (lastUpdate <= 0L) {
            return basePosition
        }

        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastUpdate).coerceAtLeast(0L)

        val speed = state.playbackSpeed
        val advanced = if (speed == 1.0f) {
            basePosition + delta
        } else {
            basePosition + (delta * speed).toLong()
        }

        return advanced.coerceAtLeast(0L)
    }

    private fun readSharedMemoryPosition(): Long {
        return try {
            positionReadBuffer?.getLong(POSITION_OFFSET)?.coerceAtLeast(0L) ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun startPositionUpdate() {
        if (released.get()) return
        if (positionProducerJob?.isActive == true) return
        if (ScreenStateMonitor.state == ScreenStateMonitor.ScreenState.OFF) return

        scope.launch {
            taskMutex.withLock {
                if (positionProducerJob?.isActive == true) return@withLock

                positionProducerJob = scope.launch {
                    val interval = positionUpdateInterval.coerceAtLeast(MIN_INTERVAL_MS)
                    var nextTick = SystemClock.elapsedRealtime()

                    while (isActive) {
                        val pos = computeCurrentPosition()
                        recorder.lastPosition = pos
                        playerListener.safeNotify {
                            onPositionChanged(recorder, pos)
                        }

                        nextTick += interval
                        val remaining =
                            nextTick - SystemClock.elapsedRealtime()

                        if (remaining > 0) {
                            delay(remaining)
                        } else {
                            nextTick = SystemClock.elapsedRealtime()
                            yield()
                        }
                    }
                }
            }
        }
    }

    private fun stopPositionUpdate() {
        val job = positionProducerJob
        job?.cancel()

        if (job != null) {
            scope.launch {
                taskMutex.withLock {
                    if (positionProducerJob === job) {
                        positionProducerJob = null
                    }
                }
            }
        }
    }

    override fun onScreenOn() {
        if (recorder.lastIsPlaying) {
            startPositionUpdate()
        }
    }

    override fun onScreenOff() {
        stopPositionUpdate()
    }

    override fun onScreenUnlocked() = Unit

    override fun setPositionUpdateInterval(interval: Int) {
        if (released.get()) return

        val newInterval = interval.toLong()
            .coerceAtLeast(MIN_INTERVAL_MS)

        if (positionUpdateInterval != newInterval) {
            positionUpdateInterval = newInterval
            if (recorder.lastIsPlaying) {
                stopPositionUpdate()
                startPositionUpdate()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun setSong(bytes: ByteArray?) {
        if (released.get()) return

        scope.launch {
            val song = bytes?.let {
                runCatching {
                    json.decodeFromStream(
                        Song.serializer(),
                        it.inflate().inputStream()
                    )
                }.getOrNull()
            }

            val normalized = song?.normalize()
            recorder.lastSong = normalized
            playerListener.safeNotify {
                onSongChanged(recorder, normalized)
            }
        }
    }

    override fun setPlaybackState(isPlaying: Boolean) {
        if (released.get()) return

        isState2Enabled.set(false)
        lastPlaybackState = null

        if (recorder.lastIsPlaying != isPlaying) {
            recorder.lastIsPlaying = isPlaying
            playerListener.safeNotify {
                onPlaybackStateChanged(recorder, isPlaying)
            }
        }

        if (isPlaying) startPositionUpdate() else stopPositionUpdate()
    }

    override fun setPlaybackState2(state: PlaybackState?) {
        if (released.get()) return

        if (state == null) {
            if (isState2Enabled.compareAndSet(true, false)) {
                lastPlaybackState = null
                stopPositionUpdate()
            }
            return
        }

        Log.d(TAG, "setPlaybackState: $state")

        if (state.state == PlaybackState.STATE_BUFFERING) {
            return
        }

        val isPlaying =
            state.state == PlaybackState.STATE_PLAYING

        isState2Enabled.set(true)
        lastPlaybackState = state

        if (recorder.lastIsPlaying != isPlaying) {
            recorder.lastIsPlaying = isPlaying
            playerListener.safeNotify {
                onPlaybackStateChanged(recorder, isPlaying)
            }
        }

        if (isPlaying) {
            startPositionUpdate()
        } else {
            stopPositionUpdate()
        }
    }

    override fun seekTo(position: Long) {
        if (released.get()) return

        val safe = position.coerceAtLeast(0L)
        recorder.lastPosition = safe
        playerListener.safeNotify {
            onSeekTo(recorder, safe)
        }
    }

    override fun sendText(text: String?) {
        if (released.get()) return

        recorder.lastText = text
        playerListener.safeNotify {
            onSendText(recorder, text)
        }
    }

    override fun setDisplayTranslation(isDisplayTranslation: Boolean) {
        if (released.get()) return

        recorder.lastIsDisplayTranslation = isDisplayTranslation
        playerListener.safeNotify {
            onDisplayTranslationChanged(recorder, isDisplayTranslation)
        }
    }

    override fun setDisplayRoma(isDisplayRoma: Boolean) {
        if (released.get()) return

        recorder.lastDisplayRoma = isDisplayRoma
        playerListener.safeNotify {
            onDisplayRomaChanged(recorder, isDisplayRoma)
        }
    }

    override fun getPositionMemory(): SharedMemory? =
        positionSharedMemory

    private inline fun PlayerListener.safeNotify(
        crossinline block: PlayerListener.() -> Unit
    ) {
        runCatching { block() }
            .onFailure { Log.e(TAG, "Listener notify failed", it) }
    }
}