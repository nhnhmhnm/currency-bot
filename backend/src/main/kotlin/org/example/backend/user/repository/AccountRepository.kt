package org.example.backend.user.repository

import jakarta.persistence.LockModeType
import org.example.backend.user.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface AccountRepository : JpaRepository<Account, Long> {
  fun findByBankIdAndAccountNum(bankId: Long, accountNum: String): Account?

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :id")
  fun findByIdForUpdate(id: Long): Account?

}
