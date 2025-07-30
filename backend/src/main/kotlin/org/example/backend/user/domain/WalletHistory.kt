package org.example.backend.user.domain

import jakarta.persistence.*
import org.example.backend.finance.domain.Currency
import org.example.backend.enums.WalletHistoryType
import org.example.backend.exchange.domain.ExchangeOrder
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet_history")
data class WalletHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    val wallet: Wallet,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    val currency: Currency,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    val order: ExchangeOrder? = null,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: WalletHistoryType,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
