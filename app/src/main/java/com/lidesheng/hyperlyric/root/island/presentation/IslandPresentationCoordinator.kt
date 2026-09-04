package com.lidesheng.hyperlyric.root.island.presentation

import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.policy.IslandModificationTargetPolicy
import com.lidesheng.hyperlyric.root.utils.HookLogger

/**
 * Owns Super Island target policy and view reconciliation.
 *
 * Xposed hookers keep responsibility for exact method signatures and
 * chain.proceed() timing, then synchronously report extracted lifecycle facts
 * here. High-frequency lyric position and color updates intentionally stay on
 * their indexed-view fast paths.
 */
internal object IslandPresentationCoordinator {
    private const val TAG = "IslandPresentation"

    data class ReconcileResult(
        val decision: IslandRenderPolicy.Decision,
        val mutation: IslandInjectionReconciler.Result
    ) {
        val isTarget: Boolean
            get() = decision == IslandRenderPolicy.Decision.TARGET

        companion object {
            fun noOp(decision: IslandRenderPolicy.Decision): ReconcileResult {
                return ReconcileResult(
                    decision = decision,
                    mutation = IslandInjectionReconciler.Result.NO_OP
                )
            }
        }
    }

    private val presentationState = IslandPresentationState()
    private val decisionEvaluator = IslandPresentationDecisionEvaluator(presentationState)
    private val hostAttachmentObserver = IslandHostAttachmentObserver(
        currentPresentationRevision = { currentPresentationRevision() },
        onHostAttached = { token, expectedRevision ->
            reconcileRegisteredHost(
                token = token,
                reason = IslandReconcileReason.HOST_ATTACHED,
                expectedPresentationRevision = expectedRevision
            )
        }
    )

    fun ownerEvidence(data: Any?): IslandRenderPolicy.OwnerEvidence {
        return decisionEvaluator.ownerEvidence(data)
    }

    fun isCurrentLyricTarget(data: Any?): Boolean {
        return evaluate(ownerEvidence(data)) == IslandRenderPolicy.Decision.TARGET
    }

    fun isCurrentLyricLongPressTarget(data: Any?): Boolean {
        return decisionEvaluator.isCurrentLyricLongPressTarget(data)
    }

    fun updatePlaybackState(isPlaying: Boolean): Boolean {
        return presentationState.updatePlaybackState(isPlaying)
    }

    fun isPlaybackActive(): Boolean = presentationState.isPlaybackActive()

    fun invalidatePresentation(): Long {
        return presentationState.invalidatePresentation()
    }

    fun currentPresentationRevision(): Long = presentationState.currentRevision()

    fun isCurrentPresentation(revision: Long): Boolean {
        return presentationState.isCurrentRevision(revision)
    }

    fun isCurrentLyricOwner(mediaInfo: IslandProbeUtils.MediaIslandInfo): Boolean {
        return decisionEvaluator.isCurrentLyricOwner(mediaInfo)
    }

    fun shouldRenderInjectedIsland(): Boolean {
        return decisionEvaluator.shouldRenderInjectedIsland()
    }

    fun onRealBeforeSystemUpdate(
        root: ViewGroup,
        owner: IslandRenderPolicy.OwnerEvidence
    ): ReconcileResult {
        return reconcileRealRoot(root, owner, IslandReconcileReason.PRE_SYSTEM_UPDATE)
    }

    fun onRealSystemUpdateComplete(
        root: ViewGroup,
        owner: IslandRenderPolicy.OwnerEvidence
    ): ReconcileResult {
        if (owner == IslandRenderPolicy.OwnerEvidence.NotMedia) {
            val result = removeRealHost(root, IslandReconcileReason.SYSTEM_UPDATE_COMPLETE)
            logReconcile(
                root = root,
                target = IslandInjectionReconciler.Target.RealRoot,
                owner = owner,
                reason = IslandReconcileReason.SYSTEM_UPDATE_COMPLETE,
                result = result
            )
            return result
        }

        if (owner is IslandRenderPolicy.OwnerEvidence.Media) {
            IslandViewRegistry.registerReal(root, owner.packageName)
            observeHostAttachment(root)
        }
        val result = reconcileRealRoot(
            root = root,
            owner = owner,
            reason = IslandReconcileReason.SYSTEM_UPDATE_COMPLETE
        )
        refreshAlbumCoverAfterInjection(result)
        return result
    }

