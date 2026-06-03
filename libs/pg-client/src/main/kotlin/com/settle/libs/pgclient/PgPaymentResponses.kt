package com.settle.libs.pgclient

import java.time.Instant

data class PgPrepareResponse(
    val pgTransactionId: String,
    val status: PgPaymentStatus,
    val requestedAt: Instant,
    val checkoutUrl: String? = null,
    val rawPayload: Map<String, Any?> = emptyMap(),
) {
    init {
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
    }
}

data class PgLookupResponse(
    val pgTransactionId: String,
    val status: PgPaymentStatus,
    val amount: PgMoney? = null,
    val approvedAt: Instant? = null,
    val canceledAt: Instant? = null,
    val rawPayload: Map<String, Any?> = emptyMap(),
) {
    init {
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
    }
}

data class PgAuthorizeResponse(
    val pgTransactionId: String,
    val status: PgPaymentStatus,
    val amount: PgMoney,
    val approvedAt: Instant,
    val rawPayload: Map<String, Any?> = emptyMap(),
) {
    init {
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
    }
}

data class PgCancelResponse(
    val pgTransactionId: String,
    val status: PgPaymentStatus,
    val canceledAmount: PgMoney,
    val canceledAt: Instant,
    val rawPayload: Map<String, Any?> = emptyMap(),
) {
    init {
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
    }
}
