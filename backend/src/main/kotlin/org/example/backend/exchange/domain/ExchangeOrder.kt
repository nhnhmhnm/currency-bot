package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.enums.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_order")
data class ExchangeOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "bank_id", nullable = false)
    val bankId: Long,

    @Column(name = "from_currency_id", nullable = false)
    val fromCurrency: Long,

    @Column(name = "to_currency_id", nullable = false)
    val toCurrency: Long,

    @Column(name = "from_amount", nullable = false)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount")
    var toAmount: BigDecimal? = null,

    @Column(name = "exchange_rate")
    var exchangeRate: BigDecimal? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "ordered_at")
    val orderedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "executed_at")
    var executedAt: LocalDateTime? = null
)
