package com.settle.libs.pgclient

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PgPaymentContractsTests {
    @Test
    fun rejectsInvalidPgMoney() {
        assertFailsWith<IllegalArgumentException> {
            PgMoney(BigDecimal("-1.00"), "KRW")
        }
        assertFailsWith<IllegalArgumentException> {
            PgMoney(BigDecimal("1000.001"), "KRW")
        }
        assertFailsWith<IllegalArgumentException> {
            PgMoney(BigDecimal("1000.00"), "KOREAN_WON")
        }
    }

    @Test
    fun rejectsInvalidPrepareRequest() {
        assertFailsWith<IllegalArgumentException> {
            prepareRequest(pgMid = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            prepareRequest(merchantOrderId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            prepareRequest(orderName = " ")
        }
    }

    @Test
    fun rejectsInvalidLookupRequest() {
        assertFailsWith<IllegalArgumentException> {
            PgLookupRequest(pgMid = " ", pgTransactionId = "pg-tx-001")
        }
        assertFailsWith<IllegalArgumentException> {
            PgLookupRequest(pgMid = "mid-001", pgTransactionId = " ")
        }
    }

    @Test
    fun rejectsInvalidAuthorizeRequest() {
        assertFailsWith<IllegalArgumentException> {
            authorizeRequest(pgMid = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            authorizeRequest(merchantOrderId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            authorizeRequest(pgTransactionId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            authorizeRequest(authorizationToken = " ")
        }
    }

    @Test
    fun rejectsInvalidCancelRequest() {
        assertFailsWith<IllegalArgumentException> {
            cancelRequest(pgMid = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            cancelRequest(pgTransactionId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            cancelRequest(reason = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            cancelRequest(idempotencyKey = " ")
        }
    }

    @Test
    fun rejectsBlankTransactionIdInResponses() {
        assertFailsWith<IllegalArgumentException> {
            PgPrepareResponse(pgTransactionId = " ", status = PgPaymentStatus.READY, requestedAt = Instant.EPOCH)
        }
        assertFailsWith<IllegalArgumentException> {
            PgLookupResponse(pgTransactionId = " ", status = PgPaymentStatus.APPROVED)
        }
        assertFailsWith<IllegalArgumentException> {
            PgAuthorizeResponse(pgTransactionId = " ", status = PgPaymentStatus.APPROVED, amount = money(), approvedAt = Instant.EPOCH)
        }
        assertFailsWith<IllegalArgumentException> {
            PgCancelResponse(pgTransactionId = " ", status = PgPaymentStatus.CANCELED, canceledAmount = money(), canceledAt = Instant.EPOCH)
        }
    }

    @Test
    fun createsProviderProductAndAccountIdentifiers() {
        assertEquals("tosspayments", PgProvider("tosspayments").code)
        assertEquals("payment", PgPaymentProduct("payment").code)
    }

    @Test
    fun rejectsBlankProviderProductAndAccountIdentifiers() {
        assertFailsWith<IllegalArgumentException> {
            PgProvider(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            PgPaymentProduct(" ")
        }
    }

    private fun money(): PgMoney = PgMoney(BigDecimal("1000.00"), "KRW")

    private fun prepareRequest(
        pgMid: String = "mid-001",
        merchantOrderId: String = "order-001",
        orderName: String = "테스트 주문",
    ): PgPrepareRequest =
        PgPrepareRequest(
            pgMid = pgMid,
            merchantOrderId = merchantOrderId,
            orderName = orderName,
            amount = money(),
        )

    private fun authorizeRequest(
        pgMid: String = "mid-001",
        merchantOrderId: String = "order-001",
        pgTransactionId: String = "pg-tx-001",
        authorizationToken: String = "auth-token-001",
    ): PgAuthorizeRequest =
        PgAuthorizeRequest(
            pgMid = pgMid,
            merchantOrderId = merchantOrderId,
            pgTransactionId = pgTransactionId,
            authorizationToken = authorizationToken,
            amount = money(),
        )

    private fun cancelRequest(
        pgMid: String = "mid-001",
        pgTransactionId: String = "pg-tx-001",
        reason: String = "고객 요청",
        idempotencyKey: String = "cancel-001",
    ): PgCancelRequest =
        PgCancelRequest(
            pgMid = pgMid,
            pgTransactionId = pgTransactionId,
            cancelAmount = money(),
            reason = reason,
            idempotencyKey = idempotencyKey,
        )
}
