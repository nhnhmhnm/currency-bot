package org.example.backend.user.service

import org.example.backend.enums.BalanceOwnerType
import org.example.backend.enums.WalletFxHistoryType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.user.domain.Account
import org.example.backend.user.domain.Wallet
import org.example.backend.user.dto.BalanceRef
import org.example.backend.user.dto.MoneyMovementCommand
import org.example.backend.user.dto.WalletFxCommand
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class MoneyMovementServiceImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val walletFxHistoryService: WalletFxHistoryService
) : MoneyMovementService {

    @Transactional
    override fun transfer(command: MoneyMovementCommand): BigDecimal? {
        require(command.amount > BigDecimal.ZERO) { "amount must be positive" }

        val sourceWallet = lockWallet(command.source, command.currencyId)
        val sourceAccount = lockAccount(command.source, command.currencyId)
        val targetWallet = lockWallet(command.target, command.currencyId)
        val targetAccount = lockAccount(command.target, command.currencyId)

        debitWallet(sourceWallet, command.amount)
        debitAccount(sourceAccount, command.amount)
        creditWallet(targetWallet, command.amount)
        creditAccount(targetAccount, command.amount)

        sourceWallet?.let { walletRepository.save(it) }
        sourceAccount?.let { accountRepository.save(it) }
        targetWallet?.let { walletRepository.save(it) }
        targetAccount?.let { accountRepository.save(it) }

        recordWalletHistory(command, sourceWallet, WalletFxHistoryType.USER_TO_COMPANY)
        recordWalletHistory(command, targetWallet, WalletFxHistoryType.COMPANY_TO_USER)

        return targetWallet?.balance ?: targetAccount?.balance
    }

    private fun lockWallet(ref: BalanceRef, currencyId: Long): Wallet? {
        if (ref.type != BalanceOwnerType.USER_WALLET) return null

        val userId = ref.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)
        return walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)
    }

    private fun lockAccount(ref: BalanceRef, currencyId: Long): Account? {
        if (ref.type != BalanceOwnerType.COMPANY_ACCOUNT) return null

        val accountId = ref.id ?: throw UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND)
        val account = accountRepository.findByIdForUpdate(accountId)
            ?: throw UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND)

        if (account.currencyId != currencyId) {
            throw UserException(ErrorCode.CURRENCY_MISMATCH)
        }

        return account
    }

    private fun debitWallet(wallet: Wallet?, amount: BigDecimal) {
        if (wallet == null) return
        if (wallet.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_WALLET_BALANCE)
        }
        wallet.balance = wallet.balance.minus(amount)
    }

    private fun debitAccount(account: Account?, amount: BigDecimal) {
        if (account == null) return
        if (account.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE)
        }
        account.balance = account.balance.minus(amount)
    }

    private fun creditWallet(wallet: Wallet?, amount: BigDecimal) {
        if (wallet == null) return
        wallet.balance = wallet.balance.plus(amount)
    }

    private fun creditAccount(account: Account?, amount: BigDecimal) {
        if (account == null) return
        account.balance = account.balance.plus(amount)
    }

    private fun recordWalletHistory(
        command: MoneyMovementCommand,
        wallet: Wallet?,
        type: WalletFxHistoryType
    ) {
        if (wallet == null) return

        walletFxHistoryService.record(
            WalletFxCommand(
                userId = wallet.userId,
                walletId = wallet.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND),
                orderId = command.orderId,
                currencyId = command.currencyId,
                amount = command.amount,
                balanceAfter = wallet.balance,
                type = type
            )
        )
    }
}
