/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.central.provider

import android.os.IBinder
import android.util.Log
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo

/**
 * 远程 Provider 的本地表示。
 *
 * 该类用于封装一个远程播放器提供者的 Binder 连接、生命周期状态，
 * 并在 Provider 失效或销毁时完成相关资源清理与状态同步。
 */
internal class RemoteProvider(
    private var binder: IProviderBinder? = null,
    val providerInfo: ProviderInfo
) {

    /**
     * 与该 Provider 关联的服务实例。
     *
     * 在 Provider 生命周期内保持有效，销毁后置空。
     */
    var service: RemoteProviderService? = RemoteProviderService(this)
        private set

    /** 当前绑定的 Binder 死亡回调 */
    private var deathRecipient: IBinder.DeathRecipient? = null

    /** 标记当前 Provider 是否已被销毁 */
    private var isDestroyed = false

    /**
     * 设置 Binder 的死亡回调。
     *
     * 在设置新的回调前，会先解除旧的回调绑定，
     * 以避免重复注册或资源泄漏。
     *
     * @param newDeathRecipient 新的死亡回调，可为空
     */
    fun setDeathRecipient(newDeathRecipient: IBinder.DeathRecipient?) {
        deathRecipient?.runCatching {
            binder?.asBinder()?.unlinkToDeath(this, 0)
        }?.onFailure {
            Log.e(TAG, "unlink to Death failed", it)
        }

        newDeathRecipient?.runCatching {
            binder?.asBinder()?.linkToDeath(this, 0)
        }?.onFailure {
            Log.e(TAG, "link to Death failed", it)
        }

        deathRecipient = newDeathRecipient
    }

    /**
     * 主动销毁当前 Provider。
     *
     * 实际的清理逻辑由 [ProviderManager] 统一触发。
     */
    fun destroy() {
        ProviderManager.unregister(this)
    }

    /**
     * Provider 注销后的回调。
     *
     * 负责释放服务实例、解除 Binder 绑定、
     * 更新内部状态，并通知活跃播放器调度器
     * 当前 Provider 已失效。
     */
    fun onDestroy() {
        service?.destroy()
        service = null

        setDeathRecipient(null)
        binder = null
        isDestroyed = true

        ActivePlayerDispatcher.notifyProviderInvalid(providerInfo)
    }

    /**
     * 基于 [ProviderInfo] 判断 Provider 等价性。
     */
    override fun equals(other: Any?) =
        (this === other) || (other is RemoteProvider && providerInfo == other.providerInfo)

    override fun hashCode() = providerInfo.hashCode()

    override fun toString() = "RemoteProvider{$providerInfo}"

    companion object {
        private const val TAG = "Provider"
    }
}