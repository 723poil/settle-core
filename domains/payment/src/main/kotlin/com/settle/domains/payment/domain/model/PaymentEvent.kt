package com.settle.domains.payment.domain.model

import java.math.BigDecimal
import java.time.Instant

data class PaymentEvent(
    val type: PaymentEventType,
    val status: String,
    val pgEventId: String?,
    val amount: BigDecimal,
    val currency: String,
    val occurredAt: Instant,
    val rawPayload: Map<String, Any?>?,
)
