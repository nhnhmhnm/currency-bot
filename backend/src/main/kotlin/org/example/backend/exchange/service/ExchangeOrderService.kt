package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeOrderResponse
import java.math.BigDecimal

interface ExchangeOrderService {
    fun buyOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse
    fun sellOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse

    fun arbitrageOrder(userId: Long, currencyCode: String, amount: BigDecimal, isArbitrage: Boolean): Pair<ExchangeOrderResponse, ExchangeOrderResponse>
}