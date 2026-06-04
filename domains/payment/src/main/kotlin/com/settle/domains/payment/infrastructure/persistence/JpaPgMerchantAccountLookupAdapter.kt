package com.settle.domains.payment.infrastructure.persistence

import com.settle.domains.merchant.persistence.MerchantStatus
import com.settle.domains.payment.application.port.PgMerchantAccountLookupPort
import com.settle.domains.payment.application.usecase.PreparePaymentCommand
import com.settle.domains.payment.domain.model.PgMerchantAccount
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentPgMerchantAccountJpaRepository
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgProvider

class JpaPgMerchantAccountLookupAdapter(
    private val accounts: PaymentPgMerchantAccountJpaRepository,
) : PgMerchantAccountLookupPort {
    override fun getActiveAccount(command: PreparePaymentCommand): PgMerchantAccount {
        val account =
            accounts
                .findByMerchantMerchantKeyAndMerchantStatusAndPgProviderCodeAndPgProviderActiveTrueAndPgProductAndPgMidAndActiveTrue(
                    merchantKey = command.merchantKey,
                    merchantStatus = MerchantStatus.ACTIVE,
                    pgProviderCode = command.pgProvider.code,
                    pgProduct = command.pgProduct.code,
                    pgMid = command.pgMid,
                )
                ?: throw PgMerchantAccountNotFoundException(command)

        return PgMerchantAccount(
            merchantId = account.merchant.id,
            pgProviderId = account.pgProvider.id,
            pgMerchantAccountId = account.id,
            merchantKey = account.merchant.merchantKey,
            pgProvider = PgProvider(account.pgProvider.code),
            pgProduct = PgPaymentProduct(account.pgProduct),
            pgMid = account.pgMid,
        )
    }
}
