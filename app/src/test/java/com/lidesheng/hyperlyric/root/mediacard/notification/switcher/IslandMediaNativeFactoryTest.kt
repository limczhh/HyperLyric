package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.media.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class IslandMediaNativeFactoryTest {
    class Model(val bgExecutor: Executor, val falsingManager: Any) {
        var stopped = false
        var disconnected = false
        fun setListening(value: Boolean) { stopped = !value }
        fun updateController(controller: MediaController?) { disconnected = controller == null }
    }
    class Buttons(val uiScope: CoroutineScope, val context: Any, val notificationStat: Any)
    class Binder(
        val scope: CoroutineScope,
        val seekBarViewModel: Model,
        val miuiMediaActionButtonUtils: Buttons,
        val sharedService: Any,
    )

    class Owner(val context: Any, val seekBarViewModel: Model, val bgExecutor: Executor)
    class OwnerBinder(
        val scope: CoroutineScope,
        val seekBarProgressOwner: Owner,
        val miuiMediaActionButtonUtils: Buttons,
    )

    @Test fun newerProgressOwnersAreIndependentAsWellAsTheirViewModels() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val executor = Executor { it.run() }
        val model = Model(executor, Any())
        val owner = Owner(Any(), model, executor)
        val buttons = Buttons(scope, Any(), Any())
        val template = OwnerBinder(scope, owner, buttons)
        val factory = NotificationMediaNativeFactory(Any(), template) { error("Unexpected holder") }
        val constructor = OwnerBinder::class.java.declaredConstructors.single()
        val first = factory.createIslandBinder(constructor, listOf(scope, owner, buttons))
        val second = factory.createIslandBinder(constructor, listOf(scope, owner, buttons))
        try {
            val a = (first.instance as OwnerBinder).seekBarProgressOwner
            val b = (second.instance as OwnerBinder).seekBarProgressOwner
            assertNotSame(owner, a)
            assertNotSame(a, b)
            assertNotSame(model, a.seekBarViewModel)
            assertNotSame(a.seekBarViewModel, b.seekBarViewModel)
            first.close()
            assertTrue(a.seekBarViewModel.disconnected)
            assertFalse(b.seekBarViewModel.disconnected)
            assertFalse(model.disconnected)
        } finally {
            first.close()
            second.close()
            scope.coroutineContext[Job]!!.cancel()
        }
    }

    @Test fun bindersOwnTheirMutableDependenciesAndCleanupDoesNotCancelTheHost() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = Model(Executor { it.run() }, Any())
        val buttons = Buttons(scope, Any(), Any())
        val shared = Any()
        val template = Binder(scope, model, buttons, shared)
        val factory = NotificationMediaNativeFactory(Any(), template) { error("Holder allocation unexpected") }
        val constructor = Binder::class.java.declaredConstructors.single()
        val args = listOf(scope, model, buttons, shared)
        val first = factory.createIslandBinder(constructor, args)
        val second = factory.createIslandBinder(constructor, args)
        val a = first.instance as Binder
        val b = second.instance as Binder
        try {
            assertNotSame(model, a.seekBarViewModel)
            assertNotSame(a.seekBarViewModel, b.seekBarViewModel)
            assertNotSame(buttons, a.miuiMediaActionButtonUtils)
            assertNotSame(a.miuiMediaActionButtonUtils, b.miuiMediaActionButtonUtils)
            assertSame(a.scope, a.miuiMediaActionButtonUtils.uiScope)
            assertSame(shared, a.sharedService)
            assertSame(shared, b.sharedService)
            first.close()
            assertTrue(a.seekBarViewModel.stopped)
            assertTrue(a.seekBarViewModel.disconnected)
            assertFalse(a.scope.coroutineContext[Job]!!.isActive)
            assertTrue(b.scope.coroutineContext[Job]!!.isActive)
            assertTrue(scope.coroutineContext[Job]!!.isActive)
            assertFalse(model.disconnected)
        } finally {
            first.close()
            second.close()
            scope.coroutineContext[Job]!!.cancel()
        }
    }
}
