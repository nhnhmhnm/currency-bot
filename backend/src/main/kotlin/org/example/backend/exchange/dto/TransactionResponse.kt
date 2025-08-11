package org.example.backend.exchange.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class TransactionResponse(
    val id: Long,
    val userId: Long,
    val walletId: Long,
    val orderId: Long,
    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,
    val commissionCurrencyId: Long,
    val commissionAmount: BigDecimal,
    val profitCurrencyId: Long,
    val profit: BigDecimal,
    val createdAt: LocalDateTime
)
