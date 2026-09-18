package com.lidesheng.hyperlyric.root.lyricenhancement

import android.app.Application
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheCommand
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.common.LyricEnhancementConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.lyricenhancement.amll.AmllTtmlFeature
import com.lidesheng.hyperlyric.root.lyricenhancement.translation.AiTranslationFeature
import com.lidesheng.hyperlyric.root.lyricenhancement.translation.TranslationApplicator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class LyricEnhancementCoordinator(
    private val module: XposedModule,
    private val application: Application,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enhancementExecutor: ExecutorService =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "HyperLyric-LyricEnhancement").apply { isDaemon = true }
        }
    private val generation = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    private var activeJob: Job? = null
    private val remotePreferences: SharedPreferences? = runCatching {
        module.getRemotePreferences(UIConstants.PREF_NAME)
    }.onFailure {
        HookLogger.w(LOG_TAG, "歌词增强配置初始化失败", it)
    }.getOrNull()
    private val cacheRemotePreferences: SharedPreferences? = runCatching {
        module.getRemotePreferences(LyricEnhancementCacheCommand.REMOTE_PREFERENCES)
    }.onFailure {
        HookLogger.w(LOG_TAG, "歌词增强缓存控制初始化失败", it)
    }.getOrNull()

    private val amllFeature: AmllTtmlFeature?
    private val aiTranslationFeature: AiTranslationFeature?
    private val cacheCommandListener: LyricEnhancementCacheCommandListener

    @Volatile
    private var configChangedListener: (() -> Unit)? = null

    init {
        amllFeature = createAmllFeature()
        aiTranslationFeature = createAiTranslationFeature()
        cacheCommandListener = LyricEnhancementCacheCommandListener(
            application = application,
            remotePreferences = cacheRemotePreferences,
            scope = scope,
            isClosed = closed::get,
            listFeatureCache = ::listFeatureCache,
            clearFeatureCache = ::clearFeatureCache,
            clearCacheEntry = ::clearCacheEntry,
        )
        HookLogger.i(
            LOG_TAG,
            "歌词增强初始化完成: " +
                    "amll=${amllFeature != null}, translation=${aiTranslationFeature != null}"
        )
    }

    fun setConfigChangedListener(listener: (() -> Unit)?) {
        configChangedListener = listener
    }

    fun isEnabled(): Boolean =
        !closed.get() &&
                (amllFeature?.let { isFeatureEnabled { it.isEnabled() } } == true ||
                        aiTranslationFeature?.let { isFeatureEnabled { it.isEnabled() } } == true)

    fun enhance(
        song: Song,
        input: LyricEnhancementInput,
        onResult: (Song?) -> Unit
    ) {
        if (closed.get()) return
        val currentGeneration = generation.incrementAndGet()
        activeJob?.cancel()

        activeJob = scope.launch {
            val sourceSong = song.deepCopy()
            var result: Song? = null

            if (!isActive || currentGeneration != generation.get()) return@launch
            if (amllFeature?.let { isFeatureEnabled { it.isEnabled() } } == true) {
                result = runFeature("AmllTtml") {
                    amllFeature.enhance(sourceSong.deepCopy(), input)
                }?.let { candidate ->
                    acceptLyricsOnly(sourceSong, candidate, "AmllTtml")
                }?.let { enhancedSong ->
                    TranslationApplicator.merge(
                        sourceSong = sourceSong,
                        targetSong = enhancedSong,
                        forceOverride = false,
                    ) ?: enhancedSong
                }
            }

            if (!isActive || currentGeneration != generation.get()) return@launch
            if (aiTranslationFeature?.let { isFeatureEnabled { it.isEnabled() } } == true
            ) {
                val translated = runFeature("AiTranslation") {
                    aiTranslationFeature.enhance(
                        song = sourceSong.deepCopy(),
                        input = input,
                        enhancedSong = result?.deepCopy()
                    )
                }?.let { candidate ->
                    acceptLyricsOnly(sourceSong, candidate, "AiTranslation")
                }
                if (translated != null) result = translated
            }

            if (!isActive || currentGeneration != generation.get()) return@launch
            notifyResult(
                onResult = onResult,
                result = result?.takeIf { it.lyrics != song.lyrics }
            )
        }
    }

    fun cancelActiveProcessing() {
        generation.incrementAndGet()
        activeJob?.cancel()
        activeJob = null
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cacheCommandListener.close()
        cancelActiveProcessing()
        amllFeature?.close()
        aiTranslationFeature?.close()
        enhancementExecutor.shutdownNow()
        scope.cancel()
        configChangedListener = null
    }

    private fun clearFeatureCache(featureId: String): Boolean {
        if (closed.get()) return false
        return when (featureId) {
            LyricEnhancementConstants.AMLL_TTML_FEATURE_ID -> {
                amllFeature?.clearCache() ?: false
            }

            LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID -> {
                aiTranslationFeature?.clearCache() ?: false
            }

            else -> false
        }
    }

    private fun listFeatureCache(featureId: String): List<LyricEnhancementCacheEntry>? {
        if (closed.get()) return null
        return when (featureId) {
            LyricEnhancementConstants.AMLL_TTML_FEATURE_ID -> amllFeature?.listCacheEntries()
            LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID ->
                aiTranslationFeature?.listCacheEntries()

            else -> null
        }
    }

    private fun clearCacheEntry(featureId: String, entryId: String): Boolean {
        if (closed.get()) return false
        return when (featureId) {
            LyricEnhancementConstants.AMLL_TTML_FEATURE_ID ->
                amllFeature?.clearCacheEntry(entryId) ?: false

            LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID ->
                aiTranslationFeature?.clearCacheEntry(entryId) ?: false

            else -> false
        }
    }

    private fun createAmllFeature(): AmllTtmlFeature? {
        val preferences = remotePreferences ?: return null
        return runCatching {
            AmllTtmlFeature(
                preferences = preferences,
                cacheStore = createCache(
                    featureId = LyricEnhancementConstants.AMLL_TTML_FEATURE_ID,
                    logTag = "$LOG_TAG/AmllTtml/Cache"
                ),
                onConfigChanged = ::notifyConfigChanged
            )
        }.onFailure {
            HookLogger.w("$LOG_TAG/AmllTtml", "AMLL 歌词增强初始化失败", it)
        }.getOrNull()
    }

    private fun createAiTranslationFeature(): AiTranslationFeature? {
        val preferences = remotePreferences ?: return null
        return runCatching {
            AiTranslationFeature(
                preferences = preferences,
                cacheStore = createCache(
                    featureId = LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID,
                    logTag = "$LOG_TAG/AiTranslation/Cache"
                ),
                onConfigChanged = ::notifyConfigChanged
            )
        }.onFailure {
            HookLogger.w("$LOG_TAG/AiTranslation", "AI 翻译初始化失败", it)
        }.getOrNull()
    }

    private fun createCache(
        featureId: String,
        logTag: String,
    ): LyricEnhancementCacheStore = FileLyricEnhancementCache(
        directory = FileLyricEnhancementCache.directory(application, featureId),
        logTag = logTag
    )

    private fun isFeatureEnabled(block: () -> Boolean): Boolean =
        runCatching(block).getOrDefault(false)

    private fun notifyConfigChanged() {
        if (!closed.get()) configChangedListener?.invoke()
    }

    private suspend fun runFeature(
        featureName: String,
        operation: () -> Song?,
    ): Song? = LyricEnhancementExecution.run(
        executor = enhancementExecutor,
        operation = operation,
        timeoutMs = MAX_FEATURE_TIMEOUT_MS,
        onFailure = { error ->
            HookLogger.w(LOG_TAG, "歌词增强功能执行失败: feature=${featureName}", error)
        },
        onTimeout = {
            HookLogger.w(
                LOG_TAG,
                "歌词增强功能执行超时: feature=${featureName}, " +
                        "timeoutMs=${MAX_FEATURE_TIMEOUT_MS}"
            )
        }
    )

    private fun acceptLyricsOnly(
        baseSong: Song,
        candidate: Song,
        featureName: String,
    ): Song? {
        val onlyLyricsChanged = candidate.id == baseSong.id &&
                candidate.name == baseSong.name &&
                candidate.artist == baseSong.artist &&
                candidate.album == baseSong.album &&
                candidate.duration == baseSong.duration &&
                candidate.metadata == baseSong.metadata
        if (!onlyLyricsChanged) {
            HookLogger.w(LOG_TAG, "歌词增强功能修改了歌曲信息，拒绝写回: feature=${featureName}")
            return null
        }
        return candidate
    }

    private fun notifyResult(
        onResult: (Song?) -> Unit,
        result: Song?,
    ) {
        runCatching { onResult(result) }.onFailure {
            HookLogger.w(LOG_TAG, "歌词增强结果回调失败", it)
        }
    }

    private companion object {
        const val LOG_TAG = "LyricEnhancement"
        const val MAX_FEATURE_TIMEOUT_MS = 40_000L
    }
}
