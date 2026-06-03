package com.settle.libs.pgclient

data class PgPrepareRequest(
    val pgMid: String,
    val merchantOrderId: String,
    val orderName: String,
    val amount: PgMoney,
    val successUrl: String? = null,
    val failureUrl: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(pgMid.isNotBlank()) { "PG MID must not be blank" }
        require(merchantOrderId.isNotBlank()) { "merchant order id must not be blank" }
        require(orderName.isNotBlank()) { "order name must not be blank" }
    }
}

data class PgLookupRequest(
    val pgMid: String,
    val pgTransactionId: String,
) {
    init {
        require(pgMid.isNotBlank()) { "PG MID must not be blank" }
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
    }
}

data class PgAuthorizeRequest(
    val pgMid: String,
    val merchantOrderId: String,
    val pgTransactionId: String,
    val authorizationToken: String,
    val amount: PgMoney,
) {
    init {
        require(pgMid.isNotBlank()) { "PG MID must not be blank" }
        require(merchantOrderId.isNotBlank()) { "merchant order id must not be blank" }
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
        require(authorizationToken.isNotBlank()) { "authorization token must not be blank" }
    }
}

data class PgCancelRequest(
    val pgMid: String,
    val pgTransactionId: String,
    val cancelAmount: PgMoney,
    val reason: String,
    val idempotencyKey: String,
) {
    init {
        require(pgMid.isNotBlank()) { "PG MID must not be blank" }
        require(pgTransactionId.isNotBlank()) { "PG transaction id must not be blank" }
        require(reason.isNotBlank()) { "cancel reason must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotency key must not be blank" }
    }
}
