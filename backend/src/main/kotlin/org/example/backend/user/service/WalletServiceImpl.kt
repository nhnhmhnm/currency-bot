package org.example.backend.user.service

import org.example.backend.enums.CompanyAccount
import org.example.backend.enums.TransferReason
import org.example.backend.enums.WalletTransactionType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.user.dto.BalanceRef
import org.example.backend.user.dto.DepositWithdrawalCommand
import org.example.backend.user.dto.MoneyMovementCommand
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class WalletServiceImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val depositWithdrawalService: DepositWithdrawalService,
    private val moneyMovementService: MoneyMovementService
) : WalletService {

    @Transactional
    override fun connectAccount(userId: Long, bankId: Long, accountNum: String) {
        val account = accountRepository.findByBankIdAndAccountNum(bankId, accountNum)
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_FOUND)

        if (account.userId != userId) {
            throw UserException(ErrorCode.INCORRECT_OWNER)
        }

        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, account.currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        wallet.accountId = account.id
        wallet.isConnected = true

        walletRepository.save(wallet)
    }

    override fun checkBalance(userId: Long, currencyId: Long): BigDecimal {
        return walletRepository.findBalanceByUserIdAndCurrencyId(userId, currencyId)
    }

    @Transactional
    override fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_CONNECTED)

        val superAccount = accountRepository.findByBankIdAndAccountNum(
            CompanyAccount.KRW_ACCOUNT.bankId,
            CompanyAccount.KRW_ACCOUNT.accountNum
        ) ?: throw UserException(ErrorCode.SUPER_ACCOUNT_NOT_FOUND)

        if (userAccount.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE)
        }

        userAccount.balance = userAccount.balance.minus(amount)
        superAccount.balance = superAccount.balance.plus(amount)

        superAccount.balance = superAccount.balance.minus(amount)
        wallet.balance = wallet.balance.plus(amount)

        accountRepository.save(userAccount)
        accountRepository.save(superAccount)
        walletRepository.save(wallet)

        depositWithdrawalService.record(
            DepositWithdrawalCommand(
                userId = userId,
                walletId = wallet.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND),
                currencyId = currencyId,
                amount = amount,
                type = WalletTransactionType.DEPOSIT
            )
        )

        return wallet.balance
    }

    @Transactional
    override fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        if (wallet.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_WALLET_BALANCE)
        }

        val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_CONNECTED)

        wallet.balance = wallet.balance.minus(amount)
        userAccount.balance = userAccount.balance.plus(amount)

        walletRepository.save(wallet)
        accountRepository.save(userAccount)

        depositWithdrawalService.record(
            DepositWithdrawalCommand(
                userId = userId,
                walletId = wallet.id ?: throw UserException(ErrorCode.WALLET_NOT_FOUND),
                currencyId = currencyId,
                amount = amount,
                type = WalletTransactionType.WITHDRAWAL
            )
        )

        return wallet.balance
    }

    @Transactional
    override fun companyToCompany(fromAccountId: Long, toAccountId: Long, amount: BigDecimal): BigDecimal {
        val fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow { UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND) }

        return moneyMovementService.transfer(
            MoneyMovementCommand(
                source = BalanceRef.companyAccount(fromAccountId),
                target = BalanceRef.companyAccount(toAccountId),
                currencyId = fromAccount.currencyId,
                amount = amount,
                orderId = null,
                reason = TransferReason.FX_SELL_COMMISSION
            )
        ) ?: BigDecimal.ZERO
    }

    @Transactional
    override fun bankToCompany(accountId: Long, bankId: Long, amount: BigDecimal): BigDecimal {
        val companyAccount = accountRepository.findById(accountId)
            .orElseThrow { UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND) }

        val source = if (amount > BigDecimal.ZERO) {
            BalanceRef.bankExternal()
        } else {
            BalanceRef.companyAccount(accountId)
        }
        val target = if (amount > BigDecimal.ZERO) {
            BalanceRef.companyAccount(accountId)
        } else {
            BalanceRef.bankExternal()
        }

        return moneyMovementService.transfer(
            MoneyMovementCommand(
                source = source,
                target = target,
                currencyId = companyAccount.currencyId,
                amount = amount.abs(),
                orderId = null,
                reason = if (amount > BigDecimal.ZERO) {
                    TransferReason.FX_BUY_BANK_TO_COMPANY
                } else {
                    TransferReason.FX_BUY_COMPANY_TO_BANK
                }
            )
        ) ?: BigDecimal.ZERO
    }

    @Transactional
    override fun companyToUser(
        accountId: Long,
        userId: Long,
        currencyId: Long,
        amount: BigDecimal,
        orderId: Long?
    ): BigDecimal {
        return moneyMovementService.transfer(
            MoneyMovementCommand(
                source = BalanceRef.companyAccount(accountId),
                target = BalanceRef.userWallet(userId),
                currencyId = currencyId,
                amount = amount,
                orderId = orderId,
                reason = TransferReason.FX_BUY_USER_SETTLEMENT
            )
        ) ?: BigDecimal.ZERO
    }

    @Transactional
    override fun userToCompany(
        userId: Long,
        currencyId: Long,
        accountId: Long,
        amount: BigDecimal,
        orderId: Long?
    ): BigDecimal {
        return moneyMovementService.transfer(
            MoneyMovementCommand(
                source = BalanceRef.userWallet(userId),
                target = BalanceRef.companyAccount(accountId),
                currencyId = currencyId,
                amount = amount,
                orderId = orderId,
                reason = TransferReason.FX_BUY_USER_PAYMENT
            )
        ) ?: BigDecimal.ZERO
    }
}
