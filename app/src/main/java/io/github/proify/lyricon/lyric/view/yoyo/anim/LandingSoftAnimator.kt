package io.github.proify.lyricon.lyric.view.yoyo.anim

import android.animation.ObjectAnimator
import android.view.View
import androidx.annotation.Keep
import com.daimajia.androidanimations.library.BaseViewAnimator
import com.daimajia.easing.Glider
import com.daimajia.easing.Skill

@Keep
class LandingSoftAnimator : BaseViewAnimator() {

    override fun prepare(target: View) {
        animatorAgent.playTogether(
            Glider.glide(
                Skill.QuintEaseOut,
                duration.toFloat(),
                ObjectAnimator.ofFloat(target, "scaleX", 1.2f, 1f)
            ),
            Glider.glide(
                Skill.QuintEaseOut,
                duration.toFloat(),
                ObjectAnimator.ofFloat(target, "scaleY", 1.2f, 1f)
            ),
            Glider.glide(
                Skill.QuintEaseOut,
                duration.toFloat(),
                ObjectAnimator.ofFloat(target, "alpha", 0f, 1f)
            )
        )
    }
}