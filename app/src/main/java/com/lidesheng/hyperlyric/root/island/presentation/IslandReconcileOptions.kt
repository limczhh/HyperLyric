package com.lidesheng.hyperlyric.root.island.presentation


/**
 * Maps a presentation reconciliation reason to the mutation options required by each target.
 *
 * The coordinator decides whether a target should be shown; this object only describes how that
 * target is restored or updated. Keeping the mapping here prevents target-specific mutation flags
 * from being mixed with lifecycle and policy decisions.
 */
internal object IslandReconcileOptions {
    fun realRoot(
        reason: IslandReconcileReason
    ): IslandInjectionReconciler.ShowOptions {
        return when (reason) {
            IslandReconcileReason.PRE_SYSTEM_UPDATE ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_EXISTING,
                    content = IslandInjectionReconciler.ContentMode.WHEN_LAYOUT_CHANGED
                )

            IslandReconcileReason.SYSTEM_UPDATE_COMPLETE ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.WHEN_RESTORING_EXISTING
                )

            IslandReconcileReason.HOST_ATTACHED ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.WHEN_RESTORING_EXISTING
                )

            IslandReconcileReason.SETTINGS_CHANGED,
            IslandReconcileReason.STABLE_REFRESH ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE
                )

            IslandReconcileReason.LYRIC_SELF_HEAL ->
                IslandInjectionReconciler.ShowOptions(
                    // Streaming sources can update the line after the previous lifecycle hid
                    // the existing wrapper. Tags still exist in that state, so self-heal must
                    // restore visibility instead of treating structure presence as readiness.
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE
                )

            IslandReconcileReason.PLAYBACK_RESUME ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE
                )

            IslandReconcileReason.MODULE_FIRST_BIND,
            IslandReconcileReason.MODULE_UPDATED ->
                error("Unsupported real-root reason: $reason")
        }
    }

    fun module(
        reason: IslandReconcileReason
    ): IslandInjectionReconciler.ShowOptions {
        return when (reason) {
            IslandReconcileReason.MODULE_FIRST_BIND ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.WHEN_RESTORING_EXISTING
                )

            IslandReconcileReason.MODULE_UPDATED ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_EXISTING,
                    content = IslandInjectionReconciler.ContentMode.WHEN_LAYOUT_CHANGED
                )

            IslandReconcileReason.HOST_ATTACHED ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.WHEN_RESTORING_EXISTING
                )

            IslandReconcileReason.SETTINGS_CHANGED,
            IslandReconcileReason.STABLE_REFRESH ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE
                )

            IslandReconcileReason.LYRIC_SELF_HEAL,
            IslandReconcileReason.PLAYBACK_RESUME ->
                IslandInjectionReconciler.ShowOptions(
                    structure = IslandInjectionReconciler.StructureMode.RESTORE_OR_ENSURE,
                    content = IslandInjectionReconciler.ContentMode.NONE
                )

            else -> error("Unsupported module reason: $reason")
        }
    }
}
