package com.lidesheng.hyperlyric.root.island.presentation

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.island.content.IslandLyricContentRefresher
import com.lidesheng.hyperlyric.root.island.host.IslandHostFacade
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.sizing.IslandDynamicWidthCoordinator
import com.lidesheng.hyperlyric.root.island.structure.IslandSlotStructureInjector
import com.lidesheng.hyperlyric.root.island.view.IslandLyricViewController

/**
 * The single upper-level entry for Super Island lyric structure, visibility,
 * native restoration, and SystemUI relayout mutations.
 *
 * High-frequency lyric, position, and color content updates intentionally use
 * indexed-view fast paths. The underlying injector still reports a coarse
 * Boolean, so the first
 * structured result deliberately calls it [Result.layoutMayHaveChanged].
 */
internal object IslandInjectionReconciler {

    sealed interface Target {
        data object RealRoot : Target
        data class RealModule(val moduleType: String?) : Target
        data class FakeModule(val moduleType: String?) : Target
    }

    enum class StructureMode {
        ENSURE,
        RESTORE_EXISTING,
        RESTORE_OR_ENSURE
    }

    enum class ContentMode {
        NONE,
        WHEN_LAYOUT_CHANGED,
        WHEN_RESTORING_EXISTING
    }

    data class ShowOptions(
        val structure: StructureMode,
        val content: ContentMode
    )

    enum class Outcome {
        APPLIED,
        RESTORED_NATIVE,
        NO_OP
    }

    data class Result(
        val outcome: Outcome,
        val layoutMayHaveChanged: Boolean,
        val contentChanged: Boolean,
        val injectedSlotsPresent: Boolean?,
        val relayoutRequested: Boolean
    ) {
        companion object {
            val NO_OP = Result(
                outcome = Outcome.NO_OP,
                layoutMayHaveChanged = false,
                contentChanged = false,
                injectedSlotsPresent = null,
                relayoutRequested = false
            )
        }
    }

    fun show(
        root: ViewGroup,
        target: Target,
        options: ShowOptions,
        playbackActive: Boolean
    ): Result {
        val hadInjectedSlots = IslandSlotStructureInjector.hasInjectedLyricText(root)
        val layoutMayHaveChanged = when (target) {
            Target.RealRoot -> reconcileRootStructure(
                root = root,
                hadInjectedSlots = hadInjectedSlots,
                options = options,
                playbackActive = playbackActive
            )

            is Target.RealModule -> reconcileModuleStructure(
                root = root,
                moduleType = target.moduleType,
                hadInjectedSlots = hadInjectedSlots,
                options = options,
                playbackActive = playbackActive
            )

            is Target.FakeModule -> reconcileModuleStructure(
                root = root,
                moduleType = target.moduleType,
                hadInjectedSlots = hadInjectedSlots,
                options = options,
                playbackActive = playbackActive
            )
        }

        var contentWasRefreshed = false
        val contentChanged = when (options.content) {
            ContentMode.NONE -> false
            ContentMode.WHEN_LAYOUT_CHANGED -> {
                if (!layoutMayHaveChanged) {
                    false
                } else {
                    contentWasRefreshed = true
                    IslandLyricContentRefresher.refreshCurrentContent(
                        root,
                        playbackActive = playbackActive
                    )
                }
            }

            ContentMode.WHEN_RESTORING_EXISTING -> {
                if (!hadInjectedSlots) {
                    false
                } else {
                    contentWasRefreshed = true
                    IslandLyricContentRefresher.refreshCurrentContent(
                        root,
                        playbackActive = playbackActive
                    )
                }
            }

        }

        if (contentWasRefreshed) {
            IslandSlotStructureInjector.linkViews(root)
        }
        IslandViewRegistry.refreshInjectedViews(root)
        val injectedSlotsPresent = IslandSlotStructureInjector.hasInjectedLyricText(root)
        if (target == Target.RealRoot && layoutMayHaveChanged) {
            // Restoring an existing hidden wrapper changes when its lyric view can be measured.
            // Content may be a signature no-op, so request width calculation independently of it.
            IslandDynamicWidthCoordinator.requestRefresh(root)
        }
        val relayoutRequested = target == Target.RealRoot && layoutMayHaveChanged
        if (relayoutRequested) {
            IslandHostFacade.triggerSystemRelayout(root)
        }

        return Result(
            outcome = if (layoutMayHaveChanged || contentChanged) {
                Outcome.APPLIED
            } else {
                Outcome.NO_OP
            },
            layoutMayHaveChanged = layoutMayHaveChanged,
            contentChanged = contentChanged,
            injectedSlotsPresent = injectedSlotsPresent,
            relayoutRequested = relayoutRequested
        )
    }

    fun restoreNative(root: ViewGroup, target: Target): Result {
        // Hidden island projections keep their renderer clock alive by design. Stop the tree and
        // cancel a delayed content animation before restoring Xiaomi's native children, otherwise
        // a non-target/recycled holder can continue consuming frames and later commit stale state.
        IslandLyricViewController.stopRecursively(root)
        val layoutMayHaveChanged = IslandHostFacade.clearInjectedViews(root)
        IslandViewRegistry.refreshInjectedViews(root)
        val relayoutRequested = target == Target.RealRoot && layoutMayHaveChanged
        if (relayoutRequested) {
            IslandHostFacade.triggerSystemRelayout(root)
        }
        return Result(
            outcome = if (layoutMayHaveChanged) {
                Outcome.RESTORED_NATIVE
            } else {
                Outcome.NO_OP
            },
            layoutMayHaveChanged = layoutMayHaveChanged,
            contentChanged = false,
            injectedSlotsPresent = IslandSlotStructureInjector.hasInjectedLyricText(root),
            relayoutRequested = relayoutRequested
        )
    }

    private fun reconcileRootStructure(
        root: ViewGroup,
        hadInjectedSlots: Boolean,
        options: ShowOptions,
        playbackActive: Boolean
    ): Boolean {
        return when (options.structure) {
            StructureMode.ENSURE -> IslandSlotStructureInjector.injectSlots(
                root,
                playbackActive = playbackActive
            )

            StructureMode.RESTORE_EXISTING -> {
                IslandSlotStructureInjector.restoreExistingSlotsLightweight(root)
            }

            StructureMode.RESTORE_OR_ENSURE -> {
                if (hadInjectedSlots) {
                    IslandSlotStructureInjector.restoreExistingSlotsLightweight(root)
                } else {
                    IslandSlotStructureInjector.injectSlots(
                        root,
                        playbackActive = playbackActive
                    )
                }
            }
        }
    }

    private fun reconcileModuleStructure(
        root: ViewGroup,
        moduleType: String?,
        hadInjectedSlots: Boolean,
        options: ShowOptions,
        playbackActive: Boolean
    ): Boolean {
        return when (options.structure) {
            StructureMode.RESTORE_EXISTING -> {
                IslandSlotStructureInjector.restoreExistingModuleSlotLightweight(root, moduleType)
            }

            StructureMode.ENSURE -> IslandSlotStructureInjector.injectSlots(
                root,
                playbackActive = playbackActive
            )

            StructureMode.RESTORE_OR_ENSURE -> {
                if (hadInjectedSlots) {
                    IslandSlotStructureInjector.restoreExistingModuleSlotLightweight(root, moduleType)
                } else {
                    IslandSlotStructureInjector.injectSlots(
                        root,
                        playbackActive = playbackActive
                    )
                }
            }
        }
    }
}
