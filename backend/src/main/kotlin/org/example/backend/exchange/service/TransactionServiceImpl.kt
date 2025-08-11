package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.exchange.domain.Transaction
import org.example.backend.exchange.dto.TransactionCommand
import org.example.backend.exchange.repository.TransactionRepository
import org.springframework.stereotype.Service

@Service
class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository
): TransactionService {
    override fun record(transactionCommand: TransactionCommand): TransactionCommand {
        val entity = Transaction(
            userId = transactionCommand.userId,
            walletId = transactionCommand.walletId,
            orderId = transactionCommand.orderId,
            fromCurrencyId = transactionCommand.fromCurrencyId,
            toCurrencyId = transactionCommand.toCurrencyId,
            fromAmount = transactionCommand.fromAmount,
            toAmount = transactionCommand.toAmount,
            exchangeRate = transactionCommand.exchangeRate,
            commissionCurrencyId = transactionCommand.commissionCurrencyId,
            commissionAmount = transactionCommand.commissionAmount,
            profitCurrencyId = transactionCommand.profitCurrencyId,
            profit = transactionCommand.profit
        )
        val saved = transactionRepository.save(entity)

        return saved.toDTO()
    }
}
