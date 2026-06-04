package com.settle.domains.payment.application.provider

import com.settle.libs.pgclient.PgPaymentRoute

class PgPaymentProviderRegistry(
    providers: Collection<PgPaymentProvider>,
) {
    private val providersByRoute =
        providers
            .groupBy { it.route }
            .mapValues { (route, providers) ->
                if (providers.size != 1) {
                    throw DuplicatePgPaymentProviderException(route)
                }

                providers.single()
            }

    fun get(route: PgPaymentRoute): PgPaymentProvider = providersByRoute[route] ?: throw PgPaymentProviderNotFoundException(route)
}

sealed class PgPaymentProviderException(
    message: String,
) : RuntimeException(message)

class DuplicatePgPaymentProviderException(
    val route: PgPaymentRoute,
) : PgPaymentProviderException("Duplicate PG payment provider for route '$route'")

class PgPaymentProviderNotFoundException(
    val route: PgPaymentRoute,
) : PgPaymentProviderException("PG payment provider not found for route '$route'")
