package com.settle.domains.payment.infrastructure.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.MerchantStatus
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import com.settle.domains.payment.application.usecase.PreparePaymentCommand
import com.settle.domains.payment.domain.model.PaymentEvent
import com.settle.domains.payment.domain.model.PaymentEventType
import com.settle.domains.payment.domain.model.PaymentStatus
import com.settle.domains.payment.domain.model.PreparedPayment
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentEventEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionStatusEntity
import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionTypeEntity
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentEventJpaRepository
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentPgMerchantAccountJpaRepository
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentTransactionJpaRepository
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgProvider
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PaymentPersistenceAdaptersTests {
    @Test
    fun lookupAdapterResolvesActiveMerchantPgAccount() {
        val merchant = merchantEntity()
        val provider = providerEntity()
        val account = accountEntity(merchant, provider)
        val repository = RecordingPgMerchantAccountRepository(account)
        val adapter = JpaPgMerchantAccountLookupAdapter(repository.proxy())

        val resolved = adapter.getActiveAccount(prepareCommand())

        assertEquals("merchant-key", repository.capturedMerchantKey)
        assertEquals(MerchantStatus.ACTIVE, repository.capturedMerchantStatus)
        assertEquals("tosspayments", repository.capturedProviderCode)
        assertEquals("payment", repository.capturedProduct)
        assertEquals("mid-001", repository.capturedPgMid)
        assertEquals(merchant.id, resolved.merchantId)
        assertEquals(provider.id, resolved.pgProviderId)
        assertEquals(account.id, resolved.pgMerchantAccountId)
        assertEquals(PgProvider("tosspayments"), resolved.pgProvider)
        assertEquals(PgPaymentProduct("payment"), resolved.pgProduct)
    }

    @Test
    fun lookupAdapterFailsWhenActiveMerchantPgAccountIsMissing() {
        val adapter = JpaPgMerchantAccountLookupAdapter(RecordingPgMerchantAccountRepository(null).proxy())

        assertFailsWith<PgMerchantAccountNotFoundException> {
            adapter.getActiveAccount(prepareCommand())
        }
    }

    @Test
    fun recordAdapterPersistsPreparedPaymentTransactionAndEventInTransactionPort() {
        val merchant = merchantEntity()
        val provider = providerEntity()
        val account = accountEntity(merchant, provider)
        val transactions = RecordingPaymentTransactionRepository()
        val events = RecordingPaymentEventRepository()
        val adapter =
            JpaPaymentRecordAdapter(
                entityManager = entityManager(mapOf(merchant.id to merchant, provider.id to provider, account.id to account)),
                paymentTransactions = transactions.proxy(),
                paymentEvents = events.proxy(),
            )

        adapter.recordPreparedPayment(preparedPayment(merchant, provider, account))

        val transaction = transactions.saved.single()
        val event = events.saved.single()
        assertEquals(merchant, transaction.merchant)
        assertEquals(provider, transaction.pgProvider)
        assertEquals(account, transaction.pgMerchantAccount)
        assertEquals("payment", transaction.pgProduct)
        assertEquals("order-001", transaction.merchantOrderId)
        assertEquals("pg-tx-001", transaction.pgTransactionId)
        assertEquals(PaymentTransactionTypeEntity.PAYMENT, transaction.transactionType)
        assertEquals(PaymentTransactionStatusEntity.REQUESTED, transaction.status)
        assertEquals(BigDecimal("1000.00"), transaction.amount)
        assertEquals("KRW", transaction.currency)
        assertNull(transaction.approvedAt)
        assertEquals(transaction, event.paymentTransaction)
        assertEquals("PREPARE", event.eventType)
        assertEquals("READY", event.eventStatus)
        assertEquals("pg-tx-001", event.pgEventId)
        assertEquals(mapOf("checkoutKey" to "checkout-001"), event.rawPayload)
    }

    @Test
    fun recordAdapterMapsPreparedPgStatusToPaymentTransactionStatus() {
        val cases =
            listOf(
                PaymentStatus.REQUESTED to PaymentTransactionStatusEntity.REQUESTED,
                PaymentStatus.APPROVED to PaymentTransactionStatusEntity.APPROVED,
                PaymentStatus.CANCELED to PaymentTransactionStatusEntity.CANCELED,
                PaymentStatus.FAILED to PaymentTransactionStatusEntity.FAILED,
            )

        cases.forEach { (status, expectedStatus) ->
            val transactions = RecordingPaymentTransactionRepository()
            val events = RecordingPaymentEventRepository()
            val merchant = merchantEntity()
            val provider = providerEntity()
            val account = accountEntity(merchant, provider)
            val adapter =
                JpaPaymentRecordAdapter(
                    entityManager = entityManager(mapOf(merchant.id to merchant, provider.id to provider, account.id to account)),
                    paymentTransactions = transactions.proxy(),
                    paymentEvents = events.proxy(),
                )

            adapter.recordPreparedPayment(preparedPayment(merchant, provider, account, status = status, rawPayload = null))

            assertEquals(expectedStatus, transactions.saved.single().status)
            assertNull(events.saved.single().rawPayload)
        }
    }

    @Test
    fun recordPreparedPaymentHasTransactionalBoundary() {
        val method =
            JpaPaymentRecordAdapter::class.java.getDeclaredMethod(
                "recordPreparedPayment",
                PreparedPayment::class.java,
            )

        assertNotNull(method.getAnnotation(Transactional::class.java))
    }

    private class RecordingPgMerchantAccountRepository(
        private val account: PgMerchantAccountEntity?,
    ) {
        var capturedMerchantKey: String? = null
        var capturedMerchantStatus: MerchantStatus? = null
        var capturedProviderCode: String? = null
        var capturedProduct: String? = null
        var capturedPgMid: String? = null

        fun proxy(): PaymentPgMerchantAccountJpaRepository =
            proxy { method, args ->
                if (method.name ==
                    "findByMerchantMerchantKeyAndMerchantStatusAndPgProviderCodeAndPgProviderActiveTrueAndPgProductAndPgMidAndActiveTrue"
                ) {
                    capturedMerchantKey = args[0] as String
                    capturedMerchantStatus = args[1] as MerchantStatus
                    capturedProviderCode = args[2] as String
                    capturedProduct = args[3] as String
                    capturedPgMid = args[4] as String
                    account
                } else {
                    defaultObjectMethod(method)
                }
            }
    }

    private class RecordingPaymentTransactionRepository {
        val saved = mutableListOf<PaymentTransactionEntity>()

        fun proxy(): PaymentTransactionJpaRepository =
            proxy { method, args ->
                if (method.name == "save") {
                    @Suppress("UNCHECKED_CAST")
                    val entity = args[0] as PaymentTransactionEntity
                    saved += entity
                    entity
                } else {
                    defaultObjectMethod(method)
                }
            }
    }

    private class RecordingPaymentEventRepository {
        val saved = mutableListOf<PaymentEventEntity>()

        fun proxy(): PaymentEventJpaRepository =
            proxy { method, args ->
                if (method.name == "save") {
                    @Suppress("UNCHECKED_CAST")
                    val entity = args[0] as PaymentEventEntity
                    saved += entity
                    entity
                } else {
                    defaultObjectMethod(method)
                }
            }
    }

    private companion object {
        val merchantId: UUID = UUID.fromString("018f0000-0000-7000-8000-000000000001")
        val providerId: UUID = UUID.fromString("018f0000-0000-7000-8000-000000000002")
        val accountId: UUID = UUID.fromString("018f0000-0000-7000-8000-000000000003")

        fun prepareCommand(): PreparePaymentCommand =
            PreparePaymentCommand(
                merchantKey = "merchant-key",
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = "mid-001",
                merchantOrderId = "order-001",
                orderName = "테스트 주문",
                amount = PgMoney(BigDecimal("1000.00"), "KRW"),
            )

        fun merchantEntity(): MerchantEntity =
            MerchantEntity(
                id = merchantId,
                merchantKey = "merchant-key",
                name = "테스트 가맹점",
            )

        fun providerEntity(): PgProviderEntity =
            PgProviderEntity(
                id = providerId,
                code = "tosspayments",
                name = "Toss Payments",
            )

        fun accountEntity(
            merchant: MerchantEntity,
            provider: PgProviderEntity,
        ): PgMerchantAccountEntity =
            PgMerchantAccountEntity(
                id = accountId,
                merchant = merchant,
                pgProvider = provider,
                pgProduct = "payment",
                pgMid = "mid-001",
                displayName = "토스 일반결제",
            )

        fun preparedPayment(
            merchant: MerchantEntity,
            provider: PgProviderEntity,
            account: PgMerchantAccountEntity,
            status: PaymentStatus = PaymentStatus.REQUESTED,
            eventStatus: String = "READY",
            rawPayload: Map<String, Any?>? = mapOf("checkoutKey" to "checkout-001"),
        ): PreparedPayment =
            PreparedPayment(
                merchantId = merchant.id,
                pgProviderId = provider.id,
                pgMerchantAccountId = account.id,
                merchantKey = merchant.merchantKey,
                pgProvider = PgProvider(provider.code),
                pgProduct = PgPaymentProduct(account.pgProduct),
                pgMid = account.pgMid,
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                status = status,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                occurredAt = Instant.parse("2026-06-04T00:00:00Z"),
                event =
                    PaymentEvent(
                        type = PaymentEventType.PREPARE,
                        status = eventStatus,
                        pgEventId = "pg-tx-001",
                        amount = BigDecimal("1000.00"),
                        currency = "KRW",
                        occurredAt = Instant.parse("2026-06-04T00:00:00Z"),
                        rawPayload = rawPayload,
                    ),
            )

        fun entityManager(references: Map<UUID, Any>): EntityManager =
            proxy { method, args ->
                if (method.name == "getReference") {
                    references[args[1] as UUID] ?: error("Reference not found: ${args[1]}")
                } else {
                    defaultObjectMethod(method)
                }
            }

        inline fun <reified T> proxy(crossinline handler: (Method, Array<Any?>) -> Any?): T =
            Proxy
                .newProxyInstance(
                    T::class.java.classLoader,
                    arrayOf(T::class.java),
                    InvocationHandler { _, method, args -> handler(method, args ?: emptyArray()) },
                ) as T

        fun defaultObjectMethod(method: Method): Any? =
            when (method.name) {
                "toString" -> "test-proxy"
                "hashCode" -> 0
                "equals" -> false
                else -> error("Unexpected method call: ${method.name}")
            }
    }
}
