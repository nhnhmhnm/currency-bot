package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ExchangeServiceImpl(
    private val exchangeRateJdbcRepository: ExchangeRateJdbcRepository
): ExchangeService {

    override fun getBestBuyRate(currencyCode: String, amount: BigDecimal): ExchangeDTO {
        val dto = exchangeRateJdbcRepository.findBestBuyRate(currencyCode)
            ?: throw IllegalStateException("No buy rate found for $currencyCode")

        return dto.copy(amount = amount)
    }

    override fun getBestSellRate(currencyCode: String, amount: BigDecimal): ExchangeDTO {
        val dto = exchangeRateJdbcRepository.findBestSellRate(currencyCode)
            ?: throw IllegalStateException("No sell rate found for $currencyCode")

        return dto.copy(amount = amount)
    }

    override fun getBestArbitrageRate(currencyCode: String, amount: BigDecimal): Pair<ExchangeDTO, ExchangeDTO> {
        val buyDto = exchangeRateJdbcRepository.findBestBuyBaseRate(currencyCode)
            ?: throw IllegalStateException("No buy base rate found for $currencyCode")

        val sellDto = exchangeRateJdbcRepository.findBestSellBaseRate(currencyCode)
            ?: throw IllegalStateException("No sell base rate found for $currencyCode")

        return buyDto.copy(amount = amount) to sellDto.copy(amount = amount)
    }
}
