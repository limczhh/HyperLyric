package com.lidesheng.hyperlyric.root.source

import android.app.Application
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.HookIslandLyric
import com.lidesheng.hyperlyric.root.HookIslandSpaceGateLyric
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.aitrans.AITranslator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class LyriconSource : LyricSource {

    companion object {
        private const val TAG = "LyriconSource"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }

    private fun Song.toLocalSong(): com.lidesheng.hyperlyric.lyric.model.Song {
        val jsonString = json.encodeToString(this)
        return json.decodeFromString(jsonString)
    }

    override val id = "lyricon"
    override val displayName = "Lyricon"

    @Volatile
    private var sink: LyricSink? = null
    private var app: Application? = null
    private var prefs: SharedPreferences? = null
    private var activeMode: Int = 0
    private var renderer: IslandRenderer? = null

    @Volatile
    private var subscriber: LyriconSubscriber? = null

    override fun isAvailable(): Boolean = true

    override fun start(sink: LyricSink) {
        if (this.subscriber != null) {
            HookLogger.w(TAG, "Lyricon 数据源已在运行，跳过重复启动")
            return
        }
        this.sink = sink
        val application = app ?: run {
            HookLogger.w(TAG, "Application 未初始化，无法启动")
            return
        }
        initializeSubscriber(application)
        HookLogger.i(TAG, "Lyricon 数据源已启动")
    }

    override fun stop() {
        try {
            subscriber?.unsubscribeActivePlayer(activePlayerListener)
            subscriber?.unregister()
            subscriber?.destroy()
        } catch (e: Exception) {
            HookLogger.e(TAG, "清理 Subscriber 时发生错误", e)
        } finally {
            subscriber = null
            sink?.onStop()
            sink = null
        }
        HookLogger.i(TAG, "Lyricon 数据源已停止")
    }

    fun initialize(app: Application, prefs: SharedPreferences, activeMode: Int) {
        this.app = app
        this.prefs = prefs
        this.activeMode = activeMode
        this.renderer = if (activeMode == 1) HookIslandSpaceGateLyric else HookIslandLyric

        LyriconDataBridge.onAiTranslationComplete = {
            renderer?.refreshActiveIsland()
        }
    }

    private fun getRenderer(): IslandRenderer =
        renderer ?: if (activeMode == 1) HookIslandSpaceGateLyric else HookIslandLyric

    private fun initializeSubscriber(app: Application) {
        AITranslator.init(app)

        val sub = LyriconFactory.createSubscriber(app)
        subscriber = sub

        sub.addConnectionListener(connectionListener)
        sub.subscribeActivePlayer(activePlayerListener)
        sub.register()
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            HookLogger.i(TAG, "Subscriber 已连接")
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            HookLogger.i(TAG, "Subscriber 已重连")
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            HookLogger.w(TAG, "Subscriber 已断开")
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            HookLogger.w(TAG, "Subscriber 连接超时")
        }
    }

    private val activePlayerListener = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            sink?.onStop()
            LyriconDataBridge.activePackageName = providerInfo?.playerPackageName
        }

        override fun onSongChanged(song: Song?) {
            val localSong = song?.toLocalSong()
            LyriconDataBridge.updateSong(localSong)
            sink?.onSongChanged(localSong)
            getRenderer().refreshActiveIsland()
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            LyriconDataBridge.isPlaying = isPlaying
            sink?.onPlaybackStateChanged(isPlaying)
            getRenderer().onPlaybackStateChanged(isPlaying)
        }

        override fun onPositionChanged(position: Long) {
            val lyricChanged = LyriconDataBridge.updatePosition(position)
            if (lyricChanged) {
                getRenderer().updateLyricLine()
            }
            sink?.onPositionChanged(position)
            getRenderer().updatePosition(position)
        }

        override fun onSeekTo(position: Long) {}

        override fun onReceiveText(text: String?) {
            LyriconDataBridge.updateLyric(text)
            sink?.onPlainText(text)
            getRenderer().updateLyricLine()
        }

        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
            LyriconDataBridge.isDisplayTranslation = isDisplayTranslation
            getRenderer().refreshActiveIsland()
        }

        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) {
            LyriconDataBridge.isDisplayRoma = isDisplayRoma
            getRenderer().refreshActiveIsland()
        }
    }
}
