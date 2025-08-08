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
    override fun record(transactionDto: TransactionDTO): TransactionDTO {
        val createdAt = LocalDateTime.now()
        val entity = Transaction(
            userId = transactionDto.userId,
            walletId = transactionDto.walletId,
            orderId = transactionDto.orderId,
            fromCurrencyId = transactionDto.fromCurrencyId,
            toCurrencyId = transactionDto.toCurrencyId,
            fromAmount = transactionDto.fromAmount,
            toAmount = transactionDto.toAmount,
            exchangeRate = transactionDto.exchangeRate,
            commissionCurrencyId = transactionDto.commissionCurrencyId,
            commissionAmount = transactionDto.commissionAmount,
            profitCurrencyId = transactionDto.profitCurrencyId,
            profit = transactionDto.profit,
            createdAt = createdAt
        )
        val saved = transactionRepository.save(entity)

        return saved.toDTO()
    }
}
