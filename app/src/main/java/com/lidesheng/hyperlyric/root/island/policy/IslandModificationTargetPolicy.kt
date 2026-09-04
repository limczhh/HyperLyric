package com.lidesheng.hyperlyric.root.island.policy

import android.content.SharedPreferences
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry

/**
 * Shared target facts for user-visible Super Island modifications.
 *
 * This policy intentionally stays separate from presentation rendering. Rendering decides
 * whether lyric views should exist; this policy decides whether an already observed island is
 * eligible for a modification such as a gesture or visual effect.
 */
internal object IslandModificationTargetPolicy {

    enum class Scope {
        ALL_MEDIA,
        INJECTED_LYRIC
    }

    enum class MediaState {
        MEDIA,
        PENDING,
        NOT_MEDIA
    }

    enum class InjectionState {
        PRESENT,
        ABSENT,
        UNKNOWN
    }

    data class TargetSnapshot(
        val mediaState: MediaState,
        val mediaInfo: IslandProbeUtils.MediaIslandInfo?,
        val isCurrentLyricOwner: Boolean,
        val hasLyricsForPresentation: Boolean,
        val injectionState: InjectionState,
        val hostToken: IslandViewRegistry.HostToken?
    ) {
        val isMediaTarget: Boolean
            get() = mediaState == MediaState.MEDIA && mediaInfo != null

        /**
         * Logical lyric target used by existing features which remain available while the
         * native media island is visible after the injected lyric content is suppressed.
         */
        val isCurrentLyricPresentationTarget: Boolean
            get() = isMediaTarget &&
                    isCurrentLyricOwner &&
                    hasLyricsForPresentation

        /**
         * Strict physical target for the future "injected lyric islands only" scope.
         * UNKNOWN is deliberately not accepted: a caller without host evidence must fall back
         * to native behavior instead of guessing from package name alone.
         */
        val isInjectedLyricTarget: Boolean
            get() = isMediaTarget &&
                    isCurrentLyricOwner &&
                    hostToken?.packageName == mediaInfo?.packageName &&
                    injectionState == InjectionState.PRESENT
    }

    fun resolve(data: Any?, hostRoot: ViewGroup? = null): TargetSnapshot {
        val mediaInfo = IslandProbeUtils.extractMediaIslandInfo(data)
        val mediaState = when {
            mediaInfo != null -> MediaState.MEDIA
            data == null || IslandProbeUtils.isMediaIsland(data) -> MediaState.PENDING
            else -> MediaState.NOT_MEDIA
        }
        val hostToken = hostRoot?.let(IslandViewRegistry::tokenForDescendant)
        val injectionState = hostToken?.root?.let(::readInjectionState)
            ?: InjectionState.UNKNOWN

        return TargetSnapshot(
            mediaState = mediaState,
            mediaInfo = mediaInfo,
            isCurrentLyricOwner = mediaInfo?.let(::isCurrentLyricOwner) == true,
            hasLyricsForPresentation = LyriconDataBridge.hasLyricsForPresentation(),
            injectionState = injectionState,
            hostToken = hostToken
        )
    }

    fun allows(
        snapshot: TargetSnapshot,
        scope: Scope
    ): Boolean {
        return when (scope) {
            Scope.ALL_MEDIA -> snapshot.isMediaTarget
            Scope.INJECTED_LYRIC -> snapshot.isInjectedLyricTarget
        }
    }

    fun allows(
        data: Any?,
        scope: Scope,
        hostRoot: ViewGroup? = null
    ): Boolean {
        return allows(resolve(data, hostRoot), scope)
    }

    fun readScope(prefs: SharedPreferences?): Scope {
        val value = prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_MODIFICATION_SCOPE,
            RootConstants.DEFAULT_HOOK_ISLAND_MODIFICATION_SCOPE
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_MODIFICATION_SCOPE
        return when (value) {
            RootConstants.ISLAND_MODIFICATION_SCOPE_INJECTED_LYRIC -> Scope.INJECTED_LYRIC
            else -> Scope.ALL_MEDIA
        }
    }

    fun currentScope(prefs: SharedPreferences? = null): Scope {
        return readScope(prefs ?: HookEntry.instance?.prefs)
    }

    fun allowsCurrentScope(
        snapshot: TargetSnapshot,
        prefs: SharedPreferences? = null
    ): Boolean {
        return allows(snapshot, currentScope(prefs))
    }

    fun allowsCurrentScope(
        data: Any?,
        hostRoot: ViewGroup? = null,
        prefs: SharedPreferences? = null
    ): Boolean {
        return allowsCurrentScope(resolve(data, hostRoot), prefs)
    }

    fun isCurrentLyricOwner(
        mediaInfo: IslandProbeUtils.MediaIslandInfo
    ): Boolean {
        val lyricPackageName = LyriconDataBridge.currentLyricPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return false
        return mediaInfo.packageName == lyricPackageName
    }

    fun isCurrentLyricPresentationTarget(data: Any?): Boolean {
        return resolve(data).isCurrentLyricPresentationTarget
    }

    private fun readInjectionState(root: ViewGroup): InjectionState {
        return when (IslandViewRegistry.hasAttachedInjectedViews(root)) {
            true -> InjectionState.PRESENT
            false -> InjectionState.ABSENT
            null -> InjectionState.UNKNOWN
        }
    }
}
