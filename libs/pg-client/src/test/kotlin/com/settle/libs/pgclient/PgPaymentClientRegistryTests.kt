package com.settle.libs.pgclient

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgPaymentClientRegistryTests {
    @Test
    fun findsClientByRoute() {
        val route = PgPaymentRoute(PgProvider("toss"), PgPaymentProduct("payment"))
        val client = StubPgPaymentClient(route)
        val registry = PgPaymentClientRegistry(listOf(client))

        assertEquals(client, registry.get(route))
    }

    @Test
    fun allowsDifferentProductsForSameProvider() {
        val provider = PgProvider("toss")
        val paymentClient = StubPgPaymentClient(PgPaymentRoute(provider, PgPaymentProduct("payment")))
        val billingClient = StubPgPaymentClient(PgPaymentRoute(provider, PgPaymentProduct("billing")))
        val registry = PgPaymentClientRegistry(listOf(paymentClient, billingClient))

        assertEquals(paymentClient, registry.get(PgPaymentRoute(provider, PgPaymentProduct("payment"))))
        assertEquals(billingClient, registry.get(PgPaymentRoute(provider, PgPaymentProduct("billing"))))
    }

    @Test
    fun rejectsDuplicateClientsForSameRoute() {
        val route = PgPaymentRoute(PgProvider("toss"), PgPaymentProduct("payment"))

        assertFailsWith<DuplicatePgPaymentClientException> {
            PgPaymentClientRegistry(
                listOf(
                    StubPgPaymentClient(route),
                    StubPgPaymentClient(route),
                ),
            )
        }
    }

    @Test
    fun throwsWhenClientDoesNotExistForRoute() {
        val registry = PgPaymentClientRegistry(listOf(StubPgPaymentClient(PgPaymentRoute(PgProvider("toss"), PgPaymentProduct("payment")))))

        assertFailsWith<PgPaymentClientNotFoundException> {
            registry.get(PgPaymentRoute(PgProvider("toss"), PgPaymentProduct("brandpay")))
        }
    }

    @Test
    fun delegatesPrepareLookupAuthorizeAndCancelContractsToClient() {
        val route = PgPaymentRoute(PgProvider("toss"), PgPaymentProduct("payment"))
        val client = StubPgPaymentClient(route)

        val prepared =
            client.prepare(
                PgPrepareRequest(
                    pgMid = "mid-001",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                ),
            )
        val lookedUp = client.lookup(PgLookupRequest(pgMid = "mid-001", pgTransactionId = prepared.pgTransactionId))
        val authorized =
            client.authorize(
                PgAuthorizeRequest(
                    pgMid = "mid-001",
                    merchantOrderId = "order-001",
                    pgTransactionId = prepared.pgTransactionId,
                    authorizationToken = "auth-token",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                ),
            )
        val canceled =
            client.cancel(
                PgCancelRequest(
                    pgMid = "mid-001",
                    pgTransactionId = prepared.pgTransactionId,
                    cancelAmount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    reason = "고객 요청",
                    idempotencyKey = "cancel-001",
                ),
            )

        assertEquals(PgPaymentStatus.READY, prepared.status)
        assertEquals(PgPaymentStatus.READY, lookedUp.status)
        assertEquals(PgPaymentStatus.APPROVED, authorized.status)
        assertEquals(PgPaymentStatus.CANCELED, canceled.status)
    }

    private class StubPgPaymentClient(
        override val route: PgPaymentRoute,
    ) : PgPaymentClient {
        override fun prepare(request: PgPrepareRequest): PgPrepareResponse =
            PgPrepareResponse(
                pgTransactionId = "pg-tx-001",
                status = PgPaymentStatus.READY,
                requestedAt = Instant.parse("2026-06-03T00:00:00Z"),
            )

        override fun lookup(request: PgLookupRequest): PgLookupResponse =
            PgLookupResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.READY,
                amount = PgMoney(BigDecimal("1000.00"), "KRW"),
            )

        override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse =
            PgAuthorizeResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.APPROVED,
                amount = request.amount,
                approvedAt = Instant.parse("2026-06-03T00:00:01Z"),
            )

        override fun cancel(request: PgCancelRequest): PgCancelResponse =
            PgCancelResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.CANCELED,
                canceledAmount = request.cancelAmount,
                canceledAt = Instant.parse("2026-06-03T00:00:02Z"),
            )
    }
}
