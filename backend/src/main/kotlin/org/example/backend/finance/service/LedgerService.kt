package org.example.backend.finance.service

import java.math.BigDecimal

interface LedgerService {
    fun record(userId: Long, currencyCode: String, amount: BigDecimal, type: String) // dto로 리턴

}