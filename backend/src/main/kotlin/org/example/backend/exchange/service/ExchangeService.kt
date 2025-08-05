package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeDTO
import java.math.BigDecimal

interface ExchangeService {
    fun getBestBuyRate(currencyCode: String, amount: BigDecimal): ExchangeDTO

    fun getBestSellRate(currencyCode: String, amount: BigDecimal): ExchangeDTO

    // 매매기준율 기준
    fun getBestArbitrageRate(currencyCode: String, amount: BigDecimal): Pair<ExchangeDTO, ExchangeDTO>
}
