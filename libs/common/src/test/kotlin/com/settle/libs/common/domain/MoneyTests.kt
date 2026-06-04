package com.settle.libs.common.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTests {
    @Test
    fun createsKrwMoneyFromLongAmount() {
        assertEquals(Money(BigDecimal("1000"), "KRW"), Money.krw(1000))
    }

    @Test
    fun normalizesAmountToTwoDecimalPlaces() {
        assertEquals(Money(BigDecimal("1000.00"), "KRW"), Money(BigDecimal("1000"), "KRW").normalized())
    }

    @Test
    fun rejectsInvalidCurrencyCode() {
        assertFailsWith<IllegalArgumentException> {
            Money(BigDecimal("1000.00"), "KOREAN_WON")
        }
    }

    @Test
    fun rejectsAmountWithMoreThanTwoDecimalPlaces() {
        assertFailsWith<IllegalArgumentException> {
            Money(BigDecimal("1000.001"), "KRW")
        }
    }
}
