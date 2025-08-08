package org.example.backend.exchange.service

import org.example.backend.exchange.dto.TransactionDTO

interface TransactionService {
    fun record(transaction: TransactionDTO): TransactionDTO
}
