package org.example.backend.finance.service

import org.example.backend.finance.dto.ExchangeDTO
import java.math.BigDecimal

interface ExchangeService {
    fun getBestBuyRate(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeDTO

    fun getBestSellRate(userId: Long, currencyCode: String, amount: BigDecimal): ExchangeDTO

    // 매매기준율 기준
    fun getBestBuySellRate(userId: Long, currencyCode: String, amount: BigDecimal): Pair<ExchangeDTO, ExchangeDTO>
}
