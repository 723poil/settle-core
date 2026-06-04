package com.settle.domains.payment.application.port.concurrency

import com.settle.domains.payment.application.usecase.PaymentLockOperation

interface PaymentConcurrencyLockPort {
    fun <T> withLock(
        operation: PaymentLockOperation,
        idempotencyKey: String,
        block: () -> T,
    ): T
}
