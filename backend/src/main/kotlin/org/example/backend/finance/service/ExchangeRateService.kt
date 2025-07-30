package org.example.backend.finance.service

import org.example.backend.finance.domain.Bank
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.domain.ExchangeRate
import java.math.BigDecimal

interface ExchangeRateService {
    fun getBestBuyRate(
        userId: Long,
        currencyCode: String,
        discountRate: BigDecimal? = null)  // 가장 싼 환율 (buyRate 가장 낮은 은행)

    fun getBestSellRate(
        userId: Long,
        currencyCode: String,
        discountRate: BigDecimal? = null)  // 가장 비싼 환율 (sellRate 가장 높은 은행)

}
