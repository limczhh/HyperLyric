/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.lyric.model.Song

/**
 * 播放器事件监听接口。
 *
 * 该接口用于接收播放器在运行过程中产生的各类事件回调，
 * 事件均携带对应的 [PlayerRecorder]，以便区分事件来源。
 */
internal interface PlayerListener {

    /**
     * 当前播放歌曲发生变化时回调。
     *
     * @param recorder 事件来源的播放器记录器
     * @param song     当前播放的歌曲，可能为空
     */
    fun onSongChanged(recorder: PlayerRecorder, song: Song?)

    /**
     * 播放状态发生变化时回调。
     *
     * @param recorder  事件来源的播放器记录器
     * @param isPlaying 当前是否处于播放状态
     */
    fun onPlaybackStateChanged(recorder: PlayerRecorder, isPlaying: Boolean)

    /**
     * 播放进度发生变化时回调。
     *
     * @param recorder 事件来源的播放器记录器
     * @param position 当前播放位置，单位毫秒
     */
    fun onPositionChanged(recorder: PlayerRecorder, position: Long)

    /**
     * 播放器执行跳转（Seek）操作时回调。
     *
     * @param recorder 事件来源的播放器记录器
     * @param position 跳转后的目标位置，单位毫秒
     */
    fun onSeekTo(recorder: PlayerRecorder, position: Long)

    /**
     * 播放器产生文本信息时回调。
     *
     * 常用于传递提示信息、错误描述或其它附加文本内容。
     *
     * @param recorder 事件来源的播放器记录器
     * @param text     需要展示或处理的文本内容，可能为空
     */
    fun onSendText(recorder: PlayerRecorder, text: String?)

    /**
     * 播放器显示翻译状态发生变化时回调。
     *
     * @param recorder              事件来源的播放器记录器
     * @param isDisplayTranslation  当前是否显示翻译
     */
    fun onDisplayTranslationChanged(recorder: PlayerRecorder, isDisplayTranslation: Boolean)
    fun onDisplayRomaChanged(recorder: PlayerRecorder, displayRoma: Boolean)
}