package com.settle.domains.payment.domain.model

import com.settle.libs.pgclient.PgPaymentStatus

enum class PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
    CANCELED,
    ;

    companion object {
        fun from(status: PgPaymentStatus): PaymentStatus =
            when (status) {
                PgPaymentStatus.READY -> REQUESTED

                PgPaymentStatus.APPROVED -> APPROVED

                PgPaymentStatus.CANCELED,
                PgPaymentStatus.PARTIAL_CANCELED,
                -> CANCELED

                PgPaymentStatus.FAILED -> FAILED
            }
    }
}
