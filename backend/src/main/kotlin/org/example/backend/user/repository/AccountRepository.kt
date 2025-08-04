package org.example.backend.user.repository

import org.example.backend.user.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, Long> {
  fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Account?
  fun findByBankIdAndAccountNum(bankId: Long, accountNum: String): Account?
}