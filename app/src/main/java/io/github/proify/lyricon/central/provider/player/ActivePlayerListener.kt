/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 活跃播放器事件监听器。
 *
 * 用于接收当前活跃播放器的状态变化与播放事件。
 * 所有回调均只会来自当前被判定为“活跃”的播放器实例。
 */
interface ActivePlayerListener {

    /**
     * 当前活跃播放器发生变化时回调。
     *
     * @param providerInfo 新的活跃播放器信息；
     * 若为 `null`，表示当前不存在活跃播放器。
     */
    fun onActiveProviderChanged(providerInfo: ProviderInfo?)

    /**
     * 当前活跃播放器的歌曲发生变化时回调。
     *
     * @param song 当前播放的歌曲；可能为 `null`，
     * 表示已停止播放或暂无歌曲信息。
     */
    fun onSongChanged(song: Song?)

    /**
     * 当前活跃播放器的播放状态发生变化时回调。
     *
     * @param isPlaying 是否处于播放状态
     */
    fun onPlaybackStateChanged(isPlaying: Boolean)

    /**
     * 当前活跃播放器的播放进度发生变化时回调。
     *
     * @param position 当前播放进度，单位为毫秒
     */
    fun onPositionChanged(position: Long)

    /**
     * 当前活跃播放器发生跳转播放位置（Seek）时回调。
     *
     * @param position 跳转后的播放进度，单位为毫秒
     */
    fun onSeekTo(position: Long)

    /**
     * 当前活跃播放器产生附加文本信息时回调。
     *
     * 通常用于状态提示、错误信息或歌词解析相关文本。
     *
     * @param text 文本内容，可能为 `null`
     */
    fun onSendText(text: String?)

    /**
     * 当前活跃播放器的翻译状态发生变化时回调。
     *
     * @param isDisplayTranslation 是否显示翻译
     */
    fun onDisplayTranslationChanged(isDisplayTranslation: Boolean)

    fun onDisplayRomaChanged(displayRoma: Boolean)
}