package org.example.backend.user.service

import org.example.backend.user.domain.Account
import org.example.backend.user.dto.AccountRegistrationRequest
import org.example.backend.user.repository.AccountRepository
import org.example.backend.user.repository.WalletRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class WalletServiceImpl(
  private val walletRepository: WalletRepository,
  private val accountRepository: AccountRepository
) : WalletService {
  override fun registerAccount(userId: Long, request: AccountRegistrationRequest) {
    // 계좌 저장
    val account = Account(
      userId = userId,
      bankId = request.bankId,
      currencyId = request.currencyId,
      accountNum = request.accountNum
    )
    accountRepository.save(account)

    // 해당 지갑 활성화
    val wallet = walletRepository.findByUserIdAndCurrencyId(userId, request.currencyId)
      ?: throw IllegalArgumentException("Wallet not found")

    wallet.isActive = true
    walletRepository.save(wallet)
  }

  override fun checkBalance(userId: Long, currencyId: Long): BigDecimal {
    return walletRepository.findBalanceByUserIdAndCurrencyId(userId, currencyId)
  }

  override fun increase(userId: Long, currencyId: Long, amount: BigDecimal) {
    TODO("Not yet implemented")
  }

  override fun decrease(userId: Long, currencyId: Long, amount: BigDecimal) {
    TODO("Not yet implemented")
  }

}