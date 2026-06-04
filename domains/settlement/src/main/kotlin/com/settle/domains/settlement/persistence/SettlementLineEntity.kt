package com.settle.domains.settlement.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionEntity
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
import java.util.UUID

@Entity
@Table(
    schema = "settlement",
    name = "settlement_lines",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_settlement_lines_transaction",
            columnNames = ["settlement_batch_id", "payment_transaction_id"],
        ),
    ],
)
open class SettlementLineEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UuidV7.generate(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_batch_id", nullable = false)
    open var settlementBatch: SettlementBatchEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_transaction_id", nullable = false)
    open var paymentTransaction: PaymentTransactionEntity,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    open var merchant: MerchantEntity,
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2)
    open var grossAmount: BigDecimal,
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    open var feeAmount: BigDecimal,
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    open var taxAmount: BigDecimal,
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    open var netAmount: BigDecimal,
    @Column(name = "currency", nullable = false, length = 3)
    open var currency: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    open var status: SettlementLineStatus,
) : BaseTimeEntity()
