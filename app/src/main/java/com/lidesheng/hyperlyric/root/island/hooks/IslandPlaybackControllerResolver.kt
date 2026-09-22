package com.lidesheng.hyperlyric.root.island.hooks

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.policy.IslandModificationTargetPolicy
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Resolves the MediaController represented by the visible Super Island.
 *
 * Xiaomi exposes the island's StatusBarNotification in DynamicIslandData, so its media-session
 * token is stronger than a package-name lookup. When the notification does not expose a token,
 * a source token or a unique media-id match may still prove the controller. A package with
 * several unresolved sessions is deliberately rejected instead of controlling an arbitrary one.
    */
internal object IslandPlaybackControllerResolver {
    private const val TAG = "IslandPlaybackResolver"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    fun resolve(
        context: Context,
        data: Any?,
        hostRoot: ViewGroup? = null
    ): MediaController? {
        val target = IslandModificationTargetPolicy.resolve(data, hostRoot)
        val islandInfo = target.mediaInfo ?: return null
        if (!IslandModificationTargetPolicy.allowsCurrentScope(target)) return null

        val sourceMetadata = LyriconDataBridge.currentLyricMediaMetadata?.normalized()
        val currentScope = IslandModificationTargetPolicy.currentScope()
        if (currentScope == IslandModificationTargetPolicy.Scope.INJECTED_LYRIC &&
            sourceMetadata?.packageName?.let { it != islandInfo.packageName } == true
        ) {
            return null
        }
        val matchingSourceMetadata = sourceMetadata?.takeIf {
            it.packageName == islandInfo.packageName
        }

        val statusBarNotification = IslandProbeUtils.extractStatusBarNotification(data)
        if (statusBarNotification?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val notificationToken = IslandProbeUtils.extractMediaSessionToken(data)
        val sourceToken = matchingSourceMetadata?.sessionToken
        if (notificationToken != null && sourceToken != null && notificationToken != sourceToken) {
            return null
        }

        val sessionContext = resolveSessionContext(context) ?: return null
        val manager = runCatching {
            sessionContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        }.onFailure { error ->
            HookLogger.w(TAG, "获取 MediaSessionManager 失败", error)
        }.getOrNull() ?: return null
        val allControllers = runCatching { manager.getActiveSessions(null) }
            .onFailure { error ->
                HookLogger.w(TAG, "获取 active sessions 失败", error)
            }.getOrNull()
            ?: return null
        val controllers = allControllers.filter { it.packageName == islandInfo.packageName }
        if (controllers.isEmpty()) return null

        val preferredToken = notificationToken ?: sourceToken
        if (preferredToken != null) {
            val resolved = controllers.firstOrNull { controller ->
                controller.sessionToken == preferredToken &&
                        matchesSourceMedia(controller, matchingSourceMetadata)
            }
            if (resolved != null) {
                return resolved
            }
            return null
        }

        val sourceMediaId = matchingSourceMetadata?.mediaId
        if (sourceMediaId != null) {
            val resolved = controllers.singleOrNull { controller ->
                controllerMediaId(controller) == sourceMediaId
            }
            if (resolved != null) {
                return resolved
            }
            return null
        }

        return controllers.singleOrNull()
    }

    /**
     * Resolves the controller for a horizontal track-switch gesture.
     *
     * The visible island's notification owns the session identity. The lyric bridge may be
     * temporarily cleared or still contain the previous media id while the player is publishing a
     * new track, so those source fields must not be hard gates for a same-session skip command.
    */
    fun resolveForSwipe(
        context: Context,
        data: Any?,
        hostRoot: ViewGroup? = null
    ): MediaController? {
        val target = IslandModificationTargetPolicy.resolve(data, hostRoot)
        val islandInfo = target.mediaInfo ?: return null
        if (!IslandModificationTargetPolicy.allowsCurrentScope(target)) return null
        val statusBarNotification = IslandProbeUtils.extractStatusBarNotification(data)
        if (statusBarNotification?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val sessionContext = resolveSessionContext(context) ?: return null

        val manager = runCatching {
            sessionContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        }.onFailure { error ->
            HookLogger.w(TAG, "获取 MediaSessionManager 失败", error)
        }.getOrNull() ?: return null
        val allControllers = runCatching { manager.getActiveSessions(null) }
            .onFailure { error ->
                HookLogger.w(TAG, "获取 active sessions 失败", error)
            }.getOrNull()
            ?: return null
        val controllers = allControllers.filter { it.packageName == islandInfo.packageName }
        if (controllers.isEmpty()) return null

        // A notification token remains stable when the player changes tracks, while the media id
        // is expected to change. Prefer it even if the source bridge is stale during that update.
        val notificationToken = IslandProbeUtils.extractMediaSessionToken(data)
        if (notificationToken != null) {
            // MediaController carries the caller package into MediaSessionService. A Dynamic
            // Island view may be created with the plugin package context even though the process
            // UID belongs to SystemUI; use the host package context for both paths.
            resolveToken(
                sessionContext,
                islandInfo.packageName,
                controllers,
                notificationToken
            )?.let {
                return it
            }
        }

        // Some notifications do not expose EXTRA_MEDIA_SESSION. A source token is still an exact
        // session identity, but it is only used as a fallback when the notification has no token.
        val sourceMetadata = LyriconDataBridge.currentLyricMediaMetadata
        val sourceToken = sourceMetadata?.sessionToken
        if (sourceToken != null) {
            resolveToken(
                sessionContext,
                islandInfo.packageName,
                controllers,
                sourceToken
            )?.let {
                return it
            }
        }

        // If a token was stale during a player transition, a unique current package session is
        // still safe to control. Multiple sessions remain ambiguous and are rejected.
        return controllers.singleOrNull()
    }

    private fun resolveSessionContext(
        context: Context
    ): Context? {
        val sourcePackage = runCatching { context.packageName }.getOrNull()
        if (sourcePackage == SYSTEM_UI_PACKAGE) return context
        return runCatching {
            context.createPackageContext(
                SYSTEM_UI_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "创建 SystemUI Context 失败: sourcePackage=$sourcePackage",
                error
            )
        }.getOrNull()
    }

    private fun resolveToken(
        context: Context,
        packageName: String,
        controllers: List<MediaController>,
        token: MediaSession.Token
    ): MediaController? {
        controllers.firstOrNull { it.sessionToken == token }?.let { return it }
        val constructed = runCatching { MediaController(context, token) }
            .onFailure { error ->
                HookLogger.w(TAG, "MediaController(token) 构造失败", error)
            }.getOrNull()
            ?: return null
        if (constructed.packageName != packageName) {
            return null
        }
        return constructed
    }

    private fun matchesSourceMedia(
        controller: MediaController,
        sourceMetadata: com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata?
    ): Boolean {
        val sourceMediaId = sourceMetadata?.mediaId ?: return true
        return controllerMediaId(controller) == sourceMediaId
    }

    private fun controllerMediaId(controller: MediaController): String? {
        return runCatching {
            controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }

}
