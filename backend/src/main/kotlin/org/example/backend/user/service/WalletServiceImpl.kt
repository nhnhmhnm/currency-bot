package org.example.backend.user.service

import org.example.backend.enums.UserType
import org.example.backend.exception.ErrorCode
import org.example.backend.exception.UserException
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class WalletServiceImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository
) : WalletService {
    private fun getForUpdate(userId: Long, currencyId: Long) =
        walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

    @Transactional
    override fun connectAccount(userId: Long, bankId: Long, accountNum: String) {
        // 계좌 조회 (은행 번호 + 계좌 번호)
        val account = accountRepository.findByBankIdAndAccountNum(bankId, accountNum)
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_FOUND)

        // 해당 계좌가 본인 소유인지 확인
        if (account.userId != userId) {
            throw UserException(ErrorCode.INCORRECT_OWNER)
        }

        // 해당 통화에 해당하는 지갑 찾기
        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, account.currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        // 지갑에 계좌 연결
        wallet.accountId = account.id
        wallet.isConnected = true

        walletRepository.save(wallet)
    }

    override fun checkBalance(userId: Long, currencyId: Long): BigDecimal {
        return walletRepository.findBalanceByUserIdAndCurrencyId(userId, currencyId)
    }

    @Transactional
    override fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        // 유저 지갑 조회
        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        // 연결된 계좌 확인
        val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_CONNECTED)

        // SUPER 계정 찾기
        val superAccount = accountRepository.findByCurrencyIdAndUser_Type(currencyId, UserType.SUPER)
            ?: throw UserException(ErrorCode.SUPER_ACCOUNT_NOT_FOUND)

        // 금액 검증
        if (userAccount.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE)
        }

        // userAccount -> superAccount 송금
        userAccount.balance = userAccount.balance.minus(amount)
        superAccount.balance = superAccount.balance.plus(amount)

        // SUPER 계좌에서 입금 확인 후, 지갑에 금액 충전
        superAccount.balance = superAccount.balance.minus(amount)
        wallet.balance = wallet.balance.plus(amount)

        accountRepository.save(userAccount)
        accountRepository.save(superAccount)
        walletRepository.save(wallet)

        return wallet.balance
    }

    @Transactional
    override fun withdrawToAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        // 지갑 조회
        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        // 금액 검증
        if (wallet.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_WALLET_BALANCE)
        }

        // 연결된 계좌 확인
        val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: throw UserException(ErrorCode.ACCOUNT_NOT_CONNECTED)

        // 지갑 -> 계좌 이체
        wallet.balance = wallet.balance.minus(amount)
        userAccount.balance = userAccount.balance.plus(amount)

        walletRepository.save(wallet)
        accountRepository.save(userAccount)

        return wallet.balance
    }

    @Transactional
    override fun companyToCompany(fromAccountId: Long, toAccountId: Long, amount: BigDecimal): BigDecimal {
        // 회사 계좌 조회
        val fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow { UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND) }
        val toAccount = accountRepository.findById(toAccountId)
            .orElseThrow { UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND) }

        // 금액 검증
        require(fromAccount.balance >= amount) {
            UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE)
        }

        // 회사 계좌 간 송금
        fromAccount.balance = fromAccount.balance.minus(amount)
        toAccount.balance = toAccount.balance.plus(amount)

        // 계좌 정보 저장
        accountRepository.save(fromAccount)
        accountRepository.save(toAccount)

        // 최종 잔액 반환 -> 나중에 dto로 변경
        return toAccount.balance
    }

    @Transactional
    override fun bankToCompany(accountId: Long, bankId: Long, amount: BigDecimal): BigDecimal {
        // 회사 계좌 조회
        val companyAccount = accountRepository.findById(accountId)
            .orElseThrow { UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND) }

        if (amount > BigDecimal.ZERO) { // 은행 -> 회사 계좌
            companyAccount.balance = companyAccount.balance.plus(amount) // amount : 환전된 금액 toAmount
        }
        else { // 회사 계좌 -> 은행
            require(companyAccount.balance >= amount.abs()) {
                UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE) }

            companyAccount.balance = companyAccount.balance.minus(amount.abs()) // amount : 환전된 금액 rawToAmount
        }

        // 계좌 정보 저장
        accountRepository.save(companyAccount)

        // 최종 잔액 반환 -> 나중에 dto로 변경
        return companyAccount.balance
    }

    @Transactional
    override fun companyToUser(accountId: Long, userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
        // 회사 계좌 조회 및 잠금
        val companyAccount = accountRepository.findById(accountId)
            .orElseThrow { UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND) }

        // 유저 지갑 조회 및 잠금
        val userWallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        if (companyAccount.currencyId != currencyId) {
            throw UserException(ErrorCode.CURRENCY_MISMATCH)
        }

        // 회사 계좌 잔액 검증 및 차감
        if (companyAccount.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE)
        }

        // 회사 계좌에서 유저 지갑으로 송금
        companyAccount.balance = companyAccount.balance.minus(amount)

        // 유저 지갑 가산
        userWallet.balance = userWallet.balance.plus(amount)

        // 저장
        accountRepository.save(companyAccount)
        walletRepository.save(userWallet)

        return userWallet.balance
    }

    @Transactional
    override fun userToCompany(userId: Long, currencyId: Long, accountId: Long, amount: BigDecimal): BigDecimal {
        // 유저 지갑 조회 및 잠금
        val userWallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw UserException(ErrorCode.WALLET_NOT_FOUND)

        // 회사 목적 계좌 조회 및 잠금 + 통화 일치 체크
        val companyAccount = accountRepository.findByIdForUpdate(accountId)
            ?: throw UserException(ErrorCode.COMPANY_ACCOUNT_NOT_FOUND)

        if (companyAccount.currencyId != currencyId) {
            throw UserException(ErrorCode.CURRENCY_MISMATCH)
        }

        // 유저 지갑 잔액 검증 및 차감
        if (userWallet.balance < amount) {
            throw UserException(ErrorCode.INSUFFICIENT_WALLET_BALANCE)
        }

        // 유저 지갑에서 회사 계좌로 송금
        userWallet.balance = userWallet.balance.minus(amount)

        // 회사 계좌 가산
        companyAccount.balance = companyAccount.balance.plus(amount)

        // 저장
        walletRepository.save(userWallet)
        accountRepository.save(companyAccount)

        return userWallet.balance
    }
}