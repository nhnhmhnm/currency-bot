package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeDto
import org.example.backend.finance.domain.Currency
import java.math.BigDecimal

interface ExchangeService {
    // 송금 기준
    fun getBestBuyRate(currencyCode: String): ExchangeDto
    fun getBestSellRate(currencyCode: String): ExchangeDto

    // 매매기준율 기준
    fun getBestBuyBaseRate(currencyCode: String): ExchangeDto
    fun getBestSellBaseRate(currencyCode: String): ExchangeDto

    // 환전 계산
    fun calculateExchange(fromCurrency: Currency, toCurrency: Currency, exchangeRate: BigDecimal, fromAmount: BigDecimal): Pair<BigDecimal, BigDecimal>
}
