package com.lidesheng.hyperlyric.root

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

/**
 * Owns every hook installed by one module generation.
 *
 * API 102 delivers a module-level reload callback, but the module's replacement boundary is the
 * Super Island runtime. Handles belonging to media cards remain installed in their original
 * generation; Super Island, lyric, color, lifecycle and whitelist handles are replaceable.
 *
 * API 101 is still supported: ids and handle replacement are only called after checking the
 * runtime API version, so the cold-start path does not resolve API 102 methods on an API 101
 * framework.
 */
internal object HookRuntimeRegistry {
    private const val ID_PREFIX = "hyperlyric"

    private val lock = Any()
    private val owners = WeakHashMap<XposedModule, Owner>()

    fun activate(module: XposedModule): Owner {
        return synchronized(lock) {
            owners[module]?.also { it.activate() }
                ?: Owner(module).also { owners[module] = it }
        }
    }

    /** Stop the old generation and wait for hook bodies already in progress. */
    fun deactivateAndAwait(module: XposedModule, timeout: Long, unit: TimeUnit): Boolean {
        return synchronized(lock) { owners[module] }?.let { owner ->
            owner.deactivateAndAwait(timeout, unit)
        } ?: true
    }

    fun beginHotReload(module: XposedModule, oldHandles: List<HookHandle>) {
        val owner = activate(module)
        owner.beginHotReload(oldHandles)
    }

    fun finishHotReload(module: XposedModule): ReconcileResult {
        val owner = synchronized(lock) { owners[module] }
            ?: return ReconcileResult(0, 0, emptyList(), null)
        return owner.finishHotReload()
    }

    fun unmatched(module: XposedModule): List<ExecutableSignature> {
        return synchronized(lock) { owners[module] }?.unmatched().orEmpty()
    }

    /** Stop and wait for hooks installed by a failed new generation before releasing it. */
    fun abortAndAwait(module: XposedModule, timeout: Long, unit: TimeUnit): Boolean {
        return synchronized(lock) { owners[module] }?.abortAndAwait(timeout, unit) ?: true
    }

    /** Drop the old generation owner after API 102 accepted its neutral handoff state. */
    fun release(module: XposedModule) {
        synchronized(lock) { owners.remove(module) }
    }

    fun forget(module: XposedModule, handle: HookHandle) {
        synchronized(lock) { owners[module] }?.forget(handle)
    }

    /**
     * Install one hook through the generation owner.  The helper is intentionally the only place
     * in the module that invokes [XposedModule.hook], which makes ownership and duplicate
     * prevention auditable with a single source search.
     */
    fun install(
        module: XposedModule,
        executable: Executable,
        capability: String,
        hooker: Hooker,
        deoptimize: Boolean = true,
    ): HookHandle {
        val owner = synchronized(lock) {
            owners[module] ?: Owner(module).also { owners[module] = it }
        }
        return owner.install(executable, capability, hooker, deoptimize)
    }

    data class ReconcileResult(
        val replaced: Int,
        val added: Int,
        val removed: List<ExecutableSignature>,
        val failure: Throwable?,
    ) {
        val succeeded: Boolean
            get() = failure == null
    }

    /** Full executable identity, never just a method name. */
    data class ExecutableSignature(
        val declaringClass: Class<*>,
        val name: String,
        val parameterTypes: List<Class<*>>,
        val returnType: Class<*>?,
        val constructor: Boolean,
    ) {
        companion object {
            fun of(executable: Executable): ExecutableSignature = when (executable) {
                is Constructor<*> -> ExecutableSignature(
                    declaringClass = executable.declaringClass,
                    name = "<init>",
                    parameterTypes = executable.parameterTypes.toList(),
                    returnType = null,
                    constructor = true,
                )

                is Method -> ExecutableSignature(
                    declaringClass = executable.declaringClass,
                    name = executable.name,
                    parameterTypes = executable.parameterTypes.toList(),
                    returnType = executable.returnType,
                    constructor = false,
                )

                else -> ExecutableSignature(
                    declaringClass = executable.declaringClass,
                    name = executable.name,
                    parameterTypes = executable.parameterTypes.toList(),
                    returnType = null,
                    constructor = false,
                )
            }
        }
    }

    /**
     * API 102 is module-wide, while the product boundary is the media-card package. All current
     * Super Island, lyric runtime, color, lifecycle and whitelist capabilities are reloadable;
     * only the explicitly namespaced media-card capabilities stay in the old generation.
     */
    private fun isReloadableCapability(capability: String): Boolean =
        !capability.startsWith("media.")

    private fun capabilityFromId(id: String?): String? {
        val prefix = "$ID_PREFIX:"
        return id?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
    }

