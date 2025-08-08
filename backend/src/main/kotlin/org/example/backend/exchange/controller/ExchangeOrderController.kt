package org.example.backend.exchange.controller

import org.example.backend.common.util.RedisOrderQueue
import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.exchange.dto.ExchangeOrderDTO
import org.example.backend.exchange.dto.ExchangeOrderRequest
import org.example.backend.exchange.dto.OrderType
import org.example.backend.exchange.service.ExchangeOrderService
import org.example.backend.exchange.service.ExchangeService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.nio.file.attribute.UserPrincipal

@RestController
@RequestMapping("/api/exchange")
class ExchangeOrderController(
    private val exchangeService: ExchangeService,
    private val exchangeOrderService: ExchangeOrderService,
    private val redisOrderQueue: RedisOrderQueue

) {
    // 임시 get 메소드로 환율 조회
    @GetMapping("/buy")
    fun buy(@RequestParam currencyCode: String): ExchangeDTO {
        return exchangeService.getBestBuyRate(currencyCode)
    }

    @GetMapping("/sell")
    fun sell(@RequestParam currencyCode: String): ExchangeDTO {
        return exchangeService.getBestSellRate(currencyCode)
    }

//    @GetMapping("/arbitrage")
//    fun arbitrage(@RequestParam currencyCode: String): Pair<ExchangeDTO, ExchangeDTO> {
//        return exchangeService.getBestArbitrageRate(currencyCode)
//    }

    // 실제 환전 버튼
    // 1. 매수 주문
//    @PostMapping("/buy")
//    fun buyOrder(@AuthenticationPrincipal user: UserPrincipal,
//                 @RequestParam currencyCode: String,
//                 @RequestParam amount: BigDecimal): ResponseEntity<String> {
//
//        // 주문 요청을 큐에 적재
//        val req = ExchangeOrderRequest(user.id, OrderType.BUY, currencyCode, amount)
//        redisOrderQueue.enqueue(req)
//
//        return ResponseEntity.ok("매수 주문이 접수되었습니다. (실제 처리는 순차적으로 진행됩니다)")
//    }
//
//    // 2. 매도 주문
//    @PostMapping("/sell")
//    fun sellOrder(@AuthenticationPrincipal user: UserPrincipal,
//                  @RequestParam currencyCode: String,
//                  @RequestParam amount: BigDecimal): ResponseEntity<String> {
//
//        val req = ExchangeOrderRequest(user.id, OrderType.SELL, currencyCode, amount)
//        redisOrderQueue.enqueue(req)
//
//        return ResponseEntity.ok("매도 주문이 접수되었습니다. (실제 처리는 순차적으로 진행됩니다)")
//    }
//
//    // 3. 차익거래 주문
//    @PostMapping("/arbitrage")
//    fun arbitrageOrder(@AuthenticationPrincipal user: UserPrincipal,
//                       @RequestParam currencyCode: String,
//                       @RequestParam amount: BigDecimal): ResponseEntity<String> {
//
//        val req = ExchangeOrderRequest(user.id, OrderType.ARBITRAGE, currencyCode, amount)
//        redisOrderQueue.enqueue(req)
//
//        return ResponseEntity.ok("차익거래 주문이 접수되었습니다. (실제 처리는 순차적으로 진행됩니다)")
//    }
}
