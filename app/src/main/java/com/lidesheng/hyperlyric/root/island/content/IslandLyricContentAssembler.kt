package com.lidesheng.hyperlyric.root.island.content

import android.content.SharedPreferences
import android.text.TextPaint
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplayPolicy
import com.lidesheng.hyperlyric.common.lyric.METADATA_RESOLVED_SECONDARY_CONTENT
import com.lidesheng.hyperlyric.common.lyric.LyricPresentation
import com.lidesheng.hyperlyric.common.lyric.LyricPresentationResolver
import com.lidesheng.hyperlyric.common.lyric.RichLyricLineSplitter
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContent
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContentTransformer
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

internal object IslandLyricContentAssembler {

    fun apply(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        secondaryLineOverride: IRichLyricLine? = null,
        force: Boolean,
        playbackActive: Boolean,
        playbackClock: LyriconDataBridge.PlaybackClockReading,
        suppressAnimation: Boolean,
        onLineWillApply: ((Float) -> Boolean)?,
        onLineApplied: (() -> Unit)?,
        onLineCancelled: (() -> Unit)?
    ): Boolean {
        val targetPresentation = if (lineOverride != null || secondaryLineOverride != null) {
            LyricPresentation(lineOverride, secondaryLineOverride)
        } else {
            buildSlotLyricPresentation(
                view = view,
                prefs = prefs,
                config = config,
                isLeft = view.tag == IslandProbeUtils.LEFT_TEST_VIEW_TAG
            )
        }
        val targetLine = targetPresentation.primary
        val nextLinePreviewEnabledForView =
            targetLine?.metadata?.getBoolean(METADATA_NEXT_LINE_PREVIEW) == true
        val targetLineSignature = presentationSignature(targetPresentation)
        val signature = "lyric|$targetLineSignature|${config.styleSignature}"
        val useSharedMarqueeClock = config.lyricMarqueeEnabled && LyriconDataBridge.isTextMode
        val marqueeClockOriginActiveTimeMs = if (useSharedMarqueeClock) {
            LyriconDataBridge.currentPlainTextMarqueeOriginActiveTimeMs()
        } else {
            0L
        }
        if (!force && IslandSlotContentSignatureCache.get(view) == signature &&
            appliedLineSignature(view) == targetLineSignature
        ) {
            IslandLyricViewController.useSharedMarqueeClock(
                view,
                useSharedMarqueeClock,
                marqueeClockOriginActiveTimeMs
            )
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
                IslandLyricViewController.useSharedMarqueeClock(
                    target,
                    useSharedMarqueeClock,
                    marqueeClockOriginActiveTimeMs
                )
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
                        onMainLineCancelled = onLineCancelled,
                        secondaryLine = targetPresentation.secondary
                    )
                }

