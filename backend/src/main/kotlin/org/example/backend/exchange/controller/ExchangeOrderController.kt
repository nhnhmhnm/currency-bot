package org.example.backend.exchange.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.exchange.service.ExchangeOrderService
import org.example.backend.exchange.dto.ExchangeOrderResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/exchange")
class ExchangeOrderController(
    private val exchangeOrderService: ExchangeOrderService
) {
    @GetMapping("/orders/{orderId}")
    fun getOrder(@AuthenticationPrincipal user: UserPrincipal, @PathVariable orderId: Long): ExchangeOrderResponse =
        exchangeOrderService.getOrder(user.id, orderId)

    @PostMapping("/buy")
    fun buyOrder(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestParam toCurrencyCode: String,
        @RequestParam fromAmount: BigDecimal,
        @RequestParam(required = false) expectedRate: BigDecimal?,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
    ): ResponseEntity<String> {
        val order = exchangeOrderService.requestBuyOrder(
            userId = user.id,
            currencyCode = toCurrencyCode.uppercase(),
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey,
            expectedRate = expectedRate
        )
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body("BUY order accepted. orderId=${order.id}")
    }

    @PostMapping("/sell")
    fun sellOrder(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestParam fromCurrencyCode: String,
        @RequestParam fromAmount: BigDecimal,
        @RequestParam(required = false) expectedRate: BigDecimal?,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
    ): ResponseEntity<String> {
        val order = exchangeOrderService.requestSellOrder(
            userId = user.id,
            currencyCode = fromCurrencyCode.uppercase(),
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey,
            expectedRate = expectedRate
        )
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body("SELL order accepted. orderId=${order.id}")
    }

    @PostMapping("/arbitrage")
    fun arbitrageOrder(
        @AuthenticationPrincipal user: UserPrincipal,
        @RequestParam currencyCode: String,
        @RequestParam fromAmount: BigDecimal,
        @RequestParam(required = false) expectedRate: BigDecimal?,
        @RequestParam(required = false) expectedCounterRate: BigDecimal?,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?
    ): ResponseEntity<String> {
        val order = exchangeOrderService.requestArbitrageOrder(
            userId = user.id,
            currencyCode = currencyCode.uppercase(),
            fromAmount = fromAmount,
            idempotencyKey = idempotencyKey,
            expectedRate = expectedRate,
            expectedCounterRate = expectedCounterRate
        )
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body("ARBITRAGE order accepted. orderId=${order.id}")
    }
}
