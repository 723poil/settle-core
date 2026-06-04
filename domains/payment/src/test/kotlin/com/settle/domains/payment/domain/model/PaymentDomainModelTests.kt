package com.settle.domains.payment.domain.model

import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class PaymentDomainModelTests {
    @Test
    fun mapsPgPaymentStatusesToDomainPaymentStatuses() {
        val cases =
            mapOf(
                PgPaymentStatus.READY to PaymentStatus.REQUESTED,
                PgPaymentStatus.APPROVED to PaymentStatus.APPROVED,
                PgPaymentStatus.CANCELED to PaymentStatus.CANCELED,
                PgPaymentStatus.PARTIAL_CANCELED to PaymentStatus.CANCELED,
                PgPaymentStatus.FAILED to PaymentStatus.FAILED,
            )

        cases.forEach { (pgStatus, expectedStatus) ->
            assertEquals(expectedStatus, PaymentStatus.from(pgStatus))
        }
    }

    @Test
    fun preparedPaymentKeepsRawPayloadWhenPgPrepareResponseContainsIt() {
        val payment =
            PreparedPayment.from(
                idempotencyKey = "idempotency-001",
                account = account(),
                merchantOrderId = "order-001",
                amount = BigDecimal("1000.00"),
                currency = "KRW",
                response =
                    PgPrepareResponse(
                        pgTransactionId = "pg-tx-001",
                        status = PgPaymentStatus.READY,
                        requestedAt = Instant.parse("2026-06-04T00:00:00Z"),
                        rawPayload = mapOf("checkoutKey" to "checkout-001"),
                    ),
            )

        assertEquals(PaymentStatus.REQUESTED, payment.status)
        assertEquals(mapOf("checkoutKey" to "checkout-001"), payment.event.rawPayload)
    }

    @Test
    fun authorizedPaymentKeepsRawPayloadWhenPgAuthorizeResponseContainsIt() {
        val payment =
            AuthorizedPayment.from(
                payment = snapshot(),
                response =
                    PgAuthorizeResponse(
                        pgTransactionId = "pg-tx-authorized-001",
                        status = PgPaymentStatus.APPROVED,
                        amount = PgMoney(BigDecimal("1000.00"), "KRW"),
                        approvedAt = Instant.parse("2026-06-04T00:00:01Z"),
                        rawPayload = mapOf("authKey" to "auth-001"),
                    ),
            )

        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(mapOf("authKey" to "auth-001"), payment.event.rawPayload)
    }

    private companion object {
        fun account(): PgMerchantAccount =
            PgMerchantAccount(
                merchantId = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                pgProviderId = UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                pgMerchantAccountId = UUID.fromString("018f0000-0000-7000-8000-000000000003"),
                merchantKey = "merchant-key",
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = "mid-001",
            )

        fun snapshot(): PaymentTransactionSnapshot =
            PaymentTransactionSnapshot(
                idempotencyKey = "idempotency-001",
                merchantId = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                pgProviderId = UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                pgMerchantAccountId = UUID.fromString("018f0000-0000-7000-8000-000000000003"),
                merchantKey = "merchant-key",
                pgProvider = PgProvider("tosspayments"),
                pgProduct = PgPaymentProduct("payment"),
                pgMid = "mid-001",
                merchantOrderId = "order-001",
                pgTransactionId = "pg-tx-001",
                status = PaymentStatus.REQUESTED,
                amount = BigDecimal("1000.00"),
                currency = "KRW",
            )
    }
}
