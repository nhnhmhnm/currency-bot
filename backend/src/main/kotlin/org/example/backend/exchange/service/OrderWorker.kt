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
        var pulled = 0
        var req = queue.dequeue()

        while (req != null) {
            log.info("dequeued req: {}", req)  // 여기서 실제로 빠지는지 확인
            try {
                when {
                    // 차익거래: KRW -> (toCurrency) -> KRW
                    req.isArbitrage -> {
                        val (buyRes, sellRes) = exchangeOrderService.arbitrageOrder(
                            userId = req.userId,
                            currencyCode = req.toCurrency,  // 중요: 대상 외화 코드
                            fromAmount = req.fromAmount,
                            isArbitrage = true
                        )
                        logOrder("ARBITRAGE_BUY", buyRes)
                        logOrder("ARBITRAGE_SELL", sellRes)
                    }

                    // 매도(외화 -> KRW)
                    req.toCurrency.equals("KRW", ignoreCase = true) -> {
                        val res = exchangeOrderService.sellOrder(
                            userId = req.userId,
                            currencyCode = req.fromCurrency, // 외화 코드
                            fromAmount = req.fromAmount,     // 외화 금액
                            isArbitrage = false
                        )
                        logOrder("SELL", res)
                    }

                    // 매수(KRW -> 외화)
                    else -> {
                        val res = exchangeOrderService.buyOrder(
                            userId = req.userId,
                            currencyCode = req.toCurrency,   // 외화 코드
                            fromAmount = req.fromAmount,     // KRW 금액
                            isArbitrage = false
                        )
                        logOrder("BUY", res)
                    }
                }
            } catch (e: Exception) {
                // 주문 처리 중 예외 발생 시 로깅하고 다음 요청으로 넘어감
                log.error("order_processing_failed: ${mapper.writeValueAsString(req)}", e)
            }
            pulled++
            req = queue.dequeue()
        }
        if (pulled == 0) log.debug("queue empty")
    }

    private fun logOrder(type: String, res: ExchangeOrderResponse) {
        // 구조화 로그: 이벤트 메타 + 응답 필드
        val payload = mapOf(
            "event" to "order_processed",
            "type" to type,
            "order" to mapOf(
                "id" to res.id,
                "userId" to res.userId,
                "bankId" to res.bankId,
                "fromCurrencyId" to res.fromCurrencyId,
                "toCurrencyId" to res.toCurrencyId,
                "fromAmount" to res.fromAmount,
                "toAmount" to res.toAmount,
                "exchangeRate" to res.exchangeRate,
                "status" to res.status.name,
                "requestedAt" to res.requestedAt
            )
        )
        log.info(mapper.writeValueAsString(payload))
    }
}
