package org.example.backend.user.service

import org.example.backend.enums.BalanceOwnerType
import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.WalletFxHistoryType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.finance.domain.InternalAccount
import org.example.backend.finance.repository.InternalAccountRepository
import org.example.backend.user.domain.Wallet
import org.example.backend.user.dto.BalanceRef
import org.example.backend.user.dto.MoneyMovementCommand
import org.example.backend.user.dto.WalletFxCommand
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class MoneyMovementServiceImpl(
    private val walletRepository: WalletRepository,
    private val internalAccountRepository: InternalAccountRepository,
    private val walletFxHistoryService: WalletFxHistoryService
) : MoneyMovementService {

    @Transactional
    override fun transfer(command: MoneyMovementCommand): BigDecimal? {
        require(command.amount > BigDecimal.ZERO) { "amount must be positive" }

        val sourceWallet = lockWallet(command.source, command.currencyId)
        val sourceAccount = lockAccount(command.source, command.currencyId)
        val targetWallet = lockWallet(command.target, command.currencyId)
        val targetAccount = lockAccount(command.target, command.currencyId)

        when {
            sourceWallet != null && targetAccount != null -> {
                debitWallet(sourceWallet, command.amount)
                if (targetAccount.code == CompanyAccount.COMMISSION_ACCOUNT) {
                    creditAccount(targetAccount, command.amount)
                }
            }
            sourceAccount != null && targetWallet != null -> creditWallet(targetWallet, command.amount)
            sourceAccount != null && targetAccount != null -> {
                if (targetAccount.code == CompanyAccount.COMMISSION_ACCOUNT) {
                    creditAccount(targetAccount, command.amount)
                } else {
                    debitAccount(sourceAccount, command.amount)
                    creditAccount(targetAccount, command.amount)
                }
            }
            command.source.type == BalanceOwnerType.BANK_EXTERNAL && targetAccount != null ->
                creditAccount(targetAccount, command.amount)
            sourceAccount != null && command.target.type == BalanceOwnerType.BANK_EXTERNAL ->
                debitAccount(sourceAccount, command.amount)
            else -> error("Unsupported money movement: ${command.source.type} -> ${command.target.type}")
        }

        sourceWallet?.let { walletRepository.save(it) }
        sourceAccount?.let { internalAccountRepository.save(it) }
        targetWallet?.let { walletRepository.save(it) }
        targetAccount?.let { internalAccountRepository.save(it) }

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

    private fun lockAccount(ref: BalanceRef, currencyId: Long): InternalAccount? {
        if (ref.type != BalanceOwnerType.COMPANY_ACCOUNT) return null

        val accountId = ref.id ?: throw UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND)
        val account = internalAccountRepository.findByIdForUpdate(accountId)
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

    private fun debitAccount(account: InternalAccount?, amount: BigDecimal) {
        if (account == null) return
        if (account.balance < amount) {
            throw UserException(ErrorCode.COMPANY_LIQUIDITY_INSUFFICIENT)
        }
        account.balance = account.balance.minus(amount)
    }

    private fun creditWallet(wallet: Wallet?, amount: BigDecimal) {
        if (wallet == null) return
        wallet.balance = wallet.balance.plus(amount)
    }

    private fun creditAccount(account: InternalAccount?, amount: BigDecimal) {
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
