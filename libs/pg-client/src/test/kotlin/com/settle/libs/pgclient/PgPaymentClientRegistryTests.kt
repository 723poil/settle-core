package com.settle.libs.pgclient

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PgPaymentClientRegistryTests {
    @Test
    fun findsClientByProvider() {
        val provider = PgProvider("toss")
        val client = StubPgPaymentClient(provider)
        val registry = PgPaymentClientRegistry(listOf(client))

        assertEquals(client, registry.get(provider))
    }

    private class StubPgPaymentClient(
        override val provider: PgProvider,
    ) : PgPaymentClient
}