    fun onModuleBound(
        holderRoot: ViewGroup,
        moduleType: String?,
        owner: IslandRenderPolicy.OwnerEvidence,
        isFake: Boolean
    ): ReconcileResult {
        val result = reconcileModule(
            holderRoot = holderRoot,
            moduleType = moduleType,
            owner = owner,
            reason = IslandReconcileReason.MODULE_FIRST_BIND,
            isFake = isFake
        )
        refreshAlbumCoverAfterInjection(result)
        return result
    }

    fun onModuleUpdated(
        holderRoot: ViewGroup,
        moduleType: String?,
        owner: IslandRenderPolicy.OwnerEvidence,
        isFake: Boolean
    ): ReconcileResult {
        val result = reconcileModule(
            holderRoot = holderRoot,
            moduleType = moduleType,
            owner = owner,
            reason = IslandReconcileReason.MODULE_UPDATED,
            isFake = isFake
        )
        refreshAlbumCoverAfterInjection(result)
        return result
    }

    fun reconcileRegisteredHost(
        token: IslandViewRegistry.HostToken,
        reason: IslandReconcileReason,
        expectedPresentationRevision: Long? = null
    ): ReconcileResult {
        if (!IslandViewRegistry.isCurrent(token) ||
            (expectedPresentationRevision != null &&
                    !isCurrentPresentation(expectedPresentationRevision))
        ) {
            return ReconcileResult.noOp(IslandRenderPolicy.Decision.PENDING)
        }
        val owner = IslandRenderPolicy.OwnerEvidence.Media(token.packageName)
        val result = when (token.kind) {
            IslandViewRegistry.HostKind.REAL -> reconcileRealRoot(
                root = token.root,
                owner = owner,
                reason = reason
            )

            IslandViewRegistry.HostKind.FAKE -> reconcileModule(
                holderRoot = token.root,
                moduleType = token.moduleType,
                owner = owner,
                reason = reason,
                isFake = true
            )
        }
        refreshAlbumCoverAfterInjection(result)
        return result
    }

    fun clearRegisteredHostIfSuppressed(
        token: IslandViewRegistry.HostToken,
        expectedPresentationRevision: Long
    ): IslandInjectionReconciler.Result {
        if (!IslandViewRegistry.isCurrent(token) ||
            !isCurrentPresentation(expectedPresentationRevision) ||
            evaluate(
                IslandRenderPolicy.OwnerEvidence.Media(token.packageName)
            ) != IslandRenderPolicy.Decision.SUPPRESSED
        ) {
            return IslandInjectionReconciler.Result.NO_OP
        }
        return IslandInjectionReconciler.restoreNative(
            token.root,
            targetFor(token)
        )
    }

    fun clearRegisteredHost(
        token: IslandViewRegistry.HostToken,
        expectedPresentationRevision: Long
    ): IslandInjectionReconciler.Result {
        if (!IslandViewRegistry.isCurrent(token) ||
            !isCurrentPresentation(expectedPresentationRevision)
        ) {
            return IslandInjectionReconciler.Result.NO_OP
        }
        return IslandInjectionReconciler.restoreNative(
            token.root,
            targetFor(token)
        )
    }

    fun snapshotAttachedHosts(
        packageName: String? = null
    ): List<IslandViewRegistry.HostToken> {
        return IslandViewRegistry.snapshotAttached(packageName)
    }

    fun snapshotAttachedRealHosts(
        packageName: String? = null
    ): List<IslandViewRegistry.HostToken> {
        return IslandViewRegistry.snapshotAttached(
            packageName = packageName,
            kind = IslandViewRegistry.HostKind.REAL
        )
    }

