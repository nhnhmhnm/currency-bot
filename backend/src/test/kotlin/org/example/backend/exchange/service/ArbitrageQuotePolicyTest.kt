package org.example.backend.exchange.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import java.math.BigDecimal

class ArbitrageQuotePolicyTest : FunSpec({
    val exchangeService = ExchangeServiceImpl(mockk<ExchangeRateJdbcRepository>())
    val krw = Currency(1L, "KRW", "Korean Won", 0, BigDecimal.ONE)
    val usd = Currency(2L, "USD", "US Dollar", 2, BigDecimal.ONE)
    val jpy = Currency(3L, "JPY", "Japanese Yen", 0, BigDecimal("100"))

    test("USD quote charges the rounded fee once on final KRW proceeds") {
        val quote = ArbitrageQuotePolicy.estimate(krw, usd, BigDecimal("1300"), BigDecimal("1310"), exchangeService)
        quote.foreignAmount shouldBe BigDecimal("769.23")
        quote.feeKrw shouldBe BigDecimal("31")
        quote.receiveKrw shouldBe BigDecimal("1007660")
        quote.profitKrw shouldBe BigDecimal("7660")
    }

    test("JPY quote respects the 100-unit quotation and zero decimal places") {
        val quote = ArbitrageQuotePolicy.estimate(krw, jpy, BigDecimal("900"), BigDecimal("910"), exchangeService)
        quote.foreignAmount shouldBe BigDecimal("111111")
        quote.feeKrw shouldBe BigDecimal("31")
        quote.profitKrw shouldBe BigDecimal("11079")
    }

    test("equal base rates do not produce a profitable arbitrage quote") {
        (ArbitrageQuotePolicy.estimate(
            krw, usd, BigDecimal("1300"), BigDecimal("1300"), exchangeService
        ).profitKrw < BigDecimal.ZERO) shouldBe true
    }

    test("USD and JPY spread examples straddle the net break-even point") {
        val usdExamples = listOf("1300.00" to "-31", "1300.04" to "-2", "1300.05" to "6", "1310.00" to "7660")
        usdExamples.forEach { (sellRate, expectedProfit) ->
            ArbitrageQuotePolicy.estimate(
                krw, usd, BigDecimal("1300"), BigDecimal(sellRate), exchangeService
            ).profitKrw shouldBe BigDecimal(expectedProfit)
        }
        val jpyExamples = listOf("900.00" to "-31", "900.02" to "-10", "900.03" to "1", "910.00" to "11079")
        jpyExamples.forEach { (sellRate, expectedProfit) ->
            ArbitrageQuotePolicy.estimate(
                krw, jpy, BigDecimal("900"), BigDecimal(sellRate), exchangeService
            ).profitKrw shouldBe BigDecimal(expectedProfit)
        }
    }
})
