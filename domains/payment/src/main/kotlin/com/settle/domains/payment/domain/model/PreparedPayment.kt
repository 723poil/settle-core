package com.settle.domains.payment.domain.model

import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PreparedPayment(
    val idempotencyKey: String,
    val merchantId: UUID,
    val pgProviderId: UUID,
    val pgMerchantAccountId: UUID,
    val merchantKey: String,
    val pgProvider: PgProvider,
    val pgProduct: PgPaymentProduct,
    val pgMid: String,
    val merchantOrderId: String,
    val pgTransactionId: String,
    val status: PaymentStatus,
    val amount: BigDecimal,
    val currency: String,
    val occurredAt: Instant,
    val event: PaymentEvent,
) {
    companion object {
        fun from(
            idempotencyKey: String,
            account: PgMerchantAccount,
            merchantOrderId: String,
            amount: BigDecimal,
            currency: String,
            response: PgPrepareResponse,
        ): PreparedPayment =
            PreparedPayment(
                idempotencyKey = idempotencyKey,
                merchantId = account.merchantId,
                pgProviderId = account.pgProviderId,
                pgMerchantAccountId = account.pgMerchantAccountId,
                merchantKey = account.merchantKey,
                pgProvider = account.pgProvider,
                pgProduct = account.pgProduct,
                pgMid = account.pgMid,
                merchantOrderId = merchantOrderId,
                pgTransactionId = response.pgTransactionId,
                status = PaymentStatus.from(response.status),
                amount = amount,
                currency = currency,
                occurredAt = response.requestedAt,
                event =
                    PaymentEvent(
                        type = PaymentEventType.PREPARE,
                        status = response.status.name,
                        pgEventId = response.pgTransactionId,
                        amount = amount,
                        currency = currency,
                        occurredAt = response.requestedAt,
                        rawPayload = response.rawPayload.ifEmpty { null },
                    ),
            )
    }
}
