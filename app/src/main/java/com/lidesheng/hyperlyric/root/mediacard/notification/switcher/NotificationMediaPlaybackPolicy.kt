package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/**
 * Owns the stable activity order used by the notification-media switcher.
 *
 * MediaSortUtils sorts by notification update time. That is useful as a
 * fallback, but it is not the same thing as the session that most recently
 * became active. The old implementation tried to avoid churn by freezing the
 * native order while two sessions were playing; that made the visible order
 * depend on whichever metadata callback happened to arrive last. This class
 * instead records activity transitions and exposes one deterministic order to
 * the selection coordinator.
 */
internal class NotificationMediaPlaybackPolicy(
    private val accessor: NotificationMediaDataAccessor
) {
    private companion object {
        const val TAG = "NotificationMediaPlaybackPolicy"
    }

    private val dataByKey = LinkedHashMap<String, Any>()
    private val activitySequenceByKey = HashMap<String, Long>()
    private val insertionSequenceByKey = HashMap<String, Long>()
    private val playbackSignalByKey = HashMap<String, Boolean>()
    private val directPlaybackKeys = HashSet<String>()
    private var nextActivitySequence = 0L
    private var nextInsertionSequence = 0L
    private var lastNativeBindKey: String? = null

    fun initialize() {
        HookLogger.d(TAG, "媒体会话活跃度排序初始化")
    }

    fun seed(initialEntries: List<Pair<String, Any>>) {
        dataByKey.clear()
        activitySequenceByKey.clear()
        insertionSequenceByKey.clear()
        playbackSignalByKey.clear()
        directPlaybackKeys.clear()
        nextActivitySequence = 0L
        nextInsertionSequence = 0L
        lastNativeBindKey = initialEntries.firstOrNull()?.first

        // Before the first callback, native order is the only available
        // chronology. Give the first native item the highest initial rank;
        // later real playback transitions always supersede this baseline.
        initialEntries.forEachIndexed { index, (key, data) ->
            if (key.isNotEmpty() && accessor.isActive(data)) {
                dataByKey[key] = data
                insertionSequenceByKey[key] = nextInsertionSequence++
                activitySequenceByKey[key] =
                    (initialEntries.size - index).toLong()
                accessor.isPlaying(data)?.let { playbackSignalByKey[key] = it }
            }
        }
        nextActivitySequence = initialEntries.size.toLong()
    }

    fun onMediaDataLoaded(key: String, oldKey: String?, data: Any) {
        if (oldKey != null && oldKey != key) {
            remove(oldKey)
        }

        val previous = dataByKey[key]
        val previousToken = previous?.let(accessor::sessionToken)
        val nextToken = accessor.sessionToken(data)
        val tokenChanged = previous != null &&
            previousToken != null &&
            nextToken != null &&
            previousToken != nextToken
        val wasPlaying = previous?.let { accessor.isPlaying(it) == true } == true

        if (!accessor.isActive(data)) {
            remove(key)
            return
        }

        dataByKey[key] = data
        if (key !in insertionSequenceByKey) {
            insertionSequenceByKey[key] = nextInsertionSequence++
        }
        if (tokenChanged) {
            playbackSignalByKey.remove(key)
            directPlaybackKeys.remove(key)
        }
        if (accessor.isPlaying(data) == true && (!wasPlaying || tokenChanged)) {
            markActive(key)
        }
        if (key !in directPlaybackKeys) {
            accessor.isPlaying(data)?.let { playbackSignalByKey[key] = it }
        }
    }

    fun onMediaDataRemoved(key: String) {
        remove(key)
    }

    /**
     * A direct MediaController callback is stronger than a delayed MediaData
     * copy. It lets a session move to the front as soon as it actually starts
     * playing, while the MediaData listener still remains the source of the
     * session payload.
     */
    fun onPlaybackSignal(key: String, playing: Boolean) {
        if (key !in dataByKey) return
        // The first direct callback is itself an observation boundary. Do not
        // suppress it because the MediaData copy still carries the previous
        // state; that copy is exactly the stale signal this callback repairs.
        val previous = playbackSignalByKey[key]
        playbackSignalByKey[key] = playing
        directPlaybackKeys += key
        if (playing && previous != true) markActive(key)
    }

    /**
     * Native binding is an ordering signal when the bound top session changes.
     * Repeated binds for the same key are content refreshes and must not make
     * metadata updates continually reshuffle the carousel.
     */
    fun onNativeBind(data: Any?, synthetic: Boolean = false) {
        if (data == null) return

        val key = accessor.notificationKey(data) ?: return
        if (!synthetic) {
            val previousKey = lastNativeBindKey
            lastNativeBindKey = key
            if (key != previousKey && key in dataByKey && isPlaying(data)) {
                markActive(key)
            }
        }

        if (key in dataByKey && accessor.isActive(data)) {
            val previous = dataByKey[key]
            val wasPlaying = previous?.let { accessor.isPlaying(it) == true } == true
            dataByKey[key] = data
            if (isPlaying(data) && !wasPlaying) markActive(key)
            if (key !in directPlaybackKeys) {
                accessor.isPlaying(data)?.let { playbackSignalByKey[key] = it }
            }
        }
    }

    /**
     * Returns the canonical page order. Currently playing sessions are always
     * ahead of paused active sessions; among sessions in the same state, the
     * most recent activity transition wins, with native order as a stable
     * fallback. The returned list only contains keys known to this policy.
     */
    fun orderedKeys(nativeKeys: List<String>): List<String> {
        val nativePositions = nativeKeys.withIndex().associate { it.value to it.index }
        val candidates = LinkedHashSet<String>().apply {
            addAll(dataByKey.keys)
            addAll(nativeKeys)
        }
        return candidates
            .filter { it in dataByKey }
            .sortedWith(
                compareByDescending<String> { isPlaying(it) }
                    .thenByDescending { activitySequenceByKey[it] ?: Long.MIN_VALUE }
                    .thenBy { nativePositions[it] ?: Int.MAX_VALUE }
                    .thenBy { insertionSequenceByKey[it] ?: Long.MAX_VALUE }
            )
    }

    fun onDetached() {
        dataByKey.clear()
        activitySequenceByKey.clear()
        insertionSequenceByKey.clear()
        playbackSignalByKey.clear()
        directPlaybackKeys.clear()
        lastNativeBindKey = null
    }

    private fun isPlaying(key: String): Boolean {
        return playbackSignalByKey[key]
            ?: dataByKey[key]?.let { accessor.isPlaying(it) == true }
            ?: false
    }

    private fun isPlaying(data: Any): Boolean {
        return accessor.isPlaying(data) == true
    }

    private fun markActive(key: String) {
        if (key !in dataByKey) return
        activitySequenceByKey[key] = ++nextActivitySequence
    }

    private fun remove(key: String) {
        dataByKey.remove(key)
        activitySequenceByKey.remove(key)
        insertionSequenceByKey.remove(key)
        playbackSignalByKey.remove(key)
        directPlaybackKeys.remove(key)
        if (lastNativeBindKey == key) lastNativeBindKey = null
    }
}
