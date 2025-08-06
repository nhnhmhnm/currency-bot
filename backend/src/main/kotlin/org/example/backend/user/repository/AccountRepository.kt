package org.example.backend.user.repository

import org.example.backend.enums.UserType
import org.example.backend.user.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AccountRepository : JpaRepository<Account, Long> {
  fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Account?
  fun findByBankIdAndAccountNum(bankId: Long, accountNum: String): Account?

  @Query("""
        SELECT a FROM Account a 
        WHERE a.currencyId = :currencyId 
        AND a.user.type = :type
        """)
  fun findByCurrencyIdAndUserType(
    @Param("currencyId") currencyId: Long,
    @Param("type") type: UserType
  ): Account?
}