                is SpaceGateRichLyricLineView -> {
                    target.setLineWithCallbacks(
                        targetLine,
                        onMainLineWillApply = onLineWillApply,
                        onMainLineApplied = onCommitted,
                        onMainLineCancelled = onLineCancelled,
                        secondaryLine = targetPresentation.secondary
                    )
                }
            }
            lineSubmissionInProgress = false
        }

        if (shouldAnimate) {
            val preset = YoYoPresets.getById(config.lyricAnimationId) ?: YoYoPresets.Default
            val speedRate = config.lyricAnimationSpeedRate
            val scaledPreset = if (speedRate == RootConstants.DEFAULT_HOOK_ANIM_SPEED_RATE) {
                preset
            } else {
                preset.first.copy(
                    duration = durationForSpeed(preset.first.duration, speedRate)
                ) to preset.second.copy(
                    duration = durationForSpeed(preset.second.duration, speedRate)
                )
            }
            when (view) {
                is RichLyricLineView -> view.animateUpdate(scaledPreset) { applyLine(this) }
                is SpaceGateRichLyricLineView -> view.animateUpdate(scaledPreset) { applyLine(this) }
                else -> applyLine(view)
            }
        } else {
            applyLine(view)
        }
        IslandSlotContentSignatureCache.set(view, signature)
        return true
    }

    private fun durationForSpeed(duration: Long, speedRate: Int): Long {
        return (duration * 100L / speedRate).coerceAtLeast(1L)
    }

    fun applyLine(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        lineOverride: IRichLyricLine?,
        secondaryLineOverride: IRichLyricLine? = null,
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
        secondaryLineOverride = secondaryLineOverride,
        force = false,
        playbackActive = playbackActive,
        playbackClock = playbackClock,
        suppressAnimation = false,
        onLineWillApply = onLineWillApply,
        onLineApplied = onLineApplied,
        onLineCancelled = onLineCancelled
    )

    fun buildSlotLyricPresentation(
        view: View,
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig,
        isLeft: Boolean
    ): LyricPresentation {
        val rawPresentation = processedPresentation(prefs, config)
        val rawLine = rawPresentation.primary
        if (!config.isSplitMode || rawLine == null) return rawPresentation
        if (rawLine.text.isNullOrEmpty()) return rawPresentation

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
        val primarySplit = RichLyricLineSplitter.split(
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
            containerWidthSpec = containerWidthSpec,
            partitionUntimedTimeline = config.syllableRelative
        )
        val secondarySplit = rawPresentation.secondary?.let { secondaryLine ->
            RichLyricLineSplitter.split(
                line = secondaryLine,
                primaryPaint = secondaryPaint,
                secondaryPaint = secondaryPaint,
                containerWidthSpec = containerWidthSpec,
                partitionUntimedTimeline = config.syllableRelative
            )
        }
        return LyricPresentation(
            primary = if (isLeft) primarySplit.left else primarySplit.right,
            secondary = secondarySplit?.let {
                if (isLeft) it.left else it.right
            },
            secondaryContent = rawPresentation.secondaryContent
        )
    }

    fun processedPresentation(
        prefs: SharedPreferences,
        config: IslandSlotRuntimeConfig? = null
    ): LyricPresentation {
        if (!LyriconDataBridge.hasLyricsForPresentation()) {
            return if (shouldUseNoLyricsPlaceholder(prefs)) {
                LyriconDataBridge.noLyricsPlaceholderLine()?.let { line ->
                    LyricPresentation(primary = line)
                } ?: LyricPresentation(null)
            } else {
                LyricPresentation(null)
            }
        }
        val rawLine = LyriconDataBridge.currentLyricLine ?: return LyricPresentation(null)
        val displaySettings = config?.lyricContentDisplay ?: LyricContentDisplayPolicy.read(prefs)
        val sourceLines = LyriconDataBridge.currentLyricLines.ifEmpty { listOf(rawLine) }
        val nextLine = LyriconDataBridge.currentNextLyricLine
        val resolved = LyricPresentationResolver.resolve(
            activeLines = sourceLines,
            nextLine = nextLine,
            songLines = LyriconDataBridge.currentSong?.lyrics,
            settings = displaySettings,
            autoDuet = config?.autoDuet ?: prefs.getBoolean(
                RootConstants.KEY_HOOK_LYRIC_AUTO_DUET,
                RootConstants.DEFAULT_HOOK_LYRIC_AUTO_DUET
            )
        )
        val primaryLine = resolved.primary ?: return resolved
        val selectedContent = resolved.secondaryContent
        if (selectedContent == LyricSecondaryContent.NEXT_LINE) {
            return resolved.copy(primary = primaryLine.withNextLinePreview(nextLine))
        }

        val onlySecondary = config?.onlySecondary ?: prefs.getBoolean(
            RootConstants.KEY_HOOK_ONLY_SECONDARY,
            RootConstants.DEFAULT_HOOK_ONLY_SECONDARY
        )
        val swapSecondary = config?.swapSecondary ?: prefs.getBoolean(
            RootConstants.KEY_HOOK_SWAP_SECONDARY,
            RootConstants.DEFAULT_HOOK_SWAP_SECONDARY
        )
        val resolvedLine = selectedContent
            ?.takeUnless {
                it == LyricSecondaryContent.NEXT_LINE ||
                        it == LyricSecondaryContent.OVERLAPPING_LINE
            }
            ?.let { primaryLine.withResolvedSecondaryContent(it) }
            ?: primaryLine
        val transformedPrimary = if (selectedContent == LyricSecondaryContent.OVERLAPPING_LINE) {
            // An independently timed overlap is already the secondary source. Applying the
            // legacy only/swap transform to the primary here would duplicate or discard rows.
            primaryLine
        } else {
            LyricSecondaryContentTransformer.apply(
                line = resolvedLine,
                settings = displaySettings,
                onlySecondary = onlySecondary,
                swapSecondary = swapSecondary,
                songLines = LyriconDataBridge.currentSong?.lyrics
            )
        }
        return resolved.copy(
            primary = transformedPrimary,
            secondary = resolved.secondary?.takeIf {
                selectedContent == LyricSecondaryContent.OVERLAPPING_LINE
            }
        )
    }

    fun shouldUseNoLyricsPlaceholder(prefs: SharedPreferences): Boolean {
        return !LyriconDataBridge.hasLyricsForPresentation() &&
                LyriconDataBridge.hasMusicInfoForPresentation() &&
                prefs.getInt(
                    RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_NO_LYRICS,
                    RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_NO_LYRICS
                ) == RootConstants.ISLAND_NO_LYRICS_BEHAVIOR_MUSIC_INFO
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

    private fun presentationSignature(presentation: LyricPresentation): Int = listOf(
        lineContentSignature(presentation.primary),
        lineContentSignature(presentation.secondary)
    ).hashCode()

    private fun appliedLineSignature(view: View): Int? {
        val presentation = when (view) {
            is RichLyricLineView -> LyricPresentation(view.rawLine, view.rawSecondaryLine)
            is SpaceGateRichLyricLineView ->
                LyricPresentation(view.rawLine, view.rawSecondaryLine)

            else -> return null
        }
        return presentationSignature(presentation)
    }

    private fun IRichLyricLine.withNextLinePreview(nextLine: IRichLyricLine?): IRichLyricLine {
        val nextText = nextLine?.text?.takeIf { it.isNotBlank() }
            ?: nextLine?.words?.joinToString("") { it.text.orEmpty() }
                ?.takeIf { it.isNotBlank() }
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

    private fun IRichLyricLine.withResolvedSecondaryContent(
        content: LyricSecondaryContent
    ): IRichLyricLine {
        val metadata = lyricMetadataOf(
            *(metadata?.entries?.filter {
                it.key != METADATA_RESOLVED_SECONDARY_CONTENT
            }?.map { it.key to it.value } ?: emptyList()).toTypedArray(),
            METADATA_RESOLVED_SECONDARY_CONTENT to content.preferenceValue
        )
        return RichLyricLine(
            begin = begin,
            end = end,
            duration = duration,
            isAlignedRight = isAlignedRight,
            metadata = metadata,
            text = text,
            words = words,
            secondary = secondary,
            secondaryWords = secondaryWords,
            translation = translation,
            translationWords = translationWords,
            roma = roma
        )
    }
}
