package com.settle.domains.payment.infrastructure.persistence.repository

import com.settle.domains.payment.infrastructure.persistence.entity.PaymentTransactionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentTransactionJpaRepository : JpaRepository<PaymentTransactionEntity, UUID>
