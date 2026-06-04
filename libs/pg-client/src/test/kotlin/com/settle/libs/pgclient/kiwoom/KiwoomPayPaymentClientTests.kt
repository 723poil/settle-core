package com.settle.libs.pgclient.kiwoom

import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentAccountNotFoundException
import com.settle.libs.pgclient.PgPaymentOperationNotSupportedException
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
import kotlin.test.assertFailsWith

class KiwoomPayPaymentClientTests {
    @Test
    fun exposesPaymentProductRoute() {
        val client =
            KiwoomPayPaymentClient(
                accounts =
                    listOf(
                        KiwoomPayAccount(cpid = "CPID001", type = KiwoomPayAccountType.PAYMENT, authorizationKey = "kiwoom-auth-key"),
                    ),
                transport = QueueTransport(emptyList()),
            )

        assertEquals(PgPaymentRoute(PgProvider("kiwoompay"), PgPaymentProduct("payment")), client.route)
    }

    @Test
    fun preparesPaymentThroughReadyAndReturnedPaymentUrl() {
        val transport =
            QueueTransport(
                listOf(
                    PgHttpResponse(
                        body =
                            mapOf(
                                "RETURNURL" to "https://apitest.kiwoompay.co.kr/pay/card",
                                "TOKEN" to "ready-token",
                            ),
                    ),
                    PgHttpResponse(
                        body =
                            mapOf(
                                "RESULTCODE" to "0000",
                                "DAOUTRX" to "DAOUTRX-001",
                                "TOKEN" to "ready-token",
                                "AMOUNT" to "1000",
                                "AUTHDATE" to "20260603000000",
                                "AUTHURL" to "https://kiwoompay.example/auth",
                            ),
                    ),
                ),
            )
        val client =
            KiwoomPayPaymentClient(
                accounts =
                    listOf(
                        KiwoomPayAccount(cpid = "CPID001", type = KiwoomPayAccountType.PAYMENT, authorizationKey = "kiwoom-auth-key"),
                    ),
                transport = transport,
            )

        val response =
            client.prepare(
                PgPrepareRequest(
                    pgMid = "CPID001",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    metadata =
                        mapOf(
                            "PAYMETHOD" to "CARD-SUGI",
                            "PRODUCTTYPE" to "1",
                            "BILLTYPE" to "1",
                            "IPADDRESS" to "127.0.0.1",
                            "USERID" to "user-001",
                        ),
                ),
            )

        assertEquals(PgHttpMethod.POST, transport.requests[0].method)
        assertEquals("https://apitest.kiwoompay.co.kr/pay/ready", transport.requests[0].url)
        assertEquals("kiwoom-auth-key", transport.requests[0].headers["Authorization"])
        assertEquals("CPID001", transport.requests[0].body["CPID"])
        assertEquals("CARD-SUGI", transport.requests[0].body["PAYMETHOD"])

        assertEquals(PgHttpMethod.POST, transport.requests[1].method)
        assertEquals("https://apitest.kiwoompay.co.kr/pay/card", transport.requests[1].url)
        assertEquals("ready-token", transport.requests[1].headers["TOKEN"])
        assertEquals("order-001", transport.requests[1].body["ORDERNO"])
        assertEquals("1000", transport.requests[1].body["AMOUNT"])
        assertEquals(PgPaymentStatus.READY, response.status)
        assertEquals("DAOUTRX-001", response.pgTransactionId)
        assertEquals("https://kiwoompay.example/auth", response.checkoutUrl)
    }

    @Test
    fun selectsKiwoomAccountByRequestedPgMid() {
        val transport =
            QueueTransport(
                listOf(
                    PgHttpResponse(
                        body =
                            mapOf(
                                "RETURNURL" to "https://apitest.kiwoompay.co.kr/pay/billing",
                                "TOKEN" to "billing-ready-token",
                            ),
                    ),
                    PgHttpResponse(
                        body =
                            mapOf(
                                "RESULTCODE" to "0000",
                                "DAOUTRX" to "DAOUTRX-002",
                                "AUTHDATE" to "20260603000000",
                            ),
                    ),
                ),
            )
        val client =
            KiwoomPayPaymentClient(
                accounts =
                    listOf(
                        KiwoomPayAccount(cpid = "CPID_PAYMENT", type = KiwoomPayAccountType.PAYMENT, authorizationKey = "payment-auth-key"),
                        KiwoomPayAccount(
                            cpid = "CPID_SETTLEMENT",
                            type = KiwoomPayAccountType.SETTLEMENT,
                            authorizationKey = "settlement-auth-key",
                        ),
                        KiwoomPayAccount(cpid = "CPID_BILLING", type = KiwoomPayAccountType.PAYMENT, authorizationKey = "billing-auth-key"),
                    ),
                transport = transport,
            )

        client.prepare(
            PgPrepareRequest(
                pgMid = "CPID_BILLING",
                merchantOrderId = "order-002",
                orderName = "빌링 테스트 주문",
                amount = PgMoney(BigDecimal("2000.00"), "KRW"),
                metadata =
                    mapOf(
                        "PAYMETHOD" to "CARD-SUGI",
                        "PRODUCTTYPE" to "1",
                        "BILLTYPE" to "1",
                        "IPADDRESS" to "127.0.0.1",
                        "USERID" to "user-002",
                    ),
            ),
        )

        assertEquals("billing-auth-key", transport.requests[0].headers["Authorization"])
        assertEquals("CPID_BILLING", transport.requests[0].body["CPID"])
        assertEquals("billing-auth-key", transport.requests[1].headers["Authorization"])
        assertEquals("CPID_BILLING", transport.requests[1].body["CPID"])
    }

