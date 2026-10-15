package org.example.backend.exchange.service

import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.domain.OrderRecoveryAudit
import org.example.backend.exchange.domain.OutboxEvent
import org.example.backend.exchange.domain.ReservationStatus
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.exchange.repository.OrderRecoveryAuditRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class OrderRecoveryResult(val orderId: Long, val result: String)

@Service
class OrderRecoveryService(
    private val orders: ExchangeOrderRepository,
    private val reservations: ExchangeReservationRepository,
    private val events: OutboxEventRepository,
    private val audits: OrderRecoveryAuditRepository
) {
    @Transactional
    fun requeue(orderId: Long, actorId: Long, reason: String): OrderRecoveryResult {
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..500) { "Recovery reason must contain 3 to 500 characters" }

        val order = orders.findByIdForUpdate(orderId)
        val result = when {
            order == null -> "ORDER_NOT_FOUND"
            order.status !in ExchangeOrderRepository.RESERVING_STATUSES -> "TERMINAL_ORDER"
            reservations.findById(orderId).orElse(null)?.status != ReservationStatus.ACTIVE -> "MISSING_RESERVATION"
            else -> "QUEUED"
        }
        if (result == "QUEUED") {
            events.save(OutboxEvent(aggregateId = orderId, eventType = OutboxEventType.ORDER_ACCEPTED))
        }
        audits.save(OrderRecoveryAudit(orderId = orderId, actorId = actorId, reason = normalizedReason, result = result))
        return OrderRecoveryResult(orderId, result)
    }
}
