package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.exchange.domain.ExchangeLedger
import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeLedgerResponse
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import org.springframework.stereotype.Service

@Service
class ExchangeLedgerServiceImpl(
    private val exchangeLedgerRepository: ExchangeLedgerRepository,
): ExchangeLedgerService {
    override fun record(ledgerDto: ExchangeLedgerCommand): ExchangeLedgerResponse {
        val entity = ExchangeLedger(
            userId = ledgerDto.userId,
            fromCurrencyId = ledgerDto.fromCurrencyId,
            toCurrencyId = ledgerDto.toCurrencyId,
            fromAmount = ledgerDto.fromAmount,
            toAmount = ledgerDto.toAmount,
            exchangeRate = ledgerDto.exchangeRate,
            commissionCurrencyId = ledgerDto.commissionCurrencyId,
            commissionRate = ledgerDto.commissionRate,
            commissionAmount = ledgerDto.commissionAmount,
            type = ledgerDto.type,
        )
        val saved = exchangeLedgerRepository.save(entity)

        return saved.toDTO()
    }
}
