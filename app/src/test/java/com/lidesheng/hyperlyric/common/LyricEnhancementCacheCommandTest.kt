package com.lidesheng.hyperlyric.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricEnhancementCacheCommandTest {
    @Test
    fun clearRequestRoundTripKeepsFeatureAndTimestamp() {
        val encoded = LyricEnhancementCacheCommand.encodeClearRequest(
            featureId = LyricEnhancementConstants.AMLL_TTML_FEATURE_ID,
            createdAtEpochMs = 1_000L
        )

        assertEquals(
            LyricEnhancementCacheCommand.ClearRequest(
                featureId = LyricEnhancementConstants.AMLL_TTML_FEATURE_ID,
                createdAtEpochMs = 1_000L
            ),
            LyricEnhancementCacheCommand.decodeClearRequest(encoded, nowEpochMs = 1_500L)
        )
    }

    @Test
    fun malformedExpiredAndUnknownRequestsAreIgnored() {
        assertNull(LyricEnhancementCacheCommand.decodeClearRequest("invalid", 1_500L))
        assertNull(
            LyricEnhancementCacheCommand.decodeClearRequest(
                LyricEnhancementCacheCommand.encodeClearRequest(
                    LyricEnhancementConstants.AMLL_TTML_FEATURE_ID,
                    createdAtEpochMs = 1L
                ),
                nowEpochMs = 1L + LyricEnhancementCacheCommand.REQUEST_TTL_MS + 1L
            )
        )
        assertNull(
            LyricEnhancementCacheCommand.decodeClearRequest(
                "plugin.example\u001F1000",
                nowEpochMs = 1_500L
            )
        )
    }
}
