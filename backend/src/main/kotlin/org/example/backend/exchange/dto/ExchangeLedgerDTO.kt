package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeLedgerType
import java.math.BigDecimal
import java.time.LocalDateTime

data class ExchangeLedgerDTO(
    val id: Long? = null,
    val userId: Long,
//    val walletId: Long,

    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,

    val commissionCurrencyId: Long?,
    val commissionAmount: BigDecimal?,

    val type: ExchangeLedgerType
)
