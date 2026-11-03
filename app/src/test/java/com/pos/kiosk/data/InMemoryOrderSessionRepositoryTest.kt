package com.pos.kiosk.data

import com.pos.kiosk.data.repository.InMemoryOrderSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InMemoryOrderSessionRepositoryTest {

    @Test
    fun `concurrent callers get one idempotency key for the same cart`() {
        val repository = InMemoryOrderSessionRepository()
        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val keys = Collections.synchronizedSet(mutableSetOf<String>())

        repeat(threads) {
            pool.execute {
                start.await()
                repeat(200) { keys += repository.checkoutAttemptFor("cart-A").idempotencyKey }
                done.countDown()
            }
        }
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertEquals(1, keys.size)
        assertEquals(keys.single(), repository.session.value.checkout?.idempotencyKey)
    }

    @Test
    fun `a changed cart gets a new key`() {
        val repository = InMemoryOrderSessionRepository()
        val first = repository.checkoutAttemptFor("cart-A")

        assertEquals(first, repository.checkoutAttemptFor("cart-A"))
        assertNotEquals(first.idempotencyKey, repository.checkoutAttemptFor("cart-B").idempotencyKey)
    }
}
