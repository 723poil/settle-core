package com.settle.domains.merchant.persistence

import com.settle.libs.common.id.UuidV7
import com.settle.libs.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    schema = "merchant",
    name = "pg_merchant_accounts",
    uniqueConstraints = [UniqueConstraint(name = "uq_pg_merchant_accounts_mid", columnNames = ["pg_provider_id", "pg_product", "pg_mid"])],
)
open class PgMerchantAccountEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UuidV7.generate(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    open var merchant: MerchantEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pg_provider_id", nullable = false)
    open var pgProvider: PgProviderEntity,
    @Column(name = "pg_product", nullable = false, length = 60)
    open var pgProduct: String,
    @Column(name = "pg_mid", nullable = false, length = 120)
    open var pgMid: String,
    @Column(name = "display_name", nullable = false, length = 120)
    open var displayName: String,
    @Column(name = "active", nullable = false)
    open var active: Boolean = true,
) : BaseTimeEntity()
