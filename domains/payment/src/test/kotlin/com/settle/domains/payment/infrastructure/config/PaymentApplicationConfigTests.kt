package com.settle.domains.payment.infrastructure.config

import com.settle.domains.payment.application.port.PaymentRecordPort
import com.settle.domains.payment.application.port.PgMerchantAccountLookupPort
import com.settle.domains.payment.application.provider.PgPaymentProvider
import com.settle.domains.payment.application.usecase.AuthorizePaymentUseCase
import com.settle.domains.payment.application.usecase.CancelPaymentUseCase
import com.settle.domains.payment.application.usecase.LookupPaymentUseCase
import com.settle.domains.payment.application.usecase.PreparePaymentCommand
import com.settle.domains.payment.application.usecase.PreparePaymentUseCase
import com.settle.domains.payment.domain.model.PgMerchantAccount
import com.settle.domains.payment.domain.model.PreparedPayment
import com.settle.domains.payment.infrastructure.circuitbreaker.PgPaymentCircuitBreakerProperties
import com.settle.domains.payment.infrastructure.circuitbreaker.Resilience4jPgPaymentOperationCircuitBreaker
import com.settle.libs.pgclient.PgAuthorizeRequest
import com.settle.libs.pgclient.PgAuthorizeResponse
import com.settle.libs.pgclient.PgCancelRequest
import com.settle.libs.pgclient.PgCancelResponse
import com.settle.libs.pgclient.PgLookupRequest
import com.settle.libs.pgclient.PgLookupResponse
import com.settle.libs.pgclient.PgMoney
import com.settle.libs.pgclient.PgPaymentClient
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgPaymentStatus
import com.settle.libs.pgclient.PgPrepareRequest
import com.settle.libs.pgclient.PgPrepareResponse
import com.settle.libs.pgclient.PgProvider
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PaymentApplicationConfigTests {
    @Test
    fun createsProvidersAndUseCasesFromPgClients() {
        val config = PaymentApplicationConfig()
        val client = StubPgPaymentClient(PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment")))
        val registry = config.pgPaymentCircuitBreakerRegistry(PgPaymentCircuitBreakerProperties())
        val circuitBreaker = config.pgPaymentOperationCircuitBreaker(registry)
        val providers = config.pgPaymentProviders(listOf(client), circuitBreaker)
        val providerRegistry = config.pgPaymentProviderRegistry(providers)

        assertEquals(1, providers.size)
        assertIs<PgPaymentProvider>(providerRegistry.get(client.route))
        assertIs<Resilience4jPgPaymentOperationCircuitBreaker>(circuitBreaker)
        assertIs<PreparePaymentUseCase>(
            config.preparePaymentUseCase(StubPgMerchantAccountLookupPort, StubPaymentRecordPort, providerRegistry),
        )
        assertIs<LookupPaymentUseCase>(config.lookupPaymentUseCase(providerRegistry))
        assertIs<AuthorizePaymentUseCase>(config.authorizePaymentUseCase(providerRegistry))
        assertIs<CancelPaymentUseCase>(config.cancelPaymentUseCase(providerRegistry))
    }

    @Test
    fun defaultCircuitBreakerCanBeReplacedByRealImplementation() {
        val method =
            PaymentApplicationConfig::class.java.getDeclaredMethod(
                "pgPaymentOperationCircuitBreaker",
                CircuitBreakerRegistry::class.java,
            )

        assertNotNull(method.getAnnotation(ConditionalOnMissingBean::class.java))
    }

    private class StubPgPaymentClient(
        override val route: PgPaymentRoute,
    ) : PgPaymentClient {
        override fun prepare(request: PgPrepareRequest): PgPrepareResponse =
            PgPrepareResponse(
                pgTransactionId = "pg-tx-001",
                status = PgPaymentStatus.READY,
                requestedAt = Instant.parse("2026-06-04T00:00:00Z"),
            )

        override fun lookup(request: PgLookupRequest): PgLookupResponse =
            PgLookupResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.APPROVED,
                amount = PgMoney(BigDecimal("1000.00"), "KRW"),
            )

        override fun authorize(request: PgAuthorizeRequest): PgAuthorizeResponse =
            PgAuthorizeResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.APPROVED,
                amount = request.amount,
                approvedAt = Instant.parse("2026-06-04T00:00:01Z"),
            )

        override fun cancel(request: PgCancelRequest): PgCancelResponse =
            PgCancelResponse(
                pgTransactionId = request.pgTransactionId,
                status = PgPaymentStatus.CANCELED,
                canceledAmount = request.cancelAmount,
                canceledAt = Instant.parse("2026-06-04T00:00:02Z"),
            )
    }

    private object StubPgMerchantAccountLookupPort : PgMerchantAccountLookupPort {
        override fun getActiveAccount(command: PreparePaymentCommand): PgMerchantAccount =
            PgMerchantAccount(
                merchantId = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                pgProviderId = UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                pgMerchantAccountId = UUID.fromString("018f0000-0000-7000-8000-000000000003"),
                merchantKey = command.merchantKey,
                pgProvider = command.pgProvider,
                pgProduct = command.pgProduct,
                pgMid = command.pgMid,
            )
    }

    private object StubPaymentRecordPort : PaymentRecordPort {
        override fun recordPreparedPayment(payment: PreparedPayment) = Unit
    }
}
