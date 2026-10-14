package org.example.backend.exchange.controller

import org.example.backend.auth.dto.UserPrincipal
import org.example.backend.exchange.service.ExchangeDashboardService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/exchange")
class ExchangeDashboardController(private val dashboard: ExchangeDashboardService) {
    @GetMapping("/rates")
    fun rates(@RequestParam(defaultValue = "USD") currency: String) = dashboard.rates(currency.uppercase(Locale.ROOT))

    @GetMapping("/summary")
    fun summary(@AuthenticationPrincipal user: UserPrincipal) = dashboard.summary(user.id)

    @GetMapping("/arbitrage/terms")
    fun arbitrageTerms(@RequestParam currency: String) = dashboard.arbitrageTerms(currency.uppercase(Locale.ROOT))
}
