package com.settle.domains.payment.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import jakarta.persistence.Table
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

class PaymentEntityMappingTests {
    @Test
    fun mapsPaymentSchemaTables() {
        assertTable(PaymentTransactionEntity::class.java, schema = "payment", name = "payment_transactions")
        assertTable(PaymentEventEntity::class.java, schema = "payment", name = "payment_events")
    }

    @Test
    fun generatesUuidV7IdsInApplicationCode() {
        val merchant = MerchantEntity(merchantKey = "merchant-key", name = "테스트 상점")
        val pgProvider = PgProviderEntity(code = "test-pg", name = "테스트 PG")
        val pgMerchantAccount =
            PgMerchantAccountEntity(
                merchant = merchant,
                pgProvider = pgProvider,
                pgMid = "mid-001",
                displayName = "기본 MID",
            )
        val paymentTransaction =
            PaymentTransactionEntity(
                merchant = merchant,
                pgProvider = pgProvider,
                pgMerchantAccount = pgMerchantAccount,
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                transactionType = PaymentTransactionType.PAYMENT,
                status = PaymentTransactionStatus.APPROVED,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                occurredAt = Instant.parse("2026-06-03T00:00:00Z"),
            )

        assertEquals(7, paymentTransaction.id.version())
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
