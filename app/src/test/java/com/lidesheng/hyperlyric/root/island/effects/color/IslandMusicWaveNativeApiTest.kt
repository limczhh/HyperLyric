package com.lidesheng.hyperlyric.root.island.effects.color

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

class IslandMusicWaveNativeApiTest {
    class Legacy {
        companion object {
            @JvmField var gradientTopColor = 0
            @JvmField var gradientBottomColor = 0
        }
        fun setLottieColor(bitmap: Bitmap?) = Unit
    }
    class Modern {
        fun getLottieColor(bitmap: Bitmap?): Any? = null
    }
    // R8 strips Kotlin Pair accessors in the supplied OS 4 plugin.
    class NativePair(val a: Any, val b: Any)

    private fun api(method: Method): IslandMusicWaveNativeApi =
        IslandMusicWaveNativeApi::class.java.declaredConstructors.first { it.parameterCount == 6 }
            .apply { isAccessible = true }
            .newInstance(method, emptyList<Method>(), emptyList<Method>(), Any::class.java, null, null)
            as IslandMusicWaveNativeApi

    @Test fun legacyCapturesDoNotChangeWhenAnotherIslandUpdatesTheStaticPalette() {
        val api = api(Legacy::class.java.getDeclaredMethod("setLottieColor", Bitmap::class.java))
        val holder = Legacy()
        Legacy.gradientTopColor = 0x99112233.toInt()
        Legacy.gradientBottomColor = 0x99445566.toInt()
        val first = api.nativeColors(holder, null)!!
        Legacy.gradientTopColor = 0x99778899.toInt()
        Legacy.gradientBottomColor = 0x99aabbcc.toInt()
        val second = api.nativeColors(holder, null)!!
        assertArrayEquals(arrayOf(0x99112233.toInt(), 0x99445566.toInt()), first)
        assertArrayEquals(arrayOf(0x99778899.toInt(), 0x99aabbcc.toInt()), second)
        assertNotSame(first, second)
    }

    @Test fun modernReadsEachNativeReturnValueIncludingItsAlpha() {
        val api = api(Modern::class.java.getDeclaredMethod("getLottieColor", Bitmap::class.java))
        val first = NativePair(0x99112233.toInt(), 0x99445566.toInt())
        val second = NativePair(0x99778899.toInt(), 0x99aabbcc.toInt())
        assertArrayEquals(arrayOf(0x99112233.toInt(), 0x99445566.toInt()), api.nativeColors(Modern(), first))
        assertArrayEquals(arrayOf(0x99778899.toInt(), 0x99aabbcc.toInt()), api.nativeColors(Modern(), second))
    }
}
