package org.example.backend.exchange.dto

import java.math.BigDecimal

data class ExchangeOrderRequest(
    val userId: Long,
    val fromCurrency: String,
    val toCurrency: String,
    val fromAmount: BigDecimal,
    val isArbitrage: Boolean = false
)
