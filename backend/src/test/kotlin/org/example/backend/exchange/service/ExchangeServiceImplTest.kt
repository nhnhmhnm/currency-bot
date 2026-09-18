package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.example.backend.finance.domain.Currency
import org.example.backend.finance.repository.ExchangeRateJdbcRepository
import java.math.BigDecimal
import java.math.RoundingMode

class ExchangeServiceImplTest : FunSpec({
    val repository = mockk<ExchangeRateJdbcRepository>(relaxed = true)
    val service = ExchangeServiceImpl(repository)
    val krw = Currency(id = 1L, code = "KRW", name = "Korean Won", scale = 0, unit = BigDecimal.ONE)
    val usd = Currency(id = 2L, code = "USD", name = "US Dollar", scale = 2, unit = BigDecimal.ONE)
    val jpy = Currency(id = 3L, code = "JPY", name = "Japanese Yen", scale = 0, unit = BigDecimal("100"))
    val usdRate = BigDecimal("1438")
    val jpyRate = BigDecimal("943")

    test("converts one USD using a unit of one") {
        service.calculateExchange(usd, krw, usdRate, BigDecimal.ONE) shouldBe
            (BigDecimal("1438") to BigDecimal.ZERO)
    }

    test("converts 1438 KRW to exactly one USD") {
        service.calculateExchange(krw, usd, usdRate, BigDecimal("1438")) shouldBe
            (BigDecimal("1.00") to BigDecimal.ZERO)
    }

    test("converts 100 JPY using the quoted unit of one hundred") {
        service.calculateExchange(jpy, krw, jpyRate, BigDecimal("100")) shouldBe
            (BigDecimal("943") to BigDecimal.ZERO)
    }

    test("converts one JPY by reversing the one hundred JPY quote") {
        service.calculateExchange(jpy, krw, jpyRate, BigDecimal.ONE) shouldBe
            (BigDecimal("9") to BigDecimal("0.43"))
    }

    test("rounds a sub-cent USD result down to the currency scale") {
        val (amount, remainder) = service.calculateExchange(krw, usd, usdRate, BigDecimal.ONE)

        amount shouldBe BigDecimal("0.00")
        remainder shouldBe BigDecimal("0.0006954102")
    }

    test("round trip never creates value through rounding") {
        val initialKrw = BigDecimal("100000")
        val (usdAmount) = service.calculateExchange(krw, usd, usdRate, initialKrw)
        val (returnedKrw) = service.calculateExchange(usd, krw, usdRate, usdAmount)

        returnedKrw shouldBe BigDecimal("99998")
        (initialKrw - returnedKrw) shouldBe BigDecimal("2")
    }

    test("keeps deterministic precision for a very large amount") {
        val input = BigDecimal("999999999999999.99")
        val expectedRaw = input.divide(usdRate, 10, RoundingMode.DOWN)
        val (amount, remainder) = service.calculateExchange(krw, usd, usdRate, input)

        amount shouldBe expectedRaw.setScale(2, RoundingMode.DOWN)
        remainder shouldBe expectedRaw.subtract(amount).stripTrailingZeros()
    }

    test("rejects zero and negative amounts") {
        shouldThrow<IllegalArgumentException> {
            service.calculateExchange(krw, usd, usdRate, BigDecimal.ZERO)
        }
        shouldThrow<IllegalArgumentException> {
            service.calculateExchange(krw, usd, usdRate, BigDecimal("-1"))
        }
    }

    test("rejects a zero rate and unsupported cross currency direction") {
        shouldThrow<IllegalArgumentException> {
            service.calculateExchange(krw, usd, BigDecimal.ZERO, BigDecimal.ONE)
        }
        shouldThrow<IllegalArgumentException> {
            service.calculateExchange(usd, jpy, usdRate, BigDecimal.ONE)
        }
    }
})
