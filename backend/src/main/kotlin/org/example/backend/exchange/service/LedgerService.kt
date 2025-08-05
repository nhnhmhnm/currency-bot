package org.example.backend.exchange.service

import org.example.backend.exchange.dto.ExchangeLedgerDTO
import java.math.BigDecimal

interface LedgerService {
    fun record(ledger: ExchangeLedgerDTO): ExchangeLedgerDTO
}
