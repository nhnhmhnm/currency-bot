package org.example.backend.exchange.service

import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.enums.OutboxEventType
import org.example.backend.exchange.domain.CompletedOrderView
import org.example.backend.exchange.repository.CompletedOrderViewRepository
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.OutboxEventRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

data class CompletedOrderHistoryItem(
    val orderId: Long,
    val orderType: OrderType,
    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val completedAt: LocalDateTime
)

@Service
class CompletedOrderProjectionService(
    private val events: OutboxEventRepository,
    private val orders: ExchangeOrderRepository,
    private val views: CompletedOrderViewRepository
) {
    @Transactional
    fun apply(eventId: Long) {
        val event = events.findById(eventId).orElseThrow { IllegalStateException("completion event $eventId not found") }
        require(event.eventType == OutboxEventType.ORDER_COMPLETED)
        if (views.existsById(event.aggregateId)) return
        val order = orders.findById(event.aggregateId).orElseThrow { IllegalStateException("order ${event.aggregateId} not found") }
        check(order.status == OrderStatus.SETTLED) { "order ${order.id} is not settled" }
        views.saveAndFlush(
            CompletedOrderView(
                orderId = order.id!!,
                eventId = eventId,
                userId = order.userId,
                orderType = order.orderType,
                fromCurrencyId = order.fromCurrencyId,
                toCurrencyId = order.toCurrencyId,
                fromAmount = order.fromAmount,
                toAmount = order.toAmount,
                completedAt = requireNotNull(order.completedAt)
            )
        )
    }

    @Transactional(readOnly = true)
    fun history(userId: Long, limit: Int): List<CompletedOrderHistoryItem> =
        views.findByUserIdOrderByCompletedAtDesc(userId, PageRequest.of(0, limit.coerceIn(1, 50)))
            .map {
                CompletedOrderHistoryItem(
                    it.orderId, it.orderType, it.fromCurrencyId, it.toCurrencyId,
                    it.fromAmount, it.toAmount, it.completedAt
                )
            }
}
