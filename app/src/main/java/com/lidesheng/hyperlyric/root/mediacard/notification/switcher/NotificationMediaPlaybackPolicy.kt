package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.LinkedHashMap

/**
 * Decides whether native MediaData updates may reorder the visible card list.
 *
 * Audio focus is not a reliable playback signal: some players ignore focus
 * loss and continue playing. MediaData.isPlaying is therefore used to detect
 * concurrent sessions. The system setting only says that concurrent playback
 * is allowed; it does not prove that two sessions are currently playing.
 *
 * A short settle window separates a real concurrent session from the normal
 * audio-focus handoff sequence, where the new session reports PLAYING before
 * the old session reports PAUSED.
 */
internal class NotificationMediaPlaybackPolicy(
    private val accessor: NotificationMediaDataAccessor,
    private val onStableModeChanged: () -> Unit
) {
    private companion object {
        const val TAG = "NotificationMediaPlaybackPolicy"
        const val SETTLE_DELAY_MS = 250L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dataByKey = LinkedHashMap<String, Any>()
    private var settlementRunnable: Runnable? = null
    private var settlementTargetConcurrent: Boolean? = null
    private var settingRead = false
    private var concurrentCandidate = false
    private var concurrentConfirmed = false
    private var latestPlayingKey: String? = null
    private var latestNativeBind: Any? = null

    /** Native sort/bind events must keep the current card order in this mode. */
    val shouldPreserveNativeOrder: Boolean
        get() = concurrentCandidate

    fun initialize() {
        if (settingRead) return
        settingRead = true
        HookLogger.d(TAG, "音频焦点策略初始化: 实际并发状态以 MediaData.isPlaying 为准")
        reevaluate(scheduleSettlement = true)
    }

    fun seed(initialEntries: List<Pair<String, Any>>) {
        dataByKey.clear()
        latestPlayingKey = null
        initialEntries.forEach { (key, data) ->
            if (key.isNotEmpty() && accessor.isActive(data)) {
                dataByKey[key] = data
                if (accessor.isPlaying(data) == true) {
                    latestPlayingKey = key
                }
            }
        }
        // The policy is initialized after the native sort has been read. Do
        // not schedule a callback before the native player View is attached.
        reevaluate(scheduleSettlement = false)
    }

    fun onMediaDataLoaded(key: String, oldKey: String?, data: Any) {
        if (oldKey != null && oldKey != key) {
            dataByKey.remove(oldKey)
            if (latestPlayingKey == oldKey) latestPlayingKey = null
        }
        if (accessor.isActive(data)) {
            val wasPlaying = dataByKey[key]?.let { accessor.isPlaying(it) == true } == true
            dataByKey[key] = data
            if (accessor.isPlaying(data) == true && !wasPlaying) {
                latestPlayingKey = key
            }
        } else {
            dataByKey.remove(key)
            if (latestPlayingKey == key) latestPlayingKey = null
        }
        reevaluate()
    }

    fun onMediaDataRemoved(key: String) {
        dataByKey.remove(key)
        if (latestPlayingKey == key) latestPlayingKey = null
        if ((latestNativeBind?.let(accessor::notificationKey)) == key) {
            latestNativeBind = null
        }
        reevaluate()
    }

    /**
     * A native bind can arrive before or after MediaDataManager's listener
     * callback. Refresh the identity when it is already known, but never use
     * the bind itself to leave the settle window.
     */
    fun onNativeBind(data: Any?, synthetic: Boolean = false) {
        if (data == null) return
        if (!synthetic) latestNativeBind = data

        val key = accessor.notificationKey(data) ?: return
        if (key in dataByKey && accessor.isActive(data)) {
            val wasPlaying = accessor.isPlaying(dataByKey[key]!!) == true
            dataByKey[key] = data
            if (accessor.isPlaying(data) == true && !wasPlaying) {
                latestPlayingKey = key
            }
            reevaluate()
        }
    }

    /**
     * Returns the session that should become native page zero after an audio
     * focus handoff settles. Prefer the most recently started session, then
     * the most recent real native bind, and finally any playing entry.
     */
    fun preferredPlayingData(): Any? {
        latestPlayingKey?.let { key ->
            dataByKey[key]?.takeIf { accessor.isPlaying(it) == true }?.let { return it }
        }
        latestNativeBind?.let { data ->
            val key = accessor.notificationKey(data)
            if (key != null && key in dataByKey && accessor.isPlaying(data) == true) {
                return dataByKey[key]
            }
        }
        return dataByKey.values.lastOrNull { accessor.isPlaying(it) == true }
    }

    fun onDetached() {
        settlementRunnable?.let(mainHandler::removeCallbacks)
        settlementRunnable = null
        settlementTargetConcurrent = null
        dataByKey.clear()
        latestPlayingKey = null
        latestNativeBind = null
        concurrentCandidate = false
        concurrentConfirmed = false
    }

    private fun reevaluate(scheduleSettlement: Boolean = settingRead) {
        val playingCount = dataByKey.values.count { accessor.isPlaying(it) == true }
        if (playingCount >= 2) {
            concurrentCandidate = true
            if (!concurrentConfirmed && scheduleSettlement) {
                scheduleSettlement(targetConcurrent = true)
            }
            return
        }

        if (concurrentCandidate && scheduleSettlement) {
            // Keep preserving order during the exit window. If the old
            // session reports PLAYING again, the pending exit is cancelled.
            scheduleSettlement(targetConcurrent = false)
        }
    }

    private fun scheduleSettlement(targetConcurrent: Boolean) {
        if (settlementTargetConcurrent == targetConcurrent && settlementRunnable != null) {
            return
        }
        settlementRunnable?.let(mainHandler::removeCallbacks)
        settlementTargetConcurrent = targetConcurrent
        val runnable = Runnable {
            settlementRunnable = null
            settlementTargetConcurrent = null
            val playingCount = dataByKey.values.count { accessor.isPlaying(it) == true }
            if (targetConcurrent && playingCount >= 2) {
                concurrentCandidate = true
                if (!concurrentConfirmed) {
                    concurrentConfirmed = true
                    HookLogger.d(
                        TAG,
                        "确认并行播放，保持卡片顺序: playing=$playingCount"
                    )
                }
            } else if (!targetConcurrent && playingCount < 2) {
                val wasConcurrent = concurrentCandidate
                concurrentCandidate = false
                concurrentConfirmed = false
                if (wasConcurrent) {
                    HookLogger.d(
                        TAG,
                        "音频焦点切换完成，恢复单播放排序: playing=$playingCount"
                    )
                    onStableModeChanged()
                }
            } else {
                reevaluate(scheduleSettlement = true)
            }
        }
        settlementRunnable = runnable
        mainHandler.postDelayed(runnable, SETTLE_DELAY_MS)
    }
}
