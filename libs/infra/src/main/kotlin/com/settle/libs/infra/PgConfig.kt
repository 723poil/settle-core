package com.settle.libs.infra

import com.settle.libs.pgclient.PgPaymentClient
import com.settle.libs.pgclient.PgPaymentClientRegistry
import com.settle.libs.pgclient.http.PgHttpTransport
import com.settle.libs.pgclient.kiwoom.KiwoomPayAccount
import com.settle.libs.pgclient.kiwoom.KiwoomPayAccountType
import com.settle.libs.pgclient.kiwoom.KiwoomPayPaymentClient
import com.settle.libs.pgclient.paypal.PayPalPaymentClient
import com.settle.libs.pgclient.toss.TossPaymentsPaymentClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(PgClientProperties::class)
class PgConfig {
    @Bean
    fun pgHttpTransport(restClientBuilder: RestClient.Builder): PgHttpTransport = RestClientPgHttpTransport(restClientBuilder.build())

    @Bean
    fun pgPaymentClientRegistry(clients: List<PgPaymentClient>): PgPaymentClientRegistry = PgPaymentClientRegistry(clients)

    @Bean
    fun tossPaymentsPaymentClient(
        properties: PgClientProperties,
        pgHttpTransport: PgHttpTransport,
    ): PgPaymentClient =
        TossPaymentsPaymentClient(
            secretKey = properties.tossPayments.secretKey,
            baseUrl = properties.tossPayments.baseUrl,
            transport = pgHttpTransport,
        )

    @Bean
    fun payPalPaymentClient(
        properties: PgClientProperties,
        pgHttpTransport: PgHttpTransport,
    ): PgPaymentClient =
        PayPalPaymentClient(
            accessToken = properties.paypal.accessToken,
            baseUrl = properties.paypal.baseUrl,
            transport = pgHttpTransport,
        )

    @Bean
    fun kiwoomPayPaymentClient(
        properties: PgClientProperties,
        pgHttpTransport: PgHttpTransport,
    ): PgPaymentClient =
        KiwoomPayPaymentClient(
            accounts =
                properties.kiwoomPay.accounts
                    .filter { it.cpid.isNotBlank() }
                    .map {
                        KiwoomPayAccount(
                            cpid = it.cpid,
                            type = it.type.toPgAccountType(),
                            authorizationKey = it.authorizationKey,
                            readyUrl = it.readyUrl,
                        )
                    },
            transport = pgHttpTransport,
        )

    private fun PgClientProperties.KiwoomPay.Account.Type.toPgAccountType(): KiwoomPayAccountType =
        when (this) {
            PgClientProperties.KiwoomPay.Account.Type.PAYMENT -> KiwoomPayAccountType.PAYMENT
            PgClientProperties.KiwoomPay.Account.Type.SETTLEMENT -> KiwoomPayAccountType.SETTLEMENT
        }
}
