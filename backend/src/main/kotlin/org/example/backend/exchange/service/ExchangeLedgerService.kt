package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeLedgerCommand
import org.example.backend.exchange.dto.ExchangeLedgerResponse

interface ExchangeLedgerService {
    fun record(ledger: ExchangeLedgerCommand): ExchangeLedgerResponse
}
