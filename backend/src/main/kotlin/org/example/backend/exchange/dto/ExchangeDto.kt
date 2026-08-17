package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeType
import java.math.BigDecimal

data class ExchangeDto(
    val bankId: Long,
    val currencyId: Long,
    val bestRate: BigDecimal,
    val type: ExchangeType
)
