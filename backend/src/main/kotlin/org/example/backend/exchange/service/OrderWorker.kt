package org.example.backend.exchange.service

import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class OrderWorker(
    private val queue: RedisOrderQueue,
    private val exchangeOrderService: ExchangeOrderService
) {
    private val log = LoggerFactory.getLogger(OrderWorker::class.java)

    @Scheduled(fixedDelay = 500)
    fun processQueue() {
        var pulled = 0
        var orderId = queue.dequeue()

        while (orderId != null) {
            try {
                val res = exchangeOrderService.processQueuedOrder(orderId)
                logOrder(res)
            } catch (e: Exception) {
                log.error("order processing failed: orderId={}", orderId, e)
                exchangeOrderService.markFailed(
                    orderId = orderId,
                    stage = "ORDER_WORKER",
                    reason = e.message ?: e::class.simpleName.orEmpty()
                )
            }

            pulled++
            orderId = queue.dequeue()
        }

        if (pulled == 0) {
            log.debug("queue empty")
        }
    }

    private fun logOrder(res: ExchangeOrderResponse) {
        log.info(
            "order processed: id={}, userId={}, status={}, fromCurrencyId={}, toCurrencyId={}, fromAmount={}, toAmount={}",
            res.id,
            res.userId,
            res.status,
            res.fromCurrencyId,
            res.toCurrencyId,
            res.fromAmount,
            res.toAmount
        )
    }
}
