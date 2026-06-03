package com.settle.libs.pgclient.paypal

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
import kotlin.test.assertEquals

class PayPalPaymentClientTests {
    @Test
    fun exposesCheckoutProductRoute() {
        val client = PayPalPaymentClient(accessToken = "access-token", transport = RecordingTransport(PgHttpResponse(body = emptyMap())))

        assertEquals(PgPaymentRoute(PgProvider("paypal"), PgPaymentProduct("checkout")), client.route)
    }

    @Test
    fun createsOrderForPrepare() {
        val transport =
            RecordingTransport(
                PgHttpResponse(
                    body =
                        mapOf(
                            "id" to "ORDER-001",
                            "status" to "CREATED",
                            "create_time" to "2026-06-03T00:00:00Z",
                            "links" to listOf(mapOf("rel" to "approve", "href" to "https://paypal.example/approve")),
                        ),
                ),
            )
        val client = PayPalPaymentClient(accessToken = "access-token", transport = transport)

        val response =
            client.prepare(
                PgPrepareRequest(
                    pgMid = "unused-mid",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "USD"),
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.lastRequest.method)
        assertEquals("https://api-m.paypal.com/v2/checkout/orders", transport.lastRequest.url)
        assertEquals("Bearer access-token", transport.lastRequest.headers["Authorization"])
        assertEquals("CAPTURE", transport.lastRequest.body["intent"])
        assertEquals("order-001", transport.lastRequest.headers["PayPal-Request-Id"])
        assertEquals(PgPaymentStatus.READY, response.status)
        assertEquals("https://paypal.example/approve", response.checkoutUrl)
    }

    @Test
    fun capturesApprovedOrderForAuthorize() {
        val transport =
            RecordingTransport(
                PgHttpResponse(
                    body =
                        mapOf(
                            "id" to "ORDER-001",
                            "status" to "COMPLETED",
                            "purchase_units" to
                                listOf(
                                    mapOf(
                                        "payments" to
                                            mapOf(
                                                "captures" to
                                                    listOf(
                                                        mapOf(
                                                            "id" to "CAPTURE-001",
                                                            "status" to "COMPLETED",
                                                            "amount" to mapOf("value" to "1000.00", "currency_code" to "USD"),
                                                            "update_time" to "2026-06-03T00:00:01Z",
                                                        ),
                                                    ),
                                            ),
                                    ),
                                ),
                        ),
                ),
            )
        val client = PayPalPaymentClient(accessToken = "access-token", transport = transport)

        val response =
            client.authorize(
                PgAuthorizeRequest(
                    pgMid = "unused-mid",
                    merchantOrderId = "order-001",
                    pgTransactionId = "ORDER-001",
                    authorizationToken = "ORDER-001",
                    amount = PgMoney(BigDecimal("1000.00"), "USD"),
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.lastRequest.method)
        assertEquals("https://api-m.paypal.com/v2/checkout/orders/ORDER-001/capture", transport.lastRequest.url)
        assertEquals("CAPTURE-001", response.pgTransactionId)
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun looksUpCapturedPayment() {
        val transport =
            RecordingTransport(
                PgHttpResponse(
                    body =
                        mapOf(
                            "id" to "CAPTURE-001",
                            "status" to "COMPLETED",
                            "amount" to mapOf("value" to "1000.00", "currency_code" to "USD"),
                            "update_time" to "2026-06-03T00:00:01Z",
                        ),
                ),
            )
        val client = PayPalPaymentClient(accessToken = "access-token", transport = transport)

        val response = client.lookup(PgLookupRequest(pgMid = "unused-mid", pgTransactionId = "CAPTURE-001"))

        assertEquals(PgHttpMethod.GET, transport.lastRequest.method)
        assertEquals("https://api-m.paypal.com/v2/payments/captures/CAPTURE-001", transport.lastRequest.url)
        assertEquals(PgPaymentStatus.APPROVED, response.status)
    }

    @Test
    fun refundsCapturedPaymentForCancel() {
        val transport =
            RecordingTransport(
                PgHttpResponse(
                    body =
                        mapOf(
                            "id" to "REFUND-001",
                            "status" to "COMPLETED",
                            "amount" to mapOf("value" to "1000.00", "currency_code" to "USD"),
                            "update_time" to "2026-06-03T00:00:02Z",
                        ),
                ),
            )
        val client = PayPalPaymentClient(accessToken = "access-token", transport = transport)

        val response =
            client.cancel(
                PgCancelRequest(
                    pgMid = "unused-mid",
                    pgTransactionId = "CAPTURE-001",
                    cancelAmount = PgMoney(BigDecimal("1000.00"), "USD"),
                    reason = "고객 요청",
                    idempotencyKey = "refund-001",
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.lastRequest.method)
        assertEquals("https://api-m.paypal.com/v2/payments/captures/CAPTURE-001/refund", transport.lastRequest.url)
        assertEquals("refund-001", transport.lastRequest.headers["PayPal-Request-Id"])
        assertEquals(mapOf("value" to "1000.00", "currency_code" to "USD"), transport.lastRequest.body["amount"])
        assertEquals(PgPaymentStatus.CANCELED, response.status)
    }

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
