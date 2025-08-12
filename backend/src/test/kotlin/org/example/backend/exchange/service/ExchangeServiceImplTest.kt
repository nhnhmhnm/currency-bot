package org.example.backend.exchange.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import java.math.BigDecimal
import java.math.RoundingMode

class ExchangeServiceImplTest : BehaviorSpec({
    val repository = mockk<ExchangeRateJdbcRepository>(relaxed = true)
    val service = ExchangeServiceImpl(repository)

    val krw = Currency(id = 1L, code = "KRW", name = "대한민국 원", scale = 0, unit = BigDecimal("1"))
    val usd = Currency(id = 2L, code = "USD", name = "미국 달러", scale = 2, unit = BigDecimal("1"))
    val jpy = Currency(id = 3L, code = "JPY", name = "일본 엔",   scale = 0, unit = BigDecimal("100"))

    // 1 USD = 1438 KRW
    Given("100,000 KRW를 달러로 환전하는 경우") {
        val ratePerUsd = BigDecimal("1438")     // DB: 1달러 기준
        val from = BigDecimal("100000")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = krw, toCurrency = usd,
                exchangeRate = ratePerUsd, fromAmount = from
            )

            Then("달러 = 69.54, 차익 0.0010292072 USD이 반환된다.") {
                val raw = from.divide(ratePerUsd, 10, RoundingMode.DOWN) // 69.5410292072
                toAmount shouldBe raw.setScale(2, RoundingMode.DOWN) // 69.54
                profit shouldBe raw.subtract(toAmount) // 0.0010292072
            }
        }
    }
    Given("80 USD를 원화로 환전하는 경우") {
        val ratePerUsd = BigDecimal("1438")
        val from = BigDecimal("80")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = usd, toCurrency = krw,
                exchangeRate = ratePerUsd, fromAmount = from
            )

            Then("원화 = 115040, 차익 0 USD이 반환된다.") {
                toAmount shouldBe BigDecimal("115040")
                profit shouldBe BigDecimal.ZERO
            }
        }
    }

    // 100 JPY = 943 KRW
    Given("100,000 KRW를 엔화로 환전하는 경우") {
        val ratePer100Jpy = BigDecimal("943")   // DB: 100엔 기준
        val from = BigDecimal("100000")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = krw, toCurrency = jpy,
                exchangeRate = ratePer100Jpy, fromAmount = from
            )

            Then("엔화 = 10604, 차익 0.4538706256 JPY이 반환된다.") {
                val ratePerOne = ratePer100Jpy.divide(BigDecimal("100"), 10, RoundingMode.DOWN) // 9.43
                val raw = from.divide(ratePerOne, 10, RoundingMode.DOWN) // 10604.4538706256
                toAmount shouldBe raw.setScale(0, RoundingMode.DOWN) // 10604
                profit shouldBe raw.subtract(toAmount) // 0.4538706256
            }
        }
    }
    Given("10,000 JPY를 원화로 환전하는 경우") {
        val ratePer100Jpy = BigDecimal("943")
        val from = BigDecimal("10000")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = jpy, toCurrency = krw,
                exchangeRate = ratePer100Jpy, fromAmount = from
            )

            Then("원화 = 94300, 차익 0 JPY이 반환된다.") {
                val ratePerOne = ratePer100Jpy.divide(BigDecimal("100"), 10, RoundingMode.DOWN) // 9.43
                val raw = from.multiply(ratePerOne) // 94300.0000000000
                toAmount shouldBe raw.setScale(0, RoundingMode.DOWN) // 94300
                profit shouldBe BigDecimal.ZERO
            }
        }
    }
})
