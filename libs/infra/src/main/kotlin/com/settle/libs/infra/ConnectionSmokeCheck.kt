package com.settle.libs.infra

import org.apache.kafka.clients.admin.AdminClient
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class ConnectionSmokeCheck(
    private val redisConnectionFactory: RedisConnectionFactory,
    private val kafkaAdmin: KafkaAdmin,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        checkRedis()
        checkKafka()
    }

    private fun checkRedis() {
        val pong =
            redisConnectionFactory.connection.use { connection ->
                connection.ping()
            }

        logger.info("Redis connection smoke check succeeded: {}", pong)
    }

    private fun checkKafka() {
        val adminClient = AdminClient.create(kafkaAdmin.configurationProperties)

        try {
            val nodes = adminClient.describeCluster().nodes().get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            logger.info("Kafka connection smoke check succeeded: {} broker(s)", nodes.size)
        } finally {
            adminClient.close(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
        }
    }

    private companion object {
        private const val CONNECTION_TIMEOUT_SECONDS = 5L
        private val logger = LoggerFactory.getLogger(ConnectionSmokeCheck::class.java)
    }
}
