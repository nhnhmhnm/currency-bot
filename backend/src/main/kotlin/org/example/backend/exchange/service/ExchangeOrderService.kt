package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeOrderResponse
import java.math.BigDecimal

interface ExchangeOrderService {
    fun requestBuyOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String? = null
    ): ExchangeOrderResponse

    fun requestSellOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String? = null
    ): ExchangeOrderResponse

    fun requestArbitrageOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String? = null
    ): ExchangeOrderResponse

    fun processQueuedOrder(orderId: Long): ExchangeOrderResponse

    fun markFailed(orderId: Long, stage: String, reason: String)

    fun buyOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse

    fun sellOrder(userId: Long, currencyCode: String, fromAmount: BigDecimal, isArbitrage: Boolean): ExchangeOrderResponse

    fun arbitrageOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        isArbitrage: Boolean
    ): Pair<ExchangeOrderResponse, ExchangeOrderResponse>
}
