package com.settle.domains.payment.application.port.circuitbreaker

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey

interface PgPaymentCircuitBreakerPort {
    fun <T> execute(
        key: PgPaymentCircuitBreakerKey,
        block: () -> T,
    ): T
}
