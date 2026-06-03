package com.settle.libs.pgclient.paypal

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
import com.settle.libs.pgclient.support.optionalStringValue
import com.settle.libs.pgclient.support.stringValue
import com.settle.libs.pgclient.support.toInstantFromPg
import com.settle.libs.pgclient.support.toPlainAmountString

class PayPalPaymentClient(
    private val accessToken: String,
    private val transport: PgHttpTransport,
    private val baseUrl: String = "https://api-m.paypal.com",
) : PgPaymentClient {
    override val route: PgPaymentRoute = PgPaymentRoute(PgProvider("paypal"), PgPaymentProduct("checkout"))

    override fun prepare(request: PgPrepareRequest): PgPrepareResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = "$baseUrl/v2/checkout/orders",
                        headers = commonHeaders() + ("PayPal-Request-Id" to request.merchantOrderId),
                        body =
                            mapOf(
                                "intent" to "CAPTURE",
                                "purchase_units" to
                                    listOf(
                                        mapOf(
                                            "reference_id" to request.merchantOrderId,
                                            "description" to request.orderName,
                                            "amount" to request.amount.toPayPalAmount(),
                                        ),
                                    ),
                            ),
                    ),
                ).body

        return PgPrepareResponse(
            pgTransactionId = body.stringValue("id"),
            status = body.payPalStatus(),
            requestedAt = body.stringValue("create_time").toInstantFromPg(),
            checkoutUrl = body.approvalUrl(),
            rawPayload = body,
        )
    }

    override fun lookup(request: PgLookupRequest): PgLookupResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.GET,
                        url = "$baseUrl/v2/payments/captures/${request.pgTransactionId}",
                        headers = commonHeaders(),
                    ),
                ).body

        return PgLookupResponse(
            pgTransactionId = body.stringValue("id"),
            status = body.payPalStatus(),
            amount = body["amount"].asMap().toPgMoney(),
            approvedAt = body.optionalStringValue("update_time")?.toInstantFromPg(),
            rawPayload = body,
        )
    }

    override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = "$baseUrl/v2/checkout/orders/${request.pgTransactionId}/capture",
                        headers = commonHeaders() + ("PayPal-Request-Id" to request.merchantOrderId),
                    ),
                ).body
        val capture = body.firstCapture()

        return PgAuthorizeResponse(
            pgTransactionId = capture.stringValue("id"),
            status = capture.payPalStatus(),
            amount = capture["amount"].asMap().toPgMoney(),
            approvedAt = capture.stringValue("update_time").toInstantFromPg(),
            rawPayload = body,
        )
    }

    override fun cancel(request: PgCancelRequest): PgCancelResponse {
        val body =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = "$baseUrl/v2/payments/captures/${request.pgTransactionId}/refund",
                        headers = commonHeaders() + ("PayPal-Request-Id" to request.idempotencyKey),
                        body =
                            mapOf(
                                "amount" to request.cancelAmount.toPayPalAmount(),
                                "note_to_payer" to request.reason,
                            ),
                    ),
                ).body

        return PgCancelResponse(
            pgTransactionId = request.pgTransactionId,
            status = body.payPalRefundStatus(),
            canceledAmount = body["amount"].asMap().toPgMoney(),
            canceledAt = body.stringValue("update_time").toInstantFromPg(),
            rawPayload = body,
        )
    }

    private fun commonHeaders(): Map<String, String> =
        mapOf(
            "Authorization" to "Bearer $accessToken",
            "Content-Type" to "application/json",
        )

    private fun PgMoney.toPayPalAmount(): Map<String, String> =
        mapOf(
            "value" to amount.toPlainAmountString(),
            "currency_code" to currency,
        )

    private fun Map<String, Any?>.toPgMoney(): PgMoney =
        PgMoney(
            decimalValue("value"),
            stringValue("currency_code"),
        )

    private fun Map<String, Any?>.approvalUrl(): String? =
        this["links"].asMapList().firstOrNull { it["rel"] == "approve" }?.optionalStringValue("href")

    private fun Map<String, Any?>.firstCapture(): Map<String, Any?> =
        this["purchase_units"]
            .asMapList()
            .first()
            .asMap()["payments"]
            .asMap()["captures"]
            .asMapList()
            .first()

    private fun Map<String, Any?>.payPalStatus(): PgPaymentStatus =
        when (stringValue("status")) {
            "CREATED", "SAVED", "APPROVED", "PENDING" -> PgPaymentStatus.READY
            "COMPLETED" -> PgPaymentStatus.APPROVED
            "VOIDED", "REFUNDED" -> PgPaymentStatus.CANCELED
            "PARTIALLY_REFUNDED" -> PgPaymentStatus.PARTIAL_CANCELED
            "DECLINED", "FAILED" -> PgPaymentStatus.FAILED
            else -> PgPaymentStatus.FAILED
        }

    private fun Map<String, Any?>.payPalRefundStatus(): PgPaymentStatus =
        when (stringValue("status")) {
            "COMPLETED" -> PgPaymentStatus.CANCELED
            "PENDING" -> PgPaymentStatus.READY
            "FAILED" -> PgPaymentStatus.FAILED
            else -> PgPaymentStatus.FAILED
        }
}
