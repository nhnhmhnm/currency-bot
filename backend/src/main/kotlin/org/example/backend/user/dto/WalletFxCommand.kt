package org.example.backend.user.dto

import org.example.backend.enums.WalletFxHistoryType
import java.math.BigDecimal

data class WalletFxCommand(
    val userId: Long,

    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,

    val commissionCurrencyId: Long?,
    val commissionRate: BigDecimal?,
    val commissionAmount: BigDecimal?,

    val type: WalletFxHistoryType
)
