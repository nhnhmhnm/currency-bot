package org.example.backend.exchange.service

import org.example.backend.exchange.dto.BestExchangeRateDto
import org.example.backend.finance.domain.Currency
import java.math.BigDecimal

interface ExchangeService {
    // 송금 기준
    fun getBestBuyRate(currencyCode: String): BestExchangeRateDto
    fun getBestSellRate(currencyCode: String): BestExchangeRateDto

    // 매매기준율 기준
    fun getBestBuyBaseRate(currencyCode: String): BestExchangeRateDto
    fun getBestSellBaseRate(currencyCode: String): BestExchangeRateDto

    // 환전 계산
    fun calculateExchange(fromCurrency: Currency, toCurrency: Currency, exchangeRate: BigDecimal, fromAmount: BigDecimal): Pair<BigDecimal, BigDecimal>
}
