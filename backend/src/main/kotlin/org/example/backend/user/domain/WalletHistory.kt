package org.example.backend.user.domain

import jakarta.persistence.*
import org.example.backend.enums.WalletHistoryType
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet_history")
data class WalletHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "wallet_id")
    val walletId: Long,

    @Column(name = "currency_id")
    val currencyId: Long,

    @Column(name = "order_id")
    val orderId: Long,

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
