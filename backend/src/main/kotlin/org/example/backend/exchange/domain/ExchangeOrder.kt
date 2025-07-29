package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.user.domain.User
import org.example.backend.finance.domain.Bank
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.domain.ExchangeRate
import org.example.backend.enums.OrderStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_order")
data class ExchangeOrder(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    val bank: Bank,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_currency_id")
    val fromCurrency: Currency,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_currency_id")
    val toCurrency: Currency,

    @Column(name = "from_amount", nullable = false)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount")
    var toAmount: BigDecimal? = null,

    @Column(name = "exchange_rate")
    var exchangeRate: BigDecimal? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_rate_id")
    val exchangeRateEntity: ExchangeRate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "ordered_at")
    val orderedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "executed_at")
    var executedAt: LocalDateTime? = null
)
