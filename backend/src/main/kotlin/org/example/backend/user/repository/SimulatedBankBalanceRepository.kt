package org.example.backend.user.repository

import jakarta.persistence.LockModeType
import org.example.backend.user.domain.SimulatedBankBalance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface SimulatedBankBalanceRepository : JpaRepository<SimulatedBankBalance, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SimulatedBankBalance b where b.accountId = :accountId")
    fun findByAccountIdForUpdate(accountId: Long): SimulatedBankBalance?
}
