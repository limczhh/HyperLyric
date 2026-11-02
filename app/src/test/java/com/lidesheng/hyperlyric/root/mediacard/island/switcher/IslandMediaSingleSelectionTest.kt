package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandMediaSingleSelectionTest {
    @Test fun browsingDoesNotChangeTheCompactIslandOrItsNextExpansion() {
        val selection = IslandMediaSingleSelection()
        val order = listOf("A", "B", "C")
        selection.setExpanded(true)
        selection.select("B")
        assertEquals("B", selection.selectedKey(order))
        selection.setExpanded(false)
        assertEquals("A", selection.selectedKey(order))
        selection.setExpanded(true)
        assertEquals("A", selection.selectedKey(order))
        assertEquals(listOf("A", "B", "C"), order)
    }

    @Test fun collapseUsesTheLatestActivityOrderInsteadOfThePreviouslyBrowsedSession() {
        val selection = IslandMediaSingleSelection()
        selection.setExpanded(true)
        selection.select("B")
        assertEquals("B", selection.selectedKey(listOf("C", "A", "B")))
        selection.setExpanded(false)
        assertEquals("C", selection.selectedKey(listOf("C", "A", "B")))
        selection.select("B")
        assertEquals("C", selection.selectedKey(listOf("C", "A", "B")))
    }

    @Test fun removingTheBrowsedSessionReturnsToTheFirstAvailableSession() {
        val selection = IslandMediaSingleSelection()
        selection.setExpanded(true)
        selection.select("B")
        assertEquals("A", selection.selectedKey(listOf("A", "C")))
        assertEquals("A", selection.selectedKey(listOf("A", "B", "C")))
    }
}
