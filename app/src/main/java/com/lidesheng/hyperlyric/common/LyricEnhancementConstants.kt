package com.lidesheng.hyperlyric.common

object LyricEnhancementConstants {
    const val AMLL_TTML_FEATURE_ID = "hyperlyric.amll.ttml"
    const val AI_TRANSLATION_FEATURE_ID = "hyperlyric.ai.translation"

    fun isSupportedFeature(featureId: String): Boolean = featureId == AMLL_TTML_FEATURE_ID ||
            featureId == AI_TRANSLATION_FEATURE_ID
}
