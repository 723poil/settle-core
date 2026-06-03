package com.settle.apps.api

import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals

class ApiConnectionPropertiesTests {
    private val properties = loadApplicationProperties()

    @Test
    fun configuresLocalDataStores() {
        assertEquals("\${DB_URL:jdbc:postgresql://localhost:5432/settle_core}", property("spring.datasource.url"))
        assertEquals("\${REDIS_HOST:localhost}", property("spring.data.redis.host"))
        assertEquals("\${REDIS_PORT:6379}", property("spring.data.redis.port"))
        assertEquals("\${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}", property("spring.kafka.bootstrap-servers"))
        assertEquals("false", property("spring.flyway.enabled"))
    }

    @Test
    fun configuresPgClientProperties() {
        assertEquals("\${PG_TOSS_PAYMENTS_SECRET_KEY:}", property("pg.toss-payments.secret-key"))
        assertEquals("\${PG_PAYPAL_ACCESS_TOKEN:}", property("pg.paypal.access-token"))
        assertEquals("\${PG_KIWOOM_PAY_CPID:}", property("pg.kiwoom-pay.accounts[0].cpid"))
        assertEquals("\${PG_KIWOOM_PAY_TYPE:PAYMENT}", property("pg.kiwoom-pay.accounts[0].type"))
    }

    private fun property(name: String): String? = properties[name]?.toString()

    @Suppress("UNCHECKED_CAST")
    private fun loadApplicationProperties(): Map<String, Any> {
        val loader = YamlPropertySourceLoader()
        val propertySource = loader.load("application.yml", ClassPathResource("application.yml")).first()

        return propertySource.source as Map<String, Any>
    }
}
