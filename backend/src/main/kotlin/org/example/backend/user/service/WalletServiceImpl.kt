package org.example.backend.user.service

import org.example.backend.enums.UserType
import org.example.backend.user.repository.AccountJdbcTemplateRepository
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class WalletServiceImpl(
  private val walletRepository: WalletRepository,
  private val accountRepository: AccountJdbcTemplateRepository
) : WalletService {
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
  }

  override fun checkBalance(userId: Long, currencyId: Long): BigDecimal {
    return walletRepository.findBalanceByUserIdAndCurrencyId(userId, currencyId)
  }

  override fun depositFromAccount(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
    // 유저 지갑 조회
    val wallet = walletRepository.findByUserIdAndCurrencyId(userId, currencyId)
      ?: throw IllegalArgumentException("지갑이 존재하지 않습니다.")

    // 연결된 계좌 확인
    val userAccount = wallet.accountId?.let { accountRepository.findById(it).orElse(null) }
      ?: throw IllegalArgumentException("지갑에 연결된 계좌가 없습니다.")

    // SUPER 계정 찾기
    val superAccount = accountRepository.findSuperByCurrencyIdAndUserType(currencyId, UserType.SUPER)
      ?: throw IllegalArgumentException("SUPER 계좌를 찾을 수 없습니다.")

    // 금액 검증
    if (userAccount.balance < amount) {
      throw IllegalArgumentException("계좌 잔액이 부족합니다.")
    }

    // userAccount -> superAccount 송금
    userAccount.balance = userAccount.balance.minus(amount)
    superAccount.balance = superAccount.balance.plus(amount)

    // SUPER 계좌에서 입금 확인 후, 지갑에 금액 충전
    wallet.balance = wallet.balance.plus(amount)

    accountRepository.save(userAccount)
    accountRepository.save(superAccount)
    walletRepository.save(wallet)

    return wallet.balance
  }

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
}