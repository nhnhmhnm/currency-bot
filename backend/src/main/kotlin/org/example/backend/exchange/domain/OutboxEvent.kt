package org.example.backend.exchange.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.example.backend.enums.OutboxEventStatus
import org.example.backend.enums.OutboxEventType
import java.time.LocalDateTime

@Entity
@Table(name = "outbox_event")
class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: OutboxEventType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,

    @Column(name = "publish_attempts", nullable = false)
    var publishAttempts: Int = 0,

    @Column(name = "last_error", length = 255)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null
)
