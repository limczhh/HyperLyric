package com.lidesheng.hyperlyric.root.island.hooks

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils

/**
 * Resolves the MediaController represented by the visible Super Island.
 *
 * Xiaomi exposes the island's StatusBarNotification in DynamicIslandData, so its media-session
 * token is stronger than a package-name lookup. When the notification does not expose a token,
 * a source token or a unique media-id match may still prove the controller. A package with
 * several unresolved sessions is deliberately rejected instead of controlling an arbitrary one.
 */
internal object IslandPlaybackControllerResolver {

    fun resolve(context: Context, data: Any?): MediaController? {
        val islandInfo = IslandProbeUtils.extractMediaIslandInfo(data) ?: return null
        val lyricPackageName = LyriconDataBridge.currentLyricPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (islandInfo.packageName != lyricPackageName) return null

        val sourceMetadata = LyriconDataBridge.currentLyricMediaMetadata?.normalized()
        if (sourceMetadata?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val statusBarNotification = IslandProbeUtils.extractStatusBarNotification(data)
        if (statusBarNotification?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val notificationToken = IslandProbeUtils.extractMediaSessionToken(data)
        val sourceToken = sourceMetadata?.sessionToken
        if (notificationToken != null && sourceToken != null && notificationToken != sourceToken) {
            return null
        }

        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager
            ?: return null
        val controllers = runCatching { manager.getActiveSessions(null) }
            .getOrNull()
            .orEmpty()
            .filter { it.packageName == islandInfo.packageName }
        if (controllers.isEmpty()) return null

        val preferredToken = notificationToken ?: sourceToken
        if (preferredToken != null) {
            return controllers.firstOrNull { controller ->
                controller.sessionToken == preferredToken &&
                        matchesSourceMedia(controller, sourceMetadata)
            }
        }

        val sourceMediaId = sourceMetadata?.mediaId
        if (sourceMediaId != null) {
            return controllers.singleOrNull { controller ->
                controllerMediaId(controller) == sourceMediaId
            }
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
    fun resolveForSwipe(context: Context, data: Any?): MediaController? {
        val islandInfo = IslandProbeUtils.extractMediaIslandInfo(data) ?: return null
        val statusBarNotification = IslandProbeUtils.extractStatusBarNotification(data)
        if (statusBarNotification?.packageName?.let { it != islandInfo.packageName } == true) {
            return null
        }

        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
            as? MediaSessionManager
            ?: return null
        val controllers = runCatching { manager.getActiveSessions(null) }
            .getOrNull()
            .orEmpty()
            .filter { it.packageName == islandInfo.packageName }

        // A notification token remains stable when the player changes tracks, while the media id
        // is expected to change. Prefer it even if the source bridge is stale during that update.
        IslandProbeUtils.extractMediaSessionToken(data)?.let { notificationToken ->
            resolveToken(context, islandInfo.packageName, controllers, notificationToken)?.let {
                return it
            }
        }

        // Some notifications do not expose EXTRA_MEDIA_SESSION. A source token is still an exact
        // session identity, but it is only used as a fallback when the notification has no token.
        LyriconDataBridge.currentLyricMediaMetadata?.sessionToken?.let { sourceToken ->
            resolveToken(context, islandInfo.packageName, controllers, sourceToken)?.let {
                return it
            }
        }

        // If a token was stale during a player transition, a unique current package session is
        // still safe to control. Multiple sessions remain ambiguous and are rejected.
        return controllers.singleOrNull()
    }

    private fun resolveToken(
        context: Context,
        packageName: String,
        controllers: List<MediaController>,
        token: MediaSession.Token
    ): MediaController? {
        controllers.firstOrNull { it.sessionToken == token }?.let { return it }
        return runCatching { MediaController(context, token) }
            .getOrNull()
            ?.takeIf { it.packageName == packageName }
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
