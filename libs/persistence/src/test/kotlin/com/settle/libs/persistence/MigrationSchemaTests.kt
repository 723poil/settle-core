package com.settle.libs.persistence

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationSchemaTests {
    private val initMigrationSql =
        requireNotNull(javaClass.classLoader.getResource("db/migration/V1__init_settlement_core.sql")).readText()
    private val addPgProductMigrationSql =
        requireNotNull(javaClass.classLoader.getResource("db/migration/V2__add_pg_payment_product.sql")).readText()

    @Test
    fun createsDomainSchemas() {
        assertTrue(initMigrationSql.contains("create schema if not exists migration;"))
        assertTrue(initMigrationSql.contains("create schema if not exists merchant;"))
        assertTrue(initMigrationSql.contains("create schema if not exists payment;"))
        assertTrue(initMigrationSql.contains("create schema if not exists settlement;"))
    }

    @Test
    fun storesTablesInDomainSchemas() {
        assertTrue(initMigrationSql.contains("create table merchant.merchants"))
        assertTrue(initMigrationSql.contains("create table payment.payment_transactions"))
        assertTrue(initMigrationSql.contains("create table settlement.settlement_batches"))
    }

    @Test
    fun keepsIdentifierGenerationInApplicationCode() {
        assertFalse(initMigrationSql.contains("gen_random_uuid()"))
        assertFalse(initMigrationSql.contains("default uuid"))
    }

    @Test
    fun keepsAppliedInitMigrationStableForPgProductChange() {
        assertFalse(initMigrationSql.contains("pg_product varchar(60) not null"))
        assertTrue(initMigrationSql.contains("constraint uq_pg_merchant_accounts_mid unique (pg_provider_id, pg_mid)"))
        assertTrue(initMigrationSql.contains("constraint uq_payment_transactions_pg unique (pg_provider_id, pg_transaction_id)"))
    }

    @Test
    fun addsPgProductForMerchantAccountsAndPaymentTransactionsInSecondMigration() {
        assertTrue(addPgProductMigrationSql.contains("add column pg_product varchar(60)"))
        assertTrue(addPgProductMigrationSql.contains("set pg_product = 'payment'"))
        assertTrue(addPgProductMigrationSql.contains("alter column pg_product set not null"))
        assertTrue(
            addPgProductMigrationSql.contains("add constraint uq_pg_merchant_accounts_mid unique (pg_provider_id, pg_product, pg_mid)"),
        )
        assertTrue(
            addPgProductMigrationSql.contains(
                "add constraint uq_payment_transactions_pg unique (pg_provider_id, pg_product, pg_transaction_id)",
            ),
        )
    }
}
