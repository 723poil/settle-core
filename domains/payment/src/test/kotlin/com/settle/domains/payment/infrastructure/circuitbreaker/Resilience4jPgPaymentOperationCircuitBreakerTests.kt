package com.settle.domains.payment.infrastructure.circuitbreaker

import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerKey
import com.settle.domains.payment.application.circuitbreaker.PgPaymentCircuitBreakerOperation
import com.settle.libs.pgclient.PgPaymentProduct
import com.settle.libs.pgclient.PgPaymentRoute
import com.settle.libs.pgclient.PgProvider
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Resilience4jPgPaymentOperationCircuitBreakerTests {
    private val route = PgPaymentRoute(PgProvider("tosspayments"), PgPaymentProduct("payment"))
    private val prepareKey = PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.PREPARE, "mid-001")

    @Test
    fun opensCircuitAndRejectsCallsWhenFailureRateThresholdIsExceeded() {
        val registry = CircuitBreakerRegistry.of(testConfig())
        val circuitBreaker = Resilience4jPgPaymentOperationCircuitBreaker(registry)
        var attempts = 0

        repeat(2) {
            assertFailsWith<IllegalStateException> {
                circuitBreaker.execute(prepareKey) {
                    attempts += 1
                    throw IllegalStateException("PG timeout")
                }
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker(prepareKey.circuitName).state)
        assertFailsWith<CallNotPermittedException> {
            circuitBreaker.execute(prepareKey) {
                attempts += 1
                "approved"
            }
        }
        assertEquals(2, attempts)
    }

    @Test
    fun managesCircuitStateByCircuitName() {
        val registry = CircuitBreakerRegistry.of(testConfig())
        val circuitBreaker = Resilience4jPgPaymentOperationCircuitBreaker(registry)
        val authorizeKey = PgPaymentCircuitBreakerKey(route, PgPaymentCircuitBreakerOperation.AUTHORIZE, "mid-001")

        repeat(2) {
            assertFailsWith<IllegalStateException> {
                circuitBreaker.execute(prepareKey) {
                    throw IllegalStateException("PG timeout")
                }
            }
        }

        val response =
            circuitBreaker.execute(authorizeKey) {
                "approved"
            }

        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker(prepareKey.circuitName).state)
        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker(authorizeKey.circuitName).state)
        assertEquals("approved", response)
    }

    @Test
    fun defaultPropertiesKeepCircuitClosedUntilMinimumCallCountIsReached() {
        val registry = CircuitBreakerRegistry.of(PgPaymentCircuitBreakerProperties().toConfig())
        val circuitBreaker = Resilience4jPgPaymentOperationCircuitBreaker(registry)

        repeat(9) {
            assertFailsWith<IllegalStateException> {
                circuitBreaker.execute(prepareKey) {
                    throw IllegalStateException("PG timeout")
                }
            }
        }

        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker(prepareKey.circuitName).state)

        assertFailsWith<IllegalStateException> {
            circuitBreaker.execute(prepareKey) {
                throw IllegalStateException("PG timeout")
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker(prepareKey.circuitName).state)
    }

    @Test
    fun halfOpenUsesConfiguredTrialCallCountBeforeClosingCircuit() {
        val registry =
            CircuitBreakerRegistry.of(
                PgPaymentCircuitBreakerProperties(
                    minimumNumberOfCalls = 3,
                    slidingWindowSize = 3,
                    waitDurationInOpenState = Duration.ofMillis(1),
                    permittedNumberOfCallsInHalfOpenState = 3,
                ).toConfig(),
            )
        val circuitBreaker = Resilience4jPgPaymentOperationCircuitBreaker(registry)

        repeat(3) {
            assertFailsWith<IllegalStateException> {
                circuitBreaker.execute(prepareKey) {
                    throw IllegalStateException("PG timeout")
                }
            }
        }

        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker(prepareKey.circuitName).state)
        Thread.sleep(5)

        repeat(2) {
            assertEquals("approved", circuitBreaker.execute(prepareKey) { "approved" })
            assertEquals(CircuitBreaker.State.HALF_OPEN, registry.circuitBreaker(prepareKey.circuitName).state)
        }

        assertEquals("approved", circuitBreaker.execute(prepareKey) { "approved" })
        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker(prepareKey.circuitName).state)
    }

    @Test
    fun slowCallRateCanOpenCircuitEvenWhenCallsSucceed() {
        val registry =
            CircuitBreakerRegistry.of(
                PgPaymentCircuitBreakerProperties(
                    slowCallRateThreshold = 50F,
                    slowCallDurationThreshold = Duration.ofMillis(1),
                    minimumNumberOfCalls = 2,
                    slidingWindowSize = 2,
                ).toConfig(),
            )
        val circuitBreaker = Resilience4jPgPaymentOperationCircuitBreaker(registry)

        repeat(2) {
            assertEquals(
                "approved",
                circuitBreaker.execute(prepareKey) {
                    Thread.sleep(2)
                    "approved"
                },
            )
        }

        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker(prepareKey.circuitName).state)
    }

    private fun testConfig(): CircuitBreakerConfig =
        CircuitBreakerConfig
            .custom()
            .failureRateThreshold(50F)
            .minimumNumberOfCalls(2)
            .slidingWindowSize(2)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build()
}
