package com.lidesheng.hyperlyric.root.mediacard.notification

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintBridge
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SquigglySeekBar
import java.lang.reflect.Field
import java.lang.reflect.Method

private val iconLoadDrawableAsUserMethod = runCatching {
    Icon::class.java.getDeclaredMethod(
        "loadDrawableAsUser",
        Context::class.java,
        Int::class.javaPrimitiveType
    ).apply { isAccessible = true }
}.getOrNull()

internal object NotificationMediaHostClasses {
    const val VIEW_CONTROLLER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
    const val LAYOUT_CONTROLLER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaNotificationControllerImpl"
    const val HOLDER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder"
    const val MEDIA_HEADER_VIEW =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"
    const val ACTION_BUTTON_UTILS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaActionButtonUtils"
    const val MEDIA_DATA = "com.android.systemui.media.controls.shared.model.MediaData"
    const val MEDIA_ACTION = "com.android.systemui.media.controls.shared.model.MediaAction"
}

internal class NotificationMediaHostApi private constructor(
    val hookMethods: List<Method>,
    private val holderField: Field,
    private val controllerMediaDataField: Field,
    private val controllerAppIconDrawableField: Field?,
    private val controllerArtworkDrawableField: Field?,
    private val playerField: Field?,
    private val albumViewField: Field,
    private val albumImageField: Field,
    private val holderAppIconField: Field?,
    private val titleTextField: Field?,
    private val artistTextField: Field?,
    private val actionButtonFields: List<Field>,
    private val seamlessContainerField: Field?,
    private val seamlessIconField: Field?,
    private val seekBarField: Field?,
    private val elapsedTimeViewField: Field?,
    private val totalTimeViewField: Field?,
    private val seekBarPaddingOffsetField: Field?,
    private val seekBarTrackPositionField: Field?,
    private val seekBarRuntimeShaderField: Field?,
    private val mediaDataIsPlayingField: Field,
    private val mediaDataPackageNameField: Field?,
    private val mediaDataAppNameField: Field?,
    private val mediaDataAppIconField: Field?,
    private val mediaDataUserIdField: Field?,
    private val layoutContextField: Field,
    private val normalLayoutField: Field,
    private val normalAlbumLayoutField: Field,
    private val setVisibilityMethod: Method,
    private val setGoneMarginMethod: Method,
    private val connectMethod: Method,
    private val setMarginMethod: Method,
    private val setHorizontalChainStyleMethod: Method?,
    private val clearMethod: Method?,
    private val constrainWidthMethod: Method?,
    private val constrainHeightMethod: Method?
) : NotificationMediaConstraintBridge {
    override val supportsFullLayout: Boolean
        get() = clearMethod != null &&
                constrainWidthMethod != null &&
                constrainHeightMethod != null

    fun getHolder(controller: Any): Any? = holderField.get(controller)

    fun getMediaData(controller: Any): Any? = controllerMediaDataField.get(controller)

    fun getAppIconDrawable(controller: Any): Drawable? =
        controllerAppIconDrawableField?.get(controller) as? Drawable

    fun getApplicationName(mediaData: Any?, context: Context): CharSequence? {
        if (mediaData == null) return null
        val mediaName = mediaDataAppNameField?.let { field ->
            runCatching { field.get(mediaData) as? CharSequence }.getOrNull()
        }
        if (!mediaName.isNullOrBlank()) return mediaName
        val packageName = mediaDataPackageNameField?.let { field ->
            runCatching { field.get(mediaData) as? String }.getOrNull()
        } ?: return null
        return runCatching {
            val packageManager = context.packageManager
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            )
        }.getOrElse { packageName.substringAfterLast('.') }
    }

    fun getAppIdentityIcon(
        controller: Any,
        mediaData: Any?,
        context: Context
    ): Drawable? {
        return getAppIconDrawable(controller)
            ?: getMediaSourceIcon(mediaData, context)
            ?: getApplicationIcon(mediaData, context)
    }

    fun getPlayer(holder: Any): ViewGroup? =
        playerField?.get(holder) as? ViewGroup

    fun getTitleText(holder: Any): TextView? =
        titleTextField?.get(holder) as? TextView

    fun getArtistText(holder: Any): TextView? =
        artistTextField?.get(holder) as? TextView

    fun getMediaForegroundColor(holder: Any): Int? {
        return getSeamlessIcon(holder)?.imageTintList?.defaultColor
            ?: artistTextField?.let { field ->
                runCatching { (field.get(holder) as? TextView)?.currentTextColor }.getOrNull()
            }
    }

    fun getAlbumView(holder: Any): View = albumViewField.get(holder) as View

    fun getAlbumImage(holder: Any): ImageView = albumImageField.get(holder) as ImageView

    /**
     * HyperOS only refreshes the native artwork when the Icon changed. A
     * player can recreate its MediaSession while keeping the same artwork,
     * which leaves the reused controller's app icon/artwork from the old
     * session. Refresh both the native fields and the holder immediately when
     * the switcher detects a session identity change.
     */
    fun refreshArtwork(controller: Any, mediaData: Any?) {
        if (mediaData == null) return
        runCatching {
            val holder = getHolder(controller) ?: return@runCatching
            val context = readField(controller, "context") as? Context ?: return@runCatching
            val appIcon = getMediaSourceIcon(mediaData, context)
                ?: getApplicationIcon(mediaData, context)
            val artworkIcon = readField(mediaData, "artwork") as? Icon
            val artwork = artworkIcon?.loadDrawable(context) ?: appIcon
            controllerAppIconDrawableField?.set(controller, appIcon)
            controllerArtworkDrawableField?.set(controller, artwork)
            holderAppIconField?.get(holder)?.let { it as? ImageView }
                ?.setImageDrawable(appIcon)
            getAlbumImage(holder).setImageDrawable(artwork)
        }
    }

    fun getActionButtons(holder: Any): List<ImageButton> {
        return actionButtonFields.mapNotNull { field ->
            runCatching { field.get(holder) as? ImageButton }.getOrNull()
        }
    }

    fun getAction4(holder: Any): ImageButton? =
        actionButtonFields.getOrNull(4)?.let { field ->
            runCatching { field.get(holder) as? ImageButton }.getOrNull()
        }

    fun getSeamlessContainer(holder: Any): ViewGroup? =
        seamlessContainerField?.get(holder) as? ViewGroup

    fun getSeamlessIcon(holder: Any): ImageView? =
        seamlessIconField?.get(holder) as? ImageView

    fun getSeekBar(holder: Any): View? =
        MediaProgressStyleHooker.replacementSeekBar(holder)
            ?: (seekBarField?.get(holder) as? View)

    fun getElapsedTimeView(holder: Any): TextView? =
        elapsedTimeViewField?.get(holder) as? TextView

    fun getTotalTimeView(holder: Any): TextView? =
        totalTimeViewField?.get(holder) as? TextView

    fun getLayoutContext(controller: Any): Context = layoutContextField.get(controller) as Context

    fun getNormalLayout(controller: Any): Any? = normalLayoutField.get(controller)

    fun getNormalAlbumLayout(controller: Any): Any? = normalAlbumLayoutField.get(controller)

    fun removeSeekBarTrackInset(seekBar: View) {
        if (seekBar is SquigglySeekBar) return
        val paddingField = seekBarPaddingOffsetField ?: return
        val trackPositionField = seekBarTrackPositionField ?: return
        val trackPosition = trackPositionField.get(seekBar) as? FloatArray ?: return
        if (trackPosition.isEmpty()) return
        val paddingChanged = paddingField.getInt(seekBar) != 0
        val positionChanged = trackPosition[0] != 0f
        if (!paddingChanged && !positionChanged) return

        if (paddingChanged) paddingField.setInt(seekBar, 0)
        if (positionChanged) trackPosition[0] = 0f
        runCatching {
            val shader = seekBarRuntimeShaderField?.get(seekBar) ?: return@runCatching
            val setFloatUniform = shader.javaClass.methods.find { method ->
                method.name == "setFloatUniform" &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == FloatArray::class.java
            } ?: return@runCatching
            setFloatUniform.invoke(shader, "uTrackPosition", trackPosition)
        }
        seekBar.requestLayout()
        seekBar.invalidate()
    }

    fun isPlaying(mediaData: Any?): Boolean {
        return mediaData?.let { mediaDataIsPlayingField.get(it) == true } ?: false
    }

    private fun getMediaSourceIcon(mediaData: Any?, context: Context): Drawable? {
        if (mediaData == null) return null
        val icon = mediaDataAppIconField?.let { field ->
            runCatching { field.get(mediaData) as? Icon }.getOrNull()
        } ?: return null
        val userId = mediaDataUserIdField?.let { field ->
            runCatching { field.getInt(mediaData) }.getOrNull()
        }
        return runCatching {
            val drawableForUser = userId?.let { id ->
                iconLoadDrawableAsUserMethod?.invoke(icon, context, id) as? Drawable
            }
            drawableForUser ?: icon.loadDrawable(context)
        }.getOrNull()
    }

    private fun getApplicationIcon(mediaData: Any?, context: Context): Drawable? {
        if (mediaData == null) return null
        val packageName = mediaDataPackageNameField?.let { field ->
            runCatching { field.get(mediaData) as? String }.getOrNull()
        } ?: return null
        return runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun readField(receiver: Any, name: String): Any? {
        return findField(receiver.javaClass, name)?.let { field ->
            runCatching { field.get(receiver) }.getOrNull()
        }
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        return null
    }

    override fun setVisibility(layout: Any, viewId: Int, visibility: Int) {
        setVisibilityMethod.invoke(layout, viewId, visibility)
    }

    override fun setGoneMargin(layout: Any, viewId: Int, side: Int, margin: Int) {
        setGoneMarginMethod.invoke(layout, viewId, side, margin)
    }

    override fun connect(
        layout: Any,
        startId: Int,
        startSide: Int,
        endId: Int,
        endSide: Int
    ) {
        connectMethod.invoke(layout, startId, startSide, endId, endSide)
    }

    override fun setMargin(layout: Any, viewId: Int, side: Int, margin: Int) {
        setMarginMethod.invoke(layout, viewId, side, margin)
    }

    override fun setHorizontalChainStyle(layout: Any, viewId: Int, chainStyle: Int) {
        setHorizontalChainStyleMethod?.invoke(layout, viewId, chainStyle)
    }

    override fun clear(layout: Any, viewId: Int, side: Int) {
        requireNotNull(clearMethod).invoke(layout, viewId, side)
    }

    override fun constrainWidth(layout: Any, viewId: Int, width: Int) {
        requireNotNull(constrainWidthMethod).invoke(layout, viewId, width)
    }

    override fun constrainHeight(layout: Any, viewId: Int, height: Int) {
        requireNotNull(constrainHeightMethod).invoke(layout, viewId, height)
    }

    companion object {
        fun create(classLoader: ClassLoader): NotificationMediaHostApi {
            val viewControllerClass = classLoader.loadClass(
                NotificationMediaHostClasses.VIEW_CONTROLLER
            )
            val layoutControllerClass = classLoader.loadClass(
                NotificationMediaHostClasses.LAYOUT_CONTROLLER
            )
            val holderClass = classLoader.loadClass(NotificationMediaHostClasses.HOLDER)
            val mediaDataClass = classLoader.loadClass(NotificationMediaHostClasses.MEDIA_DATA)
            val actionButtonUtilsClass = classLoader.loadClass(
                NotificationMediaHostClasses.ACTION_BUTTON_UTILS
            )
            val mediaActionClass = classLoader.loadClass(NotificationMediaHostClasses.MEDIA_ACTION)
            val semanticActionMethod = actionButtonUtilsClass.declaredMethods.find { method ->
                method.name == "setSemanticButton" &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == ImageButton::class.java &&
                        method.parameterTypes[1] == mediaActionClass
            }?.apply { isAccessible = true }
            val commonActionMethod = actionButtonUtilsClass.declaredMethods.find { method ->
                method.name == "bindButtonsCommon" &&
                        method.parameterCount == 3 &&
                        method.parameterTypes[0] == ImageButton::class.java &&
                        method.parameterTypes[1] == mediaActionClass
            }?.apply { isAccessible = true }
            val setAnimateHeight = runCatching {
                classLoader.loadClass(NotificationMediaHostClasses.MEDIA_HEADER_VIEW)
                    .getDeclaredMethod(
                        "setAnimateHeight",
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
            }.getOrNull()
            val constraintSetClass = classLoader.loadClass(
                "androidx.constraintlayout.widget.ConstraintSet"
            )

            val attach = viewControllerClass.getDeclaredMethod(
                "attach",
                holderClass
            ).apply { isAccessible = true }
            val bind = viewControllerClass.getDeclaredMethod(
                "bindMediaData",
                mediaDataClass
            ).apply { isAccessible = true }
            val detach = viewControllerClass.getDeclaredMethod("detach").apply {
                isAccessible = true
            }
            val onFullAodStateChanged = viewControllerClass.declaredMethods.find { method ->
                method.name == "onFullAodStateChanged" &&
                        method.parameterCount == 1 &&
                        method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
            val updateForegroundColors = viewControllerClass.declaredMethods.find { method ->
                method.name == "updateForegroundColors" && method.parameterCount == 0
            }?.apply { isAccessible = true }
            val setSeamless = viewControllerClass.getDeclaredMethod(
                "setSeamless",
                mediaDataClass
            ).apply { isAccessible = true }
            val loadLayout = layoutControllerClass.getDeclaredMethod("loadLayout\$1").apply {
                isAccessible = true
            }
            val seekBarField = runCatching {
                holderClass.getDeclaredField("seekBar").apply { isAccessible = true }
            }.getOrNull()
            val seekBarClass = seekBarField?.type
            val seekBarPaddingOffsetField = runCatching {
                seekBarClass?.getDeclaredField("mProgressPaddingOffset")?.apply {
                    isAccessible = true
                }
            }.getOrNull()
            val seekBarTrackPositionField = runCatching {
                seekBarClass?.getDeclaredField("uTrackPosition")?.apply {
                    isAccessible = true
                }
            }.getOrNull()
            val seekBarRuntimeShaderField = runCatching {
                seekBarClass?.getDeclaredField("runtimeShader")?.apply {
                    isAccessible = true
                }
            }.getOrNull()

            return NotificationMediaHostApi(
                hookMethods = listOf(attach, bind, detach, setSeamless) + listOfNotNull(
                    onFullAodStateChanged,
                    updateForegroundColors,
                    semanticActionMethod,
                    commonActionMethod,
                    setAnimateHeight
                ) + loadLayout,
                holderField = viewControllerClass.getDeclaredField("holder").apply {
                    isAccessible = true
                },
                controllerMediaDataField = viewControllerClass.getDeclaredField("mediaData")
                    .apply { isAccessible = true },
                controllerAppIconDrawableField = runCatching {
                    viewControllerClass.getDeclaredField("appIconDrawable").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                controllerArtworkDrawableField = runCatching {
                    viewControllerClass.getDeclaredField("artWorkDrawable").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                playerField = runCatching {
                    holderClass.getDeclaredField("player").apply { isAccessible = true }
                }.getOrNull(),
                albumViewField = holderClass.getDeclaredField("albumView").apply {
                    isAccessible = true
                },
                albumImageField = holderClass.getDeclaredField("albumImageView").apply {
                    isAccessible = true
                },
                holderAppIconField = runCatching {
                    holderClass.getDeclaredField("appIcon").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                titleTextField = runCatching {
                    holderClass.getDeclaredField("titleText").apply { isAccessible = true }
                }.getOrNull(),
                artistTextField = runCatching {
                    holderClass.getDeclaredField("artistText").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                actionButtonFields = (0..4).mapNotNull { index ->
                    runCatching {
                        holderClass.getDeclaredField("action$index").apply {
                            isAccessible = true
                        }
                    }.getOrNull()
                },
                seamlessContainerField = runCatching {
                    holderClass.getDeclaredField("seamless").apply { isAccessible = true }
                }.getOrNull(),
                seamlessIconField = runCatching {
                    holderClass.getDeclaredField("seamlessIcon").apply { isAccessible = true }
                }.getOrNull(),
                seekBarField = seekBarField,
                elapsedTimeViewField = runCatching {
                    holderClass.getDeclaredField("elapsedTimeView").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                totalTimeViewField = runCatching {
                    holderClass.getDeclaredField("totalTimeView").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                seekBarPaddingOffsetField = seekBarPaddingOffsetField,
                seekBarTrackPositionField = seekBarTrackPositionField,
                seekBarRuntimeShaderField = seekBarRuntimeShaderField,
                mediaDataIsPlayingField = mediaDataClass.getDeclaredField("isPlaying").apply {
                    isAccessible = true
                },
                mediaDataPackageNameField = runCatching {
                    mediaDataClass.getDeclaredField("packageName").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                mediaDataAppNameField = runCatching {
                    mediaDataClass.getDeclaredField("app").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                mediaDataAppIconField = runCatching {
                    mediaDataClass.getDeclaredField("appIcon").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                mediaDataUserIdField = runCatching {
                    mediaDataClass.getDeclaredField("userId").apply {
                        isAccessible = true
                    }
                }.getOrNull(),
                layoutContextField = layoutControllerClass.getDeclaredField("context").apply {
                    isAccessible = true
                },
                normalLayoutField = layoutControllerClass.getDeclaredField("normalLayout")
                    .apply { isAccessible = true },
                normalAlbumLayoutField = layoutControllerClass.getDeclaredField(
                    "normalAlbumLayout"
                ).apply { isAccessible = true },
                setVisibilityMethod = constraintSetClass.getDeclaredMethod(
                    "setVisibility",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true },
                setGoneMarginMethod = constraintSetClass.getDeclaredMethod(
                    "setGoneMargin",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true },
                connectMethod = constraintSetClass.getDeclaredMethod(
                    "connect",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true },
                setMarginMethod = constraintSetClass.getDeclaredMethod(
                    "setMargin",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true },
                setHorizontalChainStyleMethod = runCatching {
                    constraintSetClass.getDeclaredMethod(
                        "setHorizontalChainStyle",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                }.getOrNull(),
                clearMethod = runCatching {
                    constraintSetClass.getDeclaredMethod(
                        "clear",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                }.getOrNull(),
                constrainWidthMethod = runCatching {
                    constraintSetClass.getDeclaredMethod(
                        "constrainWidth",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                }.getOrNull(),
                constrainHeightMethod = runCatching {
                    constraintSetClass.getDeclaredMethod(
                        "constrainHeight",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                }.getOrNull()
            )
        }
    }
}
