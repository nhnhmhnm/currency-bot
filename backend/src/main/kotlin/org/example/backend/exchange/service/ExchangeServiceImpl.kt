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

    override fun calculateExchange(fromCurrency: Currency, toCurrency: Currency,
                                   exchangeRate: BigDecimal, fromAmount: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val isKrwToFx = fromCurrency.code.equals("KRW", true)

        // 환율을 '1 단위(to/from 통화) 기준'으로 정규화
        val ratePerOne = when {
            // KRW → 외화(BUY): toCurrency.unit으로 나눠 1단위 외화 기준 KRW 가격으로 맞춤
            isKrwToFx -> exchangeRate.divide(toCurrency.unit, 10, RoundingMode.DOWN)

            // 외화 → KRW(SELL): fromCurrency.unit으로 나눠 1단위 외화 기준 KRW 가격으로 맞춤
            toCurrency.code.equals("KRW", true) -> exchangeRate.divide(fromCurrency.unit, 10, RoundingMode.DOWN)

            else -> throw IllegalArgumentException("지원하지 않는 환전 방향입니다")
        }

        val roundedToAmount =
            if (isKrwToFx) {
                // KRW → 외화: KRW / (KRW per 1 FX) = FX
                fromAmount.divide(ratePerOne, 10, RoundingMode.DOWN)
            }
            else {
                // 외화 → KRW: FX * (KRW per 1 FX) = KRW
                fromAmount.multiply(ratePerOne)
            }

        // 통화 스케일에 맞춰 절사 + 절사 차익(은행 이익) 계산
        val toAmount = roundedToAmount.setScale(toCurrency.scale, RoundingMode.DOWN)

        val profitRaw = roundedToAmount.subtract(toAmount)
        val profit = if (profitRaw.signum() == 0) BigDecimal.ZERO else profitRaw.stripTrailingZeros()

        return toAmount to profit
    }
}
