package com.settle.libs.infra

import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgProvider
import com.settle.libs.pgclient.http.PgHttpResponse
import com.settle.libs.pgclient.http.PgHttpTransport
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PgConfigTests {
    @Test
    fun createsPgClientsFromConfiguredProperties() {
        val config = PgConfig()
        val transport = PgHttpTransport { PgHttpResponse(body = emptyMap()) }
        val properties =
            PgClientProperties(
                tossPayments =
                    PgClientProperties.TossPayments(
                        secretKey = "toss-secret",
                        baseUrl = "https://toss.example",
                    ),
                paypal =
                    PgClientProperties.PayPal(
                        accessToken = "paypal-token",
                        baseUrl = "https://paypal.example",
                    ),
                kiwoomPay =
                    PgClientProperties.KiwoomPay(
                        accounts =
                            listOf(
                                PgClientProperties.KiwoomPay.Account(
                                    cpid = "CPID001",
                                    type = PgClientProperties.KiwoomPay.Account.Type.PAYMENT,
                                    authorizationKey = "kiwoom-auth",
                                    readyUrl = "https://kiwoom.example/pay/ready",
                                ),
                                PgClientProperties.KiwoomPay.Account(
                                    cpid = "CPID_SETTLEMENT",
                                    type = PgClientProperties.KiwoomPay.Account.Type.SETTLEMENT,
                                    authorizationKey = "kiwoom-settlement-auth",
                                    readyUrl = "https://kiwoom.example/settlement/ready",
                                ),
                            ),
                    ),
            )

        val tossClient = config.tossPaymentsPaymentClient(properties, transport)
        val payPalClient = config.payPalPaymentClient(properties, transport)
        val kiwoomClient = config.kiwoomPayPaymentClient(properties, transport)
        val registry = config.pgPaymentClientRegistry(listOf(tossClient, payPalClient, kiwoomClient))

        assertEquals(tossClient, registry.get(PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment"))))
        assertEquals(payPalClient, registry.get(PgPaymentRoute(PgProvider("paypal"), PgPaymentProduct("checkout"))))
        assertEquals(kiwoomClient, registry.get(PgPaymentRoute(PgProvider("kiwoompay"), PgPaymentProduct("payment"))))
    }

    @Test
    fun createsRestClientBackedPgHttpTransport() {
        val transport = PgConfig().pgHttpTransport(RestClient.builder())

        assertIs<PgHttpTransport>(transport)
    }
}
