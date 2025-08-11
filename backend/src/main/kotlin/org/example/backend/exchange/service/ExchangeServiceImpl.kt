package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ExchangeServiceImpl(
    private val exchangeRateJdbcRepository: ExchangeRateJdbcRepository
): ExchangeService {

    override fun getBestBuyRate(currencyCode: String): ExchangeDTO {
        val dto = exchangeRateJdbcRepository.findBestBuyRate(currencyCode)
            ?: throw IllegalStateException("No buy rate found for $currencyCode")

        return dto
    }

    override fun getBestSellRate(currencyCode: String): ExchangeDTO {
        val dto = exchangeRateJdbcRepository.findBestSellRate(currencyCode)
            ?: throw IllegalStateException("No sell rate found for $currencyCode")

        return dto
    }

    override fun getBestBuyBaseRate(currencyCode: String): ExchangeDTO {
        val dto = exchangeRateJdbcRepository.findBestBuyBaseRate(currencyCode)
            ?: throw IllegalStateException("No buy base rate found for $currencyCode")

        return dto
    }

    override fun getBestSellBaseRate(currencyCode: String): ExchangeDTO {
        val dto = exchangeRateJdbcRepository.findBestSellBaseRate(currencyCode)
            ?: throw IllegalStateException("No sell base rate found for $currencyCode")

        return dto
    }

    override fun calculateExchange(fromCurrency: Currency, toCurrency: Currency, exchangeRate: BigDecimal, fromAmount: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val scale = toCurrency.scale

        val rawAmount =
            if (fromCurrency.code == "KRW") {
            // KRW → 외화 (Buy)
            fromAmount.divide(exchangeRate, 10, RoundingMode.DOWN)
            }
            else if (toCurrency.code == "KRW") {
            // 외화 → KRW (Sell)
            fromAmount.multiply(exchangeRate)
            }
            else {
                throw IllegalArgumentException("지원하지 않는 환전 방향입니다")
            }

        // 환전 금액
        val toAmount = rawAmount.setScale(scale, RoundingMode.DOWN)

        // 차익
        val profit = rawAmount.subtract(toAmount)

        return Pair(toAmount, profit)
    }

}
