package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.exchange.domain.Transaction
import org.example.backend.exchange.dto.TransactionDTO
import org.example.backend.exchange.repository.TransactionRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository
): TransactionService {
    override fun record(transaction: TransactionDTO): TransactionDTO {
        val entity = Transaction(
            id = 0,
            userId = transaction.userId,
            walletId = transaction.walletId,
            orderId = transaction.orderId,
            fromCurrencyId = transaction.fromCurrencyId,
            toCurrencyId = transaction.toCurrencyId,
            fromAmount = transaction.fromAmount,
            toAmount = transaction.toAmount,
            exchangeRate = transaction.exchangeRate,
            commissionRate = transaction.commissionRate,
            commissionAmount = transaction.commissionAmount,
            commissionCurrencyId = transaction.commissionCurrencyId,
            profit = transaction.profit,
            profitCurrencyId = transaction.profitCurrencyId,
            createdAt = transaction.createdAt ?: LocalDateTime.now()
        )
        val saved = transactionRepository.save(entity)

        return saved.toDTO()
    }
}
