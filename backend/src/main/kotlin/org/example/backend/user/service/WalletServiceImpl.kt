package org.example.backend.user.service

import org.example.backend.enums.UserType
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
            ?: error("지갑이 존재하지 않습니다. userId=$userId, currencyId=$currencyId")

    @Transactional
    override fun connectAccount(userId: Long, bankId: Long, accountNum: String) {
        // 계좌 조회 (은행 번호 + 계좌 번호)
        val account = accountRepository.findByBankIdAndAccountNum(bankId, accountNum)
            ?: throw IllegalArgumentException("해당 은행 계좌를 찾을 수 없습니다.")

        // 해당 계좌가 본인 소유인지 확인
        if (account.userId != userId) {
            throw IllegalArgumentException("해당 계좌는 사용자에게 속하지 않습니다.")
        }

        // 해당 통화에 해당하는 지갑 찾기
        val wallet = walletRepository.findByUserIdAndCurrencyId(userId, account.currencyId)
            ?: throw IllegalArgumentException("해당 지갑을 찾을 수 없습니다.")

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
            ?: throw IllegalArgumentException("지갑이 존재하지 않습니다.")

        // 연결된 계좌 확인
        val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: throw IllegalArgumentException("지갑에 연결된 계좌가 없습니다.")

        // SUPER 계정 찾기
        val superAccount = accountRepository.findByCurrencyIdAndUser_Type(currencyId, UserType.SUPER)
            ?: throw IllegalArgumentException("SUPER 계좌를 찾을 수 없습니다.")

        // 금액 검증
        if (userAccount.balance < amount) {
            throw IllegalArgumentException("계좌 잔액이 부족합니다.")
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
            ?: throw IllegalArgumentException("지갑이 존재하지 않습니다.")

        // 금액 검증
        if (wallet.balance < amount) {
            throw IllegalArgumentException("지갑 잔액이 부족합니다.")
        }

        // 연결된 계좌 확인
        val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
            ?: throw IllegalArgumentException("지갑에 연결된 계좌가 없습니다.")

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
            .orElseThrow { IllegalArgumentException("출금할 회사 계좌를 찾을 수 없습니다.") }
        val toAccount = accountRepository.findById(toAccountId)
            .orElseThrow { IllegalArgumentException("입금할 회사 계좌를 찾을 수 없습니다.") }

        // 금액 검증
        require(fromAccount.balance >= amount) {
            IllegalArgumentException("출금할 회사 계좌 잔액이 부족합니다.")
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
            .orElseThrow { IllegalArgumentException("회사의 계좌를 찾을 수 없습니다.") }

        if (amount > BigDecimal.ZERO) { // 은행 -> 회사 계좌
            companyAccount.balance = companyAccount.balance.plus(amount) // amount : 환전된 금액 toAmount
        }
        else { // 회사 계좌 -> 은행
            require(companyAccount.balance >= amount.abs()) {
                IllegalArgumentException("회사의 계좌 잔액이 부족합니다.") }

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
            .orElseThrow { IllegalArgumentException("회사의 계좌를 찾을 수 없습니다.") }
        // 유저 지갑 조회 및 잠금
        val userWallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw IllegalArgumentException("유저 지갑이 존재하지 않습니다.")

        val move = amount.setScale(2, RoundingMode.DOWN)

        // 회사 계좌 잔액 검증 및 차감
        if (companyAccount.balance >= move) {
            "회사 계좌 잔액 부족"
        }
        companyAccount.balance = companyAccount.balance.minus(move)

        // 유저 지갑 가산
        userWallet.balance = userWallet.balance.plus(move)

        // 저장
        accountRepository.save(companyAccount)
        walletRepository.save(userWallet)

        return userWallet.balance
    }

    @Transactional
    override fun userToCompany(userId: Long, currencyId: Long, accountId: Long, amount: BigDecimal): BigDecimal {
        // 유저 지갑 조회 및 잠금
        val userWallet = walletRepository.findByUserIdAndCurrencyIdForUpdate(userId, currencyId)
            ?: throw IllegalArgumentException("유저 지갑이 존재하지 않습니다.")

        // 회사 목적 계좌 조회 및 잠금 + 통화 일치 체크
        val companyAccount = accountRepository.findByIdForUpdate(accountId)
            ?: throw IllegalArgumentException("회사의 계좌를 찾을 수 없습니다.")

        check(companyAccount.currencyId == currencyId) {
            "회사의 계좌 통화와 요청한 통화가 일치하지 않습니다."
        }

        val move = amount.setScale(2, RoundingMode.DOWN)

        // 유저 지갑 잔액 검증 및 차감
        check(userWallet.balance >= move) { "유저 지갑 잔액이 부족합니다." }
        userWallet.balance = userWallet.balance.minus(move)

        // 회사 계좌 가산
        companyAccount.balance = companyAccount.balance.plus(move)

        // 저장
        walletRepository.save(userWallet)
        accountRepository.save(companyAccount)

        return userWallet.balance
    }
}