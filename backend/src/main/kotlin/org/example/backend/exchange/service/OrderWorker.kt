package org.example.backend.exchange.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private val mapper = jacksonObjectMapper()

    @Scheduled(fixedDelay = 500)
    fun processQueue() {
        var processedCount = 0
        var orderRequest = queue.dequeue()

        while (orderRequest != null) {
            log.info("dequeued orderRequest: {}", orderRequest)

            try {
                when {
                    orderRequest.isArbitrage -> {
                        val (buyOrderResponse, sellOrderResponse) = exchangeOrderService.arbitrageOrder(
                            userId = orderRequest.userId,
                            currencyCode = orderRequest.toCurrency,
                            fromAmount = orderRequest.fromAmount,
                            isArbitrage = true
                        )
                        logOrder("ARBITRAGE_BUY", buyOrderResponse)
                        logOrder("ARBITRAGE_SELL", sellOrderResponse)
                    }

                    orderRequest.toCurrency.equals("KRW", ignoreCase = true) -> {
                        val sellOrderResponse = exchangeOrderService.sellOrder(
                            userId = orderRequest.userId,
                            currencyCode = orderRequest.fromCurrency,
                            fromAmount = orderRequest.fromAmount,
                            isArbitrage = false
                        )
                        logOrder("SELL", sellOrderResponse)
                    }

                    else -> {
                        val buyOrderResponse = exchangeOrderService.buyOrder(
                            userId = orderRequest.userId,
                            currencyCode = orderRequest.toCurrency,
                            fromAmount = orderRequest.fromAmount,
                            isArbitrage = false
                        )
                        logOrder("BUY", buyOrderResponse)
                    }
                }
            } catch (exception: Exception) {
                log.error("order_processing_failed: ${mapper.writeValueAsString(orderRequest)}", exception)
            }

            processedCount++
            orderRequest = queue.dequeue()
        }

        if (processedCount == 0) {
            log.debug("queue empty")
        }
    }

    private fun logOrder(orderType: String, orderResponse: ExchangeOrderResponse) {
        val payload = mapOf(
            "event" to "order_processed",
            "type" to orderType,
            "order" to mapOf(
                "id" to orderResponse.id,
                "userId" to orderResponse.userId,
                "bankId" to orderResponse.bankId,
                "fromCurrencyId" to orderResponse.fromCurrencyId,
                "toCurrencyId" to orderResponse.toCurrencyId,
                "fromAmount" to orderResponse.fromAmount,
                "toAmount" to orderResponse.toAmount,
                "exchangeRate" to orderResponse.exchangeRate,
                "status" to orderResponse.status.name,
                "requestedAt" to orderResponse.requestedAt.toString()
            )
        )
        log.info(mapper.writeValueAsString(payload))
    }
}
