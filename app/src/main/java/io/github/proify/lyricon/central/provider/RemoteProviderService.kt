/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider

import io.github.proify.lyricon.central.provider.player.RemotePlayer
import io.github.proify.lyricon.provider.IRemoteService

/**
 * 远程 Provider Service 的本地实现。
 *
 * 该类作为 AIDL Service Stub，向远程进程暴露播放器相关能力，
 * 并在 Provider 生命周期结束时负责释放关联资源。
 */
internal class RemoteProviderService(
    var provider: RemoteProvider?
) : IRemoteService.Stub() {

    /**
     * 与当前 Provider 关联的远程播放器实例。
     *
     * 在 Service 存活期间保持有效，
     * Provider 销毁后会被显式释放。
     */
    private var remotePlayer: RemotePlayer? =
        provider?.let { RemotePlayer(it.providerInfo) }

    /**
     * 获取远程播放器实例。
     *
     * @return 当前可用的 [RemotePlayer]，若已销毁则返回 null
     */
    override fun getPlayer(): RemotePlayer? = remotePlayer

    /**
     * 销毁 Service 及其关联资源。
     *
     * 用于 Provider 生命周期结束时，
     * 主动释放播放器并断开引用关系。
     */
    fun destroy() {
        remotePlayer?.destroy()
        remotePlayer = null
        provider = null
    }

    /**
     * 远程进程主动请求断开连接。
     *
     * 该调用会触发 Provider 的统一销毁流程。
     */
    override fun disconnect() {
        provider?.destroy()
    }
}