package org.example.backend.exchange.service

import org.example.backend.exchange.dto.TransactionDTO
import org.example.backend.exchange.repository.TransactionRepository

class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository
): TransactionService {
    override fun record(transaction: TransactionDTO): TransactionDTO {
        TODO("Not yet implemented")
    }
}
