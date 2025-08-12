package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeLedgerType
import java.math.BigDecimal
import java.time.LocalDateTime

data class ExchangeLedgerResponse(
    val id: Long,
    val userId: Long,
    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,
    val commissionCurrencyId: Long,
    val commissionRate: BigDecimal,
    val commissionAmount: BigDecimal,
    val type: ExchangeLedgerType,
    val createdAt: LocalDateTime
)
