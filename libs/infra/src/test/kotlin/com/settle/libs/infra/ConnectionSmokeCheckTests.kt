package com.settle.libs.infra

import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationRunner
import kotlin.test.assertTrue

class ConnectionSmokeCheckTests {
    @Test
    fun isApplicationStartupRunner() {
        assertTrue(ConnectionSmokeCheck::class.java.interfaces.contains(ApplicationRunner::class.java))
    }
}
