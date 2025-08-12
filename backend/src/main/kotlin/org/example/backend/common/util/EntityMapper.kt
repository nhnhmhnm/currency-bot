package org.example.backend.common.util

import org.example.backend.enums.ExchangeType
import org.example.backend.exchange.domain.ExchangeLedger
import org.example.backend.exchange.domain.ExchangeOrder
import org.example.backend.exchange.domain.Transaction
import org.example.backend.exchange.dto.*
import java.sql.ResultSet

fun toDTO(rs: ResultSet, type: ExchangeType): ExchangeDTO {
    return ExchangeDTO(
        bankId = rs.getLong("bank_id"),
        currencyId = rs.getLong("currency_id"),
        bestRate = rs.getBigDecimal("best_rate"),
        type = type
    )
}

fun ExchangeOrder.toDTO() = ExchangeOrderResponse(
    id = this.id!!,
    userId = this.userId,
    bankId = this.bankId,
    fromCurrencyId = this.fromCurrencyId,
    toCurrencyId = this.toCurrencyId,
    fromAmount = this.fromAmount,
    toAmount = this.toAmount,
    exchangeRate = this.exchangeRate,
    status = this.status,
    requestedAt = this.requestedAt!!
)

fun ExchangeLedger.toDTO() = ExchangeLedgerResponse(
    id = this.id!!,
    userId = this.userId,
    fromCurrencyId = this.fromCurrencyId,
    toCurrencyId = this.toCurrencyId,
    fromAmount = this.fromAmount,
    toAmount = this.toAmount,
    exchangeRate = this.exchangeRate,
    commissionCurrencyId = this.commissionCurrencyId,
    commissionRate = this.commissionRate,
    commissionAmount = this.commissionAmount,
    type = this.type,
    createdAt = this.createdAt!!
)

fun Transaction.toDTO() = TransactionResponse(
    id = this.id!!,
    userId = this.userId,
//    walletId = this.walletId,
    orderId = this.orderId,
    fromCurrencyId = this.fromCurrencyId,
    toCurrencyId = this.toCurrencyId,
    fromAmount = this.fromAmount,
    toAmount = this.toAmount,
    exchangeRate = this.exchangeRate,
    commissionCurrencyId = this.commissionCurrencyId,
    commissionAmount = this.commissionAmount,
    profitCurrencyId = this.profitCurrencyId,
    profit = this.profit,
    createdAt = this.createdAt!!
)
