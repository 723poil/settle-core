package com.settle.domains.payment.infrastructure.config

import com.settle.domains.payment.application.port.PaymentRecordPort
import com.settle.domains.payment.application.port.PgMerchantAccountLookupPort
import com.settle.domains.payment.application.port.PgPaymentOperationCircuitBreaker
import com.settle.domains.payment.application.provider.PgPaymentProvider
import com.settle.domains.payment.application.provider.PgPaymentProviderRegistry
import com.settle.domains.payment.application.usecase.AuthorizePaymentUseCase
import com.settle.domains.payment.application.usecase.CancelPaymentUseCase
import com.settle.domains.payment.application.usecase.LookupPaymentUseCase
import com.settle.domains.payment.application.usecase.PreparePaymentUseCase
import com.settle.domains.payment.infrastructure.circuitbreaker.PgPaymentCircuitBreakerProperties
import com.settle.domains.payment.infrastructure.circuitbreaker.Resilience4jPgPaymentOperationCircuitBreaker
import com.settle.domains.payment.infrastructure.persistence.JpaPaymentRecordAdapter
import com.settle.domains.payment.infrastructure.persistence.JpaPgMerchantAccountLookupAdapter
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentEventJpaRepository
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentPgMerchantAccountJpaRepository
import com.settle.domains.payment.infrastructure.persistence.repository.PaymentTransactionJpaRepository
import com.settle.domains.payment.infrastructure.pg.PgClientPaymentProviderAdapter
import com.settle.libs.pgclient.PgPaymentClient
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PgPaymentCircuitBreakerProperties::class)
class PaymentApplicationConfig {
    @Bean
    @ConditionalOnMissingBean(CircuitBreakerRegistry::class)
    fun pgPaymentCircuitBreakerRegistry(properties: PgPaymentCircuitBreakerProperties): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(properties.toConfig())

    @Bean
    @ConditionalOnMissingBean(PgPaymentOperationCircuitBreaker::class)
    fun pgPaymentOperationCircuitBreaker(registry: CircuitBreakerRegistry): PgPaymentOperationCircuitBreaker =
        Resilience4jPgPaymentOperationCircuitBreaker(registry)

    @Bean
    fun pgPaymentProviders(
        clients: List<PgPaymentClient>,
        circuitBreaker: PgPaymentOperationCircuitBreaker,
    ): List<PgPaymentProvider> = clients.map { client -> PgClientPaymentProviderAdapter(client, circuitBreaker) }

    @Bean
    fun pgPaymentProviderRegistry(providers: List<PgPaymentProvider>): PgPaymentProviderRegistry = PgPaymentProviderRegistry(providers)

    @Bean
    fun pgMerchantAccountLookupPort(accounts: PaymentPgMerchantAccountJpaRepository): PgMerchantAccountLookupPort =
        JpaPgMerchantAccountLookupAdapter(accounts)

    @Bean
    fun paymentRecordPort(
        entityManager: EntityManager,
        paymentTransactions: PaymentTransactionJpaRepository,
        paymentEvents: PaymentEventJpaRepository,
    ): PaymentRecordPort =
        JpaPaymentRecordAdapter(
            entityManager = entityManager,
            paymentTransactions = paymentTransactions,
            paymentEvents = paymentEvents,
        )

    @Bean
    fun preparePaymentUseCase(
        accounts: PgMerchantAccountLookupPort,
        records: PaymentRecordPort,
        providers: PgPaymentProviderRegistry,
    ): PreparePaymentUseCase = PreparePaymentUseCase(accounts, records, providers)

    @Bean
    fun lookupPaymentUseCase(providers: PgPaymentProviderRegistry): LookupPaymentUseCase = LookupPaymentUseCase(providers)

    @Bean
    fun authorizePaymentUseCase(providers: PgPaymentProviderRegistry): AuthorizePaymentUseCase = AuthorizePaymentUseCase(providers)

    @Bean
    fun cancelPaymentUseCase(providers: PgPaymentProviderRegistry): CancelPaymentUseCase = CancelPaymentUseCase(providers)
}
