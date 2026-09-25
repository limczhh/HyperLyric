package com.lidesheng.hyperlyric.root.statusbar

import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Status-bar-only layout options. Lyric content and style continue to use their existing scopes. */
internal data class StatusBarLyricLayoutConfig(
    val insertionOrder: Int,
    val iconEnabled: Boolean,
    val iconStyle: Int,
    val iconOrder: Int,
    val iconSizePx: Int,
    val iconSpacingPx: Int,
    val dynamicMaxWidthPx: Int,
    val paddingLeftPx: Int,
    val paddingRightPx: Int,
    val adjustWidthForSuperIsland: Boolean,
) {
    val widthLimitPx: Int
        get() = dynamicMaxWidthPx

    fun desiredWidthPx(contentWidthPx: Float, iconWidthPx: Int = 0): Int {
        if (dynamicMaxWidthPx == 0) return 0
        return (ceil(contentWidthPx).toInt() + iconWidthPx + paddingLeftPx + paddingRightPx)
            .coerceAtMost(dynamicMaxWidthPx)
            .coerceAtLeast(1)
    }

    companion object {
        fun from(prefs: SharedPreferences, root: ViewGroup): StatusBarLyricLayoutConfig {
            val resources = root.resources
            val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
            val isLandscape = resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            val maxWidthDp = if (isLandscape) {
                RootConstants.STATUS_BAR_LYRIC_LANDSCAPE_MAX_WIDTH_DP
            } else {
                RootConstants.STATUS_BAR_LYRIC_PORTRAIT_MAX_WIDTH_DP
            }
            val dynamicMaxKey = if (isLandscape) {
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH
            } else {
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH
            }
            val defaultMaxWidthDp = if (isLandscape) {
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH
            } else {
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH
            }
            val dynamicMaxPreference = prefs.getInt(
                dynamicMaxKey,
                defaultMaxWidthDp,
            )
            val dynamicMaxDp = if (dynamicMaxPreference < 0) {
                defaultMaxWidthDp
            } else {
                dynamicMaxPreference.coerceIn(0, maxWidthDp)
            }

            return StatusBarLyricLayoutConfig(
                insertionOrder = prefs.getInt(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
                ).takeIf {
                    it == RootConstants.STATUS_BAR_LYRIC_INSERTION_BEFORE_CLOCK ||
                            it == RootConstants.STATUS_BAR_LYRIC_INSERTION_AFTER_CLOCK
                } ?: RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
                iconEnabled = prefs.getBoolean(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED,
                ),
                iconStyle = prefs.getInt(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_STYLE,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_STYLE,
                ).takeIf {
                    it in RootConstants.STATUS_BAR_LYRIC_ICON_MUSIC_COVER..
                            RootConstants.STATUS_BAR_LYRIC_ICON_MONOCHROME
                } ?: RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_STYLE,
                iconOrder = prefs.getInt(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ORDER,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ORDER,
                ).takeIf {
                    it == RootConstants.STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC ||
                            it == RootConstants.STATUS_BAR_LYRIC_ICON_AFTER_LYRIC
                } ?: RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ORDER,
                iconSizePx = dpToPx(
                    prefs.getInt(
                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP,
                        RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP,
                    ).coerceIn(
                        RootConstants.STATUS_BAR_LYRIC_ICON_MIN_SIZE_DP,
                        RootConstants.STATUS_BAR_LYRIC_ICON_MAX_SIZE_DP,
                    ),
                    density,
                ),
                iconSpacingPx = dpToPx(RootConstants.STATUS_BAR_LYRIC_ICON_SPACING_DP, density),
                dynamicMaxWidthPx = dpToPx(dynamicMaxDp, density),
                paddingLeftPx = paddingPx(
                    prefs,
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP,
                    density,
                ),
                paddingRightPx = paddingPx(
                    prefs,
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP,
                    density,
                ),
                adjustWidthForSuperIsland = prefs.getBoolean(
                    RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                ),
            )
        }

        private fun dpToPx(dp: Int, density: Float): Int =
            (dp * density).roundToInt().coerceAtLeast(0)

        private fun paddingPx(
            prefs: SharedPreferences,
            key: String,
            density: Float,
        ): Int {
            val paddingDp = prefs.getFloat(
                key,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
            ).takeIf { it.isFinite() }?.coerceIn(
                RootConstants.MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
                RootConstants.MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
            ) ?: RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP
            return (paddingDp * density).roundToInt()
        }
    }
}
