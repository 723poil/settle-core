package com.settle.libs.pgclient.toss

import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentClient
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import com.settle.libs.pgclient.http.PgHttpMethod
import com.settle.libs.pgclient.http.PgHttpRequest
import com.settle.libs.pgclient.http.PgHttpTransport
import com.settle.libs.pgclient.support.asMap
import com.settle.libs.pgclient.support.asMapList
import com.settle.libs.pgclient.support.decimalValue
import com.settle.libs.pgclient.support.money
import com.settle.libs.pgclient.support.optionalStringValue
import com.settle.libs.pgclient.support.stringValue
import com.settle.libs.pgclient.support.toInstantFromPg
import com.settle.libs.pgclient.support.toPlainAmount
import java.time.Instant
import java.util.Base64

class TossPaymentsPaymentClient(
    private val secretKey: String,
    private val transport: PgHttpTransport,
    private val baseUrl: String = "https://api.tosspayments.com",
) : PgPaymentClient {
    override val route: PgPaymentRoute = PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment"))

    override fun prepare(request: PgPrepareRequest): PgPrepareResponse {
        val response =
            transport.execute(
                PgHttpRequest(
                    method = PgHttpMethod.POST,
                    url = "$baseUrl/v1/payments",
                    headers = commonHeaders(),
                    body =
                        mapOf(
                            "method" to request.metadata.getOrDefault("method", "CARD"),
                            "amount" to request.amount.amount.toPlainAmount(),
                            "currency" to request.amount.currency,
                            "orderId" to request.merchantOrderId,
                            "orderName" to request.orderName,
                            "successUrl" to request.successUrl,
                            "failUrl" to request.failureUrl,
                        ).filterValues { it != null },
                ),
            )
        val body = response.body

        return PgPrepareResponse(
            pgTransactionId = body.stringValue("paymentKey"),
            status = body.tossStatus(),
            requestedAt = body.stringValue("requestedAt").toInstantFromPg(),
            checkoutUrl = body["checkout"].asMap().optionalStringValue("url"),
            rawPayload = body,
        )
    }

    override fun lookup(request: PgLookupRequest): PgLookupResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.GET,
                        url = "$baseUrl/v1/payments/${request.pgTransactionId}",
                        headers = commonHeaders(),
                    ),
                ).body

        return PgLookupResponse(
            pgTransactionId = body.stringValue("paymentKey"),
            status = body.tossStatus(),
            amount = body.money("totalAmount", "currency"),
            approvedAt = body.optionalStringValue("approvedAt")?.toInstantFromPg(),
            canceledAt = body.lastCancelInstant(),
            rawPayload = body,
        )
    }

    override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = "$baseUrl/v1/payments/confirm",
                        headers = commonHeaders(),
                        body =
                            mapOf(
                                "paymentKey" to request.authorizationToken,
                                "orderId" to request.merchantOrderId,
                                "amount" to request.amount.amount.toPlainAmount(),
                            ),
                    ),
                ).body

        return PgAuthorizeResponse(
            pgTransactionId = body.stringValue("paymentKey"),
            status = body.tossStatus(),
            amount = body.money("totalAmount", "currency"),
            approvedAt = requireNotNull(body.optionalStringValue("approvedAt")) { "Toss approvedAt is missing" }.toInstantFromPg(),
            rawPayload = body,
        )
    }

    override fun cancel(request: PgCancelRequest): PgCancelResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = "$baseUrl/v1/payments/${request.pgTransactionId}/cancel",
                        headers = commonHeaders() + ("Idempotency-Key" to request.idempotencyKey),
                        body =
                            mapOf(
                                "cancelReason" to request.reason,
                                "cancelAmount" to request.cancelAmount.amount.toPlainAmount(),
                                "currency" to request.cancelAmount.currency,
                            ),
                    ),
                ).body
        val lastCancel = body["cancels"].asMapList().last()

        return PgCancelResponse(
            pgTransactionId = body.stringValue("paymentKey"),
            status = body.tossStatus(),
            canceledAmount = PgMoney(lastCancel.decimalValue("cancelAmount"), body.stringValue("currency")),
            canceledAt = lastCancel.stringValue("canceledAt").toInstantFromPg(),
            rawPayload = body,
        )
    }

    private fun commonHeaders(): Map<String, String> =
        mapOf(
            "Authorization" to "Basic ${Base64.getEncoder().encodeToString("$secretKey:".toByteArray())}",
            "Content-Type" to "application/json",
        )

    private fun Map<String, Any?>.tossStatus(): PgPaymentStatus =
        when (stringValue("status")) {
            "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT" -> PgPaymentStatus.READY
            "DONE" -> PgPaymentStatus.APPROVED
            "CANCELED" -> PgPaymentStatus.CANCELED
            "PARTIAL_CANCELED" -> PgPaymentStatus.PARTIAL_CANCELED
            "ABORTED", "EXPIRED" -> PgPaymentStatus.FAILED
            else -> PgPaymentStatus.FAILED
        }

    private fun Map<String, Any?>.lastCancelInstant(): Instant? {
        val cancels = this["cancels"] ?: return null
        return cancels
            .asMapList()
            .lastOrNull()
            ?.optionalStringValue("canceledAt")
            ?.toInstantFromPg()
    }
}
