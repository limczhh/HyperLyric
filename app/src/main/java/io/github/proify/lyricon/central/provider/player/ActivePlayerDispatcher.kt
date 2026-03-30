/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import android.util.Log
import io.github.proify.lyricon.central.Constants
import io.github.proify.lyricon.central.provider.player.PlayerRecorder.LyricType.NONE
import io.github.proify.lyricon.central.provider.player.PlayerRecorder.LyricType.SONG
import io.github.proify.lyricon.central.provider.player.PlayerRecorder.LyricType.TEXT
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

object ActivePlayerDispatcher : PlayerListener {

    private const val TAG = "ActivePlayerDispatcher"
    private val DEBUG = Constants.isDebug()

    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var activeInfo: ProviderInfo? = null

    @Volatile
    private var activeIsPlaying: Boolean = false

    private val listeners = CopyOnWriteArraySet<ActivePlayerListener>()

    fun addActivePlayerListener(listener: ActivePlayerListener) {
        listeners += listener
    }

    fun removeActivePlayerListener(listener: ActivePlayerListener) {
        listeners -= listener
    }

    fun notifyProviderInvalid(provider: ProviderInfo) {
        val shouldNotify = lock.write {
            if (activeInfo == provider) {
                activeInfo = null
                activeIsPlaying = false
                true
            } else {
                false
            }
        }

        if (shouldNotify) {
            broadcast { it.onActiveProviderChanged(null) }
        }
    }

    // ---------------- PlayerListener ----------------

    override fun onSongChanged(recorder: PlayerRecorder, song: Song?) {
        if (DEBUG) Log.d(TAG, "onSongChanged: $song")
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onSongChanged(song)
        }
    }

    override fun onPlaybackStateChanged(recorder: PlayerRecorder, isPlaying: Boolean) {
        if (DEBUG) Log.d(TAG, "onPlaybackStateChanged: $isPlaying")
        dispatchIfActive(recorder) {
            it.onPlaybackStateChanged(isPlaying)
        }
    }

    override fun onPositionChanged(recorder: PlayerRecorder, position: Long) {
        dispatchIfActive(recorder) {
            it.onPositionChanged(position)
        }
    }

    override fun onSeekTo(recorder: PlayerRecorder, position: Long) {
        dispatchIfActive(recorder) {
            it.onSeekTo(position)
        }
    }

    override fun onSendText(recorder: PlayerRecorder, text: String?) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onSendText(text)
        }
    }

    override fun onDisplayTranslationChanged(
        recorder: PlayerRecorder,
        isDisplayTranslation: Boolean
    ) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onDisplayTranslationChanged(isDisplayTranslation)
        }
    }

    override fun onDisplayRomaChanged(
        recorder: PlayerRecorder,
        displayRoma: Boolean
    ) {
        dispatchIfActive(recorder, allowDuplicateIfSwitching = false) {
            it.onDisplayRomaChanged(displayRoma)
        }
    }

    private inline fun dispatchIfActive(
        recorder: PlayerRecorder,
        allowDuplicateIfSwitching: Boolean = true,
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        val recorderInfo = recorder.info
        val recorderPlaying = recorder.lastIsPlaying

        var isSwitched = false
        var shouldBroadcastOriginal = false

        lock.write {
            val currentInfo = activeInfo
            if (currentInfo === recorderInfo) {
                activeIsPlaying = recorderPlaying
                shouldBroadcastOriginal = true
            } else {
                val canSwitch =
                    currentInfo == null || (!activeIsPlaying && recorderPlaying)

                if (canSwitch) {
                    activeInfo = recorderInfo
                    activeIsPlaying = recorderPlaying
                    isSwitched = true
                    shouldBroadcastOriginal = allowDuplicateIfSwitching
                }
            }
        }

        if (isSwitched) {
            broadcast { it.onActiveProviderChanged(recorderInfo) }
            syncNewProviderState(recorder)
        }

        if (shouldBroadcastOriginal) {
            broadcast(notifier)
        }
    }

    private fun syncNewProviderState(recorder: PlayerRecorder) {
        val playing = activeIsPlaying
        val lyricType = recorder.lastLyricType

        broadcast { listener ->
            listener.onPlaybackStateChanged(playing)

            when (lyricType) {
                SONG -> listener.onSongChanged(recorder.lastSong)
                TEXT -> listener.onSendText(recorder.lastText)
                NONE -> Unit
            }

            listener.onDisplayTranslationChanged(recorder.lastIsDisplayTranslation)
            listener.onDisplayRomaChanged(recorder.lastDisplayRoma)
            listener.onPositionChanged(recorder.lastPosition)
        }
    }

    private inline fun broadcast(
        crossinline notifier: (ActivePlayerListener) -> Unit
    ) {
        for (listener in listeners) {
            try {
                notifier(listener)
            } catch (e: Exception) {
                if (DEBUG) Log.e(
                    TAG,
                    "Dispatch failed for listener: ${listener.javaClass.name}",
                    e
                )
            }
        }
    }
}