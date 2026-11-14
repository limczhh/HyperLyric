package com.lidesheng.hyperlyric.root.source

/** Playback transitions, rather than content/position updates, establish activity order. */
internal class LyricPlaybackOrder<K> {
    private data class State(var active: Boolean, var sequence: Long)
    private val states = LinkedHashMap<K, State>()
    private var sequence = 0L

    fun seed(keys: List<Pair<K, Boolean>>) {
        keys.asReversed().forEach { (key, active) ->
            if (key !in states) states[key] = State(active, ++sequence)
        }
    }

    fun update(key: K, active: Boolean) {
        val previous = states[key]
        if (previous == null) states[key] = State(active, ++sequence)
        else {
            if (active && !previous.active) previous.sequence = ++sequence
            previous.active = active
        }
    }

    fun remove(key: K) { states.remove(key) }
    fun clear() { states.clear(); sequence = 0L }
    fun isActive(key: K): Boolean = states[key]?.active == true
    fun rank(key: K): Long = states[key]?.sequence ?: Long.MIN_VALUE
}
