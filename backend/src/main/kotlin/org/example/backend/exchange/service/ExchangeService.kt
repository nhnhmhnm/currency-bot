package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeDTO
import java.math.BigDecimal

interface ExchangeService {
    fun getBestBuyRate(currencyCode: String): ExchangeDTO

    fun getBestSellRate(currencyCode: String): ExchangeDTO

    // 매매기준율 기준
    fun getBestArbitrageRate(currencyCode: String): Pair<ExchangeDTO, ExchangeDTO>
}
