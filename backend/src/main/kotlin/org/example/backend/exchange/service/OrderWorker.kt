package org.example.backend.exchange.service

import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class OrderWorker(
    private val queue: RedisOrderQueue,
    private val exchangeOrderService: ExchangeOrderService
) {
    private val log = LoggerFactory.getLogger(OrderWorker::class.java)

    @Scheduled(fixedDelay = 500)
    fun processQueue() {
        recoverExpiredMessages()

        var pulled = 0
        var message = queue.claim()

        while (message != null) {
            try {
                val res = exchangeOrderService.processQueuedOrder(message.orderId)
                logOrder(res)
                queue.acknowledge(message)
            } catch (e: Exception) {
                val attempts = queue.reject(message, MAX_ATTEMPTS)
                log.error(
                    "order processing failed: orderId={}, attempt={}/{}",
                    message.orderId,
                    attempts,
                    MAX_ATTEMPTS,
                    e
                )
                if (attempts >= MAX_ATTEMPTS) {
                    markFailed(message.orderId, e.message ?: e::class.simpleName.orEmpty())
                } else {
                    return
                }
            }

            pulled++
            message = queue.claim()
        }

        if (pulled == 0) {
            log.debug("queue empty")
        }
    }

    private fun recoverExpiredMessages() {
        queue.recoverExpired(MAX_ATTEMPTS, LEASE_TIMEOUT).forEach { message ->
            markFailed(message.orderId, "worker lease expired after $MAX_ATTEMPTS attempts")
        }
    }

    private fun markFailed(orderId: Long, reason: String) {
        exchangeOrderService.markFailed(
            orderId = orderId,
            stage = "ORDER_WORKER",
            reason = reason
        )
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

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val LEASE_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
