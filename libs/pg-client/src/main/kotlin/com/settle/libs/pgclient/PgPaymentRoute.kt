package com.settle.libs.pgclient

data class PgPaymentRoute(
    val provider: PgProvider,
    val product: PgPaymentProduct,
)
