package com.settle.domains.payment.infrastructure.circuitbreaker

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey
import com.settle.domains.payment.application.port.PgPaymentOperationCircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry

class Resilience4jPgPaymentOperationCircuitBreaker(
    private val registry: CircuitBreakerRegistry,
) : PgPaymentOperationCircuitBreaker {
    override fun <T> execute(
        key: PgPaymentCircuitBreakerKey,
        block: () -> T,
    ): T {
        val circuitBreaker = registry.circuitBreaker(key.circuitName)
        val supplier = CircuitBreaker.decorateSupplier(circuitBreaker) { block() }

        return supplier.get()
    }
}
