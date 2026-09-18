package org.example.backend.exchange.repository

import org.example.backend.enums.OutboxEventStatus
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.domain.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {
    fun findTop100ByStatusAndEventTypeOrderById(
        status: OutboxEventStatus,
        eventType: OutboxEventType
    ): List<OutboxEvent>
}
