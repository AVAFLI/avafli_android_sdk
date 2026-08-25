package com.avafli.avaflisdk

import com.avafli.avaflisdk.ui.v2.AvafliV2ImageWarmer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the 2.3.3 remote-art prewarm bookkeeping: the SDK warms `prizeImageUrl`
 * and the publisher logo the moment it learns the giveaway config, so a repeat
 * refresh must be free — but a URL that FAILED has to be retryable, or one
 * flaky moment at registration would leave the prize card cold forever.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImageWarmerTest {

    private val url = "https://cdn.example.com/prize.png"
    private val calls = AtomicInteger(0)

    @Before
    fun setup() {
        AvafliV2ImageWarmer.resetForTesting()
    }

    @After
    fun tearDown() {
        AvafliV2ImageWarmer.resetForTesting()
    }

    private fun stubFetcher(succeeds: Boolean) {
        AvafliV2ImageWarmer.fetcher = {
            calls.incrementAndGet()
            succeeds
        }
    }

    @Test
    fun `warms a url once`() = runTest {
        stubFetcher(succeeds = true)

        AvafliV2ImageWarmer.prewarm(url)?.join()

        assertEquals(1, calls.get())
        assertTrue(AvafliV2ImageWarmer.isWarmed(url))
    }

    @Test
    fun `repeat refresh is a no-op`() = runTest {
        stubFetcher(succeeds = true)

        AvafliV2ImageWarmer.prewarm(url)?.join()
        // Every later giveaway refresh re-offers the same URL.
        assertNull(AvafliV2ImageWarmer.prewarm(url))
        assertNull(AvafliV2ImageWarmer.prewarm(url))

        assertEquals(1, calls.get())
    }

    @Test
    fun `a failed url is dropped so the next refresh retries it`() = runTest {
        stubFetcher(succeeds = false)

        AvafliV2ImageWarmer.prewarm(url)?.join()
        assertEquals(1, calls.get())
        assertFalse(AvafliV2ImageWarmer.isWarmed(url))

        // Second chance: the next refresh must actually go back out.
        stubFetcher(succeeds = true)
        val retry = AvafliV2ImageWarmer.prewarm(url)
        assertNotNull(retry)
        retry?.join()

        assertEquals(2, calls.get())
        assertTrue(AvafliV2ImageWarmer.isWarmed(url))
    }

    @Test
    fun `a throwing fetcher is swallowed and left retryable`() = runTest {
        AvafliV2ImageWarmer.fetcher = {
            calls.incrementAndGet()
            throw RuntimeException("decode blew up")
        }

        AvafliV2ImageWarmer.prewarm(url)?.join()

        assertEquals(1, calls.get())
        assertFalse(AvafliV2ImageWarmer.isWarmed(url))
    }

    @Test
    fun `absent urls are ignored`() = runTest {
        stubFetcher(succeeds = true)

        assertNull(AvafliV2ImageWarmer.prewarm(null))
        assertNull(AvafliV2ImageWarmer.prewarm(""))
        assertNull(AvafliV2ImageWarmer.prewarm("   "))

        assertEquals(0, calls.get())
    }

    @Test
    fun `distinct urls warm independently`() = runTest {
        stubFetcher(succeeds = true)
        val logo = "https://cdn.example.com/logo.png"

        AvafliV2ImageWarmer.prewarm(url)?.join()
        AvafliV2ImageWarmer.prewarm(logo)?.join()

        assertEquals(2, calls.get())
        assertTrue(AvafliV2ImageWarmer.isWarmed(url))
        assertTrue(AvafliV2ImageWarmer.isWarmed(logo))
    }
}
