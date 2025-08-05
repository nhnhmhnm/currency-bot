package org.example.backend.user.service

import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class WalletServiceImpl(
  private val walletRepository: WalletRepository,
  private val accountRepository: AccountRepository
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

  override fun increase(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
    TODO("Not yet implemented")
  }

  override fun decrease(userId: Long, currencyId: Long, amount: BigDecimal): BigDecimal {
    TODO("Not yet implemented")
  }
}