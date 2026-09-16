package com.lidesheng.hyperlyric.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricEnhancementCacheOperationTest {
    @Test
    fun requestAndResponseRoundTripKeepOnlyCacheMetadata() {
        val request = LyricEnhancementCacheOperationRequest(
            requestId = "request-1234",
            responseToken = "token-1234",
            featureId = LyricEnhancementConstants.AMLL_TTML_FEATURE_ID,
            type = LyricEnhancementCacheOperationType.CLEAR_ENTRY,
            entryId = "a".repeat(64),
            createdAtEpochMs = 1_000L
        )
        assertEquals(request, LyricEnhancementCacheOperationCodec.decodeRequest(
            LyricEnhancementCacheOperationCodec.encodeRequest(request)
        ))

        val response = LyricEnhancementCacheOperationResponse(
            requestId = request.requestId,
            success = true,
            entries = listOf(
                LyricEnhancementCacheEntry(
                    id = request.entryId!!,
                    title = "title",
                    artist = "artist"
                )
            )
        )
        val decoded = LyricEnhancementCacheOperationCodec.decodeResponse(
            LyricEnhancementCacheOperationCodec.encodeResponse(response)
        )
        assertEquals("title", decoded?.entries?.single()?.title)
        assertEquals("artist", decoded?.entries?.single()?.artist)
        assertTrue(decoded?.success == true)
    }
}
