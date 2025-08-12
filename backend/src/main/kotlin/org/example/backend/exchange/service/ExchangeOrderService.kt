package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeOrderResponse
import java.math.BigDecimal

interface ExchangeOrderService {
    // 원화 - 회사 수수료 -> 외화 + 은행 차익
    fun buyOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse

    // 외화 -> (원화 + 회사 수수료 + 회사 차익) + 은행 차익
    fun sellOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse

    // 차익 거래
    // 원화 -> 외화 + 은행 차익
    // 외화 -> (원화 + 회사 수수료 + 회사 차익) + 은행 차익
    fun arbitrageOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): Pair<ExchangeOrderResponse, ExchangeOrderResponse>
}
