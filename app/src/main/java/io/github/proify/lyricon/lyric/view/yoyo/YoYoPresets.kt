package io.github.proify.lyricon.lyric.view.yoyo

import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.daimajia.androidanimations.library.Techniques
import io.github.proify.lyricon.lyric.view.yoyo.anim.MyTechniques

object YoYoPresets {

    private fun pair(
        outTech: Any,
        outDur: Long,
        outInterp: Interpolator,
        inTech: Any,
        inDur: Long,
        inInterp: Interpolator
    ): Pair<AnimConfig, AnimConfig> {
        @Suppress("CascadeIf")
        if (outTech is Techniques && inTech is Techniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        } else if (outTech is MyTechniques && inTech is MyTechniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        } else if (outTech is MyTechniques && inTech is Techniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        } else if (outTech is Techniques && inTech is MyTechniques) {
            return AnimConfig(outTech, outDur, outInterp) to AnimConfig(inTech, inDur, inInterp)
        }
        throw IllegalArgumentException("Invalid animation type")
    }

    // region Fade Animations

    val Default: Pair<AnimConfig, AnimConfig>
        get() = FadeOutUp_FadeInUp

    val FadeOut_FadeIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOut, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeIn, 300L, FastOutSlowInInterpolator()
        )

    val FadeOutLeft_FadeInRight: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutLeft, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInRight, 450L, OvershootInterpolator(1.5f)
        )

    val FadeOutLeft_FadeInUp: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutLeft, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

    val FadeOutLeft_ZoomIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutLeft, 300L, FastOutLinearInInterpolator(),
            Techniques.ZoomIn, 400L, FastOutSlowInInterpolator()
        )

    val FadeOutLeft_Landing: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutLeft, 300L, FastOutLinearInInterpolator(),
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

    val FadeOutRight_FadeInLeft: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutRight, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInLeft, 450L, OvershootInterpolator(1.5f)
        )

    val FadeOutRight_FadeInUp: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutRight, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

    val FadeOutRight_ZoomIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutRight, 200L, FastOutLinearInInterpolator(),
            Techniques.ZoomIn, 400L, FastOutSlowInInterpolator()
        )

    val FadeOutRight_Landing: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutRight, 300L, FastOutLinearInInterpolator(),
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

    val FadeOutUp_FadeInUp: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutUp, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

    val FadeOutDown_FadeInDown: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutDown, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInDown, 450L, FastOutSlowInInterpolator()
        )

    // endregion

    // region Slide Animations

    val SlideOutLeft_SlideInRight: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutLeft, 300L, FastOutLinearInInterpolator(),
            Techniques.SlideInRight, 450L, OvershootInterpolator(2.0f)
        )

    val SlideOutLeft_FadeInUp: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutLeft, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

    val SlideOutLeft_ZoomIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutLeft, 300L, FastOutLinearInInterpolator(),
            Techniques.ZoomIn, 450L, FastOutSlowInInterpolator()
        )

    val SlideOutLeft_Landing: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutLeft, 300L, FastOutLinearInInterpolator(),
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

    val SlideOutRight_SlideInLeft: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutRight, 300L, FastOutLinearInInterpolator(),
            Techniques.SlideInLeft, 450L, OvershootInterpolator(1.5f)
        )

    val SlideOutRight_FadeInUp: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutRight, 300L, FastOutLinearInInterpolator(),
            Techniques.FadeInUp, 450L, FastOutSlowInInterpolator()
        )

    val SlideOutRight_ZoomIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutRight, 300L, FastOutLinearInInterpolator(),
            Techniques.ZoomIn, 450L, FastOutSlowInInterpolator()
        )

    val SlideOutRight_Landing: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.SlideOutRight, 300L, FastOutLinearInInterpolator(),
            MyTechniques.LandingSoft, 700L, FastOutSlowInInterpolator()
        )

    // endregion

    // region Flip/Rotate/Zoom Animations

    val FlipOutX_FlipInX: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FlipOutX, 300L, FastOutLinearInInterpolator(),
            Techniques.FlipInX, 450L, FastOutSlowInInterpolator()
        )

    val FlipOutY_FlipInY: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FlipOutY, 300L, FastOutLinearInInterpolator(),
            Techniques.FlipInY, 450L, FastOutSlowInInterpolator()
        )

    val RotateOut_RotateIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.RotateOut, 200L, FastOutLinearInInterpolator(),
            Techniques.RotateIn, 600L, OvershootInterpolator(1.0f)
        )

    val ZoomOut_ZoomIn: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.ZoomOut, 200L, FastOutLinearInInterpolator(),
            Techniques.ZoomIn, 400L, FastOutSlowInInterpolator()
        )

    val FadeOutLeft_ZoomInRight: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutLeft, 250L, FastOutLinearInInterpolator(),
            Techniques.ZoomInRight, 600L, FastOutSlowInInterpolator()
        )

    val FadeOutRight_ZoomInLeft: Pair<AnimConfig, AnimConfig>
        get() = pair(
            Techniques.FadeOutRight, 250L, FastOutLinearInInterpolator(),
            Techniques.ZoomInLeft, 600L, FastOutSlowInInterpolator()
        )

    // endregion

    val registry: Map<String, () -> Pair<AnimConfig, AnimConfig>> by lazy {
        mapOf(
            "default" to { Default },
            "fade_out_fade_in" to { FadeOut_FadeIn },
            "fade_out_up_fade_in_up" to { FadeOutUp_FadeInUp },
            "fade_out_down_fade_in_down" to { FadeOutDown_FadeInDown },
            "fade_out_left_fade_in_right" to { FadeOutLeft_FadeInRight },
            "fade_out_left_fade_in_up" to { FadeOutLeft_FadeInUp },
            "fade_out_left_zoom_in" to { FadeOutLeft_ZoomIn },
            "fade_out_left_landing" to { FadeOutLeft_Landing },
            "fade_out_right_fade_in_left" to { FadeOutRight_FadeInLeft },
            "fade_out_right_fade_in_up" to { FadeOutRight_FadeInUp },
            "fade_out_right_zoom_in" to { FadeOutRight_ZoomIn },
            "fade_out_right_landing" to { FadeOutRight_Landing },
            "slide_out_left_slide_in_right" to { SlideOutLeft_SlideInRight },
            "slide_out_left_fade_in_up" to { SlideOutLeft_FadeInUp },
            "slide_out_left_zoom_in" to { SlideOutLeft_ZoomIn },
            "slide_out_left_landing" to { SlideOutLeft_Landing },
            "slide_out_right_slide_in_left" to { SlideOutRight_SlideInLeft },
            "slide_out_right_fade_in_up" to { SlideOutRight_FadeInUp },
            "slide_out_right_zoom_in" to { SlideOutRight_ZoomIn },
            "slide_out_right_landing" to { SlideOutRight_Landing },
            "flip_out_x_flip_in_x" to { FlipOutX_FlipInX },
            "flip_out_y_flip_in_y" to { FlipOutY_FlipInY },
            "rotate_out_rotate_in" to { RotateOut_RotateIn },
            "zoom_out_zoom_in" to { ZoomOut_ZoomIn },
            "fade_out_left_zoom_in_right" to { FadeOutLeft_ZoomInRight },
            "fade_out_right_zoom_in_left" to { FadeOutRight_ZoomInLeft }
        )
    }

    fun getById(id: String?): Pair<AnimConfig, AnimConfig>? = registry[id]?.invoke()
}