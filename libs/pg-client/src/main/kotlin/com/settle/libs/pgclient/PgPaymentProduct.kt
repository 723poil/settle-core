package com.settle.libs.pgclient

@JvmInline
value class PgPaymentProduct(
    val code: String,
) {
    init {
        require(code.isNotBlank()) { "PG payment product code must not be blank" }
    }
}
