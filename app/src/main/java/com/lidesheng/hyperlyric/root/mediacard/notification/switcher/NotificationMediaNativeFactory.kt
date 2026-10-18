package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.view.View
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

/** Creates independent native media objects without assuming an Android/HyperOS version. */
internal class NotificationMediaNativeFactory(
    private val layoutController: Any,
    private val templateController: Any,
    private val allocateHolder: (Class<*>) -> Any
) {
    private companion object {
        const val TAG = "NotificationMediaNativeFactory"
        val LEGACY_DEPENDENCIES = listOf(
            "context", "activityStarter", "seekBarViewModel", "mediaTransferManager",
            "fullAodController", "miPlayPluginManager", "mediaOutputDialogManager",
            "notificationStat", "miuiMediaAlbumAnimationUtils", "dynamicIslandController",
            "mainHandler", "miuiMediaActionButtonUtils", "miuiMediaWakeLockManager"
        )
        val OWNER_DEPENDENCIES = LEGACY_DEPENDENCIES.map {
            if (it == "seekBarViewModel") "seekBarProgressOwner" else it
        } + listOf(
            "notificationMaterialStateInteractor", "scope", "powerInteractor",
            "panelExpandController", "controlCenterShadeWrapper"
        )

        fun fields(clazz: Class<*>): Sequence<Field> =
            generateSequence(clazz) { it.superclass }
                .takeWhile { it != Any::class.java }
                .flatMap { it.declaredFields.asSequence() }
                .filterNot { Modifier.isStatic(it.modifiers) }
                .onEach { it.isAccessible = true }

        fun field(target: Any, name: String): Field? =
            fields(target.javaClass).firstOrNull { it.name == name }

        fun read(target: Any, name: String): Any? = field(target, name)?.get(target)

        fun required(target: Any, name: String): Any =
            read(target, name) ?: error("${target.javaClass.simpleName}.$name unavailable")

        fun write(target: Any, name: String, value: Any?) {
            (field(target, name) ?: error("${target.javaClass.simpleName}.$name unavailable"))
                .set(target, value)
        }
    }

    private val templateOwner = read(templateController, "seekBarProgressOwner")
    private val templateViewModel by lazy {
        required(templateOwner ?: templateController, "seekBarViewModel")
    }
    private val constructors = mutableMapOf<Class<*>, List<Constructor<*>>>()
    private val nativeFactories = mutableMapOf<Class<*>, Pair<Any, Method>>()
    private var holderFactory: ((View, Any, View) -> Any)? = null
    private val scopeApi by lazy {
        HostScopeApi(requireNotNull(templateController.javaClass.classLoader))
    }
    private val updateProgress by lazy {
        templateViewModel.javaClass.getDeclaredMethod("updateController", MediaController::class.java)
            .apply { isAccessible = true }
    }
    private val setListening by lazy {
        templateViewModel.javaClass.getDeclaredMethod("setListening", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
    }

    fun createHolder(player: View, templateHolder: Any, templatePlayer: View): Any {
        val factory = holderFactory ?: resolveHolderFactory(templateHolder.javaClass, player)
            .also { holderFactory = it }
        return factory(player, templateHolder, templatePlayer)
    }

    private fun resolveHolderFactory(clazz: Class<*>, player: View): (View, Any, View) -> Any {
        matchingConstructor(clazz, listOf(player))?.let { constructor ->
            // The legacy constructor and its hooks already initialize all views,
            // including any wave seek-bar replacement. Do not rebind those fields.
            return { view, _, _ -> constructor.newInstance(view) }
        }
        val noArgs = matchingConstructor(clazz, emptyList())
        val viewFields = fields(clazz).toList()
        if (noArgs == null) {
            // R8 can inline a trivial constructor into the caller. Only a plain
            // view holder may be initialized with Object's constructor instead.
            check(clazz.declaredConstructors.isEmpty() && clazz.superclass == Any::class.java &&
                viewFields.all { View::class.java.isAssignableFrom(it.type) }) {
                "Unsupported media holder structure: ${clazz.name}"
            }
        }
        return { view, source, sourcePlayer ->
            val holder = noArgs?.newInstance() ?: allocateHolder(clazz)
            var rootFound = false
            viewFields.filter { View::class.java.isAssignableFrom(it.type) }.forEach { field ->
                val sourceView = field.get(source) as? View ?: return@forEach
                val target = if (sourceView === sourcePlayer) {
                    rootFound = true
                    view
                } else {
                    check(sourceView.id != View.NO_ID) { "Holder view has no id: ${field.name}" }
                    view.findViewById<View>(sourceView.id)
                        ?: error("Media layout missing view: ${field.name}")
                }
                check(field.type.isInstance(target)) { "Holder view type mismatch: ${field.name}" }
                target.layoutDirection = sourceView.layoutDirection
                field.set(holder, target)
            }
            check(rootFound) { "Media holder root view unavailable" }
            holder
        }
    }

    fun createController(): Controller {
        val viewModel = createViewModel()
        val scope = templateOwner?.let { scopeApi.create(required(templateController, "scope")) }
        try {
            val owner = templateOwner?.let {
                construct(
                    it.javaClass,
                    listOf(required(it, "context"), viewModel, required(it, "bgExecutor"))
                )
            }
            val buttons = createActionButtons(scope?.value)
            val names = if (owner == null) LEGACY_DEPENDENCIES else OWNER_DEPENDENCIES
            val values = names.map { name ->
                when (name) {
                    "seekBarViewModel" -> viewModel
                    "seekBarProgressOwner" -> requireNotNull(owner)
                    "miuiMediaActionButtonUtils" -> buttons
                    "scope" -> requireNotNull(scope).value
                    else -> read(templateController, name) ?: required(layoutController, name)
                }
            }
            val instance = construct(templateController.javaClass, values)
            read(templateController, "statusBarState")?.let { write(instance, "statusBarState", it) }
            return Controller(instance, viewModel, owner, scope)
        } catch (error: Throwable) {
            scope?.close()
            throw error
        }
    }

    private fun createViewModel(): Any {
        val arguments = listOf(
            required(templateViewModel, "bgExecutor"), required(templateViewModel, "falsingManager")
        )
        return matchingConstructor(templateViewModel.javaClass, arguments)
            ?.newInstance(*arguments.toTypedArray())
            ?: fromNativeFactory(templateViewModel)
    }

    private fun createActionButtons(scope: Any?): Any {
        val template = required(templateController, "miuiMediaActionButtonUtils")
        val arguments = listOf(
            scope ?: required(template, "uiScope"),
            required(template, "context"), required(template, "notificationStat")
        )
        val buttons = matchingConstructor(template.javaClass, arguments)?.newInstance(*arguments.toTypedArray())
            ?: fromNativeFactory(template)
        if (scope != null) write(buttons, "uiScope", scope)
        return buttons
    }

    private fun matchingConstructor(clazz: Class<*>, arguments: List<Any>): Constructor<*>? =
        constructors.getOrPut(clazz) { clazz.declaredConstructors.toList() }.firstOrNull { candidate ->
            candidate.parameterCount == arguments.size &&
                candidate.parameterTypes.zip(arguments).all { (type, value) -> type.isInstance(value) }
        }?.apply { isAccessible = true }

    private fun construct(clazz: Class<*>, arguments: List<Any>): Any =
        (matchingConstructor(clazz, arguments) ?: error("Native constructor mismatch: ${clazz.name}"))
            .newInstance(*arguments.toTypedArray())

    private fun fromNativeFactory(template: Any): Any {
        val (component, method) = nativeFactories.getOrPut(template.javaClass) {
            findNativeFactory(template.javaClass)
        }
        return requireNotNull(method.invoke(component)).also {
            check(it !== template) { "Native factory returned shared state: ${template.javaClass.name}" }
        }
    }

    private fun findNativeFactory(type: Class<*>): Pair<Any, Method> {
        // The row factory retains its Dagger provider and component. Walk only
        // these two owner links; generated class names/numeric suffixes can change.
        var owners = listOf(required(layoutController, "notifRowViewModelFactory"))
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        repeat(3) { depth ->
            val next = mutableListOf<Any>()
            owners.filter { seen.add(it) }.forEach { owner ->
                owner.javaClass.declaredMethods.singleOrNull {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == type
                }?.let { return owner to it.apply { isAccessible = true } }
                if (depth < 2) {
                    fields(owner.javaClass).filterNot { it.type.isPrimitive }
                        .mapNotNull { it.get(owner) }.forEach(next::add)
                }
            }
            owners = next
        }
        error("Native media factory unavailable: ${type.name}")
    }

    inner class Controller internal constructor(
        val instance: Any,
        private val viewModel: Any,
        private val owner: Any?,
        private val scope: HostScope?
    ) {
        private var closed = false
        private val executor = required(owner ?: viewModel, "bgExecutor") as Executor

        fun prepareBind(data: Any) {
            owner ?: return
            synchronized(this) {
                check(!closed) { "Media controller already released" }
                val token = read(data, "token") as? MediaSession.Token
                var controller = read(owner, "currentController") as? MediaController
                if (token != read(owner, "currentToken") || (token != null && controller == null)) {
                    controller = token?.let { MediaController(required(owner, "context") as Context, it) }
                    write(owner, "currentToken", token)
                    write(owner, "currentController", controller)
                    write(owner, "_seekBarRenderState", null)
                }
                val target = controller
                execute {
                    synchronized(this) {
                        if (!closed && read(owner, "currentController") === target) {
                            updateProgress.invoke(viewModel, target)
                        }
                    }
                }
            }
        }

        fun close() {
            synchronized(this) {
                if (closed) return
                closed = true
            }
            try {
                execute {
                    synchronized(this) {
                        setListening.invoke(viewModel, false)
                        updateProgress.invoke(viewModel, null)
                    }
                }
            } finally {
                scope?.close()
            }
        }

        private fun execute(action: () -> Unit) {
            executor.execute {
                runCatching(action).onFailure { HookLogger.w(TAG, "更新独立媒体进度失败", it) }
            }
        }
    }

    internal class HostScope(val value: Any, private val cancel: () -> Unit) {
        fun close() = cancel()
    }

    private class HostScopeApi(loader: ClassLoader) {
        private val scopeType = loader.loadClass("kotlinx.coroutines.CoroutineScope")
        private val contextType = loader.loadClass("kotlin.coroutines.CoroutineContext")
        private val jobType = loader.loadClass("kotlinx.coroutines.Job")
        private val supervisorMethods = loader.loadClass("kotlinx.coroutines.SupervisorKt").declaredMethods
        private val newJob = (supervisorMethods.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && jobType.isAssignableFrom(it.returnType)
        } ?: supervisorMethods.first {
            it.name == "SupervisorJob" && it.parameterTypes.contentEquals(arrayOf(jobType))
        }).apply { isAccessible = true }
        private val helpers = loader.loadClass("kotlinx.coroutines.CoroutineScopeKt")
        private val plus = helpers.getDeclaredMethod("plus", scopeType, contextType).apply { isAccessible = true }
        private val cancel = helpers.getDeclaredMethod("cancel", scopeType, CancellationException::class.java)
            .apply { isAccessible = true }

        fun create(template: Any): HostScope {
            val job = if (newJob.parameterCount == 0) newJob.invoke(null) else newJob.invoke(null, null)
            // Preserve the host dispatcher, but own the job so removing a page
            // cancels its collectors and button jobs without cancelling SystemUI.
            val scope = requireNotNull(plus.invoke(null, template, job))
            return HostScope(scope) { cancel.invoke(null, scope, null) }
        }
    }
}
