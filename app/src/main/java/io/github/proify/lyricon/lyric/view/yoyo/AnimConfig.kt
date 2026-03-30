package io.github.proify.lyricon.lyric.view.yoyo

import android.view.animation.Interpolator
import com.daimajia.androidanimations.library.BaseViewAnimator
import com.daimajia.androidanimations.library.Techniques
import io.github.proify.lyricon.lyric.view.yoyo.anim.MyTechniques

/**
 * 动画配置参数类
 *
 * @property technique 动画效果
 * @property duration 动画时长
 * @property interpolator 插值器
 */
data class AnimConfig(
    val technique: BaseViewAnimator,
    val duration: Long,
    val interpolator: Interpolator
) {

    constructor(tech: Techniques, duration: Long, interpolator: Interpolator) : this(
        tech.animator,
        duration,
        interpolator
    )

    constructor(
        tech: MyTechniques,
        duration: Long,
        interpolator: Interpolator
    ) : this(tech.getAnimator(), duration, interpolator)
}