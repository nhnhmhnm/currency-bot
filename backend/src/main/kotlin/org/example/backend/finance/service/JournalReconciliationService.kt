package org.example.backend.finance.service

import org.example.backend.enums.OrderStatus
import org.example.backend.enums.OrderType
import org.example.backend.exchange.domain.ReservationStatus
import org.example.backend.exchange.repository.ExchangeOrderRepository
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.finance.domain.JournalSource
import org.example.backend.finance.domain.LedgerAccountType
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.finance.repository.JournalLineRepository
import org.example.backend.finance.repository.JournalRepository
import org.example.backend.user.repository.DepositWithdrawalRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

data class ReconciliationReport(val issues: List<String>) {
    val balanced: Boolean get() = issues.isEmpty()
}

@Service
class JournalReconciliationService(
    private val journals: JournalRepository,
    private val lines: JournalLineRepository,
    private val orders: ExchangeOrderRepository,
    private val reservations: ExchangeReservationRepository,
    private val transfers: DepositWithdrawalRepository,
    private val wallets: WalletRepository,
    private val accounts: InternalAccountRepository
) {
    @Transactional(readOnly = true)
    fun audit(): ReconciliationReport {
        val issues = mutableListOf<String>()
        val journalRows = journals.findAll()
        val journalLines = lines.findAllByOrderByIdAsc()
        val linesByJournal = journalLines.groupBy { it.journalId }
        val journalIds = journalRows.mapNotNull { it.id }.toSet()
        val journalKeys = journalRows.map { it.sourceType to it.sourceId }.toSet()

        for (journal in journalRows) {
            val entries = linesByJournal[journal.id].orEmpty()
            if (entries.isEmpty()) issues += "journal ${journal.id} has no lines"
            for ((currencyId, currencyLines) in entries.groupBy { it.currencyId }) {
                val debit = currencyLines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.debit) }
                val credit = currencyLines.fold(BigDecimal.ZERO) { sum, line -> sum.add(line.credit) }
                if (debit.compareTo(credit) != 0) issues += "journal ${journal.id} currency $currencyId is unbalanced"
            }
        }
        for (line in journalLines) {
            if (line.journalId !in journalIds) issues += "orphan journal line ${line.id}"
            if (line.debit.signum() < 0 || line.credit.signum() < 0 || (line.debit.signum() > 0) == (line.credit.signum() > 0)) {
                issues += "line ${line.id} does not have exactly one positive side"
            }
        }

        val actualBalances = buildMap {
            wallets.findAll().forEach { put(LedgerAccountType.CUSTOMER_LIABILITY to it.id!!, it.balance) }
            accounts.findAll().forEach {
                val type = if (it.code == org.example.backend.enums.CompanyAccount.COMMISSION_ACCOUNT) LedgerAccountType.COMMISSION_REVENUE else LedgerAccountType.COMPANY_CASH
                put(type to it.id!!, it.balance)
            }
        }
        for ((key, accountLines) in journalLines.groupBy { it.accountType to it.accountId }) {
            var expected = accountLines.first().balanceBefore
            for (line in accountLines) {
                if (line.balanceBefore.compareTo(expected) != 0) issues += "account $key has a balance gap at line ${line.id}"
                val delta = if (line.accountType == LedgerAccountType.COMPANY_CASH) line.debit.subtract(line.credit)
                    else line.credit.subtract(line.debit)
                if (line.balanceAfter.compareTo(line.balanceBefore.add(delta)) != 0) issues += "line ${line.id} has an invalid ending balance"
                expected = line.balanceAfter
            }
            if (actualBalances[key]?.compareTo(expected) != 0) issues += "account $key differs from journal ending balance"
        }
        val orderRows = orders.findAll()
        val ordersById = orderRows.associateBy { it.id!! }
        val transferRows = transfers.findAll()
        val transferIds = transferRows.mapNotNull { it.id }.toSet()
        for (journal in journalRows) {
            when (journal.sourceType) {
                JournalSource.EXCHANGE_ORDER -> if (ordersById[journal.sourceId]?.status != OrderStatus.SETTLED) {
                    issues += "journal ${journal.id} has no settled order"
                }
                JournalSource.WALLET_TRANSFER -> if (journal.sourceId !in transferIds) {
                    issues += "journal ${journal.id} has no transfer"
                }
            }
        }
        for (order in orderRows) {
            if (order.status == OrderStatus.SETTLED && order.orderType != OrderType.ARBITRAGE &&
                (JournalSource.EXCHANGE_ORDER to order.id!!) !in journalKeys) {
                issues += "settled order ${order.id} has no journal"
            }
        }
        for (transfer in transferRows) {
            if ((JournalSource.WALLET_TRANSFER to transfer.id!!) !in journalKeys) issues += "transfer ${transfer.id} has no journal"
        }
        val terminal = setOf(OrderStatus.SETTLED, OrderStatus.CANCELED, OrderStatus.FAILED)
        val orderStatuses = orderRows.associate { it.id!! to it.status }
        for (reservation in reservations.findAll()) {
            if (reservation.status == ReservationStatus.ACTIVE && orderStatuses[reservation.orderId] in terminal) {
                issues += "terminal order ${reservation.orderId} retains an active reservation"
            }
        }
        return ReconciliationReport(issues)
    }
}
