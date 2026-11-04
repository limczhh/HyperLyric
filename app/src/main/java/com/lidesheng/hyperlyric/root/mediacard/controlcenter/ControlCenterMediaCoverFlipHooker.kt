package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Bitmap
import android.view.View
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Overrides only Header cover updates; compact and notification cards stay native. */
internal object ControlCenterMediaCoverFlipHooker {
    private data class Update(val header: View, val metadata: Any)

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        header: Class<*>,
        options: ControlCenterMediaLayoutOptions,
    ): List<HookHandle> {
        val mode = options.coverFlip
        if (!options.immersiveCoverLayout && mode == RootConstants.CONTROL_CENTER_MEDIA_COVER_FLIP_DEFAULT) {
            return emptyList()
        }
        val handles = mutableListOf<HookHandle>()
        fun hook(method: Method, name: String, hooker: Hooker) {
            method.isAccessible = true
            handles += module.managedHook(method, "media.controlcenter.cover_flip.$name", hooker)
        }
        try {
            val setCover = header.getDeclaredMethod("setCover", Bitmap::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            val endScenario = header.getDeclaredMethod("endSwitchMusicScenario").apply { isAccessible = true }
            val nextMedia = header.getDeclaredField("nextMedia").apply { isAccessible = true }
            val refreshers = WeakHashMap<View, ControlCenterMediaCoverFlipRefresh>()
            val coverParent = header.getDeclaredField("coverParent").apply { isAccessible = true }
            val useCoverAnim = header.getDeclaredField("useCoverAnim").apply { isAccessible = true }
            val flipType = header.getDeclaredField("flipAnimation").type
            val isFlipping = flipType.getDeclaredMethod("isInFlipAnimation", View::class.java)
                .apply { isAccessible = true }
            fun refresher(view: View) = refreshers.getOrPut(view) {
                ControlCenterMediaCoverFlipRefresh(view,
                    isFlipping = { isFlipping.invoke(null, it) as Boolean },
                    canApply = { useCoverAnim.getBoolean(it) },
                    apply = { owner, bitmap ->
                        endScenario.invoke(owner)
                        setCover.invoke(owner, bitmap, true, false)
                    })
            }
            hook(header.getDeclaredMethod("performFlip", Bitmap::class.java), "perform", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val view = chain.thisObject as? View ?: return chain.proceed()
                    if (mode == RootConstants.CONTROL_CENTER_MEDIA_COVER_FLIP_DISABLED) {
                        // Match the native midpoint callback so artwork and scenario
                        // bookkeeping still complete without starting an animation.
                        endScenario.invoke(view)
                        setCover.invoke(view, chain.args[0], true, false)
                        nextMedia.setBoolean(view, true)
                        return null
                    }
                    (view.getTag(R.id.control_center_media_layout_state) as? ControlCenterMediaLayout)
                        ?.prepareCoverFlip()
                    if (mode != RootConstants.CONTROL_CENTER_MEDIA_COVER_FLIP_ENABLED) return chain.proceed()
                    val refresh = refresher(view)
                    refresh.begin(coverParent.get(view) as View, chain.args[0] as? Bitmap)
                    return try {
                        chain.proceed().also { refresh.afterStart() }
                    } catch (error: Throwable) {
                        refresh.stop()
                        throw error
                    }
                }
            })
            if (mode == RootConstants.CONTROL_CENTER_MEDIA_COVER_FLIP_ENABLED) {
                val updateCover = header.declaredMethods.single {
                    it.name == "updateCover" && it.parameterCount == 2 &&
                        it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                val metadataType = updateCover.parameterTypes[0]
                val extensions = loader.loadClass("com.android.systemui.MiPlayExtentionsKt")
                val model = loader.loadClass("com.android.systemui.MiPlayDetailViewModel")
                val modelInstance = model.getField("INSTANCE").get(null)
                val sourcePackage = model.getDeclaredMethod("getSourcePackage")
                val title = metadataType.getMethod("getTitle")
                val artist = metadataType.getMethod("getArtist")
                val album = metadataType.getMethod("getAlbum")
                val artwork = metadataType.getMethod("getArt")
                val active = ThreadLocal<Update>()
                val fallbackArtwork = WeakHashMap<View, ArtworkIdentity>()
                hook(updateCover, "update", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val view = chain.thisObject as? View ?: return chain.proceed()
                        val metadata = chain.args[0] ?: return chain.proceed()
                        refresher(view).update(artwork.invoke(metadata) as? Bitmap)
                        val previous = active.get()
                        active.set(Update(view, metadata))
                        return try {
                            // Keep native first-load, useCoverAnim, queued updates,
                            // direction and midpoint bitmap replacement behavior.
                            chain.proceed()
                        } finally {
                            if (previous == null) active.remove() else active.set(previous)
                        }
                    }
                })
                hook(extensions.getDeclaredMethod("getMediaID", metadataType, Int::class.javaObjectType),
                    "identity", object : Hooker {
                        override fun intercept(chain: Chain): Any? {
                            val nativeId = chain.proceed()
                            val update = active.get() ?: return nativeId
                            if (chain.args[0] !== update.metadata) return nativeId
                            val songTitle = title.invoke(update.metadata) as? String
                            val songArtist = artist.invoke(update.metadata) as? String
                            val songAlbum = album.invoke(update.metadata) as? String
                            val artToken = if (listOf(songTitle, songArtist, songAlbum).all { it.isNullOrBlank() }) {
                                fallbackArtwork.getOrPut(update.header) { ArtworkIdentity() }
                                    .token(artwork.invoke(update.metadata) as? Bitmap)
                            } else 0L
                            return coverFlipMediaIdentity(nativeId as? String,
                                sourcePackage.invoke(modelInstance) as? String,
                                songTitle, songArtist, songAlbum, artToken)
                        }
                    })
                hook(model.getDeclaredMethod("isLowDevice"), "device_policy", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        // The explicit Enabled choice overrides the animation
                        // policy only inside this Header update, not global UI.
                        return if (active.get() != null) false else chain.proceed()
                    }
                })
            }
            return handles
        } catch (error: Throwable) {
            handles.asReversed().forEach { runCatching { it.unhook() } }
            throw error
        }
    }

    private class ArtworkIdentity {
        private var previous: Bitmap? = null
        private var sequence = 0L

        fun token(bitmap: Bitmap?): Long {
            val same = bitmap === previous || runCatching {
                bitmap != null && previous != null && bitmap.sameAs(previous)
            }.getOrDefault(false)
            if (!same) sequence++
            previous = bitmap
            return sequence
        }
    }
}

/** Length prefixes avoid collisions when track names themselves contain separators. */
internal fun coverFlipMediaIdentity(
    nativeId: String?, packageName: String?, title: String?, artist: String?, album: String?,
    artworkToken: Long = 0L,
): String = listOf(nativeId, packageName, title, artist, album, artworkToken.toString())
    .joinToString(separator = "", prefix = "HyperLyric:") { value ->
        val text = value.orEmpty().trim()
        "${text.length}:$text"
    }
