package com.lidesheng.hyperlyric.root.source

import android.app.Application
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.renderer.BaseIslandRenderer
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.subscriber.ActivePlayerListener
import io.github.proify.lyricon.subscriber.ConnectionListener
import io.github.proify.lyricon.subscriber.LyriconFactory
import io.github.proify.lyricon.subscriber.LyriconSubscriber
import io.github.proify.lyricon.subscriber.ProviderInfo
import kotlinx.serialization.json.Json

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

    @Volatile
    private var subscriber: LyriconSubscriber? = null

    private var activeProviderPackageName: String? = null
    private var activePlayerPackageName: String? = null
    private var activeProviderDelayMs: Int = RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
    private var prefs: android.content.SharedPreferences? = null


    override fun isAvailable(): Boolean = true

    override fun start(sink: LyricSink) {
        if (this.subscriber != null) {
            HookLogger.d(TAG, "跳过重复启动: reason=already_running")
            return
        }
        this.sink = sink
        val application = app ?: run {
            HookLogger.w(TAG, "数据源启动延后: reason=application_unavailable")
            return
        }
        initializeSubscriber(application)
        HookLogger.d(TAG, "数据源已启动")
    }

    override fun stop() {
        try {
            subscriber?.unsubscribeActivePlayer(activePlayerListener)
            subscriber?.unregister()
            subscriber?.destroy()
        } catch (e: Exception) {
            HookLogger.e(TAG, "清理歌词订阅连接失败", e)
        } finally {
            subscriber = null
            activeProviderPackageName = null
            activePlayerPackageName = null
            sink?.onStop()
            sink = null
        }
        HookLogger.d(TAG, "数据源已停止")
    }

    fun initialize(app: Application, prefs: android.content.SharedPreferences?) {
        this.app = app
        this.prefs = prefs
    }

    fun onPreferenceChanged(key: String?) {
        val packageName = activeProviderPackageName ?: return
        if (key == providerDelayKey(packageName)) {
            activeProviderDelayMs = readProviderDelay(packageName)
        }
    }

    private fun providerDelayKey(packageName: String): String {
        return RootConstants.KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX + packageName
    }

    private fun readProviderDelay(packageName: String): Int {
        return prefs?.getInt(
            providerDelayKey(packageName),
            RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
        )?.coerceIn(
            RootConstants.MIN_HOOK_LYRICON_PROVIDER_DELAY,
            RootConstants.MAX_HOOK_LYRICON_PROVIDER_DELAY
        ) ?: RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
    }


    private fun initializeSubscriber(app: Application) {
        val sub = LyriconFactory.createSubscriber(app)
        subscriber = sub

        sub.addConnectionListener(connectionListener)
        sub.subscribeActivePlayer(activePlayerListener)
        sub.register()
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            HookLogger.d(TAG, "订阅连接已建立")
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            HookLogger.d(TAG, "订阅连接已恢复")
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            HookLogger.w(TAG, "订阅连接已断开")
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            HookLogger.w(TAG, "订阅连接超时")
        }
    }

    private val activePlayerListener = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            sink?.onStop()
            activeProviderPackageName = providerInfo?.providerPackageName
            activePlayerPackageName = providerInfo?.playerPackageName
            activeProviderDelayMs = providerInfo?.providerPackageName
                ?.let(::readProviderDelay)
                ?: RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY

            // Lyricon providers may publish plain text without an accompanying Song. Publish the
            // player identity at the source-session boundary so the first text event can still
            // pass the package-aware island policy without inferring ownership from MediaSession.
            activePlayerPackageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                sink?.onMetadata(
                    LyricMediaMetadata(
                        sourceId = id,
                        packageName = packageName
                    )
                )
            }
        }


        override fun onSongChanged(song: Song?) {
            val localSong = song?.toLocalSong()
            sink?.onSongChanged(localSong)
            sink?.onMetadata(
                localSong?.let {
                    LyricMediaMetadata(
                        sourceId = id,
                        packageName = activePlayerPackageName,
                        songId = it.id,
                        title = it.name,
                        artist = it.artist,
                        album = it.album,
                        duration = it.duration.takeIf { duration -> duration > 0L }
                    )
                }
            )
            BaseIslandRenderer.refreshActiveIsland()
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            sink?.onPlaybackStateChanged(isPlaying)
        }

        override fun onPositionChanged(position: Long) {
            val adjustedPosition = (position - activeProviderDelayMs).coerceAtLeast(0L)
            sink?.onPositionChanged(adjustedPosition)
        }


        override fun onSeekTo(position: Long) {}

        override fun onReceiveText(text: String?) {
            sink?.onPlainText(text)
        }

        // 提供器只负责提供歌词内容；翻译和罗马音是否显示由 HyperLyric 显示端配置决定。
        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }
}
