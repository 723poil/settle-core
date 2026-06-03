package com.settle.domains.merchant.persistence

import com.settle.libs.common.id.UuidV7
import com.settle.libs.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(schema = "merchant", name = "merchants")
open class MerchantEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UuidV7.generate(),
    @Column(name = "merchant_key", nullable = false, unique = true, length = 64)
    open var merchantKey: String,
    @Column(name = "name", nullable = false, length = 120)
    open var name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    open var status: MerchantStatus = MerchantStatus.ACTIVE,
) : BaseTimeEntity()
