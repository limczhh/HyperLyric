package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class ControlCenterMediaActionStoreTest {
    private fun action(name: String) = ControlCenterMediaAction(ColorDrawable(Color.WHITE), name, Runnable {})

    @Test
    fun openingBackgroundPlayerCannotSupplyButtonsForTheDisplayedPlayer() {
        val store = ControlCenterMediaActionStore()
        store.update("A", "player.A", "token.A", "Song A", "Artist", ControlCenterMediaActions())
        val buttons = ControlCenterMediaActions(action("Like"), action("Desktop lyrics"))
        store.update("B", "player.B", "token.B", "Song B", "Artist", buttons)
        assertEquals(ControlCenterMediaActions(), store.resolve("player.A", "Song A", "Artist"))
        assertSame(buttons, store.resolve("player.B", "Song B", "Artist"))
        assertEquals(ControlCenterMediaActions(), store.resolve("player.B", "Song A", "Artist"))
        assertEquals(ControlCenterMediaActions(), store.resolve(null, "Song B", "Artist"))
    }

    @Test
    fun equalSongTitlesAcrossAppsDoNotBypassPackageMatching() {
        val store = ControlCenterMediaActionStore()
        store.update("B", "player.B", "token.B", "Same song", "Same artist",
            ControlCenterMediaActions(action("Like"), action("Desktop lyrics")))
        assertEquals(ControlCenterMediaActions(), store.resolve("player.A", "Same song", "Same artist"))
    }

    @Test
    fun ambiguousSessionsFailClosedAndRemovedActionsClearTheSlots() {
        val store = ControlCenterMediaActionStore()
        val buttons = ControlCenterMediaActions(action("Like"), action("Desktop lyrics"))
        store.update("A1", "player.A", "token.1", "Song", "Artist", buttons)
        store.update("A2", "player.A", "token.2", "Song", "Artist", buttons)
        assertEquals(ControlCenterMediaActions(), store.resolve("player.A", "Song", "Artist"))
        store.update("A2", "player.A", null, "Song", "Artist", buttons)
        assertSame(buttons, store.resolve("player.A", "Song", "Artist"))
        store.update("A1", "player.A", "token.1", "Song", "Artist", ControlCenterMediaActions())
        assertEquals(ControlCenterMediaActions(), store.resolve("player.A", "Song", "Artist"))
    }

    @Test
    fun switchingTracksKeepsButtonsWhenNotificationUpdatesBeforeHeaderLabels() {
        val store = ControlCenterMediaActionStore()
        val binding = ControlCenterMediaActionBinding(store)
        val first = ControlCenterMediaActions(action("Like first"), action("Lyrics"))
        val next = ControlCenterMediaActions(action("Like next"), action("Lyrics"))
        store.update("A", "player.A", "token.A", "First", "Artist", first)
        assertSame(first, binding.resolve("player.A", "First", "Artist"))
        store.update("A", "player.A", "token.A", "Next", "Artist", next)
        assertSame(next, binding.resolve("player.A", "First", "Artist"))
        assertSame(next, binding.resolve("player.A", "Next", "Artist"))
    }

    @Test
    fun switchingTracksKeepsButtonsWhenHeaderLabelsUpdateBeforeNotification() {
        val store = ControlCenterMediaActionStore()
        val binding = ControlCenterMediaActionBinding(store)
        val first = ControlCenterMediaActions(action("Like"), action("Lyrics"))
        store.update("A", "player.A", "token.A", "First", "Artist", first)
        assertSame(first, binding.resolve("player.A", "First", "Artist"))
        assertSame(first, binding.resolve("player.A", "Next", "Artist"))
        // An actually unavailable action must still clear immediately; this
        // fix retains the session binding, not stale actions from old tracks.
        store.update("A", "player.A", "token.A", "Next", "Artist", ControlCenterMediaActions())
        assertEquals(ControlCenterMediaActions(), binding.resolve("player.A", "Next", "Artist"))
    }

    @Test
    fun backgroundSessionsCannotReplaceTheBoundSessionOrAnotherHeadersBinding() {
        val store = ControlCenterMediaActionStore()
        val firstHeader = ControlCenterMediaActionBinding(store)
        val secondHeader = ControlCenterMediaActionBinding(store)
        val first = ControlCenterMediaActions(action("First"))
        val background = ControlCenterMediaActions(action("Background"))
        store.update("A1", "player.A", "token.1", "First", "Artist", first)
        assertSame(first, firstHeader.resolve("player.A", "First", "Artist"))
        store.update("A2", "player.A", "token.2", "Second", "Artist", background)
        assertSame(background, secondHeader.resolve("player.A", "Second", "Artist"))
        assertSame(first, firstHeader.resolve("player.A", "First", "Artist"))
        store.update("B", "player.B", "token.B", "First", "Artist", background)
        assertSame(first, firstHeader.resolve("player.A", "First", "Artist"))
        assertEquals(ControlCenterMediaActions(), firstHeader.resolve("player.B", "Other", "Artist"))
    }

    @Test
    fun replacingTheActualSessionRequiresMatchingTheNewTrackBeforeRebinding() {
        val store = ControlCenterMediaActionStore()
        val binding = ControlCenterMediaActionBinding(store)
        val first = ControlCenterMediaActions(action("First"))
        val replacement = ControlCenterMediaActions(action("Replacement"))
        store.update("A", "player.A", "old-token", "First", "Artist", first)
        assertSame(first, binding.resolve("player.A", "First", "Artist"))
        store.update("A", "player.A", "new-token", "New session", "Artist", replacement)
        assertEquals(ControlCenterMediaActions(), binding.resolve("player.A", "First", "Artist"))
        assertSame(replacement, binding.resolve("player.A", "New session", "Artist"))
    }
}
