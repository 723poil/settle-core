package com.settle.domains.settlement.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionStatusEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionTypeEntity
import jakarta.persistence.Table
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class SettlementEntityMappingTests {
    @Test
    fun mapsSettlementSchemaTables() {
        assertTable(SettlementBatchEntity::class.java, schema = "settlement", name = "settlement_batches")
        assertTable(SettlementLineEntity::class.java, schema = "settlement", name = "settlement_lines")
    }

    @Test
    fun generatesUuidV7IdsInApplicationCode() {
        val merchant = MerchantEntity(merchantKey = "merchant-key", name = "테스트 상점")
        val pgProvider = PgProviderEntity(code = "test-pg", name = "테스트 PG")
        val pgMerchantAccount =
            PgMerchantAccountEntity(
                merchant = merchant,
                pgProvider = pgProvider,
                pgProduct = "payment",
                pgMid = "mid-001",
                displayName = "기본 MID",
            )
        val paymentTransaction =
            PaymentTransactionEntity(
                merchant = merchant,
                pgProvider = pgProvider,
                pgProduct = "payment",
                pgMerchantAccount = pgMerchantAccount,
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                transactionType = PaymentTransactionTypeEntity.PAYMENT,
                status = PaymentTransactionStatusEntity.APPROVED,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                occurredAt = Instant.parse("2026-06-03T00:00:00Z"),
            )
        val settlementBatch =
            SettlementBatchEntity(
                pgProvider = pgProvider,
                settlementDate = LocalDate.parse("2026-06-03"),
                status = SettlementBatchStatus.READY,
                grossAmount = BigDecimal("1000.00"),
                feeAmount = BigDecimal("30.00"),
                taxAmount = BigDecimal("3.00"),
                netAmount = BigDecimal("967.00"),
                currency = "KRW",
            )
        val settlementLine =
            SettlementLineEntity(
                settlementBatch = settlementBatch,
                paymentTransaction = paymentTransaction,
                merchant = merchant,
                grossAmount = BigDecimal("1000.00"),
                feeAmount = BigDecimal("30.00"),
                taxAmount = BigDecimal("3.00"),
                netAmount = BigDecimal("967.00"),
                currency = "KRW",
                status = SettlementLineStatus.READY,
            )

        assertEquals(7, settlementBatch.id.version())
        assertEquals(7, settlementLine.id.version())
    }

    private fun assertTable(
        type: Class<*>,
        schema: String,
        name: String,
    ) {
        val table = requireNotNull(type.getAnnotation(Table::class.java))

        assertEquals(schema, table.schema)
        assertEquals(name, table.name)
    }
}
