package org.example.backend.exchange.dto

import java.math.BigDecimal

data class TransactionDTO(
    val userId: Long,
    val walletId: Long,
    val orderId: Long? = null,

    val fromCurrencyId: Long,
    val toCurrencyId: Long,

    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,

    val exchangeRate: BigDecimal? = null,

    val commissionRate: BigDecimal? = null,
    val commissionAmount: BigDecimal? = null,
    val commissionCurrencyId: Long? = null,

    val profit: BigDecimal? = null,
    val profitCurrencyId: Long? = null
)
