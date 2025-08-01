package org.example.backend.user.domain

import jakarta.persistence.*
import org.example.backend.enums.WalletHistoryType
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet_history")
class WalletHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "wallet_id", nullable = false)
    val walletId: Long,

    @Column(name = "currency_id", nullable = false)
    val currencyId: Long,

    @Column(name = "order_id")
    val orderId: Long? = null,

    @Column(name = "amount", nullable = false)
    val amount: BigDecimal,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: WalletHistoryType,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
