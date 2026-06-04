package com.settle.domains.payment.application.provider

import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PgPaymentProviderRegistryTests {
    @Test
    fun findsProviderByExactProviderAndProductRoute() {
        val paymentProvider = StubPgPaymentProvider(route("tosspayments", "payment"))
        val billingProvider = StubPgPaymentProvider(route("tosspayments", "billing"))
        val registry = PgPaymentProviderRegistry(listOf(paymentProvider, billingProvider))

        assertSame(paymentProvider, registry.get(route("tosspayments", "payment")))
        assertSame(billingProvider, registry.get(route("tosspayments", "billing")))
    }

    @Test
    fun rejectsDuplicateProvidersForSameRoute() {
        val duplicateRoute = route("paypal", "vault")

        val exception =
            assertFailsWith<DuplicatePgPaymentProviderException> {
                PgPaymentProviderRegistry(
                    listOf(
                        StubPgPaymentProvider(duplicateRoute),
                        StubPgPaymentProvider(duplicateRoute),
                    ),
                )
            }

        assertEquals(duplicateRoute, exception.route)
    }

    @Test
    fun throwsWhenProviderForRouteIsMissing() {
        val registry = PgPaymentProviderRegistry(listOf(StubPgPaymentProvider(route("paypal", "vault"))))
        val missingRoute = route("paypal", "billing")

        val exception =
            assertFailsWith<PgPaymentProviderNotFoundException> {
                registry.get(missingRoute)
            }

        assertEquals(missingRoute, exception.route)
    }

    private class StubPgPaymentProvider(
        override val route: PgPaymentRoute,
    ) : PgPaymentProvider {
        override fun prepare(request: PgPrepareRequest): PgPrepareResponse = error("not used")

        override fun lookup(request: PgLookupRequest): PgLookupResponse = error("not used")

        override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse = error("not used")

        override fun cancel(request: PgCancelRequest): PgCancelResponse = error("not used")
    }

    private companion object {
        fun route(
            provider: String,
            product: String,
        ): PgPaymentRoute = PgPaymentRoute(PgProvider(provider), PgPaymentProduct(product))
    }
}
