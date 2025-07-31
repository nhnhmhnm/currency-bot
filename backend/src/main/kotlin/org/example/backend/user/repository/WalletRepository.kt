package org.example.backend.user.repository

import org.example.backend.user.domain.Wallet
import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Long> {
    fun findByUserIdAndCurrencyId(userId: Long, currencyId: Long): Wallet?
}
