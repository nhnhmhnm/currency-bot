package org.example.backend.exchange.service

import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.finance.domain.JournalSource
import org.example.backend.finance.domain.LedgerAccountType
import org.example.backend.finance.service.BalanceMovement
import org.example.backend.finance.service.JournalService
import org.example.backend.user.dto.WalletFxCommand
import org.example.backend.user.repository.WalletRepository
import org.example.backend.user.service.WalletFxHistoryService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class PostingService(
    private val walletRepository: WalletRepository,
    private val internalAccountRepository: InternalAccountRepository,
    private val walletFxHistoryService: WalletFxHistoryService,
    private val journalService: JournalService,
    private val operations: ExchangeOperationsSnapshotService
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun post(plan: PostingPlan) {
        val wallets = plan.walletPostings.map { it.currencyId }.distinct().sorted().associateWith { currencyId ->
            operations.measureLockWait { walletRepository.findByUserIdAndCurrencyIdForUpdate(plan.userId, currencyId) }
                ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)
        }
        val accounts = plan.accountPostings.map { it.code }.distinct().sortedBy { it.name }.associateWith { code ->
            operations.measureLockWait { internalAccountRepository.findByCodeForUpdate(code) }
                ?.takeIf { it.currencyId == code.currencyId }
                ?: throw UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND)
        }
        val walletBefore = wallets.mapValues { it.value.balance }
        val accountBefore = accounts.mapValues { it.value.balance }

        for (posting in plan.walletPostings) {
            val wallet = wallets.getValue(posting.currencyId)
            val nextBalance = wallet.balance.add(posting.delta)
            if (nextBalance < BigDecimal.ZERO) throw UserException(ErrorCode.INSUFFICIENT_WALLET_BALANCE)
            wallet.balance = nextBalance
            walletFxHistoryService.record(
                WalletFxCommand(
                    userId = plan.userId,
                    walletId = wallet.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND),
                    orderId = plan.orderId,
                    currencyId = posting.currencyId,
                    amount = posting.delta.abs(),
                    balanceAfter = nextBalance,
                    type = posting.historyType
                )
            )
        }
        for (posting in plan.accountPostings) {
            val account = accounts.getValue(posting.code)
            val nextBalance = account.balance.add(posting.delta)
            if (nextBalance < BigDecimal.ZERO) throw UserException(ErrorCode.COMPANY_LIQUIDITY_INSUFFICIENT)
            account.balance = nextBalance
        }
        val movements = wallets.map { (currencyId, wallet) ->
            BalanceMovement(LedgerAccountType.CUSTOMER_LIABILITY, wallet.id!!, currencyId, walletBefore.getValue(currencyId), wallet.balance)
        } + accounts.map { (code, account) ->
            BalanceMovement(
                if (code == org.example.backend.enums.CompanyAccount.COMMISSION_ACCOUNT) LedgerAccountType.COMMISSION_REVENUE else LedgerAccountType.COMPANY_CASH,
                account.id!!,
                code.currencyId,
                accountBefore.getValue(code),
                account.balance
            )
        }
        journalService.record(JournalSource.EXCHANGE_ORDER, plan.orderId, movements.filter { it.delta.signum() != 0 })
    }
}
