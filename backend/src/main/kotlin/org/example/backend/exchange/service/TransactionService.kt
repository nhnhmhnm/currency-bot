package org.example.backend.exchange.service

import org.example.backend.exchange.dto.TransactionCommand

interface TransactionService {
    fun record(transaction: TransactionCommand): TransactionCommand
}
