package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeOrderDTO
import java.math.BigDecimal

interface ExchangeOrderService {
    fun BuyOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO
    fun SellOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO
    fun ArbitrageOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO
}