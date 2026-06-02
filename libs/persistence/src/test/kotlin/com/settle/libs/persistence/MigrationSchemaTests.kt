package com.settle.libs.persistence

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationSchemaTests {
    private val migrationSql =
        requireNotNull(javaClass.classLoader.getResource("db/migration/V1__init_settlement_core.sql")).readText()

    @Test
    fun createsDomainSchemas() {
        assertTrue(migrationSql.contains("create schema if not exists migration;"))
        assertTrue(migrationSql.contains("create schema if not exists merchant;"))
        assertTrue(migrationSql.contains("create schema if not exists payment;"))
        assertTrue(migrationSql.contains("create schema if not exists settlement;"))
    }

    @Test
    fun storesTablesInDomainSchemas() {
        assertTrue(migrationSql.contains("create table merchant.merchants"))
        assertTrue(migrationSql.contains("create table payment.payment_transactions"))
        assertTrue(migrationSql.contains("create table settlement.settlement_batches"))
    }

    @Test
    fun keepsIdentifierGenerationInApplicationCode() {
        assertFalse(migrationSql.contains("gen_random_uuid()"))
        assertFalse(migrationSql.contains("default uuid"))
    }
}
