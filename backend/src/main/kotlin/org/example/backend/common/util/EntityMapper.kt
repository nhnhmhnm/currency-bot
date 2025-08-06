package org.example.backend.common.util

import org.example.backend.enums.ExchangeType
import org.example.backend.exchange.domain.ExchangeLedger
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.domain.Transaction
import org.example.backend.exchange.dto.ExchangeDTO
import org.example.backend.exchange.dto.ExchangeLedgerDTO
import org.example.backend.exchange.dto.ExchangeOrderDTO
import org.example.backend.exchange.dto.TransactionDTO
import java.sql.ResultSet

fun toDTO(rs: ResultSet, type: ExchangeType): ExchangeDTO {
    return ExchangeDTO(
        bankId = rs.getLong("bank_id"),
        currencyId = rs.getLong("currency_id"),
        bestRate = rs.getBigDecimal("best_rate"),
        type = type
    )
}

fun ExchangeOrder.toDTO() = ExchangeOrderDTO(
    id = this.id,
    userId = this.userId,
    bankId = this.bankId,
    fromCurrencyId = this.fromCurrencyId,
    toCurrencyId = this.toCurrencyId,
    fromAmount = this.fromAmount,
    toAmount = this.toAmount,
    exchangeRate = this.exchangeRate,
    status = this.status,
    statusDesc = this.statudDesc,
    requestedAt = this.requestedAt,
    executedAt = this.executedAt
)

fun ExchangeLedger.toDTO() = ExchangeLedgerDTO(
    id = this.id,
    userId = this.userId,
    walletId = this.walletId,
    currencyId = this.currencyId,
    amount = this.amount,
    balance = this.balance,
    exchangeRate = this.exchangeRate,
    commissionAmount = this.commissionAmount,
    commissionRate = this.commissionRate,
    commissionCurrencyId = this.commissionCurrencyId,
    type = this.type,
    createdAt = this.createdAt
)

fun Transaction.toDTO() = TransactionDTO(
    id = this.id,
    userId = this.userId,
    walletId = this.walletId,
    orderId = this.orderId,
    fromCurrencyId = this.fromCurrencyId,
    toCurrencyId = this.toCurrencyId,
    fromAmount = this.fromAmount,
    toAmount = this.toAmount,
    exchangeRate = this.exchangeRate,
    commissionRate = this.commissionRate,
    commissionAmount = this.commissionAmount,
    commissionCurrencyId = this.commissionCurrencyId,
    profit = this.profit,
    profitCurrencyId = this.profitCurrencyId,
    createdAt = this.createdAt
)