    fun snapshotAttachedInjectedHosts(
        packageName: String? = null
    ): List<IslandViewRegistry.InjectedHostToken> {
        return IslandViewRegistry.snapshotAttachedInjectedViews(packageName)
    }

    fun isCurrentHost(token: IslandViewRegistry.HostToken): Boolean {
        return IslandViewRegistry.isCurrent(token)
    }

    fun refreshInjectedViewIndex(token: IslandViewRegistry.HostToken) {
        if (IslandViewRegistry.isCurrent(token)) {
            IslandViewRegistry.refreshInjectedViews(token)
        }
    }

    private fun reconcileRealRoot(
        root: ViewGroup,
        owner: IslandRenderPolicy.OwnerEvidence,
        reason: IslandReconcileReason
    ): ReconcileResult {
        val decision = evaluate(owner)
        val mutation = when (decision) {
            IslandRenderPolicy.Decision.TARGET -> {
                IslandInjectionReconciler.show(
                    root = root,
                    target = IslandInjectionReconciler.Target.RealRoot,
                    options = IslandReconcileOptions.realRoot(reason),
                    playbackActive = presentationState.isPlaybackActive()
                )
            }

            IslandRenderPolicy.Decision.SUPPRESSED -> {
                IslandInjectionReconciler.restoreNative(
                    root,
                    IslandInjectionReconciler.Target.RealRoot
                )
            }

            IslandRenderPolicy.Decision.OTHER_PACKAGE -> {
                if (reason == IslandReconcileReason.PRE_SYSTEM_UPDATE) {
                    IslandInjectionReconciler.Result.NO_OP
                } else {
                    IslandInjectionReconciler.restoreNative(
                        root,
                        IslandInjectionReconciler.Target.RealRoot
                    )
                }
            }

            IslandRenderPolicy.Decision.NOT_MEDIA -> {
                return removeRealHost(root, reason).also {
                    logReconcile(
                        root = root,
                        target = IslandInjectionReconciler.Target.RealRoot,
                        owner = owner,
                        reason = reason,
                        result = it
                    )
                }
            }

            IslandRenderPolicy.Decision.PENDING -> IslandInjectionReconciler.Result.NO_OP
        }
        return ReconcileResult(decision, mutation).also {
            logReconcile(
                root = root,
                target = IslandInjectionReconciler.Target.RealRoot,
                owner = owner,
                reason = reason,
                result = it
            )
        }
    }

    private fun reconcileModule(
        holderRoot: ViewGroup,
        moduleType: String?,
        owner: IslandRenderPolicy.OwnerEvidence,
        reason: IslandReconcileReason,
        isFake: Boolean
    ): ReconcileResult {
        val decision = evaluate(owner)
        val target = if (isFake) {
            IslandInjectionReconciler.Target.FakeModule(moduleType)
        } else {
            IslandInjectionReconciler.Target.RealModule(moduleType)
        }
        if (isFake) {
            when (owner) {
                is IslandRenderPolicy.OwnerEvidence.Media -> {
                    // Registration describes Xiaomi's persistent projection, not whether lyrics
                    // happen to be ready at this instant. PENDING/SUPPRESSED hosts must remain
                    // discoverable so the first source event can populate them without waiting
                    // for a transition or another adapter callback.
                    IslandViewRegistry.registerFake(holderRoot, owner.packageName, moduleType)
                    observeHostAttachment(holderRoot)
                }

                IslandRenderPolicy.OwnerEvidence.NotMedia -> {
                    stopObservingHostAttachment(holderRoot)
                    IslandViewRegistry.unregister(holderRoot)
                }

                IslandRenderPolicy.OwnerEvidence.Pending -> Unit
            }
        }
        val mutation = when (decision) {
            IslandRenderPolicy.Decision.TARGET -> {
                IslandInjectionReconciler.show(
                    root = holderRoot,
                    target = target,
                    options = IslandReconcileOptions.module(reason),
                    playbackActive = presentationState.isPlaybackActive()
                )
            }

            IslandRenderPolicy.Decision.SUPPRESSED,
            IslandRenderPolicy.Decision.OTHER_PACKAGE,
            IslandRenderPolicy.Decision.NOT_MEDIA -> {
                IslandInjectionReconciler.restoreNative(holderRoot, target)
            }

            IslandRenderPolicy.Decision.PENDING -> IslandInjectionReconciler.Result.NO_OP
        }
        return ReconcileResult(decision, mutation).also {
            logReconcile(
                root = holderRoot,
                target = target,
                owner = owner,
                reason = reason,
                result = it
            )
        }
    }

