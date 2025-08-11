package org.example.backend.user.repository

import jakarta.persistence.LockModeType
import org.example.backend.user.domain.Wallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface WalletRepository : JpaRepository<Wallet, Long> {
    fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Wallet

    @Query("""
        SELECT w.balance FROM Wallet w 
        WHERE w.userId = :userId 
        AND w.currencyId = :currencyId 
        """)
    fun findBalanceByUserIdAndCurrencyId(
        @Param("userId") userId: Long,
        @Param("currencyId") currencyId: Long
    ): BigDecimal

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId and w.currencyId = :currencyId")
    fun findByUserIdAndCurrencyIdForUpdate(userId: Long, currencyId: Long): Wallet?
}
