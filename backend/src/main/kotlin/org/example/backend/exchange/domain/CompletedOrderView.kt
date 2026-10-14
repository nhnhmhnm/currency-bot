package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.enums.OrderType
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "completed_order_view", indexes = [Index(name = "idx_completed_order_user_time", columnList = "user_id,completed_at")])
class CompletedOrderView(
    @Id
    @Column(name = "order_id")
    val orderId: Long,
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    val orderType: OrderType,
    @Column(name = "from_currency_id", nullable = false)
    val fromCurrencyId: Long,
    @Column(name = "to_currency_id", nullable = false)
    val toCurrencyId: Long,
    @Column(name = "from_amount", nullable = false, precision = 24, scale = 8)
    val fromAmount: BigDecimal,
    @Column(name = "to_amount", nullable = false, precision = 24, scale = 8)
    val toAmount: BigDecimal,
    @Column(name = "completed_at", nullable = false)
    val completedAt: LocalDateTime
)
