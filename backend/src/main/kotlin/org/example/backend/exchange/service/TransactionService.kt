package org.example.backend.exchange.service

import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.dto.TransactionResponse

interface TransactionService {
    fun record(transaction: TransactionCommand): TransactionResponse
}
