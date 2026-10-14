package org.example.backend.finance.service

import org.example.backend.finance.domain.Journal
import org.example.backend.finance.domain.JournalLine
import org.example.backend.finance.domain.JournalSource
import org.example.backend.finance.domain.LedgerAccountType
import org.example.backend.finance.repository.JournalLineRepository
import org.example.backend.finance.repository.JournalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

data class BalanceMovement(
    val accountType: LedgerAccountType,
    val accountId: Long,
    val currencyId: Long,
    val before: BigDecimal,
    val after: BigDecimal
) {
    val delta: BigDecimal get() = after.subtract(before)
}

@Service
class JournalService(
    private val journals: JournalRepository,
    private val lines: JournalLineRepository
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun record(sourceType: JournalSource, sourceId: Long, movements: List<BalanceMovement>) {
        require(movements.isNotEmpty())
        require(movements.all { it.delta.signum() != 0 })
        require(movements.map { it.accountType to it.accountId }.distinct().size == movements.size)
        val entries = movements.map { movement ->
            val debitSide = movement.accountType == LedgerAccountType.COMPANY_CASH
            val amount = movement.delta.abs()
            val debit = if ((movement.delta.signum() > 0) == debitSide) amount else BigDecimal.ZERO
            val credit = if (debit.signum() == 0) amount else BigDecimal.ZERO
            movement to (debit to credit)
        }
        entries.groupBy { it.first.currencyId }.forEach { (currencyId, currencyEntries) ->
            val debit = currencyEntries.fold(BigDecimal.ZERO) { sum, entry -> sum.add(entry.second.first) }
            val credit = currencyEntries.fold(BigDecimal.ZERO) { sum, entry -> sum.add(entry.second.second) }
            require(debit.compareTo(credit) == 0) { "Unbalanced journal for currencyId=$currencyId" }
        }
        val journalId = journals.saveAndFlush(Journal(sourceType = sourceType, sourceId = sourceId)).id!!
        lines.saveAll(entries.mapIndexed { index, (movement, sides) ->
            JournalLine(
                journalId = journalId,
                lineNumber = index + 1,
                accountType = movement.accountType,
                accountId = movement.accountId,
                currencyId = movement.currencyId,
                debit = sides.first,
                credit = sides.second,
                balanceBefore = movement.before,
                balanceAfter = movement.after
            )
        })
    }
}
