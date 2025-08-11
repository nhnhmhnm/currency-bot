package org.example.backend.exchange.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transaction")
class Transaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

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
    val toAmount: BigDecimal,

    @Column(name = "exchange_rate", nullable = false)
    val exchangeRate: BigDecimal,

    // commission : 유저가 앱에서 환전을 할 때 발생하는 수수료 (회사가 받는 이익)
    @Column(name = "commission_currency_id", nullable = true)
    val commissionCurrencyId: Long? = null,

    @Column(name = "commission_amount", nullable = true)
    val commissionAmount: BigDecimal? = null,

    // profit : 환전으로 인해 소수점 이하가 발생하는 경우, 그 차액
    @Column(name = "profit_currency_id", nullable = true)
    val profitCurrencyId: Long? = null,

    @Column(name = "profit", nullable = true)
    val profit: BigDecimal? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime? = null
)
