package com.settle.domains.payment.application.circuitbreaker

import com.settle.libs.pgclient.PgPaymentRoute

enum class PgPaymentCircuitBreakerOperation {
    PREPARE,
    LOOKUP,
    AUTHORIZE,
    CANCEL,
}

data class PgPaymentCircuitBreakerKey(
    val route: PgPaymentRoute,
    val operation: PgPaymentCircuitBreakerOperation,
    val pgMid: String,
) {
    val circuitName: String = "pg.${route.provider.code}.${route.product.code}.${operation.name.lowercase()}.$pgMid"

    init {
        require(pgMid.isNotBlank()) { "PG MID must not be blank" }
    }
}
