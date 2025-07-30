package org.example.backend.exchange.service

import java.math.BigDecimal

interface TransactionService {
    fun record(userId: Long, currencyCode: String, amount: BigDecimal, type: String) // dto로 리턴

}