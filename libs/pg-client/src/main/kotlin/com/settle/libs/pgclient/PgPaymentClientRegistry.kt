package com.settle.libs.pgclient

class PgPaymentClientRegistry(
    clients: Collection<PgPaymentClient>,
) {
    private val clientsByProvider =
        clients
            .groupBy { it.provider }
            .mapValues { (provider, clients) ->
                if (clients.size != 1) {
                    throw DuplicatePgPaymentClientException(provider)
                }

                clients.single()
            }

    fun get(provider: PgProvider): PgPaymentClient = clientsByProvider[provider] ?: throw PgPaymentClientNotFoundException(provider)
}
