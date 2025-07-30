package org.example.backend.finance.service

import org.example.backend.finance.domain.Bank
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.domain.ExchangeRate

interface FinanceService {
    fun getBestBuyRate(currencyCode: String): ExchangeRate  // 가장 싼 환율 (buyRate 가장 낮은 은행)
    fun getBestSellRate(currencyCode: String): ExchangeRate // 가장 비싼 환율 (sellRate 가장 높은 은행)
    fun getCurrencyByCode(code: String): Currency           // 통화 코드로 Currency 조회
    fun getBankById(id: Long): Bank                          // 은행 ID로 조회 (Optional)
}
