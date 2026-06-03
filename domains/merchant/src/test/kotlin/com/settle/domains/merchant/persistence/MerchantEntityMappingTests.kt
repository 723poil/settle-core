package com.settle.domains.merchant.persistence

import jakarta.persistence.Table
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MerchantEntityMappingTests {
    @Test
    fun mapsMerchantSchemaTables() {
        assertTable(MerchantEntity::class.java, schema = "merchant", name = "merchants")
        assertTable(PgProviderEntity::class.java, schema = "merchant", name = "pg_providers")
        assertTable(PgMerchantAccountEntity::class.java, schema = "merchant", name = "pg_merchant_accounts")
    }

    @Test
    fun generatesUuidV7IdsInApplicationCode() {
        val merchant =
            MerchantEntity(
                merchantKey = "merchant-key",
                name = "테스트 상점",
            )

        assertEquals(7, merchant.id.version())
    }

    private fun assertTable(
        type: Class<*>,
        schema: String,
        name: String,
    ) {
        val table = requireNotNull(type.getAnnotation(Table::class.java))

        assertEquals(schema, table.schema)
        assertEquals(name, table.name)
    }
}
