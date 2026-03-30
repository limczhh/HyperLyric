/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
@file:Suppress("unused")

package io.github.proify.lyricon.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.view.View
import io.github.proify.lyricon.common.util.ResourceMapper.NO_ID
import io.github.proify.lyricon.common.util.ResourceMapper.getIdName
import io.github.proify.lyricon.common.util.ResourceMapper.idCache
import io.github.proify.lyricon.common.util.ResourceMapper.nameCache
import java.util.concurrent.ConcurrentHashMap

/**
 * 资源ID和名称映射工具类
 *
 * 提供视图ID和资源名称之间的双向映射功能，包含缓存机制以提高性能。
 * 该类采用单例设计模式，全局维护两个缓存：
 * - [nameCache]: ID到名称的映射缓存
 * - [idCache]: 名称到ID的映射缓存
 *
 * @see View.NO_ID
 */
object ResourceMapper {
    /**
     * 无效ID常量，等同于[View.NO_ID]
     */
    const val NO_ID: Int = View.NO_ID

    private val nameCache = ConcurrentHashMap<Int, String>()
    private val idCache = ConcurrentHashMap<String, Int>()

    /**
     * 根据资源名称获取对应的视图ID
     *
     * 优先从缓存中查找，如果缓存未命中则通过[Resources.getIdentifier]查找，
     * 找到后将结果缓存以提升后续访问性能。
     *
     * @param context 上下文对象，用于获取资源和包名信息
     * @param name 要查找的资源名称
     * @return 对应的视图ID，如果未找到则返回[NO_ID]
     *
     * @throws IllegalArgumentException 如果[name]为空或空白字符串
     *
     * @sample
     * ```
     * val viewId = ResourceMapper.getIdByName(context, "button_submit")
     * if (viewId != ResourceMapper.NO_ID) {
     *     val button = view.findViewById<Button>(viewId)
     *     // 处理按钮逻辑
     * }
     * ```
     */
    @SuppressLint("DiscouragedApi")
    fun getIdByName(
        context: Context,
        name: String,
    ): Int {
        require(name.isNotBlank()) { "资源名称不能为空或空白" }

        return idCache[name] ?: run {
            val id = runCatching {
                context.resources.getIdentifier(name, "id", context.packageName)
            }.getOrDefault(NO_ID)

            if (id != NO_ID) {
                idCache[name] = id
                nameCache[id] = name
            } else {
                idCache[name] = NO_ID
            }
            id
        }
    }

    /**
     * 根据视图对象获取其资源名称
     *
     * 这是[getIdName(Int, Resources)]的便捷方法，自动从视图对象中获取ID和资源。
     *
     * @param view 要获取资源名称的视图对象
     * @param resources 资源对象，默认为视图的资源对象
     * @return 视图的资源名称，如果ID为[NO_ID]或获取失败则返回null
     *
     * @see getIdName(Int, Resources)
     */
    fun getIdName(
        view: View,
        resources: Resources = view.resources,
    ): String? = getIdName(view.id, resources)

    /**
     * 根据资源ID获取对应的资源名称
     *
     * 优先从缓存中查找，如果缓存未命中则通过[Resources.getResourceEntryName]查找，
     * 找到后将结果缓存以提升后续访问性能。
     *
     * @param id 要查找的资源ID
     * @param resources 用于查找资源名称的资源对象
     * @return 对应的资源名称，如果ID为[NO_ID]或获取失败则返回null
     *
     * @throws IllegalArgumentException 如果[resources]为null
     *
     * ```
     * val resourceName = ResourceMapper.getIdName(R.id.button_submit, resources)
     * // resourceName 可能为 "button_submit"
     * ```
     */
    fun getIdName(
        id: Int,
        resources: Resources,
    ): String? {
        if (id == NO_ID) return null

        return nameCache[id] ?: run {
            val name = runCatching {
                resources.getResourceEntryName(id)
            }.getOrDefault("")

            nameCache[id] = name
            if (name.isNotEmpty()) {
                idCache[name] = id
            }
            name.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * 清除所有缓存
     *
     * 当应用主题变化或需要释放内存时调用此方法。
     */
    fun clearCache() {
        nameCache.clear()
        idCache.clear()
    }

    /**
     * 清除指定资源的缓存
     *
     * @param name 要清除的资源名称
     * @return 如果成功清除返回true，如果资源不存在于缓存中返回false
     */
    fun removeFromCache(name: String): Boolean {
        val id = idCache[name]
        id?.let { nameCache.remove(it) }
        return idCache.remove(name) != null
    }

    /**
     * 清除指定ID的缓存
     *
     * @param id 要清除的资源ID
     * @return 如果成功清除返回true，如果ID不存在于缓存中返回false
     */
    fun removeFromCache(id: Int): Boolean {
        val name = nameCache[id]
        name?.let { idCache.remove(it) }
        return nameCache.remove(id) != null
    }

    /**
     * 获取当前缓存大小统计
     *
     * @return 包含名称缓存和ID缓存大小的Pair对象
     */
    fun getCacheStats(): Pair<Int, Int> = Pair(nameCache.size, idCache.size)
}