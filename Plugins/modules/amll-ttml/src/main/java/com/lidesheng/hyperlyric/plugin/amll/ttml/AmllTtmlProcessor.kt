package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginContext
import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult
import com.lidesheng.hyperlyric.plugin.api.PluginWord

/** 命中结果：TTML 原文 + 来源标记（缓存命中/网络获取），用于日志准确上报 */
internal data class TtmlFetch(
    val ttml: String,
    val fromCache: Boolean,
)

/**
 * AMLL TTML 歌词处理器（LYRIC_REPLACEMENT 阶段）
 *
 * 主流程（spec §3.2）：配置检查 → 平台探测（songId 非空时：包名直查 >
 * 已解析平台快路径 > 逐平台探测）→ 搜索回退（title/artist 模糊匹配）
 * → 全部未命中返回 null（宿主保留原始歌词）。
 *
 * 查询参数优先级（对齐 main 分支 fetchTtmlInternal）：
 * title = mediaInfo.title ?: song.name；artist = mediaInfo.artist ?: song.artist；
 * album = mediaInfo.album；songId = song.id（歌词源的歌曲 ID）；
 * sourcePackageName = mediaInfo.sourcePackageName（宿主仅透传歌词源提供的包名，
 * 不做任何推断，spec §7.1 落地为平台单次直查）。
 *
 * 命中时以 REPLACE 模式完整替换歌词（仅 LYRICS 字段，name/artist 等保留主歌词源值）；
 * 处理器在 34s 总预算内同步完成（宿主硬超时 40s），每步网络前检查预算与线程中断。
 */
