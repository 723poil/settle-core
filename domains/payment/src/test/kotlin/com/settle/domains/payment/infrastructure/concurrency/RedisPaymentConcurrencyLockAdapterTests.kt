package com.settle.domains.payment.infrastructure.concurrency

import com.settle.domains.payment.application.usecase.DuplicatePaymentRequestException
import com.settle.domains.payment.application.usecase.PaymentLockOperation
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RedisPaymentConcurrencyLockAdapterTests {
    @Test
    fun acquiresRedisNxLockWithPaymentOperationKeyAndReleasesAfterWork() {
        val redis = RecordingRedisNxLockClient()
        val adapter = RedisPaymentConcurrencyLockAdapter(redis, ttl = Duration.ofSeconds(30))

        val result =
            adapter.withLock(PaymentLockOperation.PREPARE, "idempotency-001") {
                "prepared"
            }

        assertEquals("prepared", result)
        assertEquals("payment:prepare:idempotency-001", redis.setIfAbsentCalls.single().key)
        assertEquals(Duration.ofSeconds(30), redis.setIfAbsentCalls.single().ttl)
        assertEquals("payment:prepare:idempotency-001", redis.deletedKeys.single())
    }

    @Test
    fun rejectsWorkWhenRedisNxLockAlreadyExists() {
        val redis = RecordingRedisNxLockClient(acquire = false)
        val adapter = RedisPaymentConcurrencyLockAdapter(redis)

        assertFailsWith<DuplicatePaymentRequestException> {
            adapter.withLock(PaymentLockOperation.AUTHORIZE, "idempotency-001") {
                "not-used"
            }
        }

        assertEquals("payment:authorize:idempotency-001", redis.setIfAbsentCalls.single().key)
        assertEquals(emptyList(), redis.deletedKeys)
    }

    @Test
    fun doesNotReleaseLockWhenStoredTokenWasChangedByAnotherOwner() {
        val redis = RecordingRedisNxLockClient()
        val adapter = RedisPaymentConcurrencyLockAdapter(redis)

        adapter.withLock(PaymentLockOperation.CANCEL, "idempotency-001") {
            redis.overwrite("payment:cancel:idempotency-001", "other-owner-token")
        }

        assertEquals("payment:cancel:idempotency-001", redis.setIfAbsentCalls.single().key)
        assertEquals(emptyList(), redis.deletedKeys)
    }

    private class RecordingRedisNxLockClient(
        private val acquire: Boolean = true,
    ) : RedisNxLockClient {
        val setIfAbsentCalls = mutableListOf<SetIfAbsentCall>()
        val deletedKeys = mutableListOf<String>()
        private val values = mutableMapOf<String, String>()

        override fun setIfAbsent(
            key: String,
            value: String,
            ttl: Duration,
        ): Boolean {
            setIfAbsentCalls += SetIfAbsentCall(key, ttl)
            if (!acquire) {
                return false
            }
            values[key] = value
            return true
        }

        override fun get(key: String): String? = values[key]

        override fun delete(key: String) {
            deletedKeys += key
            values.remove(key)
        }

        fun overwrite(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }

    private data class SetIfAbsentCall(
        val key: String,
        val ttl: Duration,
    )
}
