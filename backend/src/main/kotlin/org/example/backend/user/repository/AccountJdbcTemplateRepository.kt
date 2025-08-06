package org.example.backend.user.repository

import org.example.backend.enums.UserType
import org.example.backend.user.domain.Account

interface AccountJdbcTemplateRepository {
  fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Account?
  fun findByBankIdAndAccountNum(bankId: Long, accountNum: String): Account?
  fun findSuperByCurrencyIdAndUserType(currencyId: Long, type: UserType): Account?
}