package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeLedgerDTO

interface ExchangeLedgerService {
    fun record(ledger: ExchangeLedgerDTO): ExchangeLedgerDTO
}
