package com.settle.libs.pgclient

class PgPaymentClientRegistry(
    clients: Collection<PgPaymentClient>,
) {
    private val clientsByProvider = clients.associateBy { it.provider }

    fun get(provider: PgProvider): PgPaymentClient = clientsByProvider.getValue(provider)
}
