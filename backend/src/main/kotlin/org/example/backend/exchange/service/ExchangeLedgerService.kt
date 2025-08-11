package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeLedgerCommand

interface ExchangeLedgerService {
    fun record(ledger: ExchangeLedgerCommand): ExchangeLedgerCommand
}
