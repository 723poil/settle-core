package com.settle.domains.payment.infrastructure.persistence.repository

import com.settle.domains.merchant.persistence.MerchantStatus
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentPgMerchantAccountJpaRepository : JpaRepository<PgMerchantAccountEntity, UUID> {
    fun findByMerchantMerchantKeyAndMerchantStatusAndPgProviderCodeAndPgProviderActiveTrueAndPgProductAndPgMidAndActiveTrue(
        merchantKey: String,
        merchantStatus: MerchantStatus,
        pgProviderCode: String,
        pgProduct: String,
        pgMid: String,
    ): PgMerchantAccountEntity?
}
