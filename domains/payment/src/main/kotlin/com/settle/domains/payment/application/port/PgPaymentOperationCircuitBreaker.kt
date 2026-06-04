package com.settle.domains.payment.application.port

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey

interface PgPaymentOperationCircuitBreaker {
    fun <T> execute(
        key: PgPaymentCircuitBreakerKey,
        block: () -> T,
    ): T
}
