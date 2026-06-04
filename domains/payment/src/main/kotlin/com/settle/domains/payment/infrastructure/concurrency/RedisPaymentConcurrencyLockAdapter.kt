package com.settle.domains.payment.infrastructure.concurrency

import com.settle.domains.payment.application.port.concurrency.PaymentConcurrencyLockPort
import com.settle.domains.payment.application.usecase.DuplicatePaymentRequestException
import com.settle.domains.payment.application.usecase.PaymentLockOperation
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

class RedisPaymentConcurrencyLockAdapter(
    private val redis: RedisNxLockClient,
    private val ttl: Duration = Duration.ofMinutes(5),
) : PaymentConcurrencyLockPort {
    override fun <T> withLock(
        operation: PaymentLockOperation,
        idempotencyKey: String,
        block: () -> T,
    ): T {
        val lockKey = operation.redisKey(idempotencyKey)
        val token = UUID.randomUUID().toString()
        val acquired = redis.setIfAbsent(lockKey, token, ttl)

        if (!acquired) {
            throw DuplicatePaymentRequestException(lockKey)
        }

        try {
            return block()
        } finally {
            if (redis.get(lockKey) == token) {
                redis.delete(lockKey)
            }
        }
    }
}

interface RedisNxLockClient {
    fun setIfAbsent(
        key: String,
        value: String,
        ttl: Duration,
    ): Boolean

    fun get(key: String): String?

    fun delete(key: String)
}

class StringRedisNxLockClient(
    private val redis: StringRedisTemplate,
) : RedisNxLockClient {
    override fun setIfAbsent(
        key: String,
        value: String,
        ttl: Duration,
    ): Boolean = redis.opsForValue().setIfAbsent(key, value, ttl) == true

    override fun get(key: String): String? = redis.opsForValue().get(key)

    override fun delete(key: String) {
        redis.delete(key)
    }
}
