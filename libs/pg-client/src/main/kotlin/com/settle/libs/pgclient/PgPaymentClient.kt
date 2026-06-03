package com.settle.libs.pgclient

interface PgPaymentClient {
    val route: PgPaymentRoute

    fun prepare(request: PgPrepareRequest): PgPrepareResponse

    fun lookup(request: PgLookupRequest): PgLookupResponse

    fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse

    fun cancel(request: PgCancelRequest): PgCancelResponse
}
