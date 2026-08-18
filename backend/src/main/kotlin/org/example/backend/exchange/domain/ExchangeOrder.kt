package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.enums.OrderType
import org.example.backend.enums.OrderStatus
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_order")
class ExchangeOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "bank_id", nullable = false)
    var bankId: Long,

    @Column(name = "from_currency_id", nullable = false)
    val fromCurrencyId: Long,

    @Column(name = "to_currency_id", nullable = false)
    val toCurrencyId: Long,

    @Column(name = "from_amount", nullable = false)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount", nullable = false)
    var toAmount: BigDecimal,

    @Column(name = "exchange_rate", nullable = false)
    var exchangeRate: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    var orderType: OrderType = OrderType.BUY,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus,

    @Column(name = "idempotency_key")
    val idempotencyKey: String? = null,

    @Column(name = "failure_stage")
    var failureStage: String? = null,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    val requestedAt: LocalDateTime? = LocalDateTime.now()
)
