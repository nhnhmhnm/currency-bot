package org.example.backend.finance.repository

import org.example.backend.exchange.dto.BestExchangeRateDto

interface ExchangeRateJdbcRepository {
    fun findBestBuyRate(currencyCode: String): BestExchangeRateDto?
    fun findBestSellRate(currencyCode: String): BestExchangeRateDto?

    fun findBestBuyBaseRate(currencyCode: String): BestExchangeRateDto?
    fun findBestSellBaseRate(currencyCode: String): BestExchangeRateDto?
}
