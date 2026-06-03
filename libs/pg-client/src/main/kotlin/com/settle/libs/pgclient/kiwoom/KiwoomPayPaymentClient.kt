package com.settle.libs.pgclient.kiwoom

import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgPaymentAccountNotFoundException
import com.settle.libs.pgclient.PgPaymentClient
import com.settle.libs.pgclient.PgPaymentOperationNotSupportedException
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import com.settle.libs.pgclient.http.PgHttpMethod
import com.settle.libs.pgclient.http.PgHttpRequest
import com.settle.libs.pgclient.http.PgHttpTransport
import com.settle.libs.pgclient.support.stringValue
import com.settle.libs.pgclient.support.toPlainAmount
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class KiwoomPayPaymentClient(
    accounts: Collection<KiwoomPayAccount>,
    private val transport: PgHttpTransport,
) : PgPaymentClient {
    override val route: PgPaymentRoute = PgPaymentRoute(PgProvider("kiwoompay"), PgPaymentProduct("payment"))
    private val accountsByCpid =
        accounts
            .filter { it.type == KiwoomPayAccountType.PAYMENT }
            .associateBy { it.cpid }

    override fun prepare(request: PgPrepareRequest): PgPrepareResponse {
        val account = account(request.pgMid)
        val payMethod = request.requiredMetadata("PAYMETHOD")
        val readyBody =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = account.readyUrl,
                        headers = account.commonHeaders(),
                        body =
                            mapOf(
                                "CPID" to account.cpid,
                                "PAYMETHOD" to payMethod,
                            ),
                    ),
                ).body

        val token = readyBody.stringValue("TOKEN")
        val paymentBody =
            transport
                .execute(
                    PgHttpRequest(
                        method = PgHttpMethod.POST,
                        url = readyBody.stringValue("RETURNURL"),
                        headers = account.commonHeaders() + ("TOKEN" to token),
                        body =
                            mapOf(
                                "CPID" to account.cpid,
                                "PAYMETHOD" to payMethod,
                                "ORDERNO" to request.merchantOrderId,
                                "PRODUCTTYPE" to request.requiredMetadata("PRODUCTTYPE"),
                                "BILLTYPE" to request.requiredMetadata("BILLTYPE"),
                                "AMOUNT" to
                                    request.amount.amount
                                        .toPlainAmount()
                                        .toPlainString(),
                                "PRODUCTNAME" to request.orderName,
                                "IPADDRESS" to request.requiredMetadata("IPADDRESS"),
                                "USERID" to request.requiredMetadata("USERID"),
                            ),
                    ),
                ).body

        return PgPrepareResponse(
            pgTransactionId = paymentBody.stringValue("DAOUTRX"),
            status = paymentBody.kiwoomStatus(),
            requestedAt = paymentBody.authDate(),
            checkoutUrl = paymentBody["AUTHURL"]?.toString(),
            rawPayload = paymentBody,
        )
    }

    override fun lookup(request: PgLookupRequest): PgLookupResponse = throw PgPaymentOperationNotSupportedException(route, "lookup")

    override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse =
        throw PgPaymentOperationNotSupportedException(route, "authorize")

    override fun cancel(request: PgCancelRequest): PgCancelResponse = throw PgPaymentOperationNotSupportedException(route, "cancel")

    private fun account(cpid: String): KiwoomPayAccount = accountsByCpid[cpid] ?: throw PgPaymentAccountNotFoundException(route, cpid)

    private fun KiwoomPayAccount.commonHeaders(): Map<String, String> =
        mapOf(
            "Content-Type" to "application/json;charset=EUC-KR",
            "Authorization" to authorizationKey,
        )

    private fun PgPrepareRequest.requiredMetadata(name: String): String =
        requireNotNull(metadata[name]) { "Kiwoom Pay prepare metadata '$name' is required" }

    private fun Map<String, Any?>.kiwoomStatus(): PgPaymentStatus =
        if (stringValue("RESULTCODE") == "0000") {
            PgPaymentStatus.READY
        } else {
            PgPaymentStatus.FAILED
        }

    private fun Map<String, Any?>.authDate(): Instant {
        val authDate = this["AUTHDATE"]?.toString() ?: return Instant.EPOCH
        return LocalDateTime.parse(authDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toInstant(ZoneOffset.UTC)
    }
}
