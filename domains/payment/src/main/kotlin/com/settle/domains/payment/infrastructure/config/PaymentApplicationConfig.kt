package com.settle.domains.payment.infrastructure.config

import com.settle.domains.payment.application.port.circuitbreaker.PgPaymentCircuitBreakerPort
import com.settle.domains.payment.application.port.concurrency.PaymentConcurrencyLockPort
import com.settle.domains.payment.application.port.persistence.PaymentPersistenceRecordPort
import com.settle.domains.payment.application.port.persistence.PgMerchantAccountPersistenceLookupPort
import com.settle.domains.payment.application.provider.PgPaymentProvider
import com.settle.domains.payment.application.provider.PgPaymentProviderRegistry
import com.settle.domains.payment.application.usecase.AuthorizePaymentUseCase
import com.settle.domains.payment.application.usecase.CancelPaymentUseCase
import com.settle.domains.payment.application.usecase.LookupPaymentUseCase
import com.settle.domains.payment.application.usecase.PreparePaymentUseCase
import com.settle.domains.payment.infrastructure.circuitbreaker.PgPaymentCircuitBreakerProperties
import com.settle.domains.payment.infrastructure.circuitbreaker.Resilience4jPgPaymentCircuitBreakerAdapter
import com.settle.domains.payment.infrastructure.concurrency.RedisPaymentConcurrencyLockAdapter
import com.settle.domains.payment.infrastructure.concurrency.StringRedisNxLockClient
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
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
@EnableConfigurationProperties(PgPaymentCircuitBreakerProperties::class)
class PaymentApplicationConfig {
    @Bean
    @ConditionalOnMissingBean(CircuitBreakerRegistry::class)
    fun pgPaymentCircuitBreakerRegistry(properties: PgPaymentCircuitBreakerProperties): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(properties.toConfig())

    @Bean
    @ConditionalOnMissingBean(PgPaymentCircuitBreakerPort::class)
    fun pgPaymentCircuitBreakerPort(registry: CircuitBreakerRegistry): PgPaymentCircuitBreakerPort =
        Resilience4jPgPaymentCircuitBreakerAdapter(registry)

    @Bean
    fun pgPaymentProviders(
        clients: List<PgPaymentClient>,
        circuitBreaker: PgPaymentCircuitBreakerPort,
    ): List<PgPaymentProvider> = clients.map { client -> PgClientPaymentProviderAdapter(client, circuitBreaker) }

    @Bean
    fun pgPaymentProviderRegistry(providers: List<PgPaymentProvider>): PgPaymentProviderRegistry = PgPaymentProviderRegistry(providers)

    @Bean
    fun paymentConcurrencyLockPort(redis: StringRedisTemplate): PaymentConcurrencyLockPort =
        RedisPaymentConcurrencyLockAdapter(StringRedisNxLockClient(redis))

    @Bean
    fun pgMerchantAccountPersistenceLookupPort(accounts: PaymentPgMerchantAccountJpaRepository): PgMerchantAccountPersistenceLookupPort =
        JpaPgMerchantAccountLookupAdapter(accounts)

    @Bean
    fun paymentPersistenceRecordPort(
        entityManager: EntityManager,
        paymentTransactions: PaymentTransactionJpaRepository,
        paymentEvents: PaymentEventJpaRepository,
    ): PaymentPersistenceRecordPort =
        JpaPaymentRecordAdapter(
            entityManager = entityManager,
            paymentTransactions = paymentTransactions,
            paymentEvents = paymentEvents,
        )

    @Bean
    fun preparePaymentUseCase(
        accounts: PgMerchantAccountPersistenceLookupPort,
        records: PaymentPersistenceRecordPort,
        locks: PaymentConcurrencyLockPort,
        providers: PgPaymentProviderRegistry,
    ): PreparePaymentUseCase = PreparePaymentUseCase(accounts, records, locks, providers)

    @Bean
    fun lookupPaymentUseCase(providers: PgPaymentProviderRegistry): LookupPaymentUseCase = LookupPaymentUseCase(providers)

    @Bean
    fun authorizePaymentUseCase(
        records: PaymentPersistenceRecordPort,
        locks: PaymentConcurrencyLockPort,
        providers: PgPaymentProviderRegistry,
    ): AuthorizePaymentUseCase = AuthorizePaymentUseCase(records, locks, providers)

    @Bean
    fun cancelPaymentUseCase(
        locks: PaymentConcurrencyLockPort,
        providers: PgPaymentProviderRegistry,
    ): CancelPaymentUseCase = CancelPaymentUseCase(locks, providers)
}
