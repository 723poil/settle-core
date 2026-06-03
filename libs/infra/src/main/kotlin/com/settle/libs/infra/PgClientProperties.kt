package com.settle.libs.infra

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pg")
data class PgClientProperties(
    val tossPayments: TossPayments = TossPayments(),
    val paypal: PayPal = PayPal(),
    val kiwoomPay: KiwoomPay = KiwoomPay(),
) {
    data class TossPayments(
        val secretKey: String = "",
        val baseUrl: String = "https://api.tosspayments.com",
    )

    data class PayPal(
        val accessToken: String = "",
        val baseUrl: String = "https://api-m.paypal.com",
    )

    data class KiwoomPay(
        val accounts: List<Account> = emptyList(),
    ) {
        data class Account(
            val cpid: String = "",
            val type: Type = Type.PAYMENT,
            val authorizationKey: String = "",
            val readyUrl: String = "https://apitest.kiwoompay.co.kr/pay/ready",
        ) {
            enum class Type {
                PAYMENT,
                SETTLEMENT,
            }
        }
    }
}
