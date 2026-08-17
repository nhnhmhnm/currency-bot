package org.example.backend.finance.repository

import org.example.backend.exchange.dto.ExchangeDto

interface ExchangeRateJdbcRepository {
    fun findBestBuyRate(currencyCode: String): ExchangeDto?
    fun findBestSellRate(currencyCode: String): ExchangeDto?

    fun findBestBuyBaseRate(currencyCode: String): ExchangeDto?
    fun findBestSellBaseRate(currencyCode: String): ExchangeDto?
}
