package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeOrderResponse
import java.math.BigDecimal

interface ExchangeOrderService {
    fun getOrder(userId: Long, orderId: Long): ExchangeOrderResponse

    fun requestBuyOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String? = null,
        expectedRate: BigDecimal? = null
    ): ExchangeOrderResponse

    fun requestSellOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String? = null,
        expectedRate: BigDecimal? = null
    ): ExchangeOrderResponse

    fun requestArbitrageOrder(
        userId: Long,
        currencyCode: String,
        fromAmount: BigDecimal,
        idempotencyKey: String? = null,
        expectedRate: BigDecimal? = null,
        expectedCounterRate: BigDecimal? = null
    ): ExchangeOrderResponse

    fun processQueuedOrder(orderId: Long): ExchangeOrderResponse

    fun recordProcessingFailure(orderId: Long, failureId: String, reason: String, maxAttempts: Int): OrderRetryDecision

}
