package org.example.backend.finance.repository

import org.example.backend.exchange.dto.ExchangeDTO

interface ExchangeRateJdbcRepository {

    fun findBestBuyRate(currencyCode: String): ExchangeDTO?

    fun findBestSellRate(currencyCode: String): ExchangeDTO?

    fun findBestBuyBaseRate(currencyCode: String): ExchangeDTO?

    fun findBestSellBaseRate(currencyCode: String): ExchangeDTO?
}
