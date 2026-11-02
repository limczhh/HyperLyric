package com.lidesheng.hyperlyric.root.island.effects.color

import android.graphics.Bitmap
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolves legacy callbacks and newer, obfuscated Lottie wrappers by their native contracts. */
internal class IslandMusicWaveNativeApi private constructor(
    val paletteMethod: Method?,
    val gradientMethods: List<Method>,
    val gradientCallers: List<Method>,
    val frameInfoClass: Class<*>,
    val callbackRegistration: Method?,
    val drawableField: Field?
) {
    fun callbackMethods(callbackClass: Class<*>): List<Method> =
        callbackClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.contentEquals(arrayOf(frameInfoClass)) &&
                (method.returnType == Any::class.java || method.returnType == BOXED_COLORS)
        }

    fun nativeColors(holder: Any, result: Any?): Array<Int>? {
        if (paletteMethod?.name == "setLottieColor") {
            return arrayOf("gradientTopColor", "gradientBottomColor").map { name ->
                holder.javaClass.getDeclaredField(name).apply { isAccessible = true }.getInt(null)
            }.toTypedArray()
        }
        result ?: return null
        // Kotlin Pair's accessors can be removed by R8. Its two ordered value fields remain.
        val fields = result.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            .sortedBy { it.name }
        if (fields.size != 2) return null
        val values = fields.map { it.apply { isAccessible = true }.get(result) as? Number ?: return null }
        return arrayOf(values[0].toInt(), values[1].toInt())
    }

    companion object {
        private val BOXED_COLORS = arrayOf<Int>().javaClass
        private val FLOAT = Float::class.javaPrimitiveType
        private val FRAME_ARGUMENTS = arrayOf(FLOAT, FLOAT, Any::class.java, Any::class.java,
            FLOAT, FLOAT, FLOAT)

        fun create(holderClass: Class<*>): IslandMusicWaveNativeApi {
            val paletteMethods = holderClass.declaredMethods.filter {
                it.parameterTypes.contentEquals(arrayOf(Bitmap::class.java))
            }
            val palette = paletteMethods.firstOrNull {
                it.name == "setLottieColor" && it.returnType == Void.TYPE
            } ?: paletteMethods.firstOrNull {
                it.name == "getLottieColor" && !it.returnType.isPrimitive
            }
            val legacy = try {
                requireNotNull(holderClass.classLoader).loadClass(
                    "${holderClass.name}\$Companion\$GradientLottieValueCallback"
                )
            } catch (_: ClassNotFoundException) {
                null
            }
            if (legacy != null) {
                val methods = legacy.declaredMethods.filter {
                    it.name == "getValue" && it.parameterCount == 1 &&
                        it.parameterTypes[0].name.startsWith("com.airbnb.lottie.value.") &&
                        (it.returnType == BOXED_COLORS || it.returnType == Any::class.java)
                }
                val typed = methods.firstOrNull { it.returnType == BOXED_COLORS }
                    ?: error("Legacy music-wave gradient callback unavailable")
                val registration = callbackRegistration(holderClass, legacy)
                return IslandMusicWaveNativeApi(
                    palette, methods, frameCallers(requireNotNull(legacy.superclass)),
                    typed.parameterTypes[0], registration.first, registration.second
                )
            }

            // The native lambda retains its name, but its frame-info type and wrapper are obfuscated.
            val lambda = holderClass.declaredMethods.singleOrNull {
                it.name.startsWith("registerLottieCallback\$lambda\$") &&
                    Modifier.isStatic(it.modifiers) && it.parameterCount == 1 &&
                    it.returnType == BOXED_COLORS
            } ?: error("Music-wave gradient lambda unavailable")
            val lottieClass = holderClass.getDeclaredField("lottieView").type
            val addCallback = lottieClass.methods.singleOrNull {
                it.name == "addValueCallback" && it.parameterCount == 3 &&
                    it.returnType == Void.TYPE && it.parameterTypes[1] == Any::class.java &&
                    !it.parameterTypes[2].isInterface && !it.parameterTypes[2].isPrimitive
            } ?: error("Lottie value-callback registration unavailable")
            val drawable = lottieClass.getDeclaredField("lottieDrawable")
                .apply { isAccessible = true }
            // Capture the real wrapper after LottieAnimationView has converted its functional callback.
            val registration = drawable.type.declaredMethods.singleOrNull {
                it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(addCallback.parameterTypes)
            } ?: error("Native Lottie drawable callback registration unavailable")
            return IslandMusicWaveNativeApi(
                palette, emptyList(), frameCallers(addCallback.parameterTypes[2]),
                lambda.parameterTypes[0], registration, drawable
            )
        }

        private fun callbackRegistration(holderClass: Class<*>, callbackType: Class<*>): Pair<Method, Field> {
            val lottieClass = holderClass.getDeclaredField("lottieView").type
            val add = lottieClass.methods.single {
                it.name == "addValueCallback" && it.parameterCount == 3 &&
                    it.parameterTypes[1] == Any::class.java &&
                    !it.parameterTypes[2].isInterface && it.parameterTypes[2].isAssignableFrom(callbackType)
            }
            val drawable = lottieClass.getDeclaredField("lottieDrawable").apply { isAccessible = true }
            val registration = drawable.type.declaredMethods.single {
                it.returnType == Void.TYPE && it.parameterTypes.contentEquals(add.parameterTypes)
            }
            return registration to drawable
        }

        private fun frameCallers(owner: Class<*>): List<Method> =
            owner.declaredMethods.filter {
                it.returnType == Any::class.java &&
                    it.parameterTypes.contentEquals(FRAME_ARGUMENTS)
            }
    }
}
