package com.settle.domains.payment.infrastructure.circuitbreaker

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pg.circuit-breaker")
data class PgPaymentCircuitBreakerProperties(
    val failureRateThreshold: Float = 50F,
    val slowCallRateThreshold: Float = 100F,
    val slowCallDurationThreshold: Duration = Duration.ofSeconds(3),
    val minimumNumberOfCalls: Int = 10,
    val slidingWindowSize: Int = 20,
    val waitDurationInOpenState: Duration = Duration.ofSeconds(30),
    val permittedNumberOfCallsInHalfOpenState: Int = 3,
) {
    fun toConfig(): CircuitBreakerConfig =
        CircuitBreakerConfig
            .custom()
            .failureRateThreshold(failureRateThreshold)
            .slowCallRateThreshold(slowCallRateThreshold)
            .slowCallDurationThreshold(slowCallDurationThreshold)
            .minimumNumberOfCalls(minimumNumberOfCalls)
            .slidingWindowSize(slidingWindowSize)
            .waitDurationInOpenState(waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
            .build()
}
