package org.example.backend.exchange.dto

import org.example.backend.enums.OrderType
import java.math.BigDecimal

data class ExchangeOrderRequest(
    val userId: Long,
    val type: OrderType,           // BUY, SELL, ARBITRAGE
    val currencyCode: String,
    val amount: BigDecimal
)
