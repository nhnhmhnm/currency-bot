package org.example.backend.finance.service

import org.example.backend.finance.dto.ExchangeLedgerDTO
import java.math.BigDecimal

interface LedgerService {
    fun record(userId: Long, currencyCode: String, amount: BigDecimal, type: String, ...): ExchangeLedgerDTO

}