    @Test
    fun rejectsSettlementTypeCpidForPaymentPrepare() {
        val client =
            KiwoomPayPaymentClient(
                accounts =
                    listOf(
                        KiwoomPayAccount(
                            cpid = "CPID_SETTLEMENT",
                            type = KiwoomPayAccountType.SETTLEMENT,
                            authorizationKey = "settlement-auth-key",
                        ),
                    ),
                transport = QueueTransport(emptyList()),
            )

        assertFailsWith<PgPaymentAccountNotFoundException> {
            client.prepare(
                PgPrepareRequest(
                    pgMid = "CPID_SETTLEMENT",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    metadata =
                        mapOf(
                            "PAYMETHOD" to "CARD-SUGI",
                            "PRODUCTTYPE" to "1",
                            "BILLTYPE" to "1",
                            "IPADDRESS" to "127.0.0.1",
                            "USERID" to "user-001",
                        ),
                ),
            )
        }
    }

    @Test
    fun rejectsUnknownKiwoomCpid() {
        val client =
            KiwoomPayPaymentClient(
                accounts =
                    listOf(
                        KiwoomPayAccount(cpid = "CPID001", type = KiwoomPayAccountType.PAYMENT, authorizationKey = "kiwoom-auth-key"),
                    ),
                transport = QueueTransport(emptyList()),
            )

        assertFailsWith<PgPaymentAccountNotFoundException> {
            client.prepare(
                PgPrepareRequest(
                    pgMid = "CPID_UNKNOWN",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    metadata =
                        mapOf(
                            "PAYMETHOD" to "CARD-SUGI",
                            "PRODUCTTYPE" to "1",
                            "BILLTYPE" to "1",
                            "IPADDRESS" to "127.0.0.1",
                            "USERID" to "user-001",
                        ),
                ),
            )
        }
    }

    @Test
    fun mapsFailedKiwoomReadyResultAndMissingAuthDateFallback() {
        val transport =
            QueueTransport(
                listOf(
                    PgHttpResponse(
                        body =
                            mapOf(
                                "RETURNURL" to "https://apitest.kiwoompay.co.kr/pay/card",
                                "TOKEN" to "ready-token",
                            ),
                    ),
                    PgHttpResponse(
                        body =
                            mapOf(
                                "RESULTCODE" to "9999",
                                "DAOUTRX" to "DAOUTRX-FAILED",
                            ),
                    ),
                ),
            )
        val client =
            KiwoomPayPaymentClient(
                accounts = listOf(KiwoomPayAccount(cpid = "CPID001", authorizationKey = "kiwoom-auth-key")),
                transport = transport,
            )

        val response =
            client.prepare(
                PgPrepareRequest(
                    pgMid = "CPID001",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    metadata =
                        mapOf(
                            "PAYMETHOD" to "CARD-SUGI",
                            "PRODUCTTYPE" to "1",
                            "BILLTYPE" to "1",
                            "IPADDRESS" to "127.0.0.1",
                            "USERID" to "user-001",
                        ),
                ),
            )

        assertEquals(PgPaymentStatus.FAILED, response.status)
        assertEquals("DAOUTRX-FAILED", response.pgTransactionId)
    }

    @Test
    fun rejectsPrepareWhenRequiredKiwoomMetadataIsMissing() {
        val client =
            KiwoomPayPaymentClient(
                accounts = listOf(KiwoomPayAccount(cpid = "CPID001", authorizationKey = "kiwoom-auth-key")),
                transport = QueueTransport(emptyList()),
            )

        assertFailsWith<IllegalArgumentException> {
            client.prepare(
                PgPrepareRequest(
                    pgMid = "CPID001",
                    merchantOrderId = "order-001",
                    orderName = "테스트 주문",
                    amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                ),
            )
        }
    }

    @Test
    fun explicitlyRejectsOperationsThatAreNotCoveredByPublicGuideYet() {
        val client =
            KiwoomPayPaymentClient(
                accounts =
                    listOf(
                        KiwoomPayAccount(cpid = "CPID001", type = KiwoomPayAccountType.PAYMENT, authorizationKey = "kiwoom-auth-key"),
                    ),
                transport = QueueTransport(emptyList()),
            )

        assertFailsWith<PgPaymentOperationNotSupportedException> {
            client.lookup(PgLookupRequest(pgMid = "CPID001", pgTransactionId = "DAOUTRX-001"))
        }
        assertFailsWith<PgPaymentOperationNotSupportedException> {
            client.cancel(
                PgCancelRequest(
                    pgMid = "CPID001",
                    pgTransactionId = "DAOUTRX-001",
                    cancelAmount = PgMoney(BigDecimal("1000.00"), "KRW"),
                    reason = "고객 요청",
                    idempotencyKey = "cancel-001",
                ),
            )
        }
    }

    private class QueueTransport(
        responses: List<PgHttpResponse>,
    ) : PgHttpTransport {
        private val responses = ArrayDeque(responses)
        val requests = mutableListOf<PgHttpRequest>()

        override fun execute(request: PgHttpRequest): PgHttpResponse {
            requests += request
            return responses.removeFirst()
        }
    }
}