    /**
     * Before API 102 ids were added, media-card hooks were also installed without ids. They must
     * remain in the old generation, while every other old hook must be replaced by the new one.
     * Class/method identity is the only evidence available for that one-time migration.
     */
    private fun isLegacyMediaSignature(signature: ExecutableSignature): Boolean {
        val className = signature.declaringClass.name
        return className.contains("media", ignoreCase = true) ||
                (className == "com.android.systemui.SystemUIApplication" &&
                        signature.name == "onConfigurationChanged") ||
                (className == "com.android.systemui.statusbar.notification.utils.NotificationUtil" &&
                        signature.name == "applyViewShadowForMediaAlbum") ||
                (className == "androidx.constraintlayout.widget.ConstraintSet" &&
                        signature.name == "load") ||
                (className == "miuix.miuixbasewidget.widget.HyperProgressSeekBar" &&
                        signature.name == "onDraw") ||
                // The single-card switcher resolves dispatchTouchEvent from the concrete player
                // class at runtime; older SystemUI builds may declare it on a generic ViewGroup.
                signature.name == "dispatchTouchEvent"
    }

    /**
     * The owner is kept by hook wrappers, not by a process-global module callback. Each hook has
     * its own quiescence slot so preserving a non-reloadable hook does not disable it globally.
     */
    class Owner internal constructor(
        private val module: XposedModule,
    ) {
        private val api102 = runCatching {
            module.getApiVersion() >= XposedInterface.API_102
        }.getOrDefault(false)
        private val records = LinkedHashMap<HookKey, Record>()
        private val oldHandles = LinkedHashMap<OldHookKey, MutableList<HookHandle>>()
        private var hotReloadFailure: Throwable? = null
        private var replacedCount = 0
        private var addedCount = 0

        internal fun activate() {
            synchronized(records) {
                records.values
                    .filter { it.reloadable }
                    .forEach { it.slot.activate() }
            }
        }

        internal fun deactivateAndAwait(timeout: Long, unit: TimeUnit): Boolean {
            val slots = synchronized(records) {
                records.values.filter { it.reloadable }.map { it.slot }
            }
            slots.forEach { it.deactivate() }
            val deadline = System.nanoTime() + unit.toNanos(timeout)
            val quiescent = slots.all { it.awaitQuiescent(deadline) }
            if (!quiescent) slots.forEach { it.activate() }
            return quiescent
        }

        internal fun beginHotReload(handles: List<HookHandle>) {
            check(api102) { "API 102 hook reconciliation requested on API 101" }
            synchronized(records) {
                oldHandles.clear()
                handles.forEach { handle ->
                    val signature = ExecutableSignature.of(handle.executable)
                    val id = runCatching { handle.getId() }.getOrNull()
                    val reloadable = if (id == null) {
                        !isLegacyMediaSignature(signature)
                    } else {
                        capabilityFromId(id)?.let(::isReloadableCapability) == true
                    }
                    if (reloadable) {
                        oldHandles.getOrPut(OldHookKey(signature, id)) { mutableListOf() }
                            .add(handle)
                    }
                }
                hotReloadFailure = null
                replacedCount = 0
                addedCount = 0
            }
        }

        internal fun install(
            executable: Executable,
            capability: String,
            hooker: Hooker,
            deoptimize: Boolean,
        ): HookHandle {
            val signature = ExecutableSignature.of(executable)
            val reloadable = isReloadableCapability(capability)
            synchronized(records) {
                val key = HookKey(signature, capability)
                records[key]?.let { return it.handle }

                val id = "$ID_PREFIX:$capability"
                val slot = HookSlot(active = true)
                val guarded = GuardedHooker(slot, hooker)
                val oldHandle = if (reloadable) takeOldHandle(signature, id) else null
                var consumedOldHandle: HookHandle? = null
                val handle = try {
                    oldHandle?.let { oldHandle ->
                        consumedOldHandle = oldHandle
                        if (!api102) {
                            throw IllegalStateException(
                                "old hook handle requires API 102: $signature"
                            )
                        }
                        oldHandle.replaceHook(guarded)
                    } ?: run {
                        if (deoptimize) module.deoptimize(executable)
                        val builder = module.hook(executable)
                        if (api102) {
                            // setId is API 102.  Do not call it on an API 101 framework.
                            builder.setId(id)
                        }
                        builder.intercept(guarded)
                    }
                } catch (error: Throwable) {
                    consumedOldHandle?.let { handle ->
                        runCatching { handle.unhook() }
                    }
                    hotReloadFailure = hotReloadFailure ?: error
                    throw error
                }
                records[key] = Record(
                    handle = handle,
                    reloadable = reloadable,
                    slot = slot,
                )
                if (oldHandle != null) replacedCount++ else addedCount++
                return handle
            }
        }

        internal fun finishHotReload(): ReconcileResult {
            val removed = mutableListOf<ExecutableSignature>()
            var removalFailure: Throwable? = null
            synchronized(records) {
                oldHandles.forEach { (key, handles) ->
                    handles.forEach { handle ->
                        runCatching { handle.unhook() }.onFailure { error ->
                            removalFailure = removalFailure ?: error
                        }
                        removed += key.signature
                    }
                }
                oldHandles.clear()
                return ReconcileResult(
                    replaced = replacedCount,
                    added = addedCount,
                    removed = removed,
                    failure = hotReloadFailure ?: removalFailure,
                )
            }
        }

        internal fun abortAndAwait(timeout: Long, unit: TimeUnit): Boolean {
            val slots = synchronized(records) {
                records.values.map { it.slot }
            }
            slots.forEach { it.deactivate() }

            val deadline = System.nanoTime() + unit.toNanos(timeout)
            var succeeded = slots.all { it.awaitQuiescent(deadline) }
            synchronized(records) {
                records.values.forEach { record ->
                    runCatching { record.handle.unhook() }.onFailure { error ->
                        succeeded = false
                        hotReloadFailure = hotReloadFailure ?: error
                    }
                }
                records.clear()
                oldHandles.values.flatten().forEach { handle ->
                    runCatching { handle.unhook() }.onFailure { error ->
                        succeeded = false
                        hotReloadFailure = hotReloadFailure ?: error
                    }
                }
                oldHandles.clear()
            }
            return succeeded
        }

        internal fun unmatched(): List<ExecutableSignature> = synchronized(records) {
            oldHandles.flatMap { (key, handles) ->
                List(handles.size) { key.signature }
            }
        }

        internal fun forget(handle: HookHandle) {
            synchronized(records) {
                records.entries.removeIf { it.value.handle === handle }
            }
        }

        private data class Record(
            val handle: HookHandle,
            val reloadable: Boolean,
            val slot: HookSlot,
        )

        private data class HookKey(
            val signature: ExecutableSignature,
            val capability: String,
        )

        private data class OldHookKey(
            val signature: ExecutableSignature,
            val id: String?,
        )

        private fun takeOldHandle(
            signature: ExecutableSignature,
            id: String,
        ): HookHandle? {
            val exactKey = OldHookKey(signature, id)
            oldHandles[exactKey]?.removeFirstOrNull()?.let { handle ->
                if (oldHandles[exactKey].isNullOrEmpty()) oldHandles.remove(exactKey)
                return handle
            }

            // The first version of HyperLyric did not assign API 102 hook ids.  Permit one
            // signature-only handoff for that baseline, while never treating a different
            // explicit id as the same capability.
            val legacyKey = OldHookKey(signature, null)
            oldHandles[legacyKey]?.removeFirstOrNull()?.let { handle ->
                if (oldHandles[legacyKey].isNullOrEmpty()) oldHandles.remove(legacyKey)
                return handle
            }
            return null
        }

        private class HookSlot(
            active: Boolean,
        ) {
            private val active = AtomicBoolean(active)
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            private val monitor = java.lang.Object()
            private var inFlight = 0

            fun activate() {
                active.set(true)
            }

            fun deactivate() {
                synchronized(monitor) {
                    active.set(false)
                }
            }

            fun tryEnter(): Boolean {
                synchronized(monitor) {
                    if (!active.get()) return false
                    inFlight++
                    return true
                }
            }

            fun exit() {
                synchronized(monitor) {
                    inFlight--
                    if (inFlight == 0) monitor.notifyAll()
                }
            }

            fun awaitQuiescent(deadline: Long): Boolean {
                synchronized(monitor) {
                    while (inFlight > 0) {
                        val remainingNanos = deadline - System.nanoTime()
                        if (remainingNanos <= 0L) return false
                        val millis = remainingNanos / 1_000_000L
                        val nanos = (remainingNanos % 1_000_000L).toInt()
                        try {
                            monitor.wait(millis, nanos)
                        } catch (error: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return false
                        }
                    }
                    return true
                }
            }
        }

        private class GuardedHooker(
            private val slot: HookSlot,
            private val delegate: Hooker,
        ) : Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                if (!slot.tryEnter()) return chain.proceed()
                return try {
                    delegate.intercept(chain)
                } finally {
                    slot.exit()
                }
            }
        }
    }
}

internal fun XposedModule.managedHook(
    executable: Executable,
    capability: String,
    hooker: Hooker,
    deoptimize: Boolean = true,
): HookHandle = HookRuntimeRegistry.install(
    module = this,
    executable = executable,
    capability = capability,
    hooker = hooker,
    deoptimize = deoptimize,
)
