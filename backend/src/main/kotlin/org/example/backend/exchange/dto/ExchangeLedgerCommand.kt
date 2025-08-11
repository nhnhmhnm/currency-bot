package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeLedgerType
import java.math.BigDecimal

data class ExchangeLedgerCommand(
    val userId: Long,

    val fromCurrencyId: Long,
    val toCurrencyId: Long,
    val fromAmount: BigDecimal,
    val toAmount: BigDecimal,
    val exchangeRate: BigDecimal,

    val commissionCurrencyId: Long? = null,
    val commissionAmount: BigDecimal? = null,

    val type: ExchangeLedgerType
)
