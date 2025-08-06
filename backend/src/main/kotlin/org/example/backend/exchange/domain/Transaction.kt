package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.example.backend.finance.domain.Currency
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction")
class Transaction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "from_currency_id", nullable = false)
    val fromCurrencyId: Long,

    @Column(name = "to_currency_id", nullable = false)
    val toCurrencyId: Long,

    @Column(name = "from_amount", nullable = false)
    val fromAmount: BigDecimal,

    @Column(name = "to_amount", nullable = false)
    val toAmount: BigDecimal? = null,

    @Column(name = "exchange_rate", nullable = false)
    val exchangeRate: BigDecimal? = null,

    @Column(name = "commission_rate", nullable = false)
    val commissionRate: BigDecimal? = null,

    @Column(name = "commission_amount", nullable = false)
    val commissionAmount: BigDecimal? = null,

    @Column(name = "commission_currency_id", nullable = false)
    val commissionCurrencyId: Long? = null,

    @Column(name = "profit", nullable = false)
    val profit: BigDecimal? = null,

    @Column(name = "profit_currency_id", nullable = false)
    val profitCurrencyId: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime? = LocalDateTime.now()
)
