package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationSchedulerTest {
    @Test
    fun completedResultStaysDeduplicatedUntilConsumerReleasesIt() {
        val scheduler = TranslationScheduler(NO_OP_LOGGER)
        val requestStarted = CountDownLatch(1)
        val allowNetworkReturn = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val caller = Executors.newSingleThreadExecutor()

        try {
            val firstFuture = caller.submit<TranslationScheduler.ScheduledTranslation> {
                scheduler.getOrEnqueue(
                    key = "same-key",
                    songName = "song"
                ) {
                    calls.incrementAndGet()
                    requestStarted.countDown()
                    allowNetworkReturn.await()
                    listOf(TranslationItem(0, "translated"))
                }
            }

            assertTrue(requestStarted.await(2, TimeUnit.SECONDS))
            allowNetworkReturn.countDown()
            val first = firstFuture.get(2, TimeUnit.SECONDS)
            Thread.sleep(50)

            val second = scheduler.getOrEnqueue(
                key = "same-key",
                songName = "song"
            ) {
                calls.incrementAndGet()
                listOf(TranslationItem(0, "duplicate-network"))
            }

            assertEquals(1, calls.get())
            assertEquals(first.items, second.items)
            first.release()
            second.release()
        } finally {
            scheduler.close()
            caller.shutdownNow()
        }
    }

    private companion object {
        val NO_OP_LOGGER = LyricEnhancementLogger(
            "Test",
            object : HyperLogger {
                override fun d(tag: String, msg: String) = Unit
                override fun i(tag: String, msg: String) = Unit
                override fun w(tag: String, msg: String, e: Throwable?) = Unit
                override fun e(tag: String, msg: String, e: Throwable?) = Unit
            }
        )
    }
}
