package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeType
import java.math.BigDecimal

data class ExchangeDTO(
    val bankId: Long,
    val currencyId: Long,
    val exchangeRate: BigDecimal,
    val amount: BigDecimal,
    val type: ExchangeType  // BUY or SELL
)
