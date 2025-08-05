package org.example.backend.exchange.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class TransactionDTO(
    val id: Long? = null,
    val userId: Long,
    val walletId: Long,
    val orderId: Long,
    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal?,
    val commissionRate: BigDecimal?,
    val commissionAmount: BigDecimal?,
    val commissionCurrencyId: Long?,
    val profit: BigDecimal?,
    val profitCurrencyId: Long?,
    val createdAt: LocalDateTime? = null
)

