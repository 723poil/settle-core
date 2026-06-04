package com.settle.domains.payment.infrastructure.pg

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey
import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerOperation
import com.settle.domains.payment.application.port.circuitbreaker.PgPaymentCircuitBreakerPort
import com.settle.domains.payment.application.provider.PgPaymentProvider
import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgPaymentClient
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse

class PgClientPaymentProviderAdapter(
    private val client: PgPaymentClient,
    private val circuitBreaker: PgPaymentCircuitBreakerPort,
) : PgPaymentProvider {
    override val route: PgPaymentRoute = client.route

    override fun prepare(request: PgPrepareRequest): PgPrepareResponse =
        execute(PgPaymentCircuitBreakerOperation.PREPARE, request.pgMid) {
            client.prepare(request)
        }

    override fun lookup(request: PgLookupRequest): PgLookupResponse =
        execute(PgPaymentCircuitBreakerOperation.LOOKUP, request.pgMid) {
            client.lookup(request)
        }

    override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse =
        execute(PgPaymentCircuitBreakerOperation.AUTHORIZE, request.pgMid) {
            client.authorize(request)
        }

    override fun cancel(request: PgCancelRequest): PgCancelResponse =
        execute(PgPaymentCircuitBreakerOperation.CANCEL, request.pgMid) {
            client.cancel(request)
        }

    private fun <T> execute(
        operation: PgPaymentCircuitBreakerOperation,
        pgMid: String,
        block: () -> T,
    ): T = circuitBreaker.execute(PgPaymentCircuitBreakerKey(route, operation, pgMid), block)
}
