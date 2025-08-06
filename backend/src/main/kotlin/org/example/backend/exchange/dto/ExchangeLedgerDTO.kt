package org.example.backend.exchange.dto

import org.example.backend.enums.ExchangeLedgerType
import java.math.BigDecimal
import java.time.LocalDateTime

data class ExchangeLedgerDTO(
    val id: Long? = null,
    val userId: Long,
    val walletId: Long,

    val currencyId: Long,
    val amount: BigDecimal?,
    val balance: BigDecimal,
    val exchangeRate: BigDecimal,

    val commissionAmount: BigDecimal?,
    val commissionRate: BigDecimal?,
    val commissionCurrencyId: Long?,

    val type: ExchangeLedgerType,
    val createdAt: LocalDateTime? = null
)
