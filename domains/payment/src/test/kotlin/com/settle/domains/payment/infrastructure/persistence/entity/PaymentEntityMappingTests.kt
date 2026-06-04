package com.settle.domains.payment.infrastructure.persistence.entity

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import jakarta.persistence.Column
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
        val paymentTransaction = paymentTransaction()

        assertEquals(7, paymentTransaction.id.version())
    }

    @Test
    fun createsPaymentEventWithUuidV7IdAndPayload() {
        val paymentTransaction = paymentTransaction()
        val event =
            PaymentEventEntity(
                paymentTransaction = paymentTransaction,
                eventType = "AUTHORIZE",
                eventStatus = "APPROVED",
                pgEventId = "pg-event-001",
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                occurredAt = Instant.parse("2026-06-03T00:00:01Z"),
                rawPayload = mapOf("pgTransactionId" to "pg-tx-001"),
            )

        assertEquals(7, event.id.version())
        assertEquals(paymentTransaction, event.paymentTransaction)
        assertEquals("AUTHORIZE", event.eventType)
        assertEquals("APPROVED", event.eventStatus)
        assertEquals("pg-event-001", event.pgEventId)
        assertEquals(BigDecimal("1000.00"), event.amount)
        assertEquals("KRW", event.currency)
        assertEquals(Instant.parse("2026-06-03T00:00:01Z"), event.occurredAt)
        assertEquals(mapOf("pgTransactionId" to "pg-tx-001"), event.rawPayload)
        assertEquals("PaymentEventEntity(id=${event.id}, eventType=AUTHORIZE, eventStatus=APPROVED)", event.toString())
    }

    @Test
    fun mapsPaymentEventColumns() {
        val eventType = requireNotNull(PaymentEventEntity::class.java.getDeclaredField("eventType").getAnnotation(Column::class.java))
        val eventStatus = requireNotNull(PaymentEventEntity::class.java.getDeclaredField("eventStatus").getAnnotation(Column::class.java))
        val pgEventId = requireNotNull(PaymentEventEntity::class.java.getDeclaredField("pgEventId").getAnnotation(Column::class.java))

        assertEquals("event_type", eventType.name)
        assertEquals(false, eventType.nullable)
        assertEquals(40, eventType.length)
        assertEquals("event_status", eventStatus.name)
        assertEquals(false, eventStatus.nullable)
        assertEquals(40, eventStatus.length)
        assertEquals("pg_event_id", pgEventId.name)
        assertEquals(160, pgEventId.length)
    }

    @Test
    fun mapsPgProductOnPaymentTransaction() {
        val column = requireNotNull(PaymentTransactionEntity::class.java.getDeclaredField("pgProduct").getAnnotation(Column::class.java))

        assertEquals("pg_product", column.name)
        assertEquals(false, column.nullable)
        assertEquals(60, column.length)
    }

    @Test
    fun mapsIdempotencyKeyOnPaymentTransaction() {
        val column =
            requireNotNull(PaymentTransactionEntity::class.java.getDeclaredField("idempotencyKey").getAnnotation(Column::class.java))

        assertEquals("idempotency_key", column.name)
        assertEquals(false, column.nullable)
        assertEquals(true, column.unique)
        assertEquals(120, column.length)
    }

    private fun paymentTransaction(): PaymentTransactionEntity {
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
                idempotencyKey = "idempotency-001",
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

        return paymentTransaction
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
