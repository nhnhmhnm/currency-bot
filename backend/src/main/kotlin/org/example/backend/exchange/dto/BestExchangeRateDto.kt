package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeType
import java.math.BigDecimal

data class BestExchangeRateDto(
    val bankId: Long,
    val currencyId: Long,
    val bestRate: BigDecimal,
    val type: ExchangeType
)
