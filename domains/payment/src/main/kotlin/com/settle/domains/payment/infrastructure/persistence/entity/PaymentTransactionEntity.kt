package com.settle.domains.payment.infrastructure.persistence.entity

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import com.settle.libs.common.id.UuidV7
import com.settle.libs.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    schema = "payment",
    name = "payment_transactions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_payment_transactions_pg",
            columnNames = ["pg_provider_id", "pg_product", "pg_transaction_id"],
        ),
    ],
)
open class PaymentTransactionEntity(
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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pg_merchant_account_id", nullable = false)
    open var pgMerchantAccount: PgMerchantAccountEntity,
    @Column(name = "merchant_order_id", nullable = false, length = 120)
    open var merchantOrderId: String,
    @Column(name = "pg_transaction_id", nullable = false, length = 160)
    open var pgTransactionId: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    open var transactionType: PaymentTransactionTypeEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    open var status: PaymentTransactionStatusEntity,
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    open var amount: BigDecimal,
    @Column(name = "currency", nullable = false, length = 3)
    open var currency: String,
    @Column(name = "approved_at")
    open var approvedAt: Instant? = null,
    @Column(name = "occurred_at", nullable = false)
    open var occurredAt: Instant,
) : BaseTimeEntity()
