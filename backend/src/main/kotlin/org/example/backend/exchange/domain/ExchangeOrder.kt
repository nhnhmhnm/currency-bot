package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.enums.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_order")
class ExchangeOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

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
    @Column(name = "status", nullable = false)
    var status: OrderStatus,

    @Column(name = "requested_at", nullable = false)
    val requestedAt: LocalDateTime

//    @Column(name = "executed_at")
//    var executedAt: LocalDateTime? = null
)
