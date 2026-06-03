package com.settle.libs.pgclient.kiwoom

data class KiwoomPayAccount(
    val cpid: String,
    val type: KiwoomPayAccountType = KiwoomPayAccountType.PAYMENT,
    val authorizationKey: String,
    val readyUrl: String = "https://apitest.kiwoompay.co.kr/pay/ready",
) {
    init {
        require(cpid.isNotBlank()) { "Kiwoom Pay CPID must not be blank" }
        require(authorizationKey.isNotBlank()) { "Kiwoom Pay authorization key must not be blank" }
        require(readyUrl.isNotBlank()) { "Kiwoom Pay ready URL must not be blank" }
    }
}

enum class KiwoomPayAccountType {
    PAYMENT,
    SETTLEMENT,
}
