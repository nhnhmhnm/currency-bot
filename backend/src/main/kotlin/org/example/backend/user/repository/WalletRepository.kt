package org.example.backend.user.repository

import org.example.backend.user.domain.Wallet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface WalletRepository : JpaRepository<Wallet, Long> {
    fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Wallet

    @Query("SELECT w.balance FROM Wallet w " +
                  "WHERE w.userId = :userId AND w.currencyId = :currencyId")
    fun findBalanceByUserIdAndCurrencyId(
        @Param("userId") userId: Long,
        @Param("currencyId") currencyId: Long
    ): BigDecimal
}
