package org.example.backend.exchange.service

import org.example.backend.exchange.dto.TransactionDTO
import java.math.BigDecimal

interface TransactionService {
    fun record(userId: Long, currencyCode: String, amount: BigDecimal, type: String, ...): TransactionDTO

}