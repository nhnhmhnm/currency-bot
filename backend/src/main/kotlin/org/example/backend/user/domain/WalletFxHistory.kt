package org.example.backend.user.domain

import jakarta.persistence.*
import org.example.backend.enums.WalletFxHistoryType
import org.example.backend.finance.domain.Currency
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "wallet_fx_history")
class WalletFxHistory(
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
    val type: WalletFxHistoryType,

    @Column(name = "executed_at")
    val executedAt: LocalDateTime = LocalDateTime.now(),

    // 선택적(읽기 전용) 연관: 필요하면 유지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", insertable = false, updatable = false)
    val wallet: Wallet? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    val currency: Currency? = null
)
