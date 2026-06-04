package com.settle.domains.payment.infrastructure.pg

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey
import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerOperation
import com.settle.domains.payment.application.port.circuitbreaker.PgPaymentCircuitBreakerPort
import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentClient
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgPaymentProviderAdapterTests {
    private val route = PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment"))

    @Test
    fun prepareRunsThroughCircuitBreakerAndCallsPgClient() {
        val circuitBreaker = RecordingCircuitBreaker()
        val client = RecordingPgPaymentClient(route)
        val provider = PgClientPaymentProviderAdapter(client, circuitBreaker)
        val request = prepareRequest()

        val response = provider.prepare(request)

        assertEquals(PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.PREPARE, "mid-001"), circuitBreaker.keys.single())
        assertEquals("pg.tosspayments.payment.prepare.mid-001", circuitBreaker.keys.single().circuitName)
        assertEquals(request, client.prepareRequests.single())
        assertEquals(PgPaymentStatus.READY, response.status)
    }

    @Test
    fun lookupRunsThroughCircuitBreakerAndCallsPgClient() {
        val circuitBreaker = RecordingCircuitBreaker()
        val client = RecordingPgPaymentClient(route)
        val provider = PgClientPaymentProviderAdapter(client, circuitBreaker)
        val request = lookupRequest()

        val response = provider.lookup(request)

        assertEquals(PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.LOOKUP, "mid-001"), circuitBreaker.keys.single())
        assertEquals(request, client.lookupRequests.single())
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun authorizeRunsThroughCircuitBreakerAndCallsPgClient() {
        val circuitBreaker = RecordingCircuitBreaker()
        val client = RecordingPgPaymentClient(route)
        val provider = PgClientPaymentProviderAdapter(client, circuitBreaker)
        val request = authorizeRequest()

        val response = provider.authorize(request)

        assertEquals(PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.AUTHORIZE, "mid-001"), circuitBreaker.keys.single())
        assertEquals(request, client.authorizeRequests.single())
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun cancelRunsThroughCircuitBreakerAndCallsPgClient() {
        val circuitBreaker = RecordingCircuitBreaker()
        val client = RecordingPgPaymentClient(route)
        val provider = PgClientPaymentProviderAdapter(client, circuitBreaker)
        val request = cancelRequest()

        val response = provider.cancel(request)

        assertEquals(PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.CANCEL, "mid-001"), circuitBreaker.keys.single())
        assertEquals(request, client.cancelRequests.single())
        assertEquals(PgPaymentStatus.CANCELED, response.status)
    }

    @Test
    fun doesNotCallPgClientWhenCircuitBreakerRejectsCall() {
        val client = RecordingPgPaymentClient(route)
        val provider = PgClientPaymentProviderAdapter(client, RejectingCircuitBreaker())

        assertFailsWith<IllegalStateException> {
            provider.prepare(prepareRequest())
        }

        assertEquals(0, client.prepareRequests.size)
    }

    private class RecordingCircuitBreaker : PgPaymentCircuitBreakerPort {
        val keys = mutableListOf<PgPaymentCircuitBreakerKey>()

        override fun <T> execute(
            key: PgPaymentCircuitBreakerKey,
            block: () -> T,
        ): T {
            keys += key
            return block()
        }
    }

    private class RejectingCircuitBreaker : PgPaymentCircuitBreakerPort {
        override fun <T> execute(
            key: PgPaymentCircuitBreakerKey,
            block: () -> T,
        ): T = throw IllegalStateException("circuit is open: ${key.circuitName}")
    }

    private class RecordingPgPaymentClient(
        override val route: PgPaymentRoute,
    ) : PgPaymentClient {
        val prepareRequests = mutableListOf<PgPrepareRequest>()
        val lookupRequests = mutableListOf<PgLookupRequest>()
        val authorizeRequests = mutableListOf<PgAuthorizeRequest>()
        val cancelRequests = mutableListOf<PgCancelRequest>()

        override fun prepare(request: PgPrepareRequest): PgPrepareResponse {
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

    private companion object {
        fun money(): PgMoney = PgMoney(BigDecimal("1000.00"), "KRW")

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
