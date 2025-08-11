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
    override fun record(dto: TransactionCommand): TransactionResponse {
        val entity = Transaction(
            userId = dto.userId,
            walletId = dto.walletId,
            orderId = dto.orderId,
            fromCurrencyId = dto.fromCurrencyId,
            toCurrencyId = dto.toCurrencyId,
            fromAmount = dto.fromAmount,
            toAmount = dto.toAmount,
            exchangeRate = dto.exchangeRate,
            commissionCurrencyId = dto.commissionCurrencyId,
            commissionAmount = dto.commissionAmount,
            profitCurrencyId = dto.profitCurrencyId,
            profit = dto.profit
        )
        val saved = transactionRepository.save(entity)

        return saved.toDTO()
    }
}
