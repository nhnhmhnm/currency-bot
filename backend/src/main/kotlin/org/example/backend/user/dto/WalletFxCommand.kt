package org.example.backend.user.dto

import org.example.backend.enums.WalletFxHistoryType
import java.math.BigDecimal
import java.time.LocalDateTime

data class WalletFxCommand(
    val userId: Long,
    val walletId: Long,
    val currencyId: Long,
    val orderId: Long? = null,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val type: WalletFxHistoryType,
    val executedAt: LocalDateTime = LocalDateTime.now()
)
