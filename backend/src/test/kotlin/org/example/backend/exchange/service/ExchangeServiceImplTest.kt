package org.example.backend.exchange.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus
import io.mockk.mockk
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import org.springframework.data.jpa.domain.AbstractPersistable_.id
import java.math.BigDecimal
import java.math.RoundingMode

class ExchangeServiceImplTest : BehaviorSpec({
    val repository = mockk<ExchangeRateJdbcRepository>(relaxed = true)
    val service = ExchangeServiceImpl(repository)

    val krw = Currency(id = 1L, code = "krw", name = "대한민국 원", scale = 0, unit = BigDecimal("1"))
    val usd = Currency(id = 2L, code = "usd", name = "미국 달러", scale = 2, unit = BigDecimal("1"))
    val jpy = Currency(id = 3L, code = "jpy", name = "일본 엔", scale = 0, unit = BigDecimal("100"))

    // 1 KRW -> 1438 USD
    Given("100,000원을 달러로 환전할 때") {
        val rate = BigDecimal("1438")
        val from = BigDecimal("100000")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = krw, toCurrency = usd,
                exchangeRate = rate, fromAmount = from
            )

            Then("달러 금액은 floor(100000/1438)=69.53달러, 절사 차익은 소수 아래 잔여") {
                toAmount shouldBe BigDecimal("69.53")
                // raw = 69.53... -> profit은 usd scale(2) 기준 절사 잔여
                val raw = from.divide(rate, 10, RoundingMode.DOWN)
                val expectedProfit = raw.setScale(10, RoundingMode.DOWN)
                    .subtract(raw.setScale(2, RoundingMode.DOWN))
                    .setScale(6, RoundingMode.DOWN)
                profit.setScale(6, RoundingMode.DOWN) shouldBe expectedProfit
            }
        }
    }

    // USD -> KRW
    Given("usd → krw(SELL), 1달러=1438원, 80달러를 원화로") {
        val rate = BigDecimal("1438")
        val from = BigDecimal("80")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = usd, toCurrency = krw,
                exchangeRate = rate, fromAmount = from
            )

            Then("원화 금액은 80*1438=115040원, 절사 차익 0원") {
                toAmount shouldBe BigDecimal("115040")
                profit shouldBe BigDecimal("0")
            }
        }
    }


    // KRW -> JPY
    Given("KRW → JPY(BUY), DB에는 100엔=943원 저장, 100,000원을 엔화로") {
        val ratePer100 = BigDecimal("943")             // DB 저장 값
        val ratePerUnit = ratePer100.divide(BigDecimal(100), 10, RoundingMode.DOWN) // 변환
        val from = BigDecimal("100000")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = krw, toCurrency = jpy,
                exchangeRate = ratePerUnit, fromAmount = from
            )

            Then("외화 수량은 floor(100000/9.43)=10604엔, 절사 차익은 약 0.453엔") {
                toAmount shouldBe BigDecimal("10604")
                val expectedProfit = from.divide(ratePerUnit, 10, RoundingMode.DOWN)
                    .subtract(BigDecimal("10604"))
                    .setScale(6, RoundingMode.DOWN)
                profit.setScale(6, RoundingMode.DOWN) shouldBe expectedProfit
            }
        }
    }

    // JPY -> KRW
    Given("jpy → krw(SELL), 1엔=9.43원, 10,000엔을 원화로") {
        val rate = BigDecimal("9.43")
        val from = BigDecimal("10000")

        When("calculateExchange를 호출하면") {
            val (toAmount, profit) = service.calculateExchange(
                fromCurrency = jpy, toCurrency = krw,
                exchangeRate = rate, fromAmount = from
            )

            Then("원화 금액은 10000*9.43=94300원, 절사 차익 0원") {
                toAmount shouldBe BigDecimal("94300")
                profit shouldBe BigDecimal("0")
            }
        }
    }
})
