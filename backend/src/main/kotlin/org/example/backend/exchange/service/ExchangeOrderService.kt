package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeOrderDTO
import java.math.BigDecimal

interface ExchangeOrderService {
    fun buyOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO
    fun sellOrder(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeOrderDTO

    fun arbitrageOrder(userId: Long, currencyCode: String, amount: BigDecimal): Pair<ExchangeOrderDTO, ExchangeOrderDTO>
}