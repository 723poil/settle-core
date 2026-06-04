package com.settle.domains.payment.application.circuitbreaker

import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgPaymentCircuitBreakerKeyTests {
    private val route = PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment"))

    @Test
    fun createsCircuitNameFromProviderProductOperationAndMid() {
        val key = PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.AUTHORIZE, "mid-001")

        assertEquals("pg.tosspayments.payment.authorize.mid-001", key.circuitName)
    }

    @Test
    fun rejectsBlankPgMid() {
        listOf("", " ").forEach { pgMid ->
            assertFailsWith<IllegalArgumentException> {
                PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.AUTHORIZE, pgMid)
            }
        }
    }
}
