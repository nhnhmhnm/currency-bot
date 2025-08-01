package org.example.backend.finance.domain

import jakarta.persistence.*
import org.example.backend.enums.ExchangeLedgerType
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_ledger")
data class ExchangeLedger(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,

    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false)
    val balance: BigDecimal,

    @Column(name = "exchange_rate")
    val exchangeRate: BigDecimal,

    @Column(name = "commission_amount")
    val commissionAmount: BigDecimal,

    @Column(name = "commission_rate")
    val commissionRate: BigDecimal,

    @Column(name = "commission_currency_id")
    val commissionCurrencyId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ExchangeLedgerType,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
