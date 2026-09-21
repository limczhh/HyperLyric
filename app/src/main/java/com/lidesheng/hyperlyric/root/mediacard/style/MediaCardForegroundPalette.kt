package com.lidesheng.hyperlyric.root.mediacard.style

import android.content.Context
import android.content.res.Configuration

/**
 * Semantic foreground roles shared by the notification center and the
 * expanded media island. A custom background supplies the tone. When no
 * custom background tone exists, callers leave SystemUI's native foreground
 * untouched.
 */
internal data class MediaCardForegroundPalette(
    val primary: Int,
    val secondary: Int,
    val duration: Int,
    val progressTrack: Int
)

internal data class MediaCardForegroundColors(
    val primary: Int,
    val secondary: Int
)

internal object MediaCardForegroundPaletteResolver {
    /**
     * Resolves colors only from the explicit tone of an active custom
     * background. A missing background/resource is intentionally represented
     * by null so SystemUI keeps its own foreground implementation.
     */
    fun resolve(
        context: Context?,
        backgroundIsDark: Boolean
    ): MediaCardForegroundPalette? {
        val themedContext = context?.withNightMode(backgroundIsDark) ?: return null
        val primary = themedContext.colorOrNull("media_primary_text") ?: return null
        val secondary = themedContext.colorOrNull("media_secondary_text") ?: return null
        val duration = themedContext.colorOrNull(
            if (backgroundIsDark) "media_duration_time_font_dark_color"
            else "media_duration_time_font_color"
        ) ?: return null
        val progressTrack = themedContext.colorOrNull(
            "media_seekbar_background_color",
            "notification_media_seekbar_bg_color"
        ) ?: return null
        return MediaCardForegroundPalette(
            primary = primary,
            secondary = secondary,
            duration = duration,
            progressTrack = progressTrack
        )
    }

    private fun Context.withNightMode(dark: Boolean): Context {
        val nightMode = if (dark) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (currentNightMode == nightMode) return this
        val configuration = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        return createConfigurationContext(configuration)
    }

    @Suppress("DiscouragedApi")
    private fun Context.colorOrNull(vararg names: String): Int? {
        names.forEach { name ->
            val id = resources.getIdentifier(name, "color", packageName)
            if (id != 0) {
                runCatching { return getColor(id) }
            }
        }
        return null
    }
}
