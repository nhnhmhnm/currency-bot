package org.example.backend.exchange.controller

import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.exchange.service.ExchangeService
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/exchange")
class ExchangeController(
    private val exchangeService: ExchangeService
) {
    // 테스트용 get
    @GetMapping("/buy")
    fun buy(@RequestParam currencyCode: String, @RequestParam amount: BigDecimal): ExchangeDTO {
        return exchangeService.getBestBuyRate(currencyCode, amount)
    }

    @GetMapping("/sell")
    fun sell(@RequestParam currencyCode: String, @RequestParam amount: BigDecimal): ExchangeDTO {
        return exchangeService.getBestSellRate(currencyCode, amount)
    }

    @GetMapping("/arbitrage")
    fun arbitrage(@RequestParam currencyCode: String, @RequestParam amount: BigDecimal): Pair<ExchangeDTO, ExchangeDTO> {
        return exchangeService.getBestArbitrageRate(currencyCode, amount)
    }
}
