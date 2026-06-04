package com.settle.domains.payment.application.provider

import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse

interface PgPaymentProvider {
    val route: PgPaymentRoute

    fun prepare(request: PgPrepareRequest): PgPrepareResponse

    fun lookup(request: PgLookupRequest): PgLookupResponse

    fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse

    fun cancel(request: PgCancelRequest): PgCancelResponse
}
