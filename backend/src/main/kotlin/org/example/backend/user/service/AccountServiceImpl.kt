package org.example.backend.user.service

import org.example.backend.user.domain.Account
import org.example.backend.user.dto.AccountCreateRequest
import org.example.backend.user.repository.AccountRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountServiceImpl(
  private val accountRepository: AccountRepository
) : AccountService{
  override fun createAccount(request: AccountCreateRequest) {
    // 중복 계좌 확인
    if (accountRepository.findByUserIdAndCurrencyId(userId = request.userid, currencyId = request.currencyId) != null) {
      throw IllegalArgumentException("이미 등록된 계좌가 있습니다.")
    }

    // 객체 생성
    val account = Account(
      userId = request.userid,
      bankId = request.bankId,
      currencyId = request.currencyId,
      accountNum = request.accountNum,
      balance = BigDecimal("1000000000.00"),
      isActive = true
    )
    
    // 저장
    accountRepository.save(account)
  }
}