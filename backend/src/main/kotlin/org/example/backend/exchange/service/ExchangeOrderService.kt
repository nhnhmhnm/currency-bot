package org.example.backend.exchange.service

import java.math.BigDecimal

interface ExchangeOrderService {
    fun buyCurrency(
        userId: Long,
        currencyCode: String,
        amount: BigDecimal,
        discountRate: BigDecimal? = null
    ) // dto로 리턴

    fun sellCurrency(
        userId: Long,
        currencyCode: String,
        amount: BigDecimal,
        premiumRate: BigDecimal? = null
    ) // dto로 리턴
}