package com.lidesheng.hyperlyric.root.mediacard.island.switcher

/** Browsing belongs to one expansion; the compact island always follows activity order. */
internal class IslandMediaSingleSelection {
    private var expanded = false
    private var browsedKey: String? = null

    fun setExpanded(value: Boolean) {
        expanded = value
        if (!value) browsedKey = null
    }

    fun select(key: String) {
        if (expanded) browsedKey = key
    }

    fun selectedKey(orderedKeys: List<String>): String? {
        if (!expanded || browsedKey !in orderedKeys) browsedKey = null
        return browsedKey ?: orderedKeys.firstOrNull()
    }
}
