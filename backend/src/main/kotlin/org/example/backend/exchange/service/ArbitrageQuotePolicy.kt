package org.example.backend.exchange.service

import org.example.backend.finance.domain.Currency
import java.math.BigDecimal

object ArbitrageQuotePolicy {
    val AMOUNT_KRW: BigDecimal = BigDecimal("1000000")
    const val DAILY_LIMIT = 4

    data class Estimate(val foreignAmount: BigDecimal, val grossKrw: BigDecimal, val feeKrw: BigDecimal, val receiveKrw: BigDecimal) {
        val profitKrw: BigDecimal get() = receiveKrw.subtract(AMOUNT_KRW)
    }

    fun estimate(krw: Currency, foreign: Currency, buyRate: BigDecimal, sellRate: BigDecimal, exchangeService: ExchangeService): Estimate {
        val foreignAmount = exchangeService.calculateExchange(krw, foreign, buyRate, AMOUNT_KRW).first
        val grossKrw = exchangeService.calculateExchange(foreign, krw, sellRate, foreignAmount).first
        val feeKrw = ExchangeCommissionPolicy.calculate(grossKrw, krw.scale, isArbitrage = false)
        return Estimate(foreignAmount, grossKrw, feeKrw, grossKrw.subtract(feeKrw))
    }

}
