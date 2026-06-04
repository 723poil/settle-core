package com.settle.domains.payment.application.port

import com.settle.domains.payment.application.usecase.PreparePaymentCommand
import com.settle.domains.payment.domain.model.PgMerchantAccount
import com.settle.domains.payment.domain.model.PreparedPayment

interface PgMerchantAccountLookupPort {
    fun getActiveAccount(command: PreparePaymentCommand): PgMerchantAccount
}

interface PaymentRecordPort {
    fun recordPreparedPayment(payment: PreparedPayment)
}
