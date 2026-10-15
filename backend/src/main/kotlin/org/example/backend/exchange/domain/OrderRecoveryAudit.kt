package org.example.backend.exchange.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "order_recovery_audit", indexes = [Index(name = "idx_recovery_order_time", columnList = "order_id,created_at")])
class OrderRecoveryAudit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "order_id", nullable = false)
    val orderId: Long,
    @Column(name = "actor_id", nullable = false)
    val actorId: Long,
    @Column(name = "reason", nullable = false, length = 500)
    val reason: String,
    @Column(name = "result", nullable = false, length = 32)
    val result: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
