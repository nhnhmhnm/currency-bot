package org.example.backend.finance.controller

import org.example.backend.finance.dto.ExchangeDTO
import org.example.backend.finance.service.ExchangeService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/exchange")
class ExchangeController(
    private val exchangeService: ExchangeService
) {

    @PostMapping("/buy")
    fun buy(
        @RequestParam currencyCode: String,
        @RequestParam amount: BigDecimal
    ): ResponseEntity<ExchangeDTO> {
        val userId = SecurityContextHolder.getContext().authentication.name.toLong()
        val result = exchangeService.getBestBuyRate(userId, currencyCode, amount)

        return ResponseEntity.ok(result)
    }

    @PostMapping("/sell")
    fun sell(
        @RequestParam currencyCode: String,
        @RequestParam amount: BigDecimal
    ): ResponseEntity<ExchangeDTO> {
        val userId = SecurityContextHolder.getContext().authentication.name.toLong()
        val result = exchangeService.getBestSellRate(userId, currencyCode, amount)

        return ResponseEntity.ok(result)
    }

    @PostMapping("/arbitrage")
    fun arbitrage(
        @RequestParam currencyCode: String,
        @RequestParam amount: BigDecimal
    ): ResponseEntity<Pair<ExchangeDTO, ExchangeDTO>> {
        val userId = SecurityContextHolder.getContext().authentication.name.toLong()
        val result = exchangeService.getBestBuySellRate(userId, currencyCode, amount)

        return ResponseEntity.ok(result)
    }
}
