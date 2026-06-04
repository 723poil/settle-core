package com.settle.domains.payment.application.usecase

import com.settle.domains.payment.application.port.concurrency.PaymentConcurrencyLockPort
import com.settle.domains.payment.application.port.persistence.PaymentPersistenceRecordPort
import com.settle.domains.payment.application.port.persistence.PgMerchantAccountPersistenceLookupPort
import com.settle.domains.payment.application.provider.PgPaymentProvider
import com.settle.domains.payment.application.provider.PgPaymentProviderRegistry
import com.settle.domains.payment.domain.model.AuthorizedPayment
import com.settle.domains.payment.domain.model.PaymentEventType
import com.settle.domains.payment.domain.model.PaymentStatus
import com.settle.domains.payment.domain.model.PaymentTransactionSnapshot
import com.settle.domains.payment.domain.model.PgMerchantAccount
import com.settle.domains.payment.domain.model.PreparedPayment
import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentUseCasesTests {
    private val route = PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment"))

    @Test
    fun prepareUseCaseResolvesMerchantAccountCallsPgOutsideTransactionAndRecordsResult() {
        val transactionProbe = TransactionProbe()
        val accountLookup = StubPgMerchantAccountPersistenceLookupPort(account())
        val provider = RecordingPgPaymentProvider(route, transactionProbe)
        val records = RecordingPaymentPersistenceRecordPort(transactionProbe, existingPayment = null)
        val locks = RecordingPaymentConcurrencyLockPort()
        val useCase = PreparePaymentUseCase(accountLookup, records, locks, PgPaymentProviderRegistry(listOf(provider)))
        val command = prepareCommand()

        val response = useCase.prepare(command)

        assertEquals("payment:prepare:idempotency-001", locks.keys.single())
        assertEquals("mid-001", provider.prepareRequests.single().pgMid)
        assertEquals("order-001", provider.prepareRequests.single().merchantOrderId)
        assertEquals("테스트 주문", provider.prepareRequests.single().orderName)
        assertEquals(money(), provider.prepareRequests.single().amount)
        assertEquals(account().merchantId, records.preparedPayments.single().merchantId)
        assertEquals(account().pgProviderId, records.preparedPayments.single().pgProviderId)
        assertEquals(account().pgMerchantAccountId, records.preparedPayments.single().pgMerchantAccountId)
        assertEquals("idempotency-001", records.preparedPayments.single().idempotencyKey)
        assertEquals("order-001", records.preparedPayments.single().merchantOrderId)
        assertEquals("pg-tx-001", records.preparedPayments.single().pgTransactionId)
        assertEquals(PaymentStatus.REQUESTED, records.preparedPayments.single().status)
        assertEquals(
            PaymentEventType.PREPARE,
            records.preparedPayments
                .single()
                .event.type,
        )
        assertEquals(
            response.pgTransactionId,
            records.preparedPayments
                .single()
                .event.pgEventId,
        )
        assertTrue(records.recordedInsideTransaction)
        assertFalse(provider.calledInsideTransaction)
        assertEquals(PgPaymentStatus.READY, response.status)
    }

    @Test
    fun prepareUseCaseDoesNotRecordPaymentWhenPgPrepareFails() {
        val transactionProbe = TransactionProbe()
        val accountLookup = StubPgMerchantAccountPersistenceLookupPort(account())
        val provider = FailingPgPaymentProvider(route)
        val records = RecordingPaymentPersistenceRecordPort(transactionProbe, existingPayment = null)
        val useCase =
            PreparePaymentUseCase(
                accountLookup,
                records,
                RecordingPaymentConcurrencyLockPort(),
                PgPaymentProviderRegistry(listOf(provider)),
            )

        assertFailsWith<IllegalStateException> {
            useCase.prepare(prepareCommand())
        }

        assertEquals(emptyList(), records.preparedPayments)
    }

    @Test
    fun prepareUseCaseDoesNotCallPgWhenIdempotencyKeyAlreadyExistsInDatabase() {
        val provider = RecordingPgPaymentProvider(route)
        val useCase =
            PreparePaymentUseCase(
                StubPgMerchantAccountPersistenceLookupPort(account()),
                RecordingPaymentPersistenceRecordPort(TransactionProbe(), existingPayment = paymentSnapshot()),
                RecordingPaymentConcurrencyLockPort(),
                PgPaymentProviderRegistry(listOf(provider)),
            )

        assertFailsWith<DuplicatePaymentRequestException> {
            useCase.prepare(prepareCommand())
        }

        assertEquals(emptyList(), provider.prepareRequests)
    }

    @Test
    fun prepareUseCaseDoesNotCallPgWhenPaymentConcurrencyLockIsAlreadyHeld() {
        val provider = RecordingPgPaymentProvider(route)
        val useCase =
            PreparePaymentUseCase(
                StubPgMerchantAccountPersistenceLookupPort(account()),
                RecordingPaymentPersistenceRecordPort(TransactionProbe(), existingPayment = null),
                RejectingPaymentConcurrencyLockPort(),
                PgPaymentProviderRegistry(listOf(provider)),
            )

        assertFailsWith<DuplicatePaymentRequestException> {
            useCase.prepare(prepareCommand())
        }

        assertEquals(emptyList(), provider.prepareRequests)
    }

    @Test
    fun prepareCommandRejectsBlankRequiredFields() {
        val invalidCommands =
            listOf(
                { prepareCommand(idempotencyKey = " ") },
                { prepareCommand(merchantKey = " ") },
                { prepareCommand(pgMid = " ") },
                { prepareCommand(merchantOrderId = " ") },
                { prepareCommand(orderName = " ") },
            )

        invalidCommands.forEach { createInvalidCommand ->
            assertFailsWith<IllegalArgumentException> {
                createInvalidCommand()
            }
        }
    }

    @Test
    fun lookupUseCaseDelegatesToProviderSelectedByRoute() {
        val provider = RecordingPgPaymentProvider(route)
        val useCase = LookupPaymentUseCase(PgPaymentProviderRegistry(listOf(provider)))
        val request = lookupRequest()

        val response = useCase.lookup(LookupPaymentCommand(route, request))

        assertEquals(request, provider.lookupRequests.single())
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun authorizeUseCaseLoadsPaymentByIdempotencyKeyAndRecordsApprovedResult() {
        val provider = RecordingPgPaymentProvider(route)
        val records = RecordingPaymentPersistenceRecordPort(TransactionProbe(), existingPayment = paymentSnapshot())
        val locks = RecordingPaymentConcurrencyLockPort()
        val useCase = AuthorizePaymentUseCase(records, locks, PgPaymentProviderRegistry(listOf(provider)))

        val response = useCase.authorize(authorizeCommand())

        assertEquals("payment:authorize:idempotency-001", locks.keys.single())
        assertEquals(authorizeRequest(), provider.authorizeRequests.single())
        assertEquals("idempotency-001", records.authorizedPayments.single().idempotencyKey)
        assertEquals(PaymentStatus.APPROVED, records.authorizedPayments.single().status)
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun authorizeUseCaseCancelsApprovedPaymentWhenDatabaseRecordFails() {
        val provider = RecordingPgPaymentProvider(route)
        val records =
            RecordingPaymentPersistenceRecordPort(TransactionProbe(), existingPayment = paymentSnapshot()).apply {
                failAuthorizeRecord = true
            }
        val useCase = AuthorizePaymentUseCase(records, RecordingPaymentConcurrencyLockPort(), PgPaymentProviderRegistry(listOf(provider)))

        assertFailsWith<IllegalStateException> {
            useCase.authorize(authorizeCommand())
        }

        assertEquals(authorizeRequest(), provider.authorizeRequests.single())
        assertEquals(cancelCompensationRequest(), provider.cancelRequests.single())
    }

    @Test
    fun authorizeUseCaseDoesNotCallPgWhenPaymentCannotBeFoundByIdempotencyKey() {
        val provider = RecordingPgPaymentProvider(route)
        val records = RecordingPaymentPersistenceRecordPort(TransactionProbe(), existingPayment = null)
        val useCase = AuthorizePaymentUseCase(records, RecordingPaymentConcurrencyLockPort(), PgPaymentProviderRegistry(listOf(provider)))

        assertFailsWith<PaymentTransactionNotFoundException> {
            useCase.authorize(authorizeCommand())
        }

        assertEquals(emptyList(), provider.authorizeRequests)
    }

    @Test
    fun authorizeUseCaseDoesNotCompensateWhenFailedAuthorizationRecordFails() {
        val provider = RecordingPgPaymentProvider(route, authorizeStatus = PgPaymentStatus.FAILED)
        val records =
            RecordingPaymentPersistenceRecordPort(TransactionProbe(), existingPayment = paymentSnapshot()).apply {
                failAuthorizeRecord = true
            }
        val useCase = AuthorizePaymentUseCase(records, RecordingPaymentConcurrencyLockPort(), PgPaymentProviderRegistry(listOf(provider)))

        assertFailsWith<IllegalStateException> {
            useCase.authorize(authorizeCommand())
        }

        assertEquals(authorizeRequest(), provider.authorizeRequests.single())
        assertEquals(emptyList(), provider.cancelRequests)
    }

    @Test
    fun cancelUseCaseDelegatesToProviderSelectedByRouteWithConcurrencyLock() {
        val provider = RecordingPgPaymentProvider(route)
        val locks = RecordingPaymentConcurrencyLockPort()
        val useCase = CancelPaymentUseCase(locks, PgPaymentProviderRegistry(listOf(provider)))
        val request = cancelRequest()

        val response = useCase.cancel(CancelPaymentCommand(route, request))

        assertEquals("payment:cancel:idempotency-001", locks.keys.single())
        assertEquals(request, provider.cancelRequests.single())
        assertEquals(PgPaymentStatus.CANCELED, response.status)
    }

    private class StubPgMerchantAccountPersistenceLookupPort(
        private val account: PgMerchantAccount,
    ) : PgMerchantAccountPersistenceLookupPort {
        override fun getActiveAccount(command: PreparePaymentCommand): PgMerchantAccount = account
    }

    private class RecordingPaymentPersistenceRecordPort(
        private val transactionProbe: TransactionProbe,
        private val existingPayment: PaymentTransactionSnapshot?,
    ) : PaymentPersistenceRecordPort {
        val preparedPayments = mutableListOf<PreparedPayment>()
        val authorizedPayments = mutableListOf<AuthorizedPayment>()
        var recordedInsideTransaction = false
        var failAuthorizeRecord = false

        override fun findPaymentByIdempotencyKey(idempotencyKey: String): PaymentTransactionSnapshot? = existingPayment

        override fun recordPreparedPayment(payment: PreparedPayment) {
            transactionProbe.inTransaction = true
            recordedInsideTransaction = transactionProbe.inTransaction
            preparedPayments += payment
            transactionProbe.inTransaction = false
        }

        override fun recordAuthorizedPayment(payment: AuthorizedPayment) {
            if (failAuthorizeRecord) {
                throw IllegalStateException("DB record failed")
            }
            transactionProbe.inTransaction = true
            authorizedPayments += payment
            transactionProbe.inTransaction = false
        }
    }

    private class RecordingPaymentConcurrencyLockPort : PaymentConcurrencyLockPort {
        val keys = mutableListOf<String>()

        override fun <T> withLock(
            operation: PaymentLockOperation,
            idempotencyKey: String,
            block: () -> T,
        ): T {
            keys += operation.redisKey(idempotencyKey)
            return block()
        }
    }

    private class RejectingPaymentConcurrencyLockPort : PaymentConcurrencyLockPort {
        override fun <T> withLock(
            operation: PaymentLockOperation,
            idempotencyKey: String,
            block: () -> T,
        ): T = throw DuplicatePaymentRequestException(operation.redisKey(idempotencyKey))
    }

    private class TransactionProbe {
        var inTransaction = false
    }

    private class RecordingPgPaymentProvider(
        override val route: PgPaymentRoute,
        private val transactionProbe: TransactionProbe = TransactionProbe(),
        private val authorizeStatus: PgPaymentStatus = PgPaymentStatus.APPROVED,
    ) : PgPaymentProvider {
        val prepareRequests = mutableListOf<PgPrepareRequest>()
        val lookupRequests = mutableListOf<PgLookupRequest>()
        val authorizeRequests = mutableListOf<PgAuthorizeRequest>()
        val cancelRequests = mutableListOf<PgCancelRequest>()
        var calledInsideTransaction = false

        override fun prepare(request: PgPrepareRequest): PgPrepareResponse {
            calledInsideTransaction = transactionProbe.inTransaction
            prepareRequests += request
            return PgPrepareResponse(
                pgTransactionId = "pg-tx-001",
                status = PgPaymentStatus.READY,
                requestedAt = Instant.parse("2026-06-04T00:00:00Z"),
            )
        }

        override fun lookup(request: PgLookupRequest): PgLookupResponse {
            lookupRequests += request
            return PgLookupResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.APPROVED,
                amount = money(),
            )
        }

        override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse {
            authorizeRequests += request
            return PgAuthorizeResponse(
                pgTransactionId = request.pgTransactionId,
                status = authorizeStatus,
                amount = request.amount,
                approvedAt = Instant.parse("2026-06-04T00:00:01Z"),
            )
        }

        override fun cancel(request: PgCancelRequest): PgCancelResponse {
            cancelRequests += request
            return PgCancelResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.CANCELED,
                canceledAmount = request.cancelAmount,
                canceledAt = Instant.parse("2026-06-04T00:00:02Z"),
            )
        }
    }

    private class FailingPgPaymentProvider(
        override val route: PgPaymentRoute,
    ) : PgPaymentProvider {
        override fun prepare(request: PgPrepareRequest): PgPrepareResponse = throw IllegalStateException("PG prepare failed")

        override fun lookup(request: PgLookupRequest): PgLookupResponse = error("not used")

        override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse = error("not used")

        override fun cancel(request: PgCancelRequest): PgCancelResponse = error("not used")
    }

    private companion object {
        fun money(): PgMoney = PgMoney(BigDecimal("1000.00"), "KRW")

        fun account(): PgMerchantAccount =
            PgMerchantAccount(
                merchantId = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                pgProviderId = UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                pgMerchantAccountId = UUID.fromString("018f0000-0000-7000-8000-000000000003"),
                merchantKey = "merchant-key",
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = "mid-001",
            )

        fun paymentSnapshot(): PaymentTransactionSnapshot =
            PaymentTransactionSnapshot(
                idempotencyKey = "idempotency-001",
                merchantId = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                pgProviderId = UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                pgMerchantAccountId = UUID.fromString("018f0000-0000-7000-8000-000000000003"),
                merchantKey = "merchant-key",
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = "mid-001",
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                status = PaymentStatus.REQUESTED,
                amount = money().amount,
                currency = money().currency,
            )

        fun prepareCommand(
            idempotencyKey: String = "idempotency-001",
            merchantKey: String = "merchant-key",
            pgMid: String = "mid-001",
            merchantOrderId: String = "order-001",
            orderName: String = "테스트 주문",
        ): PreparePaymentCommand =
            PreparePaymentCommand(
                idempotencyKey = idempotencyKey,
                merchantKey = merchantKey,
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = pgMid,
                merchantOrderId = merchantOrderId,
                orderName = orderName,
                amount = money(),
            )

        fun lookupRequest(): PgLookupRequest =
            PgLookupRequest(
                pgMid = "mid-001",
                pgTransactionId = "pg-tx-001",
            )

        fun authorizeCommand(): AuthorizePaymentCommand =
            AuthorizePaymentCommand(
                idempotencyKey = "idempotency-001",
                authorizationToken = "auth-token-001",
            )

        fun authorizeRequest(): PgAuthorizeRequest =
            PgAuthorizeRequest(
                pgMid = "mid-001",
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                authorizationToken = "auth-token-001",
                amount = money(),
            )

        fun cancelRequest(): PgCancelRequest =
            PgCancelRequest(
                pgMid = "mid-001",
                pgTransactionId = "pg-tx-001",
                cancelAmount = money(),
                reason = "사용자 요청",
                idempotencyKey = "idempotency-001",
            )

        fun cancelCompensationRequest(): PgCancelRequest =
            PgCancelRequest(
                pgMid = "mid-001",
                pgTransactionId = "pg-tx-001",
                cancelAmount = money(),
                reason = "authorization persistence failed",
                idempotencyKey = "idempotency-001",
            )
    }
}
