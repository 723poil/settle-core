package com.settle.libs.pgclient

class PgPaymentClientRegistry(
    clients: Collection<PgPaymentClient>,
) {
    private val clientsByRoute =
        clients
            .groupBy { it.route }
            .mapValues { (route, clients) ->
                if (clients.size != 1) {
                    throw DuplicatePgPaymentClientException(route)
                }

                clients.single()
            }

    fun get(route: PgPaymentRoute): PgPaymentClient = clientsByRoute[route] ?: throw PgPaymentClientNotFoundException(route)
}
