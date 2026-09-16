package com.lidesheng.hyperlyric.root.lyricenhancement

import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper

internal data class LyricEnhancementMediaInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long? = null,
    val sourcePackageName: String? = null,
)

internal data class LyricEnhancementInput(
    val mediaInfo: LyricEnhancementMediaInfo? = null,
)

/** Keeps the lyric source package separate from any package inferred from MediaSession metadata. */
internal fun MediaMetadataHelper.MediaInfo.toLyricEnhancementMediaInfo(
    sourcePackageName: String?,
): LyricEnhancementMediaInfo? = LyricEnhancementMediaInfo(
    title = title.takeIf { it.isNotBlank() },
    artist = artist.takeIf { it.isNotBlank() },
    album = album.takeIf { it.isNotBlank() },
    duration = duration.takeIf { it > 0L },
    sourcePackageName = sourcePackageName?.takeIf { it.isNotBlank() },
).takeIf { info ->
    info.title != null ||
            info.artist != null ||
            info.album != null ||
            info.duration != null ||
            info.sourcePackageName != null
}
