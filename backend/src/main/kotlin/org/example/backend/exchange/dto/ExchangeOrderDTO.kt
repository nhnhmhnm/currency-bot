package org.example.backend.exchange.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ExchangeOrderDTO(
    val id: Long? = null,
    val userId: Long,
    val bankId: Long,
    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal?,
    val exchangeRate: BigDecimal,
    val exchangeRateId: Long,
    val status: String,
    val orderedAt: LocalDateTime? = null,
    val executedAt: LocalDateTime? = null
)