    private fun removeRealHost(
        root: ViewGroup,
        reason: IslandReconcileReason
    ): ReconcileResult {
        stopObservingHostAttachment(root)
        IslandViewRegistry.unregister(root)
        val mutation = IslandInjectionReconciler.restoreNative(
            root,
            IslandInjectionReconciler.Target.RealRoot
        )
        return ReconcileResult(IslandRenderPolicy.Decision.NOT_MEDIA, mutation)
    }

    private fun evaluate(
        owner: IslandRenderPolicy.OwnerEvidence
    ): IslandRenderPolicy.Decision {
        return decisionEvaluator.evaluate(owner)
    }

    private fun refreshAlbumCoverAfterInjection(result: ReconcileResult) {
        if (!result.isTarget ||
            IslandModificationTargetPolicy.currentScope() !=
            IslandModificationTargetPolicy.Scope.INJECTED_LYRIC
        ) {
            return
        }
        IslandAlbumCoverStyleHooker.refresh()
    }

    private fun targetFor(token: IslandViewRegistry.HostToken): IslandInjectionReconciler.Target {
        return when (token.kind) {
            IslandViewRegistry.HostKind.REAL -> IslandInjectionReconciler.Target.RealRoot
            IslandViewRegistry.HostKind.FAKE ->
                IslandInjectionReconciler.Target.FakeModule(token.moduleType)
        }
    }

    private fun logReconcile(
        root: ViewGroup,
        target: IslandInjectionReconciler.Target,
        owner: IslandRenderPolicy.OwnerEvidence,
        reason: IslandReconcileReason,
        result: ReconcileResult
    ) {
        val mutation = result.mutation
        val enabled = IslandProbeUtils.isSuperIslandEnabled()
        val playbackActive = presentationState.isPlaybackActive()
        val pauseBehavior = HookEntry.instance?.prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
            RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        val lyricPackage = LyriconDataBridge.currentLyricPackageName
        val ownerPackage = (owner as? IslandRenderPolicy.OwnerEvidence.Media)?.packageName
        val hasLyrics = LyriconDataBridge.hasLyricsForPresentation()
        val state = listOf(
            reason,
            owner,
            result.decision,
            mutation.outcome,
            enabled,
            playbackActive,
            pauseBehavior,
            lyricPackage,
            hasLyrics,
            mutation.layoutMayHaveChanged,
            mutation.contentChanged,
            mutation.injectedSlotsPresent,
            mutation.relayoutRequested
        ).joinToString("|")
        HookLogger.dState(
            stateId = "IslandPresentation:${System.identityHashCode(root)}:$target",
            tag = TAG,
            state = state
        ) {
            "超级岛注入决策: root=${System.identityHashCode(root)}, target=$target, " +
                    "owner=${ownerPackage ?: owner}, reason=$reason, decision=${result.decision}, " +
                    "enabled=$enabled, playbackActive=$playbackActive, pauseBehavior=$pauseBehavior, " +
                    "lyricPackage=${lyricPackage ?: "<none>"}, hasLyrics=$hasLyrics, " +
                    "outcome=${mutation.outcome}, layoutChanged=${mutation.layoutMayHaveChanged}, " +
                    "contentChanged=${mutation.contentChanged}, " +
                    "injectedSlots=${mutation.injectedSlotsPresent}, relayout=${mutation.relayoutRequested}"
        }
    }

    private fun observeHostAttachment(root: ViewGroup) {
        hostAttachmentObserver.observe(root)
    }

    private fun stopObservingHostAttachment(root: ViewGroup) {
        hostAttachmentObserver.stop(root)
    }
}
