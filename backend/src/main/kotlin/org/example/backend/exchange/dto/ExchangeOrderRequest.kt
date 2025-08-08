package org.example.backend.exchange.dto

import java.math.BigDecimal

data class ExchangeOrderRequest(
    val userId: Long,
    val type: OrderType,           // BUY, SELL, ARBITRAGE
    val currencyCode: String,
    val amount: BigDecimal
)

enum class OrderType { BUY, SELL, ARBITRAGE }