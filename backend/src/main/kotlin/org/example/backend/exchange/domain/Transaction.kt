package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.finance.domain.Currency
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction")
data class Transaction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,

    @Column(name = "order_id")
    val orderId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_currency_id")
    val fromCurrency: Currency,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_currency_id")
    val toCurrency: Currency,

    @Column(name = "from_amount", nullable = false)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount", nullable = false)
    val toAmount: BigDecimal,

    @Column(name = "exchange_rate")
    val exchangeRate: BigDecimal? = null,

    @Column(name = "commission_rate")
    val commissionRate: BigDecimal? = null,

    @Column(name = "commission_amount")
    val commissionAmount: BigDecimal? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_currency_id")
    val commissionCurrency: Currency? = null,

    @Column(name = "profit")
    val profit: BigDecimal? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profit_currency_id")
    val profitCurrency: Currency? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
