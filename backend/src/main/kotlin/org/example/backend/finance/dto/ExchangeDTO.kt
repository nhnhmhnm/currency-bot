package org.example.backend.finance.dto

import org.example.backend.enums.ExchangeType
import java.math.BigDecimal

data class ExchangeDTO(
    val userId: Long,
    val bankId: Long,
    val currencyId: Long,
    val exchangeRate: BigDecimal,
    val amount: BigDecimal,
    val type: ExchangeType  // BUY or SELL
)
