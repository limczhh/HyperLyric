package com.lidesheng.hyperlyric.root.island.presentation

import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.host.IslandTextHookerSupport
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
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
                reason = IslandReconcileReason.STABLE_REFRESH,
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
            IslandViewRegistry.register(root, owner.packageName)
            observeRealHostAttachment(root)
        }
        val result = reconcileRealRoot(
            root = root,
            owner = owner,
            reason = IslandReconcileReason.SYSTEM_UPDATE_COMPLETE
        )
        return result
    }

    fun onRealVisibilityChanged(
        root: ViewGroup,
        owner: IslandRenderPolicy.OwnerEvidence
    ): ReconcileResult {
        if (owner is IslandRenderPolicy.OwnerEvidence.Media) {
            IslandViewRegistry.register(root, owner.packageName)
            observeRealHostAttachment(root)
        }
        return reconcileRealRoot(root, owner, IslandReconcileReason.VISIBILITY_CHANGED)
    }

    fun onModuleBound(
        holderRoot: ViewGroup,
        moduleType: String?,
        owner: IslandRenderPolicy.OwnerEvidence
    ): ReconcileResult {
        return reconcileModule(
            holderRoot = holderRoot,
            moduleType = moduleType,
            owner = owner,
            reason = IslandReconcileReason.MODULE_FIRST_BIND
        )
    }

    fun onModuleUpdated(
        holderRoot: ViewGroup,
        moduleType: String?,
        owner: IslandRenderPolicy.OwnerEvidence
    ): ReconcileResult {
        return reconcileModule(
            holderRoot = holderRoot,
            moduleType = moduleType,
            owner = owner,
            reason = IslandReconcileReason.MODULE_UPDATED
        )
    }

    fun onFakeSnapshotRequested(
        fakeOwner: ViewGroup,
        snapshotRoot: ViewGroup,
        owner: IslandRenderPolicy.OwnerEvidence,
        realRoot: ViewGroup?,
        position: Long
    ): ReconcileResult {
        var realOwnerConflict = false
        val realHost = if (owner == IslandRenderPolicy.OwnerEvidence.NotMedia) {
            null
        } else {
            realRoot?.let { root ->
                val existing = IslandViewRegistry.tokenFor(root)
                val snapshotOwner = owner as? IslandRenderPolicy.OwnerEvidence.Media
                when {
                    existing != null &&
                            snapshotOwner != null &&
                            existing.packageName != snapshotOwner.packageName -> {
                        realOwnerConflict = true
                        null
                    }

                    existing != null -> existing

                    else -> {
                        val realOwner = ownerEvidence(
                            IslandTextHookerSupport.extractIslandDataFromContentOrReal(root)
                        ) as? IslandRenderPolicy.OwnerEvidence.Media
                            ?: return@let null
                        if (snapshotOwner != null &&
                            snapshotOwner.packageName != realOwner.packageName
                        ) {
                            realOwnerConflict = true
                            return@let null
                        }
                        IslandViewRegistry.register(root, realOwner.packageName).also {
                            observeRealHostAttachment(root)
                        }
                    }
                }
            }
        }
        val resolvedOwner = realHost
            ?.let { IslandRenderPolicy.OwnerEvidence.Media(it.packageName) }
            ?: owner
        val decision = if (realOwnerConflict) {
            IslandRenderPolicy.Decision.OTHER_PACKAGE
        } else {
            evaluate(owner = resolvedOwner)
        }
        val mutation = when (decision) {
            IslandRenderPolicy.Decision.TARGET -> {
                if (realHost != null) {
                    IslandFakeTransitionRegistry.remember(
                        fakeOwner = fakeOwner,
                        realHost = realHost,
                        frozenPosition = position,
                        lyricVersion = LyriconDataBridge.versionCounter.get()
                    )
                    IslandInjectionReconciler.prepareFrozenRealHost(
                        realHost.root,
                        position
                    )
                } else {
                    IslandFakeTransitionRegistry.remove(fakeOwner)
                }
                IslandInjectionReconciler.prepareFrozenSnapshot(snapshotRoot, position)
                    .also { IslandHostFacade.showFrozenSnapshot(snapshotRoot) }
            }

            IslandRenderPolicy.Decision.SUPPRESSED,
            IslandRenderPolicy.Decision.OTHER_PACKAGE,
            IslandRenderPolicy.Decision.NOT_MEDIA -> {
                IslandFakeTransitionRegistry.remove(fakeOwner)
                IslandInjectionReconciler.restoreNative(
                    snapshotRoot,
                    IslandInjectionReconciler.Target.FakeSnapshot
                )
            }

            IslandRenderPolicy.Decision.PENDING -> {
                IslandFakeTransitionRegistry.remove(fakeOwner)
                IslandInjectionReconciler.Result.NO_OP
            }
        }
        return ReconcileResult(decision, mutation)
    }

    fun onFakeTransitionHandoff(
        fakeOwner: ViewGroup,
        realRoot: ViewGroup
    ): ReconcileResult {
        val transition = IslandFakeTransitionRegistry.find(fakeOwner)
            ?: return ReconcileResult.noOp(IslandRenderPolicy.Decision.PENDING)

        if (transition.realHost.root !== realRoot ||
            !IslandViewRegistry.isCurrent(transition.realHost)
        ) {
            return ReconcileResult.noOp(IslandRenderPolicy.Decision.PENDING)
        }

        val decision = evaluate(
            IslandRenderPolicy.OwnerEvidence.Media(transition.realHost.packageName)
        )
        if (decision != IslandRenderPolicy.Decision.TARGET) {
            return ReconcileResult.noOp(decision)
        }

        val mutation = IslandInjectionReconciler.restoreFrozenRealHost(
            realRoot,
            transition.frozenPosition
        )
        IslandHostFacade.showRealHost(realRoot)
        return ReconcileResult(decision, mutation)
    }

    fun onFakeTransitionEnded(
        fakeOwner: ViewGroup,
        realRoot: ViewGroup
    ): ReconcileResult {
        // SystemUI does not return our generation in the end callback. The
        // latest request for one fake owner is therefore authoritative; two
        // preparation hooks can legitimately report the same transition.
        val transition = IslandFakeTransitionRegistry.remove(fakeOwner)
        if (transition == null) {
            return restoreRealHostFromCurrentEvidence(realRoot)
        }
        if (transition.realHost.root !== realRoot) {
            HookLogger.d(
                TAG,
                "fake 真实岛已切换: generation=${transition.generation}"
            )
            if (IslandViewRegistry.isCurrent(transition.realHost) &&
                presentationState.isPlaybackActive() &&
                LyriconDataBridge.currentLyricPackageName ==
                transition.realHost.packageName
            ) {
                IslandLyricPlaybackController.resumeInjectedLyricProgress(
                    transition.realHost.root,
                    LyriconDataBridge.currentPosition
                )
            }
            return restoreRealHostFromCurrentEvidence(realRoot)
        }
        if (!IslandViewRegistry.isCurrent(transition.realHost)) {
            HookLogger.d(
                TAG,
                "忽略过期 fake 结束回调: generation=${transition.generation}"
            )
            return restoreRealHostFromCurrentEvidence(realRoot)
        }
        val result = reconcileRealRoot(
            root = realRoot,
            owner = IslandRenderPolicy.OwnerEvidence.Media(
                transition.realHost.packageName
            ),
            reason = IslandReconcileReason.FAKE_FINISHED
        )
        if (result.isTarget) {
            IslandHostFacade.showRealHost(realRoot)
            if (presentationState.isPlaybackActive()) {
                IslandLyricPlaybackController.resumeInjectedLyricProgress(
                    realRoot,
                    LyriconDataBridge.currentPosition
                )
            }
            HookLogger.d(
                TAG,
                "fake 交接完成: generation=${transition.generation}, " +
                        "lyricChanged=${transition.lyricVersion != LyriconDataBridge.versionCounter.get()}"
            )
        }
        return result
    }

    private fun restoreRealHostFromCurrentEvidence(
        realRoot: ViewGroup
    ): ReconcileResult {
        val owner = ownerEvidence(
            IslandTextHookerSupport.extractIslandDataFromContentOrReal(realRoot)
        )
        if (owner !is IslandRenderPolicy.OwnerEvidence.Media) {
            return ReconcileResult.noOp(
                if (owner == IslandRenderPolicy.OwnerEvidence.NotMedia) {
                    IslandRenderPolicy.Decision.NOT_MEDIA
                } else {
                    IslandRenderPolicy.Decision.PENDING
                }
            )
        }
        IslandViewRegistry.register(realRoot, owner.packageName)
        observeRealHostAttachment(realRoot)
        val result = reconcileRealRoot(
            root = realRoot,
            owner = owner,
            reason = IslandReconcileReason.FAKE_FINISHED
        )
        if (result.isTarget) {
            IslandHostFacade.showRealHost(realRoot)
        }
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
        return reconcileRealRoot(
            root = token.root,
            owner = IslandRenderPolicy.OwnerEvidence.Media(token.packageName),
            reason = reason
        )
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
        discardFakeTransitionsForHost(token)
        return IslandInjectionReconciler.restoreNative(
            token.root,
            IslandInjectionReconciler.Target.RealRoot
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
        discardFakeTransitionsForHost(token)
        return IslandInjectionReconciler.restoreNative(
            token.root,
            IslandInjectionReconciler.Target.RealRoot
        )
    }

    fun snapshotAttachedHosts(
        packageName: String? = null
    ): List<IslandViewRegistry.HostToken> {
        return IslandViewRegistry.snapshotAttached(packageName)
    }

    fun snapshotAttachedInjectedHosts(
        packageName: String? = null
    ): List<IslandViewRegistry.InjectedHostToken> {
        return IslandViewRegistry.snapshotAttachedInjectedViews(packageName)
    }

    fun isCurrentHost(token: IslandViewRegistry.HostToken): Boolean {
        return IslandViewRegistry.isCurrent(token)
    }

    fun isHostFrozenForFakeTransition(
        token: IslandViewRegistry.HostToken
    ): Boolean {
        if (!IslandViewRegistry.isCurrent(token)) return false
        return IslandFakeTransitionRegistry.isHostFrozen(token)
    }

    fun refreshInjectedViewIndex(token: IslandViewRegistry.HostToken) {
        if (IslandViewRegistry.isCurrent(token)) {
            IslandViewRegistry.refreshInjectedViews(token.root)
        }
    }

    private fun reconcileRealRoot(
        root: ViewGroup,
        owner: IslandRenderPolicy.OwnerEvidence,
        reason: IslandReconcileReason
    ): ReconcileResult {
        val decision = evaluate(owner)
        val token = IslandViewRegistry.tokenFor(root)
        if (reason != IslandReconcileReason.FAKE_FINISHED &&
            decision == IslandRenderPolicy.Decision.TARGET &&
            token != null &&
            isHostFrozenForFakeTransition(token)
        ) {
            return ReconcileResult.noOp(IslandRenderPolicy.Decision.TARGET).also {
                logReconcile(
                    root = root,
                    target = IslandInjectionReconciler.Target.RealRoot,
                    owner = owner,
                    reason = reason,
                    result = it
                )
            }
        }
        val mutation = when (decision) {
            IslandRenderPolicy.Decision.TARGET -> {
                IslandInjectionReconciler.show(
                    root = root,
                    target = IslandInjectionReconciler.Target.RealRoot,
                    options = IslandReconcileOptions.realRoot(reason)
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
        reason: IslandReconcileReason
    ): ReconcileResult {
        val decision = evaluate(owner)
        val target = IslandInjectionReconciler.Target.RealModule(moduleType)
        val mutation = when (decision) {
            IslandRenderPolicy.Decision.TARGET -> {
                IslandInjectionReconciler.show(
                    root = holderRoot,
                    target = target,
                    options = IslandReconcileOptions.module(reason)
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
        val token = IslandViewRegistry.tokenFor(root)
        token?.let(::discardFakeTransitionsForHost)
        stopObservingRealHostAttachment(root)
        token?.let(IslandViewRegistry::unregister)
        val mutation = IslandInjectionReconciler.restoreNative(
            root,
            IslandInjectionReconciler.Target.RealRoot
        )
        return ReconcileResult(IslandRenderPolicy.Decision.NOT_MEDIA, mutation)
    }

    private fun discardFakeTransitionsForHost(
        token: IslandViewRegistry.HostToken
    ) {
        IslandFakeTransitionRegistry.discardForHost(token)
    }

    private fun evaluate(
        owner: IslandRenderPolicy.OwnerEvidence
    ): IslandRenderPolicy.Decision {
        return decisionEvaluator.evaluate(owner)
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

    private fun observeRealHostAttachment(root: ViewGroup) {
        hostAttachmentObserver.observe(root)
    }

    private fun stopObservingRealHostAttachment(root: ViewGroup) {
        hostAttachmentObserver.stop(root)
    }
}
