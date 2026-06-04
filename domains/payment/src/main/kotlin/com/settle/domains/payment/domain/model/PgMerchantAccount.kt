package com.settle.domains.payment.domain.model

import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgProvider
import java.util.UUID

data class PgMerchantAccount(
    val merchantId: UUID,
    val pgProviderId: UUID,
    val pgMerchantAccountId: UUID,
    val merchantKey: String,
    val pgProvider: PgProvider,
    val pgProduct: PgPaymentProduct,
    val pgMid: String,
)
