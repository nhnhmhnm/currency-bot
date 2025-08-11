package org.example.backend.exchange.service

import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.OrderType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class OrderWorker(
    private val redisOrderQueue: RedisOrderQueue,
    private val exchangeOrderService: ExchangeOrderService
) {
    @Scheduled(fixedDelay = 500)
    fun processQueue() {
        var req = redisOrderQueue.dequeue()

        while (req != null) {
            try {
                when (req.type) {
                    OrderType.BUY -> exchangeOrderService.buyOrder(req.userId, req.currencyCode, req.amount, false)
                    OrderType.SELL -> exchangeOrderService.sellOrder(req.userId, req.currencyCode, req.amount, false)
                    OrderType.ARBITRAGE -> exchangeOrderService.arbitrageOrder(req.userId, req.currencyCode, req.amount, true)
                }
            } catch (e: Exception) {
                // 실패시 DeadLetterQueue, 재시도, 로그 등
            }
            req = redisOrderQueue.dequeue()
        }
    }
}
