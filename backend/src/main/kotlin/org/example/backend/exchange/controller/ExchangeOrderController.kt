package org.example.backend.exchange.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.exchange.dto.ExchangeOrderRequest
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
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
                 @RequestParam toCurrencyCode: String,
                 @RequestParam fromAmount: BigDecimal): ResponseEntity<String> {
        // 주문 요청을 큐에 적재
        val q = ExchangeOrderRequest(
            userId = user.id,
            fromCurrency = "KRW",
            toCurrency = toCurrencyCode.uppercase(),
            fromAmount = fromAmount,
            isArbitrage = false
        )
        redisOrderQueue.enqueue(q)

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body("${toCurrencyCode.uppercase()} 구매 주문이 접수되었습니다.")
    }

    // 2. 매도 주문
    @PostMapping("/sell")
    fun sellOrder(@AuthenticationPrincipal user: UserPrincipal,
                  @RequestParam fromCurrencyCode: String,
                  @RequestParam fromAmount: BigDecimal): ResponseEntity<String> {
        // 주문 요청을 큐에 적재
        val q = ExchangeOrderRequest(
            userId = user.id,
            fromCurrency = fromCurrencyCode.uppercase(),
            toCurrency = "KRW",
            fromAmount = fromAmount,
            isArbitrage = false
        )
        redisOrderQueue.enqueue(q)

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body("${fromCurrencyCode.uppercase()} 판매 주문이 접수되었습니다.")
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

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body("${currencyCode.uppercase()} 차익거래 주문이 접수되었습니다.")
    }
}
