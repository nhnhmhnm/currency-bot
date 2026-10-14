package org.example.backend.user.service

import org.example.backend.enums.WalletTransactionType
import org.example.backend.enums.CompanyAccount
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.exchange.repository.ExchangeReservationRepository
import org.example.backend.finance.repository.CurrencyRepository
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.finance.domain.JournalSource
import org.example.backend.finance.domain.LedgerAccountType
import org.example.backend.finance.service.BalanceMovement
import org.example.backend.finance.service.JournalService
import org.example.backend.user.dto.DepositWithdrawalCommand
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.UserRepository
import org.example.backend.user.repository.WalletRepository
import org.example.backend.user.repository.SimulatedBankBalanceRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class WalletServiceImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val internalAccountRepository: InternalAccountRepository,
    private val simulatedBankBalanceRepository: SimulatedBankBalanceRepository,
    private val userRepository: UserRepository,
    private val currencyRepository: CurrencyRepository,
    private val reservationRepository: ExchangeReservationRepository,
    private val depositWithdrawalService: DepositWithdrawalService,
    private val journalService: JournalService,
    @Value("\${app.bank.simulation-enabled:false}") private val bankSimulationEnabled: Boolean
) : WalletService {

    @Transactional
    override fun connectAccount(userId: Long, bankId: Long, accountNum: String) {
        lockActiveUser(userId)
        val account = accountRepository.findByBankIdAndAccountNum(bankId, accountNum)
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_FOUND)

        if (account.userId != userId) {
            throw UserException(ErrorCode.INCORRECT_OWNER)
        }
        if (!account.isActive) {
            throw UserException(ErrorCode.ACCOUNT_INACTIVE)
        }

        val wallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, account.currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        wallet.accountId = account.id

        walletRepository.save(wallet)
    }

    override fun checkBalance(userId: Long, currencyId: Long): BigDecimal {
        return walletRepository.findBalanceByUserIdAndCurrencyId(userId, currencyId)
    }

    @Transactional
    override fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        requireBankSimulation()
        lockActiveUser(userId)
        validateTransferAmount(currencyId, amount)
        val wallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)
        val userAccount = lockConnectedAccount(userId, currencyId, wallet.accountId)
        val cashAccount = lockCashAccount(currencyId)
        val bankBalance = simulatedBankBalanceRepository.findByAccountIdForUpdate(userAccount.id!!)
            ?: throw UserException(ErrorCode.SIMULATED_BANK_BALANCE_NOT_FOUND)

        if (bankBalance.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE)
        }

        bankBalance.balance = bankBalance.balance.minus(amount)
        cashAccount.balance = cashAccount.balance.plus(amount)
        wallet.balance = wallet.balance.plus(amount)

        simulatedBankBalanceRepository.save(bankBalance)
        internalAccountRepository.save(cashAccount)
        walletRepository.save(wallet)

        val transfer = depositWithdrawalService.record(
            DepositWithdrawalCommand(
                userId = userId,
                walletId = wallet.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND),
                currencyId = currencyId,
                amount = amount,
                type = WalletTransactionType.DEPOSIT
            )
        )
        journalService.record(
            JournalSource.WALLET_TRANSFER,
            transfer.id,
            listOf(
                BalanceMovement(LedgerAccountType.COMPANY_CASH, cashAccount.id!!, currencyId, cashAccount.balance.minus(amount), cashAccount.balance),
                BalanceMovement(LedgerAccountType.CUSTOMER_LIABILITY, wallet.id!!, currencyId, wallet.balance.minus(amount), wallet.balance)
            )
        )

        return wallet.balance
    }

    @Transactional
    override fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        requireBankSimulation()
        lockActiveUser(userId)
        validateTransferAmount(currencyId, amount)
        val wallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        val userAccount = lockConnectedAccount(userId, currencyId, wallet.accountId)
        val cashAccount = lockCashAccount(currencyId)
        val bankBalance = simulatedBankBalanceRepository.findByAccountIdForUpdate(userAccount.id!!)
            ?: throw UserException(ErrorCode.SIMULATED_BANK_BALANCE_NOT_FOUND)
        val reservedAmount = reservationRepository.sumActiveFunds(userId, currencyId)
        if (wallet.balance.subtract(reservedAmount) < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_WALLET_BALANCE)
        }
        if (cashAccount.balance < amount) {
            throw UserException(ErrorCode.COMPANY_LIQUIDITY_INSUFFICIENT)
        }

        wallet.balance = wallet.balance.minus(amount)
        cashAccount.balance = cashAccount.balance.minus(amount)
        bankBalance.balance = bankBalance.balance.plus(amount)

        walletRepository.save(wallet)
        internalAccountRepository.save(cashAccount)
        simulatedBankBalanceRepository.save(bankBalance)

        val transfer = depositWithdrawalService.record(
            DepositWithdrawalCommand(
                userId = userId,
                walletId = wallet.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND),
                currencyId = currencyId,
                amount = amount,
                type = WalletTransactionType.WITHDRAWAL
            )
        )
        journalService.record(
            JournalSource.WALLET_TRANSFER,
            transfer.id,
            listOf(
                BalanceMovement(LedgerAccountType.COMPANY_CASH, cashAccount.id!!, currencyId, cashAccount.balance.plus(amount), cashAccount.balance),
                BalanceMovement(LedgerAccountType.CUSTOMER_LIABILITY, wallet.id!!, currencyId, wallet.balance.plus(amount), wallet.balance)
            )
        )

        return wallet.balance
    }

    private fun lockActiveUser(userId: Long) {
        val user = userRepository.findByIdForUpdate(userId)
            ?: throw UserException(ErrorCode.USER_NOT_FOUND)
        if (!user.isActive) {
            throw UserException(ErrorCode.INCORRECT_OWNER)
        }
    }

    private fun validateTransferAmount(currencyId: Long, amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw UserException(ErrorCode.INVALID_TRANSFER_AMOUNT)
        }
        val currency = currencyRepository.findById(currencyId).orElseThrow {
            UserException(ErrorCode.CURRENCY_MISMATCH)
        }
        if (amount.stripTrailingZeros().scale() > currency.scale) {
            throw UserException(ErrorCode.INVALID_TRANSFER_AMOUNT)
        }
    }

    private fun requireBankSimulation() {
        if (!bankSimulationEnabled) throw UserException(ErrorCode.BANK_INTEGRATION_UNAVAILABLE)
    }

    private fun lockCashAccount(currencyId: Long) =
        internalAccountRepository.findByCodeForUpdate(CompanyAccount.ofCurrencyId(currencyId))
            ?.also {
                if (it.currencyId != currencyId) throw UserException(ErrorCode.CURRENCY_MISMATCH)
            } ?: throw UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND)

    private fun lockConnectedAccount(userId: Long, currencyId: Long, accountId: Long?) =
        if (accountId == null) {
            throw UserException(ErrorCode.ACCOUNT_NOT_CONNECTED)
        } else {
            val account = accountRepository.findByIdForUpdate(accountId)
                ?: throw UserException(ErrorCode.ACCOUNT_NOT_FOUND)
            if (account.userId != userId) {
                throw UserException(ErrorCode.INCORRECT_OWNER)
            }
            if (account.currencyId != currencyId) {
                throw UserException(ErrorCode.CURRENCY_MISMATCH)
            }
            if (!account.isActive) {
                throw UserException(ErrorCode.ACCOUNT_INACTIVE)
            }
            account
        }

}
