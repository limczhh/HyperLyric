package com.lidesheng.hyperlyric.root.island.presentation

/**
 * Pure presentation policy for Super Island lyric targets.
 *
 * Hookers provide already extracted facts. This class deliberately does not
 * access views, preferences, reflection, or the SystemUI lifecycle.
 */
internal object IslandRenderPolicy {

    sealed interface OwnerEvidence {
        data class Media(val packageName: String) : OwnerEvidence
        data object Pending : OwnerEvidence
        data object NotMedia : OwnerEvidence
    }

    data class Input(
        val owner: OwnerEvidence,
        val lyricPackageName: String?,
        val hasLyricsForPresentation: Boolean,
        val hasMusicInfoForPresentation: Boolean,
        val showMusicInfoWhenNoLyrics: Boolean,
        val enabled: Boolean,
        val playbackActive: Boolean,
        val pauseBehavior: Int
    )

    enum class Decision {
        TARGET,
        PENDING,
        OTHER_PACKAGE,
        SUPPRESSED,
        NOT_MEDIA
    }

    fun evaluate(input: Input): Decision {
        if (input.owner == OwnerEvidence.NotMedia) return Decision.NOT_MEDIA
        if (!input.enabled) return Decision.SUPPRESSED
        if (!input.hasLyricsForPresentation &&
            (!input.showMusicInfoWhenNoLyrics || !input.hasMusicInfoForPresentation)
        ) {
            return Decision.SUPPRESSED
        }

        val mediaOwner = input.owner as? OwnerEvidence.Media
            ?: return Decision.PENDING
        val lyricPackageName = input.lyricPackageName
            ?.takeIf(String::isNotEmpty)
            ?: return Decision.PENDING

        if (mediaOwner.packageName != lyricPackageName) {
            return Decision.OTHER_PACKAGE
        }
        if (!input.playbackActive && input.pauseBehavior == 0) {
            return Decision.SUPPRESSED
        }
        return Decision.TARGET
    }

    fun isPresentationAllowed(
        enabled: Boolean,
        playbackActive: Boolean,
        pauseBehavior: Int
    ): Boolean {
        return enabled && (playbackActive || pauseBehavior != 0)
    }
}
