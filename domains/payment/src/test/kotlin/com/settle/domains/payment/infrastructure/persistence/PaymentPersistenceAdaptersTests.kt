package com.settle.domains.payment.infrastructure.persistence

import com.settle.domains.merchant.persistence.MerchantEntity
import com.settle.domains.merchant.persistence.MerchantStatus
import com.settle.domains.merchant.persistence.PgMerchantAccountEntity
import com.settle.domains.merchant.persistence.PgProviderEntity
import com.settle.domains.payment.application.usecase.PreparePaymentCommand
import com.settle.domains.payment.domain.model.AuthorizedPayment
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
        assertEquals("idempotency-001", transaction.idempotencyKey)
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
    fun recordAdapterFindsPaymentSnapshotByIdempotencyKey() {
        val merchant = merchantEntity()
        val provider = providerEntity()
        val account = accountEntity(merchant, provider)
        val transactions =
            RecordingPaymentTransactionRepository().apply {
                paymentByIdempotencyKey = paymentTransactionEntity(merchant, provider, account)
            }
        val adapter =
            JpaPaymentRecordAdapter(
                entityManager = entityManager(emptyMap()),
                paymentTransactions = transactions.proxy(),
                paymentEvents = RecordingPaymentEventRepository().proxy(),
            )

        val snapshot = adapter.findPaymentByIdempotencyKey("idempotency-001")

        assertNotNull(snapshot)
        assertEquals("idempotency-001", snapshot.idempotencyKey)
        assertEquals(merchant.id, snapshot.merchantId)
        assertEquals(provider.id, snapshot.pgProviderId)
        assertEquals(account.id, snapshot.pgMerchantAccountId)
        assertEquals("merchant-key", snapshot.merchantKey)
        assertEquals(PgProvider("tosspayments"), snapshot.pgProvider)
        assertEquals(PgPaymentProduct("payment"), snapshot.pgProduct)
        assertEquals("mid-001", snapshot.pgMid)
        assertEquals("order-001", snapshot.merchantOrderId)
        assertEquals("pg-tx-001", snapshot.pgTransactionId)
        assertEquals(PaymentStatus.REQUESTED, snapshot.status)
        assertEquals(BigDecimal("1000.00"), snapshot.amount)
        assertEquals("KRW", snapshot.currency)
    }

    @Test
    fun recordAdapterRecordsAuthorizedPaymentOnExistingTransaction() {
        val merchant = merchantEntity()
        val provider = providerEntity()
        val account = accountEntity(merchant, provider)
        val transaction = paymentTransactionEntity(merchant, provider, account)
        val transactions =
            RecordingPaymentTransactionRepository().apply {
                paymentByIdempotencyKey = transaction
            }
        val events = RecordingPaymentEventRepository()
        val adapter =
            JpaPaymentRecordAdapter(
                entityManager = entityManager(emptyMap()),
                paymentTransactions = transactions.proxy(),
                paymentEvents = events.proxy(),
            )

        adapter.recordAuthorizedPayment(authorizedPayment())

        assertEquals("pg-tx-authorized-001", transaction.pgTransactionId)
        assertEquals(PaymentTransactionStatusEntity.APPROVED, transaction.status)
        assertEquals(BigDecimal("1000.00"), transaction.amount)
        assertEquals("KRW", transaction.currency)
        assertEquals(Instant.parse("2026-06-04T00:00:01Z"), transaction.approvedAt)

        val event = events.saved.single()
        assertEquals(transaction, event.paymentTransaction)
        assertEquals("AUTHORIZE", event.eventType)
        assertEquals("APPROVED", event.eventStatus)
        assertEquals("pg-tx-authorized-001", event.pgEventId)
        assertEquals(BigDecimal("1000.00"), event.amount)
        assertEquals("KRW", event.currency)
        assertEquals(mapOf("authKey" to "auth-001"), event.rawPayload)
    }

    @Test
    fun recordAdapterFailsWhenAuthorizingMissingTransaction() {
        val adapter =
            JpaPaymentRecordAdapter(
                entityManager = entityManager(emptyMap()),
                paymentTransactions = RecordingPaymentTransactionRepository().proxy(),
                paymentEvents = RecordingPaymentEventRepository().proxy(),
            )

        assertFailsWith<PaymentTransactionEntityNotFoundException> {
            adapter.recordAuthorizedPayment(authorizedPayment())
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
        var paymentByIdempotencyKey: PaymentTransactionEntity? = null

        fun proxy(): PaymentTransactionJpaRepository =
            proxy { method, args ->
                if (method.name == "save") {
                    @Suppress("UNCHECKED_CAST")
                    val entity = args[0] as PaymentTransactionEntity
                    saved += entity
                    paymentByIdempotencyKey = entity
                    entity
                } else if (method.name == "findByIdempotencyKey") {
                    paymentByIdempotencyKey
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
                idempotencyKey = "idempotency-001",
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
                idempotencyKey = "idempotency-001",
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

        fun authorizedPayment(): AuthorizedPayment =
            AuthorizedPayment(
                idempotencyKey = "idempotency-001",
                pgTransactionId = "pg-tx-authorized-001",
                status = PaymentStatus.APPROVED,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                approvedAt = Instant.parse("2026-06-04T00:00:01Z"),
                event =
                    PaymentEvent(
                        type = PaymentEventType.AUTHORIZE,
                        status = "APPROVED",
                        pgEventId = "pg-tx-authorized-001",
                        amount = BigDecimal("1000.00"),
                        currency = "KRW",
                        occurredAt = Instant.parse("2026-06-04T00:00:01Z"),
                        rawPayload = mapOf("authKey" to "auth-001"),
                    ),
            )

        fun paymentTransactionEntity(
            merchant: MerchantEntity,
            provider: PgProviderEntity,
            account: PgMerchantAccountEntity,
        ): PaymentTransactionEntity =
            PaymentTransactionEntity(
                idempotencyKey = "idempotency-001",
                merchant = merchant,
                pgProvider = provider,
                pgProduct = account.pgProduct,
                pgMerchantAccount = account,
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                transactionType = PaymentTransactionTypeEntity.PAYMENT,
                status = PaymentTransactionStatusEntity.REQUESTED,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                occurredAt = Instant.parse("2026-06-04T00:00:00Z"),
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
