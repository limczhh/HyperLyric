package com.lidesheng.hyperlyric.root.island

import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.WeakHashMap

internal object IslandProgressGlowController {
    private const val TAG = "IslandProgressGlowController"
    private const val BACKGROUND_VIEW_NAME = "DynamicIslandBackgroundView"
    private const val DEFAULT_PROGRESS_COLOR = 0xFF5B8CFF.toInt()

    private val backgroundViewsByRoot = WeakHashMap<ViewGroup, View>()
    private val diagnosticStageByRoot = WeakHashMap<ViewGroup, DiagnosticStage>()

    fun update(rootView: ViewGroup, packageName: String, prefs: SharedPreferences) {
        runCatching {
            updateInternal(rootView, packageName, prefs)
        }.onFailure { e ->
            clear(rootView)
            HookLogger.e(TAG, "Failed to update island background progress", e)
        }
    }

    private fun updateInternal(
        rootView: ViewGroup,
        packageName: String,
        prefs: SharedPreferences
    ) {
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GLOW
            )
        ) {
            logStage(rootView, DiagnosticStage.DISABLED, "Progress glow is disabled")
            clear(rootView)
            return
        }

        val playbackProgress = MediaMetadataHelper.getPlaybackProgress(rootView.context, packageName)
        if (playbackProgress.fraction < 0f) {
            logStage(
                rootView,
                DiagnosticStage.INVALID_MEDIA_PROGRESS,
                "Media progress unavailable: package=$packageName position=${playbackProgress.position} " +
                    "duration=${playbackProgress.duration} playing=${playbackProgress.isPlaying}"
            )
            clear(rootView)
            return
        }

        val backgroundView = findBackgroundView(rootView) ?: run {
            logStage(
                rootView,
                DiagnosticStage.BACKGROUND_VIEW_NOT_FOUND,
                "DynamicIslandBackgroundView not found from ${rootView.javaClass.name}"
            )
            clear(rootView)
            return
        }
        replaceBackgroundView(rootView, backgroundView)

        val colors = resolveProgressColors(prefs)
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
        logStage(
            rootView,
            DiagnosticStage.BACKGROUND_REGISTERED,
            "Native background registered: package=$packageName " +
                "progress=${playbackProgress.fraction} " +
                "progressColor=${colors.progress.toUInt().toString(16)} " +
                "trackColor=${colors.track.toUInt().toString(16)} " +
                "style=$progressStyle"
        )
    }

    fun clear(rootView: ViewGroup) {
        val backgroundView = synchronized(backgroundViewsByRoot) {
            backgroundViewsByRoot.remove(rootView)
        } ?: return
        IslandProgressGlowHooker.clearMediaProgress(backgroundView)
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
            backgroundViewsByRoot.put(rootView, backgroundView)
        }
        if (previous != null && previous !== backgroundView) {
            IslandProgressGlowHooker.clearMediaProgress(previous)
        }
    }

    private fun resolveProgressColors(prefs: SharedPreferences): ProgressColors {
        if (!prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR,
                RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR
            )
        ) {
            return ProgressColors(DEFAULT_PROGRESS_COLOR, DEFAULT_TRACK_COLOR)
        }
        val palette = CoverColorHelper.getCachedColors()
            ?: return ProgressColors(DEFAULT_PROGRESS_COLOR, DEFAULT_TRACK_COLOR)
        val highlight = palette.second.firstOrNull()
            ?: return ProgressColors(DEFAULT_PROGRESS_COLOR, DEFAULT_TRACK_COLOR)
        val highlightBackground = palette.first.firstOrNull() ?: highlight
        return ProgressColors(
            progress = highlight,
            track = withAlpha(highlightBackground, COVER_TRACK_ALPHA)
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun logStage(rootView: ViewGroup, stage: DiagnosticStage, message: String) {
        val changed = synchronized(diagnosticStageByRoot) {
            if (diagnosticStageByRoot[rootView] == stage) {
                false
            } else {
                diagnosticStageByRoot[rootView] = stage
                true
            }
        }
        if (changed) HookLogger.i(TAG, message)
    }

    private enum class DiagnosticStage {
        DISABLED,
        INVALID_MEDIA_PROGRESS,
        BACKGROUND_VIEW_NOT_FOUND,
        BACKGROUND_REGISTERED
    }

    private data class ProgressColors(
        val progress: Int,
        val track: Int
    )

    private const val DEFAULT_TRACK_COLOR = 0x66757575
    private const val COVER_TRACK_ALPHA = 112
}
