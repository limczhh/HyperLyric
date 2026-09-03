package com.lidesheng.hyperlyric.root.island.content

import android.content.SharedPreferences
import android.text.TextPaint
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.RichLyricLineSplitter
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf
import com.lidesheng.hyperlyric.lyric.view.METADATA_NEXT_LINE_PREVIEW
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.yoyo.YoYoPresets
import com.lidesheng.hyperlyric.lyric.view.yoyo.animateUpdate
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.config.IslandSlotRuntimeConfig
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.utils.TranslationHelper

internal object IslandLyricContentAssembler {

    fun apply(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        force: Boolean,
        playbackActive: Boolean,
        playbackClock: LyriconDataBridge.PlaybackClockReading,
        suppressAnimation: Boolean,
        onLineWillApply: ((Float) -> Boolean)?,
        onLineApplied: (() -> Unit)?,
        onLineCancelled: (() -> Unit)?
    ): Boolean {
        val targetLine = lineOverride ?: buildSlotLyricLine(
            view = view,
            prefs = prefs,
            config = config,
            isLeft = view.tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
        )
        val nextLinePreviewEnabledForView = isNextLinePreviewEnabled(prefs, config)
        val disableAll = TranslationHelper.isTranslationDisabled(prefs) ||
                nextLinePreviewEnabledForView
        val translationOnly = TranslationHelper.isTranslationOnly(prefs)
        val targetLineSignature = lineContentSignature(targetLine)
        val signature = "lyric|$targetLineSignature|${config.styleSignature}"
        if (!force && IslandSlotContentSignatureCache.get(view) == signature &&
            appliedLineSignature(view) == targetLineSignature
        ) {
            applyPlaybackSnapshot(view, playbackActive, playbackClock)
            return false
        }

        val suppressContentAnimation = suppressAnimation || nextLinePreviewEnabledForView ||
                view.parent == null || !view.isAttachedToWindow
        val shouldAnimate = config.lyricAnimationEnabled && !suppressContentAnimation
        val applyLine: (View) -> Unit = { target ->
            // A non-animated creation/restoration owns the initial active state. Once a View is
            // live, playback callbacks remain authoritative; delayed line commits must never
            // replay a state captured before their animation or width preflight.
            if (!shouldAnimate) {
                IslandLyricViewController.setPlaybackActive(target, playbackActive)
            }

            var lineSubmissionInProgress = true
            val onCommitted: () -> Unit = {
                // Dynamic-width preflight and next-line promotion can both defer the real line
                // commit even when the outer content animation is disabled. Anchor media time
                // only after the renderer has accepted the new line, otherwise the seek applies
                // to the old renderer and the new renderer draws one stale frame.
                val clockAtCommit = if (shouldAnimate || !lineSubmissionInProgress) {
                    LyriconDataBridge.currentPlaybackClock()
                } else {
                    playbackClock
                }
                IslandLyricViewController.synchronizePosition(
                    target,
                    clockAtCommit.positionMs,
                    clockAtCommit.playbackSpeed,
                    clockAtCommit.activeTimeMs
                )
                if (config.lyricMarqueeEnabled) {
                    target.post {
                        when (target) {
                            is RichLyricLineView -> target.requestStartMarquee()
                            is SpaceGateRichLyricLineView -> target.requestStartMarquee()
                        }
                    }
                }
                onLineApplied?.invoke()
            }
            when (target) {
                is RichLyricLineView -> {
                    target.setLineWithCallbacks(
                        targetLine,
                        onMainLineWillApply = onLineWillApply,
                        onMainLineApplied = onCommitted,
                        onMainLineCancelled = onLineCancelled
                    )
                }

                is SpaceGateRichLyricLineView -> {
                    target.setLineWithCallbacks(
                        targetLine,
                        onMainLineWillApply = onLineWillApply,
                        onMainLineApplied = onCommitted,
                        onMainLineCancelled = onLineCancelled
                    )
                }
            }
            lineSubmissionInProgress = false
        }

        if (shouldAnimate) {
            val preset = YoYoPresets.getById(config.lyricAnimationId) ?: YoYoPresets.Default
            when (view) {
                is RichLyricLineView -> view.animateUpdate(preset) { applyLine(this) }
                is SpaceGateRichLyricLineView -> view.animateUpdate(preset) { applyLine(this) }
                else -> applyLine(view)
            }
        } else {
            applyLine(view)
        }
        IslandSlotContentSignatureCache.set(view, signature)
        val viewKey = view.tag?.toString() ?: view.javaClass.simpleName
        val animated = shouldAnimate
        val linePresent = targetLine != null
        val secondaryPresent = !targetLine?.secondary.isNullOrBlank()
        val translationPresent = !targetLine?.translation.isNullOrBlank()
        val debugState = listOf(
            linePresent,
            secondaryPresent,
            translationPresent,
            disableAll,
            translationOnly,
            nextLinePreviewEnabledForView,
            animated,
            view.isAttachedToWindow
        ).joinToString("|")
        HookLogger.dState(
            stateId = "IslandLyricContentAssembler:$viewKey",
            tag = "IslandLyricContentAssembler",
            state = debugState
        ) {
            "歌词内容状态已提交: tag=$viewKey, linePresent=$linePresent, " +
                    "secondaryPresent=$secondaryPresent, translationPresent=$translationPresent, " +
                    "translationOnly=$translationOnly, disableAll=$disableAll, " +
                    "nextLinePreview=$nextLinePreviewEnabledForView, " +
                    "animationEnabled=${config.lyricAnimationEnabled}, animated=$animated, " +
                    "attached=${view.isAttachedToWindow}"
        }
        return true
    }

