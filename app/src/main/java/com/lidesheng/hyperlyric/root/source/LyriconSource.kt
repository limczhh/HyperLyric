package com.lidesheng.hyperlyric.root.source

import android.app.Application
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
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
    private var arbiter: LyricSessionArbiter? = null
    private var activePlayerListener: ActivePlayerListener? = null
    private var prefs: android.content.SharedPreferences? = null


    override fun isAvailable(): Boolean = true

    override fun start(sink: LyricSink) {
        if (this.subscriber != null) {
            return
        }
        this.sink = sink
        val application = app ?: run {
            HookLogger.w(TAG, "数据源启动延后: reason=application_unavailable")
            return
        }
        val owner = LyricSessionArbiter(application, sink, 33L)
        arbiter = owner
        owner.start()
        initializeSubscriber(application, owner)
        HookLogger.d(TAG, "数据源已启动")
    }

    override fun stop() {
        val owner = arbiter
        owner?.close()
        try {
            activePlayerListener?.let { subscriber?.unsubscribeActivePlayer(it) }
            subscriber?.unregister()
            subscriber?.destroy()
        } catch (e: Exception) {
            HookLogger.e(TAG, "清理歌词订阅连接失败", e)
        } finally {
            subscriber = null
            activeProviderPackageName = null
            activePlayerPackageName = null
            if (owner == null) sink?.onStop()
            arbiter = null
            activePlayerListener = null
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


    private fun initializeSubscriber(app: Application, owner: LyricSessionArbiter) {
        val sub = LyriconFactory.createSubscriber(app)
        subscriber = sub

        val playerListener = createPlayerListener(owner)
        activePlayerListener = playerListener
        sub.addConnectionListener(createConnectionListener(owner))
        sub.subscribeActivePlayer(playerListener)
        sub.register()
    }

    private fun createConnectionListener(owner: LyricSessionArbiter) = object : ConnectionListener {
        override fun onConnected(subscriber: LyriconSubscriber) {
            HookLogger.d(TAG, "订阅连接已建立")
        }

        override fun onReconnected(subscriber: LyriconSubscriber) {
            HookLogger.d(TAG, "订阅连接已恢复")
        }

        override fun onDisconnected(subscriber: LyriconSubscriber) {
            if (this@LyriconSource.subscriber !== subscriber) return
            HookLogger.w(TAG, "订阅连接已断开")
            owner.execute { clearActivePlayerState(owner) }
        }

        override fun onConnectTimeout(subscriber: LyriconSubscriber) {
            if (this@LyriconSource.subscriber !== subscriber) return
            HookLogger.w(TAG, "订阅连接超时")
            owner.execute { clearActivePlayerState(owner) }
        }
    }

    private fun createPlayerListener(owner: LyricSessionArbiter) = object : ActivePlayerListener {
        override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
            owner.execute {
                val previous = activePlayerPackageName
                val next = providerInfo?.playerPackageName
                if (previous != null && previous != next) owner.detachStreamingOwner(previous)
                activeProviderPackageName = providerInfo?.providerPackageName
                activePlayerPackageName = next
                activeProviderDelayMs = providerInfo?.providerPackageName?.let(::readProviderDelay)
                    ?: RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
            }
        }


        override fun onSongChanged(song: Song?) {
            val localSong = song?.toLocalSong()
            publish(owner) { packageName ->
                onSourceStateChanged(true, MediaIdentity(packageName = packageName, songId = localSong?.id))
                onSongChanged(localSong)
                onMetadata(LyricMediaMetadata(
                    sourceId = id, packageName = packageName, songId = localSong?.id,
                    title = localSong?.name, artist = localSong?.artist, album = localSong?.album,
                    duration = localSong?.duration?.takeIf { it > 0L }
                ))
            }
        }


        override fun onPlaybackStateChanged(isPlaying: Boolean) {
            publish(owner) { onPlaybackStateChanged(isPlaying) }
        }

        override fun onPositionChanged(position: Long) {
            publish(owner) { onPositionChanged(position) }
        }


        override fun onSeekTo(position: Long) {}

        override fun onReceiveText(text: String?) {
            publish(owner) { packageName ->
                onSourceStateChanged(true, MediaIdentity(packageName = packageName))
                onMetadata(LyricMediaMetadata(sourceId = id, packageName = packageName))
                onPlainText(text)
            }
        }

        // 提供器只负责提供歌词内容；翻译和罗马音是否显示由 HyperLyric 显示端配置决定。
        override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) = Unit

        override fun onDisplayRomaChanged(isDisplayRoma: Boolean) = Unit
    }

    private fun publish(owner: LyricSessionArbiter, block: LyricSink.(String) -> Unit) {
        owner.execute {
            val packageName = activePlayerPackageName?.takeIf { it.isNotBlank() } ?: return@execute
            owner.update(packageName, packageName, delayMs = activeProviderDelayMs) { block(packageName) }
        }
    }

    private fun clearActivePlayerState(owner: LyricSessionArbiter) {
        activeProviderPackageName = null
        activePlayerPackageName = null
        activeProviderDelayMs = RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY
        owner.clear()
    }
}
