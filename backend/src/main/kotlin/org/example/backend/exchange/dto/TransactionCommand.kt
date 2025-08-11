package org.example.backend.exchange.dto

import java.math.BigDecimal

data class TransactionCommand(
    val userId: Long,
//    val walletId: Long,

    val orderId: Long,

    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,

    val commissionCurrencyId: Long?,
    val commissionAmount: BigDecimal? = null,
    val profitCurrencyId: Long?,
    val profit: BigDecimal? = null
)
