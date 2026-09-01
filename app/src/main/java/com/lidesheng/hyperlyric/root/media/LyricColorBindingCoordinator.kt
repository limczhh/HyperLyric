package com.lidesheng.hyperlyric.root.media

import android.content.Context
import android.media.session.MediaSession
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper

internal data class LyricColorBindingUpdate(
    val colorSession: CoverColorHelper.ColorSession?,
    val stateChanged: Boolean,
    val reason: String
)

/** Pins one lyric-source owner to one player session. Lyric content never advances this state. */
internal object LyricColorBindingCoordinator {

    private data class SourceOwner(
        val sourceId: String,
        val packageName: String,
        val sessionToken: MediaSession.Token?
    )

    private data class Selection(
        val token: MediaSession.Token?,
        val reason: String
    )

    private var sourceOwner: SourceOwner? = null

    @Synchronized
    fun updateSource(
        context: Context,
        metadata: LyricMediaMetadata
    ): LyricColorBindingUpdate {
        val normalized = metadata.normalized()
        val sourceId = normalized.sourceId.trim()
        val packageName = normalized.packageName.orEmpty().trim()
        if (sourceId.isEmpty() || packageName.isEmpty()) {
            sourceOwner = null
            return pending(CoverColorHelper.endSession(), "invalid_source_owner")
        }

        val previous = sourceOwner
        val sameOwner = previous?.sourceId == sourceId &&
                previous.packageName == packageName
        val sourceToken = normalized.sessionToken
            ?: previous?.sessionToken?.takeIf { sameOwner }
        val ownerChanged = !sameOwner ||
                (normalized.sessionToken != null && normalized.sessionToken != previous?.sessionToken)
        sourceOwner = SourceOwner(sourceId, packageName, sourceToken)

        val cleared = ownerChanged && CoverColorHelper.endSession()
        return resolveLocked(context, preferPlaying = false, initialStateChanged = cleared)
    }

    @Synchronized
    fun onPlaybackStarted(context: Context): LyricColorBindingUpdate =
        resolveLocked(context, preferPlaying = true)

    @Synchronized
    fun retry(
        context: Context,
        playbackActive: Boolean
    ): LyricColorBindingUpdate = resolveLocked(context, preferPlaying = playbackActive)

    @Synchronized
    fun clear(): Boolean {
        sourceOwner = null
        return CoverColorHelper.endSession()
    }

    private fun resolveLocked(
        context: Context,
        preferPlaying: Boolean,
        initialStateChanged: Boolean = false
    ): LyricColorBindingUpdate {
        val owner = sourceOwner
            ?: return pending(initialStateChanged, "no_source_owner")
        val candidates = MediaMetadataHelper.getSessionCandidates(context, owner.packageName)
        val currentToken = CoverColorHelper
            .currentSession(owner.packageName)
            ?.sessionToken
        val selection = selectSession(
            candidates = candidates,
            sourceToken = owner.sessionToken,
            currentToken = currentToken,
            preferPlaying = preferPlaying
        )
        val selectedToken = selection.token ?: run {
            val changed = CoverColorHelper.endSession() || initialStateChanged
            return pending(changed, selection.reason)
        }

        val previousRevision = CoverColorHelper.currentSession()?.revision
        val session = CoverColorHelper.activateSession(owner.packageName, selectedToken)
        return LyricColorBindingUpdate(
            colorSession = session,
            stateChanged = initialStateChanged || previousRevision != session.revision,
            reason = selection.reason
        )
    }

    private fun selectSession(
        candidates: List<MediaMetadataHelper.MediaSessionCandidate>,
        sourceToken: MediaSession.Token?,
        currentToken: MediaSession.Token?,
        preferPlaying: Boolean
    ): Selection {
        if (candidates.isEmpty()) return Selection(null, "no_controller")

        if (sourceToken != null) {
            val exact = candidates.firstOrNull { it.sessionToken == sourceToken }
                ?: return Selection(null, "source_token_missing")
            return Selection(exact.sessionToken, "source_token")
        }

        val current = currentToken?.let { token ->
            candidates.firstOrNull { it.sessionToken == token }
        }
        if (current != null) {
            if (!preferPlaying || current.isPlaying) {
                return Selection(current.sessionToken, "pinned_session")
            }
            val playing = candidates.filter { it.isPlaying }
            return when {
                playing.size == 1 -> Selection(playing.single().sessionToken, "unique_playing")
                playing.size > 1 -> Selection(null, "ambiguous_playing_sessions")
                else -> Selection(current.sessionToken, "pinned_session_transition")
            }
        }

        val playing = candidates.filter { it.isPlaying }
        return when {
            playing.size == 1 -> Selection(playing.single().sessionToken, "unique_playing")
            playing.size > 1 -> Selection(null, "ambiguous_playing_sessions")
            candidates.size == 1 -> Selection(candidates.single().sessionToken, "unique_active")
            else -> Selection(null, "ambiguous_active_sessions")
        }
    }

    private fun pending(
        stateChanged: Boolean,
        reason: String
    ) = LyricColorBindingUpdate(
        colorSession = null,
        stateChanged = stateChanged,
        reason = reason
    )
}
