package com.settle.domains.payment.domain.model

import com.settle.libs.pgclient.PgAuthorizeResponse
import java.math.BigDecimal
import java.time.Instant

data class AuthorizedPayment(
    val idempotencyKey: String,
    val pgTransactionId: String,
    val status: PaymentStatus,
    val amount: BigDecimal,
    val currency: String,
    val approvedAt: Instant,
    val event: PaymentEvent,
) {
    companion object {
        fun from(
            payment: PaymentTransactionSnapshot,
            response: PgAuthorizeResponse,
        ): AuthorizedPayment =
            AuthorizedPayment(
                idempotencyKey = payment.idempotencyKey,
                pgTransactionId = response.pgTransactionId,
                status = PaymentStatus.from(response.status),
                amount = response.amount.amount,
                currency = response.amount.currency,
                approvedAt = response.approvedAt,
                event =
                    PaymentEvent(
                        type = PaymentEventType.AUTHORIZE,
                        status = response.status.name,
                        pgEventId = response.pgTransactionId,
                        amount = response.amount.amount,
                        currency = response.amount.currency,
                        occurredAt = response.approvedAt,
                        rawPayload = response.rawPayload.ifEmpty { null },
                    ),
            )
    }
}
