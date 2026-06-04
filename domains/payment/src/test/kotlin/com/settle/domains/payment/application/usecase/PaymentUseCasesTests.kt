package com.settle.domains.payment.application.usecase

import com.settle.domains.payment.application.port.PaymentRecordPort
import com.settle.domains.payment.application.port.PgMerchantAccountLookupPort
import com.settle.domains.payment.application.provider.PgPaymentProvider
import com.settle.domains.payment.application.provider.PgPaymentProviderRegistry
import com.settle.domains.payment.domain.model.PaymentEventType
import com.settle.domains.payment.domain.model.PaymentStatus
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
        val accountLookup = StubPgMerchantAccountLookupPort(account())
        val provider = RecordingPgPaymentProvider(route, transactionProbe)
        val records = RecordingPaymentRecordPort(transactionProbe)
        val useCase = PreparePaymentUseCase(accountLookup, records, PgPaymentProviderRegistry(listOf(provider)))
        val command = prepareCommand()

        val response = useCase.prepare(command)

        assertEquals("mid-001", provider.prepareRequests.single().pgMid)
        assertEquals("order-001", provider.prepareRequests.single().merchantOrderId)
        assertEquals("테스트 주문", provider.prepareRequests.single().orderName)
        assertEquals(money(), provider.prepareRequests.single().amount)
        assertEquals(account().merchantId, records.preparedPayments.single().merchantId)
        assertEquals(account().pgProviderId, records.preparedPayments.single().pgProviderId)
        assertEquals(account().pgMerchantAccountId, records.preparedPayments.single().pgMerchantAccountId)
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
        val accountLookup = StubPgMerchantAccountLookupPort(account())
        val provider = FailingPgPaymentProvider(route)
        val records = RecordingPaymentRecordPort(transactionProbe)
        val useCase = PreparePaymentUseCase(accountLookup, records, PgPaymentProviderRegistry(listOf(provider)))

        assertFailsWith<IllegalStateException> {
            useCase.prepare(prepareCommand())
        }

        assertEquals(emptyList(), records.preparedPayments)
    }

    @Test
    fun prepareCommandRejectsBlankRequiredFields() {
        val invalidCommands =
            listOf(
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
    fun authorizeUseCaseDelegatesToProviderSelectedByRoute() {
        val provider = RecordingPgPaymentProvider(route)
        val useCase = AuthorizePaymentUseCase(PgPaymentProviderRegistry(listOf(provider)))
        val request = authorizeRequest()

        val response = useCase.authorize(AuthorizePaymentCommand(route, request))

        assertEquals(request, provider.authorizeRequests.single())
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun cancelUseCaseDelegatesToProviderSelectedByRoute() {
        val provider = RecordingPgPaymentProvider(route)
        val useCase = CancelPaymentUseCase(PgPaymentProviderRegistry(listOf(provider)))
        val request = cancelRequest()

        val response = useCase.cancel(CancelPaymentCommand(route, request))

        assertEquals(request, provider.cancelRequests.single())
        assertEquals(PgPaymentStatus.CANCELED, response.status)
    }

    private class StubPgMerchantAccountLookupPort(
        private val account: PgMerchantAccount,
    ) : PgMerchantAccountLookupPort {
        override fun getActiveAccount(command: PreparePaymentCommand): PgMerchantAccount = account
    }

    private class RecordingPaymentRecordPort(
        private val transactionProbe: TransactionProbe,
    ) : PaymentRecordPort {
        val preparedPayments = mutableListOf<PreparedPayment>()
        var recordedInsideTransaction = false

        override fun recordPreparedPayment(payment: PreparedPayment) {
            transactionProbe.inTransaction = true
            recordedInsideTransaction = transactionProbe.inTransaction
            preparedPayments += payment
            transactionProbe.inTransaction = false
        }
    }

    private class TransactionProbe {
        var inTransaction = false
    }

    private class RecordingPgPaymentProvider(
        override val route: PgPaymentRoute,
        private val transactionProbe: TransactionProbe = TransactionProbe(),
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
                status = PgPaymentStatus.APPROVED,
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

        fun prepareCommand(
            merchantKey: String = "merchant-key",
            pgMid: String = "mid-001",
            merchantOrderId: String = "order-001",
            orderName: String = "테스트 주문",
        ): PreparePaymentCommand =
            PreparePaymentCommand(
                merchantKey = merchantKey,
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = pgMid,
                merchantOrderId = merchantOrderId,
                orderName = orderName,
                amount = money(),
            )

        fun prepareRequest(): PgPrepareRequest =
            PgPrepareRequest(
                pgMid = "mid-001",
                merchantOrderId = "order-001",
                orderName = "테스트 주문",
                amount = money(),
            )

        fun lookupRequest(): PgLookupRequest =
            PgLookupRequest(
                pgMid = "mid-001",
                pgTransactionId = "pg-tx-001",
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
                idempotencyKey = "cancel-001",
            )
    }
}
