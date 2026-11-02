package com.lidesheng.hyperlyric.root.island.effects.color

import org.junit.Assert.*
import org.junit.Test

class IslandMusicWavePaletteStoreTest {
    @Test fun coverAndWaveHoldersResolveByIslandKeyInsteadOfHolderIdentity() {
        val store = IslandMusicWavePaletteStore()
        val coverHolderKey = String(charArrayOf('a'))
        val waveHolderKey = String(charArrayOf('a'))
        assertNotSame(coverHolderKey, waveHolderKey)
        store.remember(coverHolderKey, intArrayOf(1, 2), arrayOf(3, 4))
        assertArrayEquals(intArrayOf(1, 2), store.get(waveHolderKey)!!.cover)
        assertArrayEquals(arrayOf(3, 4), store.get(waveHolderKey)!!.native)
    }

    @Test fun updatingAnotherIslandCannotChangeTheFirstIslandsPalette() {
        val store = IslandMusicWavePaletteStore()
        val native = arrayOf(3, 4)
        store.remember("a", intArrayOf(1, 2), native)
        store.remember("b", intArrayOf(5, 6), arrayOf(7, 8))
        native[0] = 99
        store.remember("b", null, arrayOf(9, 10))
        assertArrayEquals(arrayOf(3, 4), store.get("a")!!.native)
        assertArrayEquals(intArrayOf(5, 6), store.get("b")!!.cover)
        assertArrayEquals(arrayOf(9, 10), store.get("b")!!.native)
    }

    @Test fun discardingAnIslandDoesNotDiscardOtherLiveIslands() {
        val store = IslandMusicWavePaletteStore()
        store.remember("a", null, arrayOf(1, 2))
        store.remember("b", null, arrayOf(3, 4))
        store.retain(setOf("b"))
        assertNull(store.get("a"))
        assertArrayEquals(arrayOf(3, 4), store.get("b")!!.native)
        store.clear()
        assertNull(store.get("b"))
    }
}
