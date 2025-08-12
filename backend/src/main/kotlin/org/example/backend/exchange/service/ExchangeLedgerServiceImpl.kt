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
    override fun record(ledger: ExchangeLedgerCommand): ExchangeLedgerResponse {
        val entity = ExchangeLedger(
            userId = ledger.userId,
            fromCurrencyId = ledger.fromCurrencyId,
            toCurrencyId = ledger.toCurrencyId,
            fromAmount = ledger.fromAmount,
            toAmount = ledger.toAmount,
            exchangeRate = ledger.exchangeRate,
            commissionCurrencyId = ledger.commissionCurrencyId,
            commissionRate = ledger.commissionRate,
            commissionAmount = ledger.commissionAmount,
            type = ledger.type,
        )
        val saved = exchangeLedgerRepository.save(entity)

        return saved.toDTO()
    }
}
