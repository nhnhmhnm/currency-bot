package org.example.backend.exchange.service

import java.math.BigDecimal
import java.math.RoundingMode

object ExchangeCommissionPolicy {
    val RATE: BigDecimal = BigDecimal("0.00003")

    fun calculate(baseAmount: BigDecimal, scale: Int, isArbitrage: Boolean): BigDecimal {
        require(baseAmount >= BigDecimal.ZERO) { "commission base amount must not be negative" }
        if (isArbitrage) return BigDecimal.ZERO.setScale(scale)
        return baseAmount.multiply(RATE).setScale(scale, RoundingMode.UP)
    }
}
