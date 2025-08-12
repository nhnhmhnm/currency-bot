package org.example.backend.exchange.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.exchange.dto.ExchangeOrderRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/exchange")
class ExchangeOrderController(
    private val redisOrderQueue: RedisOrderQueue
) {
    // 1. 매수 주문
    @PostMapping("/buy")
    fun buyOrder(@AuthenticationPrincipal user: UserPrincipal,
                 @RequestParam currencyCode: String,
                 @RequestParam fromAmount: BigDecimal): ResponseEntity<String> {
        // 주문 요청을 큐에 적재
        val q = ExchangeOrderRequest(
            userId = user.id,
            fromCurrency = "KRW",
            toCurrency = currencyCode.uppercase(),
            fromAmount = fromAmount,
            isArbitrage = false
        )

        redisOrderQueue.enqueue(q)

        return ResponseEntity.ok("매수 주문이 접수되었습니다. (실제 처리는 순차적으로 진행됩니다)")
    }

    // 2. 매도 주문
    @PostMapping("/sell")
    fun sellOrder(@AuthenticationPrincipal user: UserPrincipal,
                  @RequestParam currencyCode: String,
                  @RequestParam fromAmount: BigDecimal): ResponseEntity<String> {
        // 주문 요청을 큐에 적재
        val q = ExchangeOrderRequest(
            userId = user.id,
            fromCurrency = currencyCode.uppercase(),
            toCurrency = "KRW",
            fromAmount = fromAmount,
            isArbitrage = false
        )
        redisOrderQueue.enqueue(q)

        return ResponseEntity.ok("매도 주문이 접수되었습니다. (실제 처리는 순차적으로 진행됩니다)")
    }

    // 3. 차익거래 주문
    @PostMapping("/arbitrage")
    fun arbitrageOrder(@AuthenticationPrincipal user: UserPrincipal,
                       @RequestParam currencyCode: String,
                       @RequestParam fromAmount: BigDecimal): ResponseEntity<String> {
        // 주문 요청을 큐에 적재
        val q = ExchangeOrderRequest(
            userId = user.id,
            fromCurrency = "KRW",
            toCurrency = currencyCode.uppercase(),
            fromAmount = fromAmount,
            isArbitrage = true
        )
        redisOrderQueue.enqueue(q)

        return ResponseEntity.ok("차익거래 주문이 접수되었습니다. (실제 처리는 순차적으로 진행됩니다)")
    }
}
