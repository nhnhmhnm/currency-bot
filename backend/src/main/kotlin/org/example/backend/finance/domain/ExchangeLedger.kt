package org.example.backend.finance.domain

import jakarta.persistence.*
import org.example.backend.enums.ExchangeLedgerType
import org.example.backend.user.domain.User
import org.example.backend.user.domain.Wallet
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "exchange_ledger")
data class ExchangeLedger(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    val wallet: Wallet,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    val currency: Currency,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false)
    val balance: BigDecimal,

    @Column(name = "exchange_rate")
    val exchangeRate: BigDecimal? = null,

    @Column(name = "commission_amount")
    val commissionAmount: BigDecimal? = null,

    @Column(name = "commission_rate")
    val commissionRate: BigDecimal? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_currency_id")
    val commissionCurrency: Currency? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ExchangeLedgerType,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