    fun applyLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        playbackActive: Boolean,
        playbackClock: LyriconDataBridge.PlaybackClockReading =
            LyriconDataBridge.currentPlaybackClock(),
        onLineWillApply: ((Float) -> Boolean)? = null,
        onLineApplied: (() -> Unit)? = null,
        onLineCancelled: (() -> Unit)? = null
    ): Boolean = apply(
        view = view,
        prefs = prefs,
        config = config,
        lineOverride = lineOverride,
        force = false,
        playbackActive = playbackActive,
        playbackClock = playbackClock,
        suppressAnimation = false,
        onLineWillApply = onLineWillApply,
        onLineApplied = onLineApplied,
        onLineCancelled = onLineCancelled
    )

    fun buildSlotLyricLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): IRichLyricLine? {
        val rawLine = processedRawLine(prefs, config)
        if (!config.isSplitMode || rawLine == null) return rawLine
        if (rawLine.text.isNullOrEmpty()) return rawLine

        val density = view.resources.displayMetrics.density
        val fallbackPaint = TextPaint().apply {
            textSize = config.textSizeSp.toFloat() * density
        }
        val textPaint = when (view) {
            is RichLyricLineView -> TextPaint(view.main.textPaint)
            is SpaceGateRichLyricLineView -> TextPaint(view.main.textPaint)
            else -> fallbackPaint
        }.takeIf { it.textSize > 0f } ?: fallbackPaint
        val secondaryPaint = when (view) {
            is RichLyricLineView -> TextPaint(view.secondary.textPaint)
            is SpaceGateRichLyricLineView -> TextPaint(view.secondary.textPaint)
            else -> TextPaint(textPaint).apply {
                textSize *= config.textSizeRatio
            }
        }.takeIf { it.textSize > 0f } ?: TextPaint(textPaint).apply {
            textSize *= config.textSizeRatio
        }

        fun contentWidthPx(widthDp: Int, parentName: String): Float {
            val wrapperWidthPx = widthDp * density
            val paddingPx = config.geometry.paddingLeftPx(view, parentName) +
                    config.geometry.paddingRightPx(view, parentName)
            return (wrapperWidthPx - paddingPx).coerceAtLeast(1f)
        }

        val leftMinContentPx = contentWidthPx(
            config.geometry.leftMinWidthDp,
            IslandProbeUtils.LEFT_PARENT_NAME
        )
        val leftMaxContentPx = contentWidthPx(
            config.geometry.leftMaxWidthDp,
            IslandProbeUtils.LEFT_PARENT_NAME
        )
        val rightMinContentPx = contentWidthPx(
            config.geometry.rightMinWidthDp,
            IslandProbeUtils.RIGHT_PARENT_NAME
        )
        val rightMaxContentPx = contentWidthPx(
            config.geometry.rightMaxWidthDp,
            IslandProbeUtils.RIGHT_PARENT_NAME
        )
        val containerWidthSpec = if (config.geometry.isDynamicWidth) {
            RichLyricLineSplitter.ContainerWidthSpec.Dynamic(
                leftMinWidthPx = leftMinContentPx,
                leftMaxWidthPx = leftMaxContentPx,
                rightMinWidthPx = rightMinContentPx,
                rightMaxWidthPx = rightMaxContentPx
            )
        } else {
            RichLyricLineSplitter.ContainerWidthSpec.Fixed(
                leftWidthPx = leftMaxContentPx,
                rightWidthPx = rightMaxContentPx
            )
        }
        val splitResult = RichLyricLineSplitter.split(
            line = rawLine,
            primaryPaint = textPaint,
            // A next-line preview is rendered in the secondary slot only temporarily. It will
            // be promoted to the primary slot on the next lyric change, so keep its split
            // boundary identical to the primary row even though its current paint is smaller.
            secondaryPaint = if (rawLine.metadata?.getBoolean(METADATA_NEXT_LINE_PREVIEW) == true) {
                textPaint
            } else {
                secondaryPaint
            },
            containerWidthSpec = containerWidthSpec
        )
        return if (isLeft) splitResult.left else splitResult.right
    }

    fun processedRawLine(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig? = null
    ): IRichLyricLine? {
        var rawLine = LyriconDataBridge.currentLyricLine
            ?: return null

        if (config != null && isNextLinePreviewEnabled(prefs, config, rawLine)) {
            return rawLine.withNextLinePreview(LyriconDataBridge.currentNextLyricLine)
        }

        if (TranslationHelper.isTranslationOnly(prefs)) {
            rawLine = TranslationHelper.applyTranslationOnly(rawLine)
        } else if (TranslationHelper.isSwapTranslation(prefs)) {
            rawLine = TranslationHelper.swapTranslation(rawLine)
        }
        return rawLine
    }

    internal fun isNextLinePreviewEnabled(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        currentLine: IRichLyricLine? = LyriconDataBridge.currentLyricLine
    ): Boolean {
        if (!config.nextLyricLine) return false
        if (LyriconDataBridge.isTextMode) return false
        val source = prefs.getString(
            RootConstants.KEY_HOOK_LYRIC_SOURCE,
            RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
        )
        if (source != "lyricon" && source != "lyricinfo") return false

        if (config.autoSwitchTranslation) {
            val hasSongTranslation =
                LyriconDataBridge.currentSong?.lyrics?.any { !it.translation.isNullOrBlank() } == true
            val hasLineTranslation = !currentLine?.translation.isNullOrBlank()
            if (hasSongTranslation || hasLineTranslation) return false
        }
        return true
    }

    private fun applyPlaybackSnapshot(
        view: View,
        playbackActive: Boolean,
        playbackClock: LyriconDataBridge.PlaybackClockReading
    ) {
        IslandLyricViewController.applyPlaybackSnapshot(
            view,
            playbackClock.positionMs,
            playbackClock.playbackSpeed,
            playbackClock.activeTimeMs,
            playbackActive
        )
    }

    private fun lineContentSignature(line: IRichLyricLine?): Int {
        if (line == null) return 0
        return listOf(
            line.begin,
            line.end,
            line.duration,
            line.text,
            line.words,
            line.secondary,
            line.secondaryWords,
            line.translation,
            line.translationWords,
            line.roma,
            line.isAlignedRight,
            line.metadata
        ).hashCode()
    }

    private fun appliedLineSignature(view: View): Int? {
        val line = when (view) {
            is RichLyricLineView -> view.rawLine
            is SpaceGateRichLyricLineView -> view.rawLine
            else -> return null
        }
        return lineContentSignature(line)
    }

    private fun IRichLyricLine.withNextLinePreview(nextLine: IRichLyricLine?): IRichLyricLine {
        val nextText = nextLine?.text?.takeIf { it.isNotBlank() }
        return RichLyricLine(
            begin = begin,
            end = end,
            duration = duration,
            isAlignedRight = isAlignedRight,
            metadata = lyricMetadataOf(
                *(metadata?.entries?.map { it.key to it.value } ?: emptyList()).toTypedArray(),
                METADATA_NEXT_LINE_PREVIEW to "true"
            ),
            text = text,
            words = words,
            secondary = nextText,
            secondaryWords = emptyList(),
            translation = null,
            translationWords = null,
            roma = null
        )
    }
}
