package com.settle.domains.payment.infrastructure.circuitbreaker

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey
import com.settle.domains.payment.application.port.circuitbreaker.PgPaymentCircuitBreakerPort
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry

class Resilience4jPgPaymentCircuitBreakerAdapter(
    private val registry: CircuitBreakerRegistry,
) : PgPaymentCircuitBreakerPort {
    override fun <T> execute(
        key: PgPaymentCircuitBreakerKey,
        block: () -> T,
    ): T {
        val circuitBreaker = registry.circuitBreaker(key.circuitName)
        val supplier = CircuitBreaker.decorateSupplier(circuitBreaker) { block() }

        return supplier.get()
    }
}
