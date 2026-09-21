package com.lidesheng.hyperlyric.root.mediacard.style

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils

internal sealed interface MediaCardForegroundColorSource {
    data class Monet(
        val primary: Int,
        val secondary: Int = primary
    ) : MediaCardForegroundColorSource

    data class Soft(
        val colors: List<Int>
    ) : MediaCardForegroundColorSource
}

/**
 * Semantic foreground roles shared by the notification center and the
 * expanded media island. A custom background supplies the tone. When no
 * custom background tone exists, callers leave SystemUI's native foreground
 * untouched.
 */
internal data class MediaCardForegroundPalette(
    val primary: Int,
    val secondary: Int
)

internal data class MediaCardForegroundColors(
    val primary: Int,
    val secondary: Int
)

internal object MediaCardForegroundPaletteResolver {
    /**
     * Resolves the semantic foreground roles shared by all media-card hosts.
     * Dynamic sources are supplied by the background renderer; a missing
     * source falls back to the native SystemUI resource colors.
     */
    fun resolve(
        context: Context?,
        backgroundIsDark: Boolean,
        foregroundSource: MediaCardForegroundColorSource? = null
    ): MediaCardForegroundPalette? {
        foregroundSource?.let { source ->
            resolveDynamic(source, backgroundIsDark)?.let { return it }
        }

        val themedContext = context?.withNightMode(backgroundIsDark) ?: return null
        val primary = themedContext.colorOrNull("media_primary_text") ?: return null
        val secondary = themedContext.colorOrNull("media_secondary_text") ?: return null
        return MediaCardForegroundPalette(
            primary = primary,
            secondary = secondary
        )
    }

    private fun resolveDynamic(
        source: MediaCardForegroundColorSource,
        backgroundIsDark: Boolean
    ): MediaCardForegroundPalette? {
        val bases = when (source) {
            is MediaCardForegroundColorSource.Monet -> {
                DynamicBases(
                    primary = source.primary,
                    secondary = source.secondary,
                    adaptLightness = false,
                    alignSecondaryHue = false
                )
            }

            is MediaCardForegroundColorSource.Soft -> {
                val colors = source.colors.filter { Color.alpha(it) != 0 }
                val primary = colors.getOrNull(0) ?: return null
                // A dark soft background can contain several unrelated artwork
                // colors. Keep both foreground roles on the dominant color
                // family there; using the second palette entry made the two
                // roles feel disconnected after the tonal adaptation.
                val secondary = if (backgroundIsDark) {
                    primary
                } else {
                    colors.getOrNull(1) ?: primary
                }
                DynamicBases(
                    primary = primary,
                    secondary = secondary,
                    adaptLightness = true,
                    alignSecondaryHue = true
                )
            }
        }

        val primary = if (bases.adaptLightness) {
            adaptForBackground(
                color = bases.primary,
                backgroundIsDark = backgroundIsDark,
                targetLightness = if (backgroundIsDark) 88.0 else 34.0
            )
        } else {
            ColorUtils.setAlphaComponent(bases.primary, 0xFF)
        }
        val secondaryCandidate = if (bases.adaptLightness) {
            adaptForBackground(
                color = bases.secondary,
                backgroundIsDark = backgroundIsDark,
                targetLightness = if (backgroundIsDark) 78.0 else 42.0
            )
        } else {
            ColorUtils.setAlphaComponent(bases.secondary, 0xFF)
        }
        val secondary = if (bases.alignSecondaryHue) {
            alignHue(primary, secondaryCandidate)
        } else {
            secondaryCandidate
        }

        return MediaCardForegroundPalette(
            primary = primary,
            secondary = secondary
        )
    }

    private fun alignHue(primary: Int, variant: Int): Int {
        val primaryHsl = FloatArray(3)
        val variantHsl = FloatArray(3)
        ColorUtils.colorToHSL(primary, primaryHsl)
        ColorUtils.colorToHSL(variant, variantHsl)
        variantHsl[0] = primaryHsl[0]
        return ColorUtils.HSLToColor(variantHsl)
    }

    /**
     * Preserves source hue/chroma while moving perceptual lightness into a
     * readable range, so dynamic foregrounds do not collapse to black/white.
     */
    private fun adaptForBackground(
        color: Int,
        backgroundIsDark: Boolean,
        targetLightness: Double
    ): Int {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(color, lab)
        val readableLightness = if (backgroundIsDark) {
            targetLightness.coerceIn(70.0, 92.0)
        } else {
            targetLightness.coerceIn(28.0, 48.0)
        }
        return ColorUtils.LABToColor(readableLightness, lab[1], lab[2])
    }

    private data class DynamicBases(
        val primary: Int,
        val secondary: Int,
        val adaptLightness: Boolean,
        val alignSecondaryHue: Boolean
    )

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
