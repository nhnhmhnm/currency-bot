package org.example.backend.exchange.controller

import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.exchange.service.ExchangeService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/exchange")
class ExchangeController(
    private val exchangeService: ExchangeService
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

    @GetMapping("/arbitrage")
    fun arbitrage(@RequestParam currencyCode: String): Pair<ExchangeDTO, ExchangeDTO> {
        return exchangeService.getBestArbitrageRate(currencyCode)
    }

    // 실제 환전 버튼

}
