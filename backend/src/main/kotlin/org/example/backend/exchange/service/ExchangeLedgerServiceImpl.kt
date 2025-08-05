package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeLedgerDTO

class ExchangeLedgerServiceImpl(
    private val exchangeLedgerRepository: org.example.backend.exchange.repository.ExchangeLedgerRepository,
): ExchangeLedgerService {
    override fun record(ledger: ExchangeLedgerDTO): ExchangeLedgerDTO {
        TODO("Not yet implemented")
    }
}
