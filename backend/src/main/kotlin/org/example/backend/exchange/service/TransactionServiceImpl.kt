package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.exchange.domain.Transaction
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.dto.TransactionResponse
import org.example.backend.exchange.repository.TransactionRepository
import org.springframework.stereotype.Service

@Service
class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository
): TransactionService {
    override fun record(transaction: TransactionCommand): TransactionResponse {
        val entity = Transaction(
            userId = transaction.userId,
//            walletId = transaction.walletId,
            orderId = transaction.orderId,
            fromCurrencyId = transaction.fromCurrencyId,
            toCurrencyId = transaction.toCurrencyId,
            fromAmount = transaction.fromAmount,
            toAmount = transaction.toAmount,
            exchangeRate = transaction.exchangeRate,
            commissionCurrencyId = transaction.commissionCurrencyId,
            commissionAmount = transaction.commissionAmount,
            profitCurrencyId = transaction.profitCurrencyId,
            profit = transaction.profit
        )
        val saved = transactionRepository.save(entity)

        return saved.toDTO()
    }
}
