package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.exchange.domain.ExchangeLedger
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ExchangeLedgerServiceImpl(
    private val exchangeLedgerRepository: ExchangeLedgerRepository,
): ExchangeLedgerService {
    override fun record(ledgerDto: ExchangeLedgerCommand): ExchangeLedgerCommand {
        val createdAt = LocalDateTime.now()
        val entity = ExchangeLedger(
            id = 0,
            userId = ledgerDto.userId,
            fromCurrencyId = ledgerDto.fromCurrencyId,
            toCurrencyId = ledgerDto.toCurrencyId,
            fromAmount = ledgerDto.fromAmount,
            toAmount = ledgerDto.toAmount,
            exchangeRate = ledgerDto.exchangeRate,
            commissionAmount = ledgerDto.commissionAmount,
            commissionCurrencyId = ledgerDto.commissionCurrencyId,
            type = ledgerDto.type,
            createdAt = createdAt
        )
        val saved = exchangeLedgerRepository.save(entity)

        return saved.toDTO()
    }
}
