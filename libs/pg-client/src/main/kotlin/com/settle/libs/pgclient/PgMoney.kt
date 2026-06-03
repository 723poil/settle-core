package com.settle.libs.pgclient

import java.math.BigDecimal

data class PgMoney(
    val amount: BigDecimal,
    val currency: String,
) {
    init {
        require(amount.signum() >= 0) { "PG money amount must be zero or positive" }
        require(amount.scale() <= 2) { "PG money amount scale must be less than or equal to 2" }
        require(currency.length == 3) { "PG money currency must be an ISO-4217 code" }
    }
}
