package com.settle.libs.pgclient

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgPaymentClientRegistryTests {
    @Test
    fun findsClientByProvider() {
        val provider = PgProvider("toss")
        val client = StubPgPaymentClient(provider)
        val registry = PgPaymentClientRegistry(listOf(client))

        assertEquals(client, registry.get(provider))
    }

    @Test
    fun rejectsDuplicateClientsForSameProvider() {
        val provider = PgProvider("toss")

        assertFailsWith<DuplicatePgPaymentClientException> {
            PgPaymentClientRegistry(
                listOf(
                    StubPgPaymentClient(provider),
                    StubPgPaymentClient(provider),
                ),
            )
        }
    }

    @Test
    fun throwsWhenClientDoesNotExistForProvider() {
        val registry = PgPaymentClientRegistry(listOf(StubPgPaymentClient(PgProvider("toss"))))

        assertFailsWith<PgPaymentClientNotFoundException> {
            registry.get(PgProvider("kakao"))
        }
    }

    @Test
    fun delegatesPrepareLookupAuthorizeAndCancelContractsToClient() {
        val provider = PgProvider("toss")
        val client = StubPgPaymentClient(provider)

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
        override val provider: PgProvider,
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
