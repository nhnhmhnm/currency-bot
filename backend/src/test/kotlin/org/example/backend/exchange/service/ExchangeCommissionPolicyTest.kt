package org.example.backend.exchange.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ExchangeCommissionPolicyTest : FunSpec({
    test("rounds a fractional KRW commission up to the next won") {
        ExchangeCommissionPolicy.calculate(
            baseAmount = BigDecimal("100001"),
            scale = 0,
            isArbitrage = false
        ) shouldBe BigDecimal("4")
    }

    test("uses the target currency scale for commission rounding") {
        ExchangeCommissionPolicy.calculate(
            baseAmount = BigDecimal("10.001"),
            scale = 2,
            isArbitrage = false
        ) shouldBe BigDecimal("0.01")
    }

    test("does not charge commission for arbitrage") {
        ExchangeCommissionPolicy.calculate(
            baseAmount = BigDecimal("100001"),
            scale = 0,
            isArbitrage = true
        ) shouldBe BigDecimal("0")
    }

    test("rejects a negative commission base") {
        shouldThrow<IllegalArgumentException> {
            ExchangeCommissionPolicy.calculate(BigDecimal("-1"), 0, false)
        }
    }
})
