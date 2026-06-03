package com.settle.libs.pgclient

enum class PgPaymentStatus {
    READY,
    APPROVED,
    CANCELED,
    PARTIAL_CANCELED,
    FAILED,
}
