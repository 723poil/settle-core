package com.settle.domains.merchant.persistence

import com.settle.libs.common.id.UuidV7
import com.settle.libs.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(schema = "merchant", name = "pg_providers")
open class PgProviderEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UuidV7.generate(),
    @Column(name = "code", nullable = false, unique = true, length = 40)
    open var code: String,
    @Column(name = "name", nullable = false, length = 120)
    open var name: String,
    @Column(name = "active", nullable = false)
    open var active: Boolean = true,
) : BaseTimeEntity()
