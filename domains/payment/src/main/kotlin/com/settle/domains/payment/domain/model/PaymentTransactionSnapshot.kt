package com.settle.domains.payment.domain.model

import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgProvider
import java.math.BigDecimal
import java.util.UUID

data class PaymentTransactionSnapshot(
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
)
