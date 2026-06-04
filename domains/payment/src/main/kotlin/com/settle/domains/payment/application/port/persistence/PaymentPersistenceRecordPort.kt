package com.settle.domains.payment.application.port.persistence

import com.settle.domains.payment.domain.model.AuthorizedPayment
import com.settle.domains.payment.domain.model.PaymentTransactionSnapshot
import com.settle.domains.payment.domain.model.PreparedPayment

interface PaymentPersistenceRecordPort {
    fun findPaymentByIdempotencyKey(idempotencyKey: String): PaymentTransactionSnapshot?

    fun recordPreparedPayment(payment: PreparedPayment)

    fun recordAuthorizedPayment(payment: AuthorizedPayment)
}
