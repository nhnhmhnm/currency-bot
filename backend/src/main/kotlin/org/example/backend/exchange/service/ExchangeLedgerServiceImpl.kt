package org.example.backend.exchange.service

import org.example.backend.common.util.toDTO
import org.example.backend.exchange.domain.ExchangeLedger
import org.example.backend.exchange.dto.ExchangeLedgerDTO
import org.example.backend.exchange.repository.ExchangeLedgerRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ExchangeLedgerServiceImpl(
    private val exchangeLedgerRepository: ExchangeLedgerRepository,
): ExchangeLedgerService {
    override fun record(ledger: ExchangeLedgerDTO): ExchangeLedgerDTO {
        val entity = ExchangeLedger(
            id = 0,
            userId = ledger.userId,
            walletId = ledger.walletId,
            currencyId = ledger.currencyId,
            amount = ledger.amount,
            balance = ledger.balance,
            exchangeRate = ledger.exchangeRate,
            commissionAmount = ledger.commissionAmount,
            commissionRate = ledger.commissionRate,
            commissionCurrencyId = ledger.commissionCurrencyId,
            type = ledger.type,
            createdAt = ledger.createdAt ?: LocalDateTime.now()
        )
        val saved = exchangeLedgerRepository.save(entity)

        return saved.toDTO()
    }
}
