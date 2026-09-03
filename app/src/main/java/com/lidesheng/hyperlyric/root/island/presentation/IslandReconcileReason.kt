package com.lidesheng.hyperlyric.root.island.presentation

/**
 * Describes why a Super Island target is being reconciled.
 *
 * The reason is shared by lifecycle adapters, the presentation coordinator, and target option
 * mapping. It is intentionally independent from any one coordinator implementation.
 */
internal enum class IslandReconcileReason {
    PRE_SYSTEM_UPDATE,
    SYSTEM_UPDATE_COMPLETE,
    MODULE_FIRST_BIND,
    MODULE_UPDATED,
    HOST_ATTACHED,
    SETTINGS_CHANGED,
    STABLE_REFRESH,
    LYRIC_SELF_HEAL,
    PLAYBACK_RESUME
}
