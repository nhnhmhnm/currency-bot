package org.example.backend.finance.repository

import org.example.backend.finance.dto.ExchangeDTO

interface ExchangeJdbcRepository {

    fun findBestBuyRate(currencyCode: String): ExchangeDTO?

    fun findBestSellRate(currencyCode: String): ExchangeDTO?

    fun findBestBuyBaseRate(currencyCode: String): ExchangeDTO?

    fun findBestSellBaseRate(currencyCode: String): ExchangeDTO?
}
