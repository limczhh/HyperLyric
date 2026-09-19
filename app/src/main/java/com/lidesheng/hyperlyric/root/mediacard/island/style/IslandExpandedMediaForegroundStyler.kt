package com.lidesheng.hyperlyric.root.mediacard.island.style

import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.ColorFilter
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SquigglySeekBar
import com.lidesheng.hyperlyric.root.mediacard.style.MediaCardForegroundPalette
import java.util.Collections
import java.util.WeakHashMap

internal interface IslandExpandedMediaForegroundAccess {
    fun getTitleText(holder: Any): TextView

    fun getArtistText(holder: Any): TextView

    fun getElapsedTime(holder: Any): TextView

    fun getTotalTime(holder: Any): TextView

    fun getSeamlessIcon(holder: Any): ImageView

    fun getActionViews(holder: Any): List<ImageView>

    fun getSeekBar(holder: Any): View

    fun setSeekBarForeground(seekBar: View, color: Int)

    fun setSeekBarBackground(seekBar: View, color: Int)

    fun getSeekBarShaderColorFilter(seekBar: View): ColorFilter?

    fun setSeekBarShaderColorFilter(seekBar: View, colorFilter: ColorFilter?)

    fun getSeekBarHeadGlowAlpha(seekBar: View): Float

    fun setSeekBarHeadGlowAlpha(seekBar: View, alpha: Float)
}

internal object IslandExpandedMediaForegroundStyler {
    private val seekBarThemeStates = Collections.synchronizedMap(
        WeakHashMap<View, SeekBarThemeState>()
    )
    private val appliedPalettes = Collections.synchronizedMap(
        WeakHashMap<Any, MediaCardForegroundPalette>()
    )
    private val islandSeekBars = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )

    fun applyLightForeground(
        access: IslandExpandedMediaForegroundAccess,
        holder: Any,
        colors: MediaCardForegroundPalette
    ) {
        val seekBar = access.getSeekBar(holder)
        val state = seekBarThemeStates.getOrPut(seekBar) {
            SeekBarThemeState(
                originalColorFilter = access.getSeekBarShaderColorFilter(seekBar),
                originalHeadGlowAlpha = access.getSeekBarHeadGlowAlpha(seekBar)
            )
        }
        access.getTitleText(holder).setTextColor(colors.primary)
        access.getArtistText(holder).setTextColor(colors.secondary)
        access.getElapsedTime(holder).setTextColor(colors.duration)
        access.getTotalTime(holder).setTextColor(colors.duration)

        access.getSeamlessIcon(holder).imageTintList =
            ColorStateList.valueOf(colors.primary)
        val actionTint = ColorStateList.valueOf(colors.primary)
        access.getActionViews(holder).forEach { action ->
            action.imageTintBlendMode = BlendMode.SRC_IN
            action.imageTintList = actionTint
        }

        access.setSeekBarForeground(seekBar, colors.primary)
        access.setSeekBarBackground(seekBar, colors.progressTrack)
        access.setSeekBarShaderColorFilter(
            seekBar,
            BlendModeColorFilter(colors.primary, BlendMode.SRC_IN)
        )
        access.setSeekBarHeadGlowAlpha(seekBar, state.originalHeadGlowAlpha)
        appliedPalettes[holder] = colors
    }

    fun applyCustomForeground(
        access: IslandExpandedMediaForegroundAccess,
        holder: Any,
        colors: MediaCardForegroundPalette
    ) {
        val seekBar = access.getSeekBar(holder)
        val state = seekBarThemeStates.getOrPut(seekBar) {
            SeekBarThemeState(
                originalColorFilter = access.getSeekBarShaderColorFilter(seekBar),
                originalHeadGlowAlpha = access.getSeekBarHeadGlowAlpha(seekBar)
            )
        }
        access.getTitleText(holder).setTextColor(colors.primary)
        access.getArtistText(holder).setTextColor(colors.secondary)
        access.getElapsedTime(holder).setTextColor(colors.duration)
        access.getTotalTime(holder).setTextColor(colors.duration)

        val tint = ColorStateList.valueOf(colors.primary)
        access.getSeamlessIcon(holder).imageTintList = tint
        access.getActionViews(holder).forEach { action ->
            action.imageTintBlendMode = BlendMode.SRC_IN
            action.imageTintList = tint
        }

        access.setSeekBarForeground(seekBar, colors.primary)
        access.setSeekBarBackground(seekBar, colors.progressTrack)
        access.setSeekBarShaderColorFilter(
            seekBar,
            BlendModeColorFilter(colors.primary, BlendMode.SRC_IN)
        )
        access.setSeekBarHeadGlowAlpha(seekBar, state.originalHeadGlowAlpha)
        appliedPalettes[holder] = colors
    }

    fun appliedPalette(holder: Any): MediaCardForegroundPalette? = appliedPalettes[holder]

    fun restore(access: IslandExpandedMediaForegroundAccess, holder: Any) {
        appliedPalettes.remove(holder)
        val seekBar = access.getSeekBar(holder)
        if (seekBar is SquigglySeekBar) {
            seekBar.clearWaveColors()
        }
        seekBarThemeStates.remove(seekBar)?.let { state ->
            access.setSeekBarShaderColorFilter(seekBar, state.originalColorFilter)
            access.setSeekBarHeadGlowAlpha(seekBar, state.originalHeadGlowAlpha)
        }
    }

    fun trackSeekBar(seekBar: View) {
        islandSeekBars.add(seekBar)
    }

    fun untrackSeekBar(seekBar: View) {
        islandSeekBars.remove(seekBar)
    }

    fun isTracked(seekBar: View): Boolean = islandSeekBars.contains(seekBar)

    private data class SeekBarThemeState(
        val originalColorFilter: ColorFilter?,
        val originalHeadGlowAlpha: Float
    )
}
