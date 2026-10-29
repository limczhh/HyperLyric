package com.lidesheng.hyperlyric.root.island.effects

import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.effects.spectrum.IslandMusicWaveSpectrumHooker

/** Keeps the native color override and the optional spectrum view in sync. */
internal object IslandMusicWaveEffects {
    fun refresh() {
        IslandMusicWaveColorHooker.refresh()
        IslandMusicWaveSpectrumHooker.refresh()
    }
}
