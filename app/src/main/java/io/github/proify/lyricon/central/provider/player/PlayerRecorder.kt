/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

data class PlayerRecorder(val info: ProviderInfo) {

    @Volatile
    var lastSong: Song? = null
        set(value) {
            field = value
            lastLyricType = LyricType.SONG
        }

    @Volatile
    var lastIsPlaying: Boolean = false

    @Volatile
    var lastPosition: Long = -1

    @Volatile
    var lastText: String? = null
        set(value) {
            field = value
            lastLyricType = LyricType.TEXT
        }

    @Volatile
    var lastIsDisplayTranslation: Boolean = false

    @Volatile
    var lastDisplayRoma = false

    @Volatile
    var lastLyricType: LyricType = LyricType.NONE
        private set

    enum class LyricType {
        NONE,
        SONG,
        TEXT
    }
}