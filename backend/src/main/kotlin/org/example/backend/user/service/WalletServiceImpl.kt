package org.example.backend.user.service

import org.example.backend.enums.UserType
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

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


    /*
    회사 계좌 수수료 계좌 1,차익 계좌 1, 유저 돈 받는 지갑 3

    buy  fromAmount -> toAmount
    유저 지갑에서 잔액이 fromAmount보다 더 있는지 확인하고
    회사 지갑에 금액 이동(유저 - 회사 +) 원화

    회사 지갑에서 잔액이 fromAmount보다 더 있는지 확인하고
    회사 원화 -  외화 +

    회사 외화 -> 유저 외화 +
     */
    @Transactional
    override fun settleFxBuy(
        orderId: Long,
        userId: Long,
        fromCurrencyId: Long,
        toCurrencyId: Long,
        amount: BigDecimal,
        commissionAmount: BigDecimal,
        toAmount: BigDecimal
    ) {
        val companyId = 1L // 회사(=SUPER) 사용자 ID

        val userFrom = getForUpdate(userId, fromCurrencyId)      // 사용자 KRW
        val userTo = getForUpdate(userId, toCurrencyId)        // 사용자 외화
        val companyFrom = getForUpdate(companyId, fromCurrencyId) // 회사 KRW

        // 1) 사용자 KRW 차감 (amount 전체)
        require(userFrom.balance >= amount) { "지갑 잔액 부족" }
        userFrom.balance -= amount

        // 2) 회사 KRW 수수료 가산
        if (commissionAmount > BigDecimal.ZERO) {
            companyFrom.balance += commissionAmount
        }

        // 3) 사용자 외화 가산
        userTo.balance += toAmount

        // flush는 @Transactional 커밋 시 더티체킹으로 자동 반영
    }

    @Transactional
    override fun settleFxSell(
        orderId: Long,
        userId: Long,
        fromCurrencyId: Long,
        toCurrencyId: Long,
        amount: BigDecimal,
        commissionAmount: BigDecimal,
        toAmount: BigDecimal
    ) {
        val companyId = 1L

        val userFrom = getForUpdate(userId, fromCurrencyId)      // 사용자 외화
        val userTo = getForUpdate(userId, toCurrencyId)        // 사용자 KRW
        val companyTo = getForUpdate(companyId, toCurrencyId)    // 회사 KRW

        // 1) 사용자 외화 차감
        require(userFrom.balance >= amount) { "지갑 잔액 부족" }
        userFrom.balance -= amount

        // 2) 회사 KRW 수수료 가산
        if (commissionAmount > BigDecimal.ZERO) {
            companyTo.balance += commissionAmount
        }

        // 3) 사용자 KRW 가산
        userTo.balance += toAmount
    }

    //
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

}
