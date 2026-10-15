package org.example.backend.exchange.repository

import org.example.backend.exchange.domain.OrderRecoveryAudit
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRecoveryAuditRepository : JpaRepository<OrderRecoveryAudit, Long>
