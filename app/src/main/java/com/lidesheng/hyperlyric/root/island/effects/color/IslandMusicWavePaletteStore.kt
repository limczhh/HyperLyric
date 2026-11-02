package com.lidesheng.hyperlyric.root.island.effects.color

/** Cover holders produce palettes; wave holders of the same island consume them. */
internal class IslandMusicWavePaletteStore {
    data class Palette(val cover: IntArray?, val native: Array<Int>?)
    private val palettes = LinkedHashMap<String, Palette>()

    fun remember(key: String, cover: IntArray?, native: Array<Int>?) {
        val previous = palettes[key]
        palettes[key] = Palette(
            cover?.copyOf() ?: previous?.cover,
            native?.copyOf() ?: previous?.native,
        )
    }
    fun get(key: String?): Palette? = palettes[key]
    fun retain(keys: Set<String>) { palettes.keys.retainAll(keys) }
    fun clear() = palettes.clear()
}
