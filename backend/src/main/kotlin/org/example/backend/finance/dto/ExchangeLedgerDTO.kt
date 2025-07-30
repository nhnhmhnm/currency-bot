package org.example.backend.finance.dto

import org.example.backend.enums.ExchangeLedgerType
import java.math.BigDecimal

data class ExchangeLedgerDTO(
    val userId: Long,
    val walletId: Long,
    val currencyId: Long,

    val amount: BigDecimal,
    val balance: BigDecimal,

    val exchangeRate: BigDecimal,
    val commissionAmount: BigDecimal,
    val commissionRate: BigDecimal,
    val commissionCurrencyId: Long,

    val type: ExchangeLedgerType
)
