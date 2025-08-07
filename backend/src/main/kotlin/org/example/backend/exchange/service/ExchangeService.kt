package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.CurrencyRepository
import java.math.BigDecimal

interface ExchangeService {
    fun getBestBuyRate(currencyCode: String): ExchangeDTO
    fun getBestSellRate(currencyCode: String): ExchangeDTO

    // 매매기준율 기준
    fun getBestBuyBaseRate(currencyCode: String): ExchangeDTO
    fun getBestSellBaseRate(currencyCode: String): ExchangeDTO

    fun calculateExchange(fromCurrency: Currency, toCurrency: Currency, exchangeRate: BigDecimal, fromAmount: BigDecimal): Pair<BigDecimal, BigDecimal>
}
