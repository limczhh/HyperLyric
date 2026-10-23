package com.lidesheng.hyperlyric.root.island.hooks

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.view.View
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

/**
 * Soda Music uses a Service PendingIntent for its media notification click action. HyperOS 4's
 * Dynamic Island freeform path accepts only Activity PendingIntents, so provide Soda's launcher
 * Activity temporarily for eligibility checks and for the final freeform launch.
 */
internal object SodaIslandMiniWindowCompatHooker {
    private const val TAG = "SodaIslandMiniWindow"
    private const val SODA_PACKAGE = "com.luna.music"
    private const val ISLAND_BASE_CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val ISLAND_CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val DYNAMIC_ISLAND_CONTROLLER =
        "com.android.systemui.statusbar.notification.DynamicIslandController"
    private const val MEDIA_PACKAGE = "miui.pkg.name"
    private const val MEDIA_USER_ID = "miui.user.id"
    private const val MEDIA_SBN = "miui.sbn"
    private const val MEDIA_PENDING_INTENT = "miui.pending.intent"
    private const val OPEN_FREEFORM_CALLBACK = "onDynamicPluginCallback_openFreeform"
    private const val SODA_ACTIVITY_REQUEST_CODE = 0x4C554E41

    private val pluginClassLoaders = Collections.newSetFromMap(
        Collections.synchronizedMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val systemUiClassLoaders = Collections.newSetFromMap(
        Collections.synchronizedMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val loggedStages = Collections.synchronizedSet(mutableSetOf<String>())

    fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        if (!pluginClassLoaders.add(classLoader)) return
        try {
            val baseView = classLoader.loadClass(ISLAND_BASE_CONTENT_VIEW)
            val contentView = classLoader.loadClass(ISLAND_CONTENT_VIEW)
            val method = baseView.getDeclaredMethod("canExpandedViewSlide", contentView)
            require(method.returnType == Boolean::class.javaPrimitiveType) {
                "Unexpected return type: $method"
            }
            method.isAccessible = true
            module.managedHook(
                executable = method,
                capability = "island.soda.activity_intent.eligibility",
                hooker = SodaSlideEligibilityHooker(),
            )
            HookLogger.d(TAG, "汽水音乐小窗资格兼容 Hook 已安装: target=$method")
        } catch (error: ClassNotFoundException) {
            pluginClassLoaders.remove(classLoader)
            HookLogger.d(TAG, "当前插件没有汽水小窗兼容目标，跳过: ${error.message}")
        } catch (error: NoSuchMethodException) {
            pluginClassLoaders.remove(classLoader)
            HookLogger.d(TAG, "当前插件没有汽水小窗资格方法，跳过: ${error.message}")
        } catch (error: Throwable) {
            pluginClassLoaders.remove(classLoader)
            HookLogger.e(TAG, "安装汽水音乐小窗资格兼容 Hook 失败", error)
        }
    }

    fun hookSystemUi(module: XposedModule, classLoader: ClassLoader) {
        if (!systemUiClassLoaders.add(classLoader)) return
        try {
            val targetClass = classLoader.loadClass(DYNAMIC_ISLAND_CONTROLLER)
            val method = targetClass.getDeclaredMethod(
                "onDynamicPluginCallback",
                String::class.java,
                Bundle::class.java,
            )
            method.isAccessible = true
            module.managedHook(
                executable = method,
                capability = "island.soda.activity_intent.freeform_callback",
                hooker = FreeformCallbackHooker(),
            )
            HookLogger.d(TAG, "汽水音乐 freeform 启动兼容 Hook 已安装: target=$method")
        } catch (error: ClassNotFoundException) {
            systemUiClassLoaders.remove(classLoader)
            HookLogger.d(TAG, "当前 SystemUI 没有汽水 freeform 兼容目标，跳过: ${error.message}")
        } catch (error: NoSuchMethodException) {
            systemUiClassLoaders.remove(classLoader)
            HookLogger.d(TAG, "当前 SystemUI 没有 freeform 回调方法，跳过: ${error.message}")
        } catch (error: Throwable) {
            systemUiClassLoaders.remove(classLoader)
            HookLogger.e(TAG, "安装汽水音乐 freeform 启动兼容 Hook 失败", error)
        }
    }

    fun prepareForHotReload() {
        pluginClassLoaders.clear()
        systemUiClassLoaders.clear()
        loggedStages.clear()
    }

    private class SodaSlideEligibilityHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.args.firstOrNull() ?: return chain.proceed()
            val context = (chain.thisObject as? View)?.context ?: return chain.proceed()
            val bundles = islandDataBundles(chain.thisObject, view)
            val mutation = createSodaActivityIntentMutation(context, bundles, "eligibility")
                ?: return chain.proceed()
            return try {
                chain.proceed()
            } finally {
                mutation.restore()
            }
        }
    }

    private class FreeformCallbackHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (chain.args.firstOrNull() != OPEN_FREEFORM_CALLBACK) return chain.proceed()
            val extras = chain.args.getOrNull(1) as? Bundle ?: return chain.proceed()
            val controller = chain.thisObject ?: return chain.proceed()
            val context = readField(controller, "context") as? Context ?: return chain.proceed()
            val mutation = createSodaActivityIntentMutation(context, listOf(extras), "freeform")
                ?: return chain.proceed()
            return try {
                chain.proceed()
            } finally {
                mutation.restore()
            }
        }
    }

    private fun createSodaActivityIntentMutation(
        context: Context,
        bundles: List<Bundle>,
        stage: String,
    ): IntentMutation? {
        val uniqueBundles = identityDistinct(bundles)
        if (uniqueBundles.isEmpty()) return null
        val statusBarNotifications = uniqueBundles.mapNotNull(::readStatusBarNotification)
        val packageName = uniqueBundles.firstNotNullOfOrNull { bundle ->
            bundle.getString(MEDIA_PACKAGE)
        } ?: statusBarNotifications.firstOrNull()?.packageName
        if (packageName != SODA_PACKAGE) return null

        val notificationIntents = statusBarNotifications.mapNotNull { sbn ->
            sbn.notification?.let { notification -> notification to notification.contentIntent }
        }
        val mediaIntents = uniqueBundles.mapNotNull { bundle ->
            readPendingIntent(bundle)?.let { bundle to it }
        }
        val needsActivityIntent = notificationIntents.any { (_, pendingIntent) ->
            pendingIntent != null && !isActivity(pendingIntent)
        } || mediaIntents.any { (_, pendingIntent) -> !isActivity(pendingIntent) }
        if (!needsActivityIntent) return null

        val userId = uniqueBundles.firstNotNullOfOrNull { bundle ->
            if (bundle.containsKey(MEDIA_USER_ID)) bundle.getInt(MEDIA_USER_ID) else null
        } ?: statusBarNotifications.firstOrNull()?.let(::readStatusBarNotificationUserId)
            ?: 0
        val activityPendingIntent = createActivityPendingIntent(context, userId)
            ?: return null

        val restoreActions = mutableListOf<() -> Unit>()
        val seenNotifications = Collections.newSetFromMap(
            java.util.IdentityHashMap<Notification, Boolean>()
        )
        notificationIntents.forEach { (notification, original) ->
            if (original != null && !isActivity(original) && seenNotifications.add(notification)) {
                notification.contentIntent = activityPendingIntent
                restoreActions += {
                    if (notification.contentIntent === activityPendingIntent) {
                        notification.contentIntent = original
                    }
                }
            }
        }
        mediaIntents.forEach { (bundle, original) ->
            if (!isActivity(original)) {
                bundle.putParcelable(MEDIA_PENDING_INTENT, activityPendingIntent)
                restoreActions += {
                    val current = readPendingIntent(bundle)
                    if (current === activityPendingIntent) {
                        bundle.putParcelable(MEDIA_PENDING_INTENT, original)
                    }
                }
            }
        }
        if (restoreActions.isEmpty()) return null
        if (loggedStages.add(stage)) {
            HookLogger.d(TAG, "已为汽水音乐临时提供 Activity PendingIntent: stage=$stage")
        }
        return IntentMutation(restoreActions)
    }

    private fun createActivityPendingIntent(context: Context, userId: Int): PendingIntent? {
        return runCatching {
            val userContext = contextForUser(context, userId)
            val launchIntent = userContext.packageManager
                .getLaunchIntentForPackage(SODA_PACKAGE)
                ?: return null
            val component = launchIntent.component
            if (component == null || component.packageName != SODA_PACKAGE) return null
            PendingIntent.getActivity(
                userContext,
                SODA_ACTIVITY_REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ).takeIf(::isActivity)
        }.onFailure { error ->
            HookLogger.w(TAG, "无法解析汽水音乐启动 Activity: ${error.message}")
        }.getOrNull()
    }

    private fun contextForUser(context: Context, userId: Int): Context {
        if (userId < 0) return context
        return runCatching {
            val user = UserHandle::class.java
                .getDeclaredMethod("of", Int::class.javaPrimitiveType)
                .invoke(null, userId)
            val createContextAsUser = Context::class.java.getDeclaredMethod(
                "createContextAsUser",
                UserHandle::class.java,
                Int::class.javaPrimitiveType,
            )
            createContextAsUser.isAccessible = true
            createContextAsUser.invoke(context, user, 0) as Context
        }.getOrDefault(context)
    }

    private fun islandDataBundles(receiver: Any?, contentView: Any): List<Bundle> {
        val data = buildList {
            callNoArg(contentView, "getCurrentIslandData")?.let(::add)
            readField(receiver, "currentIslandData")?.let(::add)
        }
        return data.mapNotNull { callNoArg(it, "getExtras") as? Bundle }
    }

    private fun readStatusBarNotification(bundle: Bundle): StatusBarNotification? =
        runCatching {
            bundle.getParcelable(MEDIA_SBN, StatusBarNotification::class.java)
        }.getOrNull()

    private fun readPendingIntent(bundle: Bundle): PendingIntent? = runCatching {
        bundle.getParcelable(MEDIA_PENDING_INTENT, PendingIntent::class.java)
    }.getOrNull()

    private fun readStatusBarNotificationUserId(
        statusBarNotification: StatusBarNotification,
    ): Int? {
        val user = runCatching { statusBarNotification.user }.getOrNull() ?: return null
        return runCatching {
            user.javaClass.methods.firstOrNull {
                it.name == "getIdentifier" && it.parameterTypes.isEmpty()
            }?.invoke(user) as? Int
        }.getOrNull()
    }

    private fun isActivity(pendingIntent: PendingIntent): Boolean =
        runCatching { pendingIntent.isActivity }.getOrDefault(false)

    private fun callNoArg(receiver: Any?, methodName: String): Any? {
        receiver ?: return null
        return runCatching {
            receiver.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty()
            }?.invoke(receiver)
        }.getOrNull()
    }

    private fun readField(receiver: Any?, name: String): Any? {
        var current = receiver?.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == name }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(receiver)
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    private fun identityDistinct(values: List<Bundle>): List<Bundle> {
        val seen = Collections.newSetFromMap(java.util.IdentityHashMap<Bundle, Boolean>())
        return values.filter(seen::add)
    }

    private class IntentMutation(
        private val restoreActions: List<() -> Unit>,
    ) {
        fun restore() {
            restoreActions.asReversed().forEach { restore ->
                runCatching(restore).onFailure { error ->
                    HookLogger.e(TAG, "恢复汽水音乐通知 PendingIntent 失败", error)
                }
            }
        }
    }
}
