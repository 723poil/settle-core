package com.settle.libs.pgclient.toss

import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgProvider
import com.settle.libs.pgclient.http.PgHttpMethod
import com.settle.libs.pgclient.http.PgHttpRequest
import com.settle.libs.pgclient.http.PgHttpResponse
import com.settle.libs.pgclient.http.PgHttpTransport
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Base64
import kotlin.test.assertEquals

class TossPaymentsPaymentClientTests {
    @Test
    fun exposesPaymentProductRoute() {
        val client = TossPaymentsPaymentClient(secretKey = "test_sk", transport = RecordingTransport(PgHttpResponse(body = emptyMap())))

        assertEquals(PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment")), client.route)
    }

    @Test
    fun preparesPaymentWindow() {
        val transport =
            RecordingTransport(
                PgHttpResponse(
                    body =
                        mapOf(
                            "paymentKey" to "payment-key",
                            "status" to "READY",
                            "requestedAt" to "2026-06-03T00:00:00+09:00",
                            "checkout" to mapOf("url" to "https://checkout.tosspayments.com/payment-key"),
                        ),
                ),
            )
        val client = TossPaymentsPaymentClient(secretKey = "test_sk", transport = transport)

        val response =
            client.prepare(
                PgPrepareRequest(
                    pgMid = "unused-mid",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    successUrl = "https://example.com/success",
                    failureUrl = "https://example.com/fail",
                    metadata = mapOf("method" to "CARD"),
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.lastRequest.method)
        assertEquals("https://api.tosspayments.com/v1/payments", transport.lastRequest.url)
        assertEquals(
            "Basic ${Base64.getEncoder().encodeToString("test_sk:".toByteArray())}",
            transport.lastRequest.headers["Authorization"],
        )
        assertEquals("CARD", transport.lastRequest.body["method"])
        assertEquals("order-001", transport.lastRequest.body["orderId"])
        assertEquals("테스트 주문", transport.lastRequest.body["orderName"])
        assertEquals(BigDecimal("1000"), transport.lastRequest.body["amount"])
        assertEquals(PgPaymentStatus.READY, response.status)
        assertEquals("https://checkout.tosspayments.com/payment-key", response.checkoutUrl)
    }

    @Test
    fun authorizesPayment() {
        val transport = RecordingTransport(tossPaymentResponse(status = "DONE", approvedAt = "2026-06-03T00:00:01+09:00"))
        val client = TossPaymentsPaymentClient(secretKey = "test_sk", transport = transport)

        val response =
            client.authorize(
                PgAuthorizeRequest(
                    pgMid = "unused-mid",
                    merchantOrderId = "order-001",
                    pgTransactionId = "payment-key",
                    authorizationToken = "payment-key",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.lastRequest.method)
        assertEquals("https://api.tosspayments.com/v1/payments/confirm", transport.lastRequest.url)
        assertEquals("payment-key", transport.lastRequest.body["paymentKey"])
        assertEquals("order-001", transport.lastRequest.body["orderId"])
        assertEquals(BigDecimal("1000"), transport.lastRequest.body["amount"])
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun looksUpPayment() {
        val transport = RecordingTransport(tossPaymentResponse(status = "DONE", approvedAt = "2026-06-03T00:00:01+09:00"))
        val client = TossPaymentsPaymentClient(secretKey = "test_sk", transport = transport)

        val response = client.lookup(PgLookupRequest(pgMid = "unused-mid", pgTransactionId = "payment-key"))

        assertEquals(PgHttpMethod.GET, transport.lastRequest.method)
        assertEquals("https://api.tosspayments.com/v1/payments/payment-key", transport.lastRequest.url)
        assertEquals(PgPaymentStatus.APPROVED, response.status)
        assertEquals(PgMoney(BigDecimal("1000"), "KRW"), response.amount)
    }

    @Test
    fun cancelsPaymentWithIdempotencyKey() {
        val transport =
            RecordingTransport(
                tossPaymentResponse(
                    status = "CANCELED",
                    cancels =
                        listOf(
                            mapOf(
                                "cancelAmount" to BigDecimal("1000"),
                                "canceledAt" to "2026-06-03T00:00:02+09:00",
                            ),
                        ),
                ),
            )
        val client = TossPaymentsPaymentClient(secretKey = "test_sk", transport = transport)

        val response =
            client.cancel(
                PgCancelRequest(
                    pgMid = "unused-mid",
                    pgTransactionId = "payment-key",
                    cancelAmount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    reason = "고객 요청",
                    idempotencyKey = "cancel-001",
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.lastRequest.method)
        assertEquals("https://api.tosspayments.com/v1/payments/payment-key/cancel", transport.lastRequest.url)
        assertEquals("cancel-001", transport.lastRequest.headers["Idempotency-Key"])
        assertEquals("고객 요청", transport.lastRequest.body["cancelReason"])
        assertEquals(BigDecimal("1000"), transport.lastRequest.body["cancelAmount"])
        assertEquals(PgPaymentStatus.CANCELED, response.status)
    }

    private fun tossPaymentResponse(
        status: String,
        approvedAt: String? = null,
        cancels: List<Map<String, Any?>> = emptyList(),
    ): PgHttpResponse =
        PgHttpResponse(
            body =
                mapOf(
                    "paymentKey" to "payment-key",
                    "status" to status,
                    "totalAmount" to BigDecimal("1000"),
                    "currency" to "KRW",
                    "approvedAt" to approvedAt,
                    "cancels" to cancels,
                ),
        )

    private class RecordingTransport(
        private val response: PgHttpResponse,
    ) : PgHttpTransport {
        lateinit var lastRequest: PgHttpRequest

        override fun execute(request: PgHttpRequest): PgHttpResponse {
            lastRequest = request
            return response
        }
    }
}