internal class AmllTtmlProcessor(
    private val context: PluginContext,
) : LyricProcessorExtension {

    override val id: String = "amll.ttml"
    override val stage = PluginProcessorStage.LYRIC_REPLACEMENT

    private val logger = context.logger.withTag("AmllTtmlProcessor")
    private val client = AmllTtmlClient(context.logger.withTag("AmllTtmlClient"))
    private val parser = TtmlParser(context.logger.withTag("AmllTtmlParser"))
    private val cache = TtmlCache(context.cache, context.logger.withTag("TtmlCache"))
    private val cacheExtension = AmllTtmlCacheExtension(cache)

    /** 缓存管理扩展（onLoad 时注册给宿主，id 与 manifest cacheScopes 的 "ttml" 一致） */
    fun cacheExtension(): AmllTtmlCacheExtension = cacheExtension

    override fun processResult(
        song: PluginSong,
        processingContext: PluginProcessingContext
    ): PluginSongResult? {
        return try {
            processSongInternal(song, processingContext)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.debug("处理被中断")
            null
        } catch (error: Exception) {
            logger.warn(
                "处理异常: type=${error.javaClass.simpleName}, " +
                        "msg=${error.message?.take(200)}",
                null
            )
            null
        }
    }

    private fun processSongInternal(
        song: PluginSong,
        processingContext: PluginProcessingContext
    ): PluginSongResult? {
        val config = AmllTtmlConfig.from(context.config)
        if (!config.enabled) {
            logger.debug("跳过处理: 插件已禁用, song=${song.name}")
            return null
        }

        val budget = ProcessingBudget(BUDGET_MS)
        val mediaInfo = processingContext.mediaInfo

        // 查询参数优先级：Core 组装确认过的 mediaInfo 优先，缺失回落歌词源 Song 字段
        val title = mediaInfo?.title?.takeIf { it.isNotBlank() }
            ?: song.name?.takeIf { it.isNotBlank() }
        val artist = mediaInfo?.artist?.takeIf { it.isNotBlank() }
            ?: song.artist?.takeIf { it.isNotBlank() }
        val album = mediaInfo?.album?.takeIf { it.isNotBlank() }
        val songId = song.id?.takeIf { it.isNotBlank() }
        // 歌词源包名：宿主仅透传本次歌词源提供的 packageName（无 MediaSession 推断），
        // 可信度高，映射到平台后单次精确直查（spec §7.1 宿主建议已落地）
        val sourcePackageName = mediaInfo?.sourcePackageName?.takeIf { it.isNotBlank() }

        logger.debug(
            "开始处理: song=\"${song.name.orEmpty()}\", songId=${songId ?: "null"}, " +
                    "package=${sourcePackageName ?: "null"}, " +
                    "title=${title ?: "null"}, artist=${artist ?: "null"}, album=${album ?: "null"}"
        )

        // 1. 平台探测：songId 非空、开关开启；包名直查无需 title/artist（平台已明确），
        //    逐平台探测需至少一个用于交叉校验（无法验证即不冒险——spec §3.3）
        if (songId != null && config.platformProbe &&
            (sourcePackageName != null || title != null || artist != null)
        ) {
            val probeTtml = probePlatforms(songId, title, artist, sourcePackageName, budget)
            if (probeTtml != null) {
                return buildResult(song, probeTtml.ttml, probeTtml.fromCache)
            }
        }

        // 2. 搜索回退：title 或 artist 至少一个可用（对齐 main：二者皆空不触发搜索）
        if (title == null && artist == null) {
            logger.debug("搜索未触发: 无标题与歌手")
            return null
        }
        val searchTtml = searchFallback(title, artist, album, budget) ?: return null
        return buildResult(song, searchTtml.ttml, searchTtml.fromCache)
    }

    /**
     * 平台探测，平台解析优先级（spec §3.3 + §7.1 落地）：
     * 1. 包名直查：sourcePackageName 由歌词源明确提供（宿主不做任何推断），
     *    映射到平台后单次精确查询，命中无需交叉校验（对齐 main 分支直查行为）；
     * 2. resolve 缓存快路径：上次探测已解析出平台（写入前已通过校验），仅查该平台；
     * 3. ID 格式预判逐平台探测：每平台命中须通过 title/artist 交叉校验
     *    （防跨平台 ID 撞号误匹配）；title/artist 皆空则不探测（无法验证即不冒险）。
     * 探测命中后缓存 exact 与 resolve 两个条目，二次播放零网络命中。
     */
    private fun probePlatforms(
        songId: String,
        title: String?,
        artist: String?,
        sourcePackageName: String?,
        budget: ProcessingBudget
    ): TtmlFetch? {
        // 包名直查：歌词源已明确平台（无撞号风险，跳过交叉校验与 resolve 快路径）
        val mappedPlatform = AmllPlatformId.mapPackageName(sourcePackageName)
        // 快路径：上次探测已解析出平台（写入前已通过校验，同样无需再校验）
        val resolvedPlatform = if (mappedPlatform == null) {
            cache.getResolve(songId)
                ?.let { cached -> AmllPlatformIdField.entries.firstOrNull { it.name == cached } }
        } else {
            null
        }
        val probeOrder = when {
            mappedPlatform != null -> listOf(mappedPlatform)
            resolvedPlatform != null -> listOf(resolvedPlatform)
            else -> if (title == null && artist == null) {
                logger.debug("平台探测未触发: 无标题与歌手")
                return null
            } else {
                AmllPlatformId.probeOrderFor(songId)
            }
        }
        // 仅逐平台探测路径需要交叉校验（包名/resolve 已确定平台）
        val requireVerification = mappedPlatform == null && resolvedPlatform == null

        val generation = cache.currentGeneration()
        for (platform in probeOrder) {
            if (Thread.currentThread().isInterrupted) {
                logger.debug("处理被中断")
                return null
            }
            if (budget.isExhausted()) {
                logger.debug("预算耗尽: phase=平台探测")
                return null
            }

            // 平台内精确缓存命中：零网络返回
            val exactKey = TtmlCache.exactKey(platform, songId)
            cache.get(exactKey)?.let { lookup ->
                logger.debug(
                    "缓存命中: key=${TtmlCache.shortKey(exactKey)}, " +
                            "size=${lookup.ttml.toByteArray().size}B"
                )
                return TtmlFetch(lookup.ttml, fromCache = true)
            }
            logger.debug("缓存未命中: key=${TtmlCache.shortKey(exactKey)}")

            val item = client.fetchByPlatformId(platform, songId, budget)
            if (item == null) {
                logger.debug("平台探测未命中: platform=${platform.name}")
                continue
            }
            val ttml = item.lyrics
            if (ttml.isNullOrBlank()) {
                logger.debug("平台探测歌词为空: platform=${platform.name}")
                continue
            }
            if (requireVerification && !AmllMatch.isPlausibleMatch(item, title, artist)) {
                // 跨平台 ID 撞号：条目与请求 title/artist 不匹配，拒绝并继续下一平台
                logger.debug("平台探测校验拒绝: platform=${platform.name}")
                continue
            }
            logger.debug(
                "平台探测命中: platform=${platform.name}, " +
                        "id=${item.id}, size=${ttml.toByteArray().size}B"
            )
            cache.put(exactKey, ttml, title, artist, generation)
            cache.putResolve(songId, platform.name)
            return TtmlFetch(ttml, fromCache = false)
        }
        return null
    }

    /** 搜索回退：search 缓存 → 模糊搜索 + 交叉校验 → 按 id 取完整 TTML → 缓存 */
    private fun searchFallback(
        title: String?,
        artist: String?,
        album: String?,
        budget: ProcessingBudget
    ): TtmlFetch? {
        val searchKey = TtmlCache.searchKey(title.orEmpty(), artist.orEmpty())
        cache.get(searchKey)?.let { lookup ->
            logger.debug(
                "缓存命中: key=${TtmlCache.shortKey(searchKey)}, " +
                        "size=${lookup.ttml.toByteArray().size}B"
            )
            return TtmlFetch(lookup.ttml, fromCache = true)
        }
        logger.debug("缓存未命中: key=${TtmlCache.shortKey(searchKey)}")

        if (Thread.currentThread().isInterrupted) {
            logger.debug("处理被中断")
            return null
        }
        if (budget.isExhausted()) {
            logger.debug("预算耗尽: phase=搜索")
            return null
        }

        val generation = cache.currentGeneration()
        val searchItem = client.searchByMetadata(title, artist, album, budget) ?: return null
        logger.debug(
            "搜索命中: id=${searchItem.id}, " +
                    "music=${searchItem.musicNames?.joinToString("/")}, " +
                    "artist=${searchItem.artistNames?.joinToString("/")}"
        )
        val fullItem = searchItem.id?.let { client.fetchById(it, budget) } ?: return null
        val ttml = fullItem.lyrics
        if (ttml.isNullOrBlank()) return null
        logger.debug("搜索回退取回歌词: size=${ttml.toByteArray().size}B")
        // 展示元数据优先用 AMLL 条目自身的歌名/歌手（与缓存正文内容一致）
        cache.put(
            searchKey, ttml,
            title = fullItem.musicNames?.firstOrNull() ?: title,
            artist = fullItem.artistNames?.joinToString(" / ") ?: artist,
            expectedGeneration = generation
        )
        return TtmlFetch(ttml, fromCache = false)
    }

    /**
     * 解析 TTML 并构造 REPLACE 结果：仅替换 lyrics，name/artist 等保留主歌词源值
     * （对齐 main 分支 buildSong 语义）。解析失败/无有效行/终检不通过均返回 null
     * （视为未命中回落原歌词，防止空歌词或非法歌词替换掉原本可用的平台歌词）。
     */
    private fun buildResult(song: PluginSong, ttml: String, fromCache: Boolean): PluginSongResult? {
        val lines = parser.parse(ttml)
        if (lines == null) {
            logger.debug("解析失败: fromCache=$fromCache")
            return null
        }
        // 终检（宿主 REPLACE 校验等价）：解析器已规整，此处防御性复核并留下明确日志
        if (!LyricsValidator.isValidLyrics(lines)) {
            logger.warn("终检失败，放弃替换: lines=${lines.size}", null)
            return null
        }
        logger.debug("替换歌词: lines=${lines.size}, fromCache=$fromCache")
        return PluginSongResult(
            song = song.copy(lyrics = lines),
            changedFields = setOf(PluginSongField.LYRICS),
            lyricsUpdateMode = PluginLyricsUpdateMode.REPLACE,
            changedLyricFields = emptySet()
        )
    }

    private companion object {
        /** 处理总预算：低于宿主 40s 处理器硬超时，保证结果在硬超时前产出 */
        const val BUDGET_MS = 34_000L
    }
}

