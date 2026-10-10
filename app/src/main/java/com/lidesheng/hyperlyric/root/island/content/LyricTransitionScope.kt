package com.lidesheng.hyperlyric.root.island.content

/**
 * The view one lyric transition preset runs on.
 *
 * The island renderer owns at most two rows. Content derived from the main line (translation,
 * roma, background vocal, next-line preview) is rebuilt together with that line, so the whole
 * block is one visual unit. An independently timed overlapping line is not: the duet row keeps
 * its own timeline, and replaying the sibling row whenever one of them changes restarts a line
 * that is still being sung.
 */
internal enum class LyricTransitionScope {
    PROJECTION,
    MAIN_ROW,
    SECONDARY_ROW
}

/**
 * Resolves the transition scope for one lyric content update.
 *
 * @param secondaryIsIndependent true when the second row renders its own lyric line instead of
 * content derived from the main line.
 * @param mainRowChanged true when this update's main row content differs from the applied one.
 * @param secondaryRowChanged true when this update's second row content differs from the applied
 * one.
 */
internal fun resolveLyricTransitionScope(
    secondaryIsIndependent: Boolean,
    mainRowChanged: Boolean,
    secondaryRowChanged: Boolean
): LyricTransitionScope = when {
    !secondaryIsIndependent -> LyricTransitionScope.PROJECTION
    mainRowChanged && !secondaryRowChanged -> LyricTransitionScope.MAIN_ROW
    !mainRowChanged && secondaryRowChanged -> LyricTransitionScope.SECONDARY_ROW
    else -> LyricTransitionScope.PROJECTION
}
