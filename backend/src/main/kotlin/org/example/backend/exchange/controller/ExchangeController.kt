package org.example.backend.exchange.controller

import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.exchange.service.ExchangeService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/exchange")
class ExchangeController(
    private val exchangeService: ExchangeService
) {
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
}
