package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

internal data class NotificationMediaSelectionSnapshot(
    val entries: List<Pair<String, Any>>,
    val selectedKey: String?
) {
    val selectedIndex: Int
        get() = selectedKey?.let { key ->
            entries.indexOfFirst { entry -> entry.first == key }
        }?.takeIf { it >= 0 } ?: -1
}

/**
 * Reflection-independent view of the target SystemUI MediaData model.
 *
 * The concrete MediaData class belongs to SystemUI, so the module must not put
 * that private class in its own compile-time API.
 */
internal interface NotificationMediaDataAccessor {
    fun notificationKey(data: Any): String?

    fun sessionToken(data: Any): Any?

    fun isActive(data: Any): Boolean

    fun isPlaying(data: Any): Boolean?

    fun sortKey(sortKey: Any): String?

    fun sortData(sortKey: Any): Any?
}

/**
 * Owns the media entries, their canonical page order, and the selected key.
 *
 * The order provider is the only ordering authority. The coordinator never
 * freezes its own copy of the order for a playing session; this keeps the
 * renderer and the indicator on the same page model after an activity change.
 */
internal class NotificationMediaSelectionCoordinator(
    private val accessor: NotificationMediaDataAccessor,
    private val orderedKeyProvider: () -> List<String>,
    private val bindSelected: (Any) -> Unit,
    private val maxPageCount: Int = Int.MAX_VALUE
) {
    private data class Entry(
        val key: String,
        val data: Any,
        val sessionToken: Any?
    )

    private val entries = LinkedHashMap<String, Entry>()
    private val orderedKeys = ArrayList<String>()

    private var selectedKey: String? = null
    private var selectedToken: Any? = null
    private var selectedByUser = false

    val size: Int
        get() = orderedKeys.size

    val selectedIndex: Int
        get() = selectedKey?.let(orderedKeys::indexOf)?.takeIf { it >= 0 } ?: -1

    /**
     * Returns the bounded page model. If the user has not manually selected a
     * page, page zero follows the canonical activity order automatically.
     */
    fun snapshot(maxEntries: Int = Int.MAX_VALUE): NotificationMediaSelectionSnapshot {
        reconcileAutomaticSelection()
        val allEntries = orderedKeys.mapNotNull { key ->
            entries[key]?.let { key to it.data }
        }
        if (allEntries.size <= maxEntries) {
            return NotificationMediaSelectionSnapshot(allEntries, selectedKey)
        }

        val limit = maxEntries.coerceAtLeast(1)
        val priorityKeys = LinkedHashSet<String>()
        selectedKey?.takeIf { it in entries }?.let(priorityKeys::add)
        // orderedKeys already contains the policy's direct playback signals;
        // do not re-read the possibly stale MediaData boolean here.
        orderedKeys.forEach { key -> priorityKeys += key }

        val keptKeys = priorityKeys.take(limit).toHashSet()
        val visibleEntries = allEntries.filter { it.first in keptKeys }
        val visibleSelectedKey = selectedKey?.takeIf { key ->
            visibleEntries.any { it.first == key }
        } ?: visibleEntries.firstOrNull()?.first
        return NotificationMediaSelectionSnapshot(visibleEntries, visibleSelectedKey)
    }

    fun seed(initialEntries: List<Pair<String, Any>>) {
        entries.clear()
        orderedKeys.clear()
        selectedKey = null
        selectedToken = null
        selectedByUser = false
        initialEntries.forEach { (key, data) ->
            if (key.isNotEmpty() && accessor.isActive(data)) {
                entries[key] = Entry(key, data, accessor.sessionToken(data))
            }
        }
        reorder()
        adoptFirstSelection()
    }

    fun onMediaDataLoaded(key: String, oldKey: String?, data: Any) {
        val previousSelectedKey = selectedKey
        val previousSelectedData = selectedKey?.let { entries[it]?.data }
        if (oldKey != null && oldKey != key) {
            val oldWasSelected = selectedKey == oldKey
            entries.remove(oldKey)
            if (oldWasSelected) selectedKey = key
        }

        if (accessor.isActive(data)) {
            entries[key] = Entry(key, data, accessor.sessionToken(data))
        } else {
            entries.remove(key)
        }

        reorder()
        if (selectedKey == null || selectedKey !in entries) {
            selectedByUser = false
            adoptFirstSelection()
            if (previousSelectedKey != null) bindCurrentSelection()
            return
        }

        updateSelectedToken()
        var selectionChanged = false
        if (!selectedByUser) {
            val firstKey = orderedKeys.firstOrNull()
            if (firstKey != null && firstKey != selectedKey) {
                selectedKey = firstKey
                updateSelectedToken()
                bindCurrentSelection()
                selectionChanged = true
            }
        }

        val selectedDataChanged = selectedKey?.let { entries[it]?.data } !== previousSelectedData
        if (!selectionChanged && (selectedByUser || selectedKey == key) &&
            (selectedKey != previousSelectedKey || selectedDataChanged)
        ) {
            bindCurrentSelection()
        }
    }

    fun onMediaDataRemoved(key: String) {
        val previousSelectedKey = selectedKey
        entries.remove(key)
        reorder()
        if (selectedKey == key || selectedKey !in entries) {
            selectedByUser = false
            adoptFirstSelection()
            if (previousSelectedKey != null) bindCurrentSelection()
        } else {
            updateSelectedToken()
            if (!selectedByUser) {
                val firstKey = orderedKeys.firstOrNull()
                if (firstKey != null && firstKey != selectedKey) {
                    selectedKey = firstKey
                    updateSelectedToken()
                    bindCurrentSelection()
                }
            }
        }
    }

    /**
     * Observes the native one-card binder. A synthetic bind issued by this
     * coordinator is a content refresh, not a new ordering signal.
     */
    fun onNativeBind(data: Any?, synthetic: Boolean = false) {
        if (data == null) {
            if (entries.isEmpty()) resetSelection()
            return
        }

        val key = accessor.notificationKey(data)
        val incomingToken = accessor.sessionToken(data)
        val knownToken = key?.let { entries[it]?.sessionToken }
        val tokenChangedBeforeMediaDataUpdate = knownToken != null &&
            incomingToken != null && knownToken != incomingToken

        reorder()
        val userSelection = selectedKey?.takeIf { selectedByUser && it in entries }
        if (userSelection != null) {
            if (key == userSelection && tokenChangedBeforeMediaDataUpdate) {
                if (!synthetic) bindCurrentSelection()
                return
            }
            if (key != userSelection) {
                if (!synthetic) bindCurrentSelection()
                return
            }
            if (synthetic) {
                updateSelectedToken()
                return
            }
            updateSelectedToken()
            return
        }

        if (!synthetic) {
            val nextSelectedKey = orderedKeys.firstOrNull()
                ?: key?.takeIf { it in entries }
            val shouldBindSelected = nextSelectedKey != null && nextSelectedKey != key
            selectedKey = nextSelectedKey
            selectedToken = selectedKey?.let { entries[it]?.sessionToken } ?: incomingToken
            selectedByUser = false
            if (shouldBindSelected) bindCurrentSelection()
            return
        }

        if (tokenChangedBeforeMediaDataUpdate) {
            bindCurrentSelection()
            return
        }
        selectedKey = orderedKeys.firstOrNull()
            ?: key?.takeIf { it in entries }
        updateSelectedToken()
    }

    /** Rebuilds the page model after a direct MediaController activity signal. */
    fun onActivityOrderChanged() {
        val previousSelectedKey = selectedKey
        reorder()
        if (!selectedByUser) {
            val firstKey = orderedKeys.firstOrNull()
            if (firstKey == null) {
                resetSelection()
            } else if (firstKey != selectedKey) {
                selectedKey = firstKey
                updateSelectedToken()
                if (previousSelectedKey != null) bindCurrentSelection()
            }
        }
    }

    fun selectRelative(step: Int) {
        val pageKeys = visiblePageKeys()
        if (step == 0 || pageKeys.size < 2) return

        val currentPageIndex = pageKeys.indexOf(selectedKey).takeIf { it >= 0 } ?: 0
        selectIndex(currentPageIndex + step)
    }

    fun selectIndex(index: Int) {
        val pageKeys = visiblePageKeys()
        if (pageKeys.isEmpty()) return

        val currentPageIndex = pageKeys.indexOf(selectedKey).takeIf { it >= 0 } ?: -1
        val targetIndex = index.coerceIn(0, pageKeys.lastIndex)
        if (targetIndex == currentPageIndex && selectedKey == pageKeys[targetIndex]) return

        selectedKey = pageKeys[targetIndex]
        selectedByUser = true
        updateSelectedToken()
        bindCurrentSelection()
    }

    fun selectKey(key: String) {
        val index = visiblePageKeys().indexOf(key)
        if (index >= 0) selectIndex(index)
    }

    fun onDetached() {
        resetSelection()
        entries.clear()
        orderedKeys.clear()
    }

    private fun bindCurrentSelection() {
        val key = selectedKey ?: return
        val entry = entries[key] ?: return
        if (selectedToken != null && entry.sessionToken != null &&
            selectedToken != entry.sessionToken
        ) {
            selectedToken = entry.sessionToken
        }
        bindSelected(entry.data)
    }

    private fun adoptFirstSelection() {
        if (orderedKeys.isEmpty()) {
            resetSelection()
            return
        }
        selectedKey = orderedKeys.first()
        updateSelectedToken()
    }

    private fun reconcileAutomaticSelection() {
        if (selectedByUser) return
        val firstKey = orderedKeys.firstOrNull()
        if (firstKey == null) {
            resetSelection()
        } else if (selectedKey != firstKey) {
            selectedKey = firstKey
            updateSelectedToken()
        }
    }

    private fun updateSelectedToken() {
        selectedToken = selectedKey?.let { entries[it]?.sessionToken }
    }

    private fun visiblePageKeys(): List<String> {
        return snapshot(maxPageCount).entries.map { it.first }
    }

    private fun resetSelection() {
        selectedKey = null
        selectedToken = null
        selectedByUser = false
    }

    private fun reorder() {
        val preferredKeys = runCatching { orderedKeyProvider() }
            .getOrDefault(emptyList())
        orderedKeys.clear()
        preferredKeys.forEach { key ->
            if (key in entries && key !in orderedKeys) orderedKeys += key
        }
        entries.keys.forEach { key ->
            if (key !in orderedKeys) orderedKeys += key
        }
    }
}
