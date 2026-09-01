package com.lidesheng.hyperlyric.root.island.effects.glow

import android.content.SharedPreferences
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.SystemUiEnhancementGate
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object IslandProgressGlowController {
    private const val TAG = "IslandProgressGlowController"
    private const val BACKGROUND_VIEW_NAME = "DynamicIslandBackgroundView"
    private const val DEFAULT_PROGRESS_COLOR = 0xFF5B8CFF.toInt()
    private const val MIN_PROGRESS_UPDATE_INTERVAL_MS = 100L
    private const val PLAYBACK_SAMPLE_INTERVAL_MS = 500L
    private const val MAX_CACHED_PACKAGES = 8

    private val backgroundViewsByRoot = WeakHashMap<ViewGroup, WeakReference<View>>()
    private val lastUpdateByRoot = WeakHashMap<ViewGroup, Long>()
    private val playbackProgressByPackage = HashMap<String, TimedPlaybackProgress>()

    fun update(
        rootView: ViewGroup,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?,
        prefs: SharedPreferences
    ) {
        runCatching {
            updateInternal(rootView, packageName, mediaInfo, prefs)
        }.onFailure { e ->
            clear(rootView)
            HookLogger.e(TAG, "更新边缘光效进度条失败", e)
        }
    }

    private fun updateInternal(
        rootView: ViewGroup,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?,
        prefs: SharedPreferences
    ) {
        if (!SystemUiEnhancementGate.isEnabled()) {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.gate",
                tag = TAG,
                state = "system_ui_disabled"
            ) {
                "进度光效未生效: reason=system_ui_enhancement_disabled"
            }
            clear(rootView)
            return
        }
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GLOW
            )
        ) {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.gate",
                tag = TAG,
                state = "preference_disabled"
            ) {
                "进度光效未生效: reason=preference_disabled"
            }
            clear(rootView)
            return
        }

        if (mediaInfo == null) {
            val now = SystemClock.uptimeMillis()
            val previous = synchronized(lastUpdateByRoot) { lastUpdateByRoot[rootView] }
            if (previous != null && now - previous < MIN_PROGRESS_UPDATE_INTERVAL_MS) return
            synchronized(lastUpdateByRoot) { lastUpdateByRoot[rootView] = now }
        } else {
            synchronized(lastUpdateByRoot) {
                lastUpdateByRoot[rootView] = SystemClock.uptimeMillis()
            }
        }

        val playbackProgress = resolvePlaybackProgress(
            rootView = rootView,
            packageName = packageName,
            forceRefresh = mediaInfo != null
        )
        if (playbackProgress.fraction < 0f) {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.input",
                tag = TAG,
                state = "invalid_progress"
            ) {
                "进度光效未生效: reason=invalid_playback_progress"
            }
            clear(rootView)
            return
        }

        val backgroundView = cachedBackgroundView(rootView) ?: findBackgroundView(rootView)?.also {
            replaceBackgroundView(rootView, it)
        } ?: run {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.input",
                tag = TAG,
                state = "background_missing"
            ) {
                "进度光效未生效: reason=background_view_missing"
            }
            clear(rootView)
            return
        }
        val colors = resolveProgressColors(prefs, packageName, mediaInfo)
        val progressStyle = prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_STYLE
        )
        IslandProgressGlowHooker.setMediaProgress(
            backgroundView,
            playbackProgress.fraction,
            colors.progress,
            colors.track,
            progressStyle
        )
        HookLogger.dState(
            stateId = "IslandProgressGlowController.applied",
            tag = TAG,
            state = "applied|$progressStyle|${colors.progress.size}|${colors.track}"
        ) {
            "进度光效已应用: style=$progressStyle, progressColors=${colors.progress.size}, " +
                    "track=#${Integer.toHexString(colors.track)}"
        }
    }

    fun clear(rootView: ViewGroup) {
        val backgroundView = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot.remove(rootView)
        }?.get() ?: return
        IslandProgressGlowHooker.clearMediaProgress(backgroundView)
    }

    fun clearAll() {
        synchronized(backgroundViewsByRoot) { backgroundViewsByRoot.clear() }
        synchronized(lastUpdateByRoot) { lastUpdateByRoot.clear() }
        synchronized(playbackProgressByPackage) { playbackProgressByPackage.clear() }
        IslandProgressGlowHooker.clearAllMediaProgress()
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        val now = SystemClock.elapsedRealtime()
        synchronized(playbackProgressByPackage) {
            if (isPlaying) {
                playbackProgressByPackage.clear()
                return
            }
            val frozen = playbackProgressByPackage.mapValues { (_, sample) ->
                TimedPlaybackProgress(
                    progress = sample.estimate(now).copy(
                        isPlaying = false,
                        playbackSpeed = 0f
                    ),
                    sampledAt = now
                )
            }
            playbackProgressByPackage.clear()
            playbackProgressByPackage.putAll(frozen)
        }
    }

    private fun resolvePlaybackProgress(
        rootView: ViewGroup,
        packageName: String,
        forceRefresh: Boolean
    ): MediaMetadataHelper.PlaybackProgress {
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh) {
            val cached = synchronized(playbackProgressByPackage) {
                playbackProgressByPackage[packageName]
            }
            if (cached != null && now - cached.sampledAt < PLAYBACK_SAMPLE_INTERVAL_MS) {
                return cached.estimate(now)
            }
        }

        val progress = MediaMetadataHelper.getPlaybackProgress(rootView.context, packageName)
        synchronized(playbackProgressByPackage) {
            if (
                playbackProgressByPackage.size >= MAX_CACHED_PACKAGES &&
                packageName !in playbackProgressByPackage
            ) {
                playbackProgressByPackage.clear()
            }
            playbackProgressByPackage[packageName] = TimedPlaybackProgress(progress, now)
        }
        return progress
    }

    private fun cachedBackgroundView(rootView: ViewGroup): View? {
        val cached = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot[rootView]
        }?.get() ?: return null
        var current: View? = rootView
        while (current != null) {
            if (current === cached) return cached
            current = current.parent as? View
        }
        return null
    }

    private fun findBackgroundView(rootView: ViewGroup): View? {
        (invokeNoArg(rootView, "getBackgroundView") as? View)?.let { return it }
        var current: View? = rootView
        while (current != null) {
            if (current.javaClass.simpleName == BACKGROUND_VIEW_NAME) return current
            current = current.parent as? View
        }
        return null
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return runCatching {
            target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty()
            }?.invoke(target)
        }.getOrNull()
    }

    private fun replaceBackgroundView(rootView: ViewGroup, backgroundView: View) {
        val previous = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot.put(rootView, WeakReference(backgroundView))
        }?.get()
        if (previous != null && previous !== backgroundView) {
            IslandProgressGlowHooker.clearMediaProgress(previous)
        }
    }

    private fun resolveProgressColors(
        prefs: SharedPreferences,
        packageName: String,
        mediaInfo: MediaMetadataHelper.MediaInfo?
    ): ProgressColors {
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
            )
        ) {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.colors",
                tag = TAG,
                state = "fallback|extract_disabled"
            ) {
                "进度光效颜色回退: reason=cover_color_extraction_disabled"
            }
            return ProgressColors(
                intArrayOf(DEFAULT_PROGRESS_COLOR),
                DEFAULT_TRACK_COLOR
            )
        }

        val useGradient = prefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_PROGRESS_GRADIENT,
            RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GRADIENT
        )
        val colorSession = mediaInfo
            ?.let { CoverColorHelper.currentSession(it) }
            ?: CoverColorHelper.currentSession(packageName).takeIf { mediaInfo == null }
            ?: return ProgressColors(
                intArrayOf(DEFAULT_PROGRESS_COLOR),
                DEFAULT_TRACK_COLOR
            ).also {
                HookLogger.dState(
                    stateId = "IslandProgressGlowController.colors",
                    tag = TAG,
                    state = "fallback|no_session"
                ) {
                    "进度光效颜色回退: reason=no_matching_color_session, package=$packageName"
                }
            }
        val artworkRequest = mediaInfo?.let(CoverColorHelper::ensureArtworkColors)
        val matchingArtworkRequest = artworkRequest?.takeIf {
            it.colorSession.revision == colorSession.revision
        }
        val palette = if (matchingArtworkRequest != null) {
            CoverColorHelper.getCachedColors(useGradient, matchingArtworkRequest)
        } else {
            CoverColorHelper.getCachedColors(useGradient, colorSession)
        }
        ?: return ProgressColors(
            intArrayOf(DEFAULT_PROGRESS_COLOR),
            DEFAULT_TRACK_COLOR
        ).also {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.colors",
                tag = TAG,
                state = "fallback|no_palette"
            ) {
                "进度光效颜色回退: reason=no_cached_cover_palette, package=$packageName"
            }
        }
        val highlights = palette.second.takeIf { it.isNotEmpty() }
            ?: return ProgressColors(
                intArrayOf(DEFAULT_PROGRESS_COLOR),
                DEFAULT_TRACK_COLOR
            ).also {
                HookLogger.dState(
                    stateId = "IslandProgressGlowController.colors",
                    tag = TAG,
                    state = "fallback|empty_highlight"
                ) {
                    "进度光效颜色回退: reason=empty_cover_highlight_palette, package=$packageName"
                }
            }
        val highlight = highlights.first()
        val highlightBackground = palette.first.firstOrNull() ?: highlight
        return ProgressColors(
            progress = highlights.copyOf(),
            track = withAlpha(highlightBackground, COVER_TRACK_ALPHA)
        ).also {
            HookLogger.dState(
                stateId = "IslandProgressGlowController.colors",
                tag = TAG,
                state = "cover|$useGradient|${it.progress.size}|${it.track}"
            ) {
                "进度光效颜色已解析: source=cover, gradient=$useGradient, " +
                        "progressColors=${it.progress.size}"
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private data class ProgressColors(
        val progress: IntArray,
        val track: Int
    )

    private data class TimedPlaybackProgress(
        val progress: MediaMetadataHelper.PlaybackProgress,
        val sampledAt: Long
    ) {
        fun estimate(now: Long): MediaMetadataHelper.PlaybackProgress {
            if (
                !progress.isPlaying ||
                progress.position < 0L ||
                progress.playbackSpeed <= 0f
            ) {
                return progress
            }
            val elapsed = (now - sampledAt).coerceAtLeast(0L)
            val estimated = (
                    progress.position + elapsed * progress.playbackSpeed
                    ).toLong().coerceAtLeast(0L)
            val bounded = if (progress.duration > 0L) {
                estimated.coerceAtMost(progress.duration)
            } else {
                estimated
            }
            return progress.copy(position = bounded)
        }
    }

    private const val DEFAULT_TRACK_COLOR = 0x66757575
    private const val COVER_TRACK_ALPHA = 112
}
