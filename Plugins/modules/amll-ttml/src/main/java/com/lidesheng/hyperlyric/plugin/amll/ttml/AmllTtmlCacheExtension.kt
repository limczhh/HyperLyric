package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginCacheEntry
import com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension

/**
 * AMLL TTML 缓存管理扩展（id 与 manifest cacheScopes 声明的 "ttml" 一致）
 *
 * 宿主 App 经此入口列出/清理插件缓存：插件负责物理 key 的映射、索引与
 * 序列化；仅返回展示元数据（title/artist/size/updatedAt），缓存正文
 * 不跨边界。清理在宿主侧先取消进行中的处理器，TtmlCache 的 generation
 * 机制再兜底丢弃过期代次的写回。
 */
internal class AmllTtmlCacheExtension(
    private val cache: TtmlCache,
) : PluginCacheExtension {
    override val id: String = "ttml"

    override fun listEntries(): List<PluginCacheEntry> = cache.listEntries()

    override fun clearAll() {
        cache.clearAll()
    }

    override fun clearEntry(entryId: String): Boolean = cache.clearEntry(entryId)
}
