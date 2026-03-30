/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider

import io.github.proify.lyricon.provider.ProviderInfo
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 远程 Provider 管理器。
 *
 * 负责统一维护当前已连接的 [RemoteProvider] 实例，
 * 并在其进程死亡或主动注销时执行对应的清理逻辑。
 */
internal object ProviderManager {

    /**
     * 当前已注册的远程 Provider 集合。
     *
     * 使用 [CopyOnWriteArraySet] 以保证在并发注册、
     * 注销及遍历场景下的线程安全。
     */
    private val providers = CopyOnWriteArraySet<RemoteProvider>()

    /**
     * 注册一个远程 Provider。
     *
     * 注册成功后，会为该 Provider 绑定死亡回调，
     * 以便在远程进程异常退出时自动执行注销流程。
     *
     * @param provider 需要注册的远程 Provider 实例
     */
    fun register(provider: RemoteProvider) {
        if (providers.add(provider)) {
            provider.setDeathRecipient { unregister(provider) }
        }
    }

    /**
     * 注销一个远程 Provider。
     *
     * 当 Provider 被移除时，会同步触发其销毁回调，
     * 用于释放内部资源。
     *
     * @param provider 需要注销的远程 Provider 实例
     */
    fun unregister(provider: RemoteProvider) {
        if (providers.remove(provider)) {
            provider.onDestroy()
        }
    }

    //fun getProviders(): Set<RemoteProvider> = providers

    fun getProvider(providerInfo: ProviderInfo): RemoteProvider? =
        providers.firstOrNull { it.providerInfo == providerInfo }
}