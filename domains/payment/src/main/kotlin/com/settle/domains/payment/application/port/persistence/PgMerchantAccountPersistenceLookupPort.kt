package com.settle.domains.payment.application.port.persistence

import com.settle.domains.payment.application.usecase.PreparePaymentCommand
import com.settle.domains.payment.domain.model.PgMerchantAccount

interface PgMerchantAccountPersistenceLookupPort {
    fun getActiveAccount(command: PreparePaymentCommand): PgMerchantAccount
}
