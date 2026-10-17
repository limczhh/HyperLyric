/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.lidesheng.hyperlyric.lyric.model

import com.lidesheng.hyperlyric.lyric.model.extensions.deepCopy
import com.lidesheng.hyperlyric.lyric.model.extensions.normalizeSortByTime
import com.lidesheng.hyperlyric.lyric.model.interfaces.DeepCopyable
import com.lidesheng.hyperlyric.lyric.model.interfaces.Normalize
import kotlinx.serialization.Serializable

/**
 * 歌曲信息
 *
 * @property id 歌曲ID
 * @property name 歌曲名
 * @property artist 艺术家
 * @property album 专辑
 * @property duration 歌曲时长
 * @property metadata 元数据
 * @property lyrics 歌词列表
 * @property songMid 歌曲MID
 */
@Serializable
data class Song(
    var id: String? = null,
    var name: String? = null,
    var artist: String? = null,
    var duration: Long = 0,
    var metadata: LyricMetadata? = null,
    var lyrics: List<RichLyricLine>? = null,
    var album: String? = null,
    var songMid: String? = null,
) : DeepCopyable<Song>, Normalize<Song> {

    override fun deepCopy(): Song = copy(lyrics = lyrics?.deepCopy())

    override fun normalize(): Song = deepCopy().apply {
        lyrics = lyrics?.mapNotNull { line ->
            if (line.duration <= 0) line.duration = line.end - line.begin

            val isValid = line.begin >= 0
                    && line.begin < line.end
                    && line.duration > 0
                    && !line.text.isNullOrBlank()
            if (isValid) line else null
        }?.normalizeSortByTime()
    }
}
