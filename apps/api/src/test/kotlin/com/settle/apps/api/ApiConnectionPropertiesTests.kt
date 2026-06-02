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
    }

    private fun property(name: String): String? = properties[name]?.toString()

    @Suppress("UNCHECKED_CAST")
    private fun loadApplicationProperties(): Map<String, Any> {
        val loader = YamlPropertySourceLoader()
        val propertySource = loader.load("application.yml", ClassPathResource("application.yml")).first()

        return propertySource.source as Map<String, Any>
    }
}
