package org.example.backend.exchange.service

import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.enums.OrderType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class OrderWorker(
    private val queue: RedisOrderQueue,
    private val exchangeOrderService: ExchangeOrderService
) {
    @Scheduled(fixedDelay = 500)
    fun processQueue() {
        var req = queue.dequeue()
        while (req != null) {
            try {
                when (req.type) {
                    OrderType.BUY -> exchangeOrderService.buyOrder(req.userId, req.currencyCode, req.amount, false)
                    OrderType.SELL -> exchangeOrderService.sellOrder(req.userId, req.currencyCode, req.amount, false)
                    OrderType.ARBITRAGE -> exchangeOrderService.arbitrageOrder(req.userId, req.currencyCode, req.amount, true)
                }
            } catch (e: Exception) {
                // 주문 처리 중 예외 발생 시 로깅하고 다음 요청으로 넘어감
//                 logger.error("Unexpected error while processing order request: $req", e)
            }
            req = queue.dequeue()
        }
    }
}