/**
 * 宿主 REPLACE 校验的等价实现（PluginSongMapper.hasValidLyrics/hasValidWords），
 * 用于处理器返回前的终检。规则与宿主逐条对齐：
 * - 行：begin >= 0、end > begin、duration == end - begin、按 begin 非递减、text 或 words 非空；
 * - 词：begin >= 行 begin、end <= 行 end、end > begin、duration == end - begin、
 *   按上一词 end 非回退；
 * - 规模：行 ≤ 20000、单行词 ≤ 2000（三个词列表分别计）、总词 ≤ 100000。
 */
internal object LyricsValidator {
    private const val MAX_LYRICS = 20_000
    private const val MAX_WORDS_PER_LINE = 2_000
    private const val MAX_TOTAL_WORDS = 100_000

    fun isValidLyrics(lines: List<PluginLyricLine>): Boolean {
        if (lines.isEmpty() || lines.size > MAX_LYRICS) return false
        var previousBegin = Long.MIN_VALUE
        var totalWords = 0
        return lines.all { line ->
            val lineValid = line.begin >= 0L &&
                    line.end > line.begin &&
                    line.duration == line.end - line.begin &&
                    line.begin >= previousBegin &&
                    (!line.text.isNullOrBlank() || !line.words.isNullOrEmpty())
            if (!lineValid) return@all false
            previousBegin = line.begin

            val wordLists = listOf(line.words, line.secondaryWords, line.translationWords)
            if (wordLists.any { it != null && it.size > MAX_WORDS_PER_LINE }) return@all false
            totalWords += wordLists.sumOf { it?.size ?: 0 }
            if (totalWords > MAX_TOTAL_WORDS) return@all false
            wordLists.all { words -> hasValidWords(line, words) }
        }
    }

    private fun hasValidWords(line: PluginLyricLine, words: List<PluginWord>?): Boolean {
        if (words == null) return true
        var previousEnd = line.begin
        return words.all { word ->
            val valid = word.begin >= line.begin &&
                    word.end > word.begin &&
                    word.end <= line.end &&
                    word.duration == word.end - word.begin &&
                    word.begin >= previousEnd
            if (valid) previousEnd = word.end
            valid
        }
    }
}
