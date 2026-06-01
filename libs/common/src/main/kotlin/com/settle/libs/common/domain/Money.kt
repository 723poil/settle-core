package com.settle.libs.common.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class Money(
    val amount: BigDecimal = BigDecimal.ZERO,
    val currency: String = DEFAULT_CURRENCY,
) {
    init {
        require(currency.length == 3) { "currency must be an ISO-4217 code" }
        require(amount.scale() <= 2) { "amount scale must be less than or equal to 2" }
    }

    fun normalized(): Money = copy(amount = amount.setScale(2, RoundingMode.UNNECESSARY))

    companion object {
        const val DEFAULT_CURRENCY = "KRW"

        fun krw(amount: Long): Money = Money(BigDecimal.valueOf(amount), DEFAULT_CURRENCY)
    }
